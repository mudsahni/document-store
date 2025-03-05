package com.muditsahni.documentstore.service.impl

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.Timestamp
import com.google.cloud.firestore.Firestore
import com.google.cloud.tasks.v2.CloudTasksClient
import com.muditsahni.documentstore.config.documentparser.DocumentParserProperties
import com.muditsahni.documentstore.config.getObjectMapper
import com.muditsahni.documentstore.exception.MajorErrorCode
import com.muditsahni.documentstore.exception.throwable.CollectionCreationError
import com.muditsahni.documentstore.model.cloudtasks.DocumentProcessingTask
import com.muditsahni.documentstore.model.dto.request.ProcessDocumentCallbackRequest
import com.muditsahni.documentstore.model.dto.response.CreateCollectionResponse
import com.muditsahni.documentstore.model.dto.response.GetCollectionWithDocumentsResponse
import com.muditsahni.documentstore.model.dto.response.InvoiceWrapperDTO
import com.muditsahni.documentstore.model.dto.response.toInvoiceWrapper
import com.muditsahni.documentstore.model.entity.Collection
import com.muditsahni.documentstore.model.entity.PromptTemplate
import com.muditsahni.documentstore.model.entity.StorageEvent
import com.muditsahni.documentstore.model.entity.document.Document
import com.muditsahni.documentstore.model.entity.document.StructuredData
import com.muditsahni.documentstore.model.entity.toCollection
import com.muditsahni.documentstore.model.entity.toCollectionStatusEvent
import com.muditsahni.documentstore.model.entity.toCreateCollectionResponse
import com.muditsahni.documentstore.model.entity.toGetCollectionWithDocumentsResponse
import com.muditsahni.documentstore.model.enum.*
import com.muditsahni.documentstore.model.event.CollectionStatusEvent
import com.muditsahni.documentstore.respository.BatchUpdateResult
import com.muditsahni.documentstore.respository.DocumentCreate
import com.muditsahni.documentstore.respository.DocumentUpdate
import com.muditsahni.documentstore.respository.FieldUpdate
import com.muditsahni.documentstore.respository.FirestoreHelper
import com.muditsahni.documentstore.service.CollectionService
import com.muditsahni.documentstore.service.EventStreamService
import com.muditsahni.documentstore.service.StorageService
import com.muditsahni.documentstore.util.CollectionHelper
import com.muditsahni.documentstore.util.DocumentHelper
import com.muditsahni.documentstore.util.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.FileNotFoundException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.mapNotNull
import kotlin.jvm.java
import kotlin.text.split

@Service
class DefaultCollectionService(
    eventStreamService: EventStreamService,
    scope: CoroutineScope,
    googleCredentials: GoogleCredentials,
    firestore: Firestore,
    documentParserProperties: DocumentParserProperties,
    cloudTasksClient: CloudTasksClient,
    storageService: StorageService,
    val documentService: DefaultDocumentService,
    val invoiceExportService: DefaultInvoiceExportService,
    @Qualifier("InvoicePromptTemplate") invoiceParsingPromptTemplate: PromptTemplate,
    @Value("\${spring.application.name}") applicationName: String,
    @Value("\${spring.cloud.gcp.project-number}") projectNumber: String,
    @Value("\${gcp.project-id}") gcpProjectId: String,
    @Value("\${gcp.region}") gcpRegion: String,
    @Value("\${spring.application.region}") applicationRegion: String,
    @Value("\${gcp.cloud-tasks.location}") cloudTasksRegion: String,
    @Value("\${gcp.cloud-tasks.queue}") cloudTasksQueue: String
): CollectionService(
    eventStreamService,
    scope,
    googleCredentials,
    firestore,
    documentParserProperties,
    cloudTasksClient,
    storageService,
    invoiceParsingPromptTemplate,
    applicationName,
    projectNumber,
    gcpProjectId,
    gcpRegion,
    applicationRegion,
    cloudTasksRegion,
    cloudTasksQueue
) {

    companion object {
        private val logger = KotlinLogging.logger {}
        private val objectMapper = getObjectMapper()

        // Cache for pending task tracking to avoid duplicate processing
        private val pendingTasks = ConcurrentHashMap<String, Long>()

        // Constants
        private const val TASK_EXPIRY_MS = 3600000L // 1 hour
    }

    suspend fun createCollectionAndDocuments(
        userId: String,
        tenant: Tenant,
        collectionName: String,
        collectionType: CollectionType,
        documents: Map<String, String>
    ): CreateCollectionResponse {

        logger.info { "Creating collection and documents for user $userId with ${documents.size} documents" }

        val batch: MutableList<DocumentCreate> = mutableListOf()

        // Create collection object
        val collection = Collection(
            id = UUID.randomUUID().toString(),
            name = collectionName,
            type = collectionType,
            createdBy = userId,
            status = CollectionStatus.RECEIVED,
            createdAt = Timestamp.now(),
        )

        val documentMapWithSignedUrls = createSignedUrlsAndAddDocumentsToBatch(userId, collection.id, tenant, batch, documents)
        val documentMap = documentMapWithSignedUrls.map { it.key to it.value.first }.toMap()
        val signedUrls = documentMapWithSignedUrls.map { it.key to it.value.second }.toMap()

        collection.documents = documentMap.toMutableMap()
        batch.add(DocumentCreate("tenants/${tenant.tenantId}/collections", collection.id, collection))

        val updateBatch = listOf(
            DocumentUpdate(
                "tenants/${tenant.tenantId}/users",
                userId,
                mapOf(
                    "collections" to FieldUpdate.ArrayUnion(listOf(collection.id)),
                    "documents" to FieldUpdate.ArrayUnion(documentMap.keys.toList())
                )
            )
        )

        createCollectionAndDocumentsUpdateUserAndEmitEvent(collection, batch, updateBatch)
        logger.info { "Collection ${collection.id} created successfully with ${documentMap.size} documents" }

        return collection.toCreateCollectionResponse(signedUrls)

    }

    suspend fun processStorageEvent(event: StorageEvent) {
        logger.info { "Processing storage event for file: ${event.name}" }

        try {
            // Extract tenant and collection IDs from path
            val pathParts = event.name.split("/")
            if (pathParts.size < 4) {
                logger.error { "Invalid storage event path: ${event.name}" }
                return
            }
            val tenantId = pathParts[0]
            val collectionId = pathParts[1]
            val documentId = pathParts[2]
            val fileName = pathParts[3]

            // Check if we're already processing this document
            val now = System.currentTimeMillis()
            val taskKey = "$tenantId:$documentId"

            if (pendingTasks.containsKey(taskKey)) {
                val lastProcessed = pendingTasks[taskKey] ?: 0L
                if (now - lastProcessed < TASK_EXPIRY_MS) {
                    logger.info { "Ignoring duplicate processing request for document: $documentId" }
                    return
                }
            }

            // Mark document as being processed
            pendingTasks[taskKey] = now

            val collection = updateCollectionForUploadedDocument(tenantId, collectionId, documentId)

            val batchUpdateResult: BatchUpdateResult = FirestoreHelper.batchUpdateDocuments(
                firestore,
                listOf(
                    DocumentUpdate(
                        "tenants/$tenantId/collections",
                        collectionId,
                        mapOf(
                            "documents" to FieldUpdate.Set(collection.documents),
                            "status" to FieldUpdate.Set(collection.status)
                        )
                    ),
                    DocumentUpdate(
                        "tenants/$tenantId/documents",
                        documentId,
                        mapOf(
                            "status" to FieldUpdate.Set(DocumentStatus.UPLOADED)
                        )
                    )
                )
            )

            logger.info { "Batch update result: $batchUpdateResult" }
            if (batchUpdateResult.failures.isNotEmpty() || batchUpdateResult.failureCount > 0) {
                logger.error { "Error updating document status" }
                eventStreamService.errorStream(
                    CollectionStatusEvent(
                        id = collectionId,
                        name = collection.name,
                        status = CollectionStatus.FAILED,
                        type = collection.type,
                        error = MajorErrorCode.toCollectionError(MajorErrorCode.GEN_MAJ_COL_002)
                    )
                )
                throw CollectionCreationError(MajorErrorCode.GEN_MAJ_COL_002, "Error updating document status.")
            }

            logger.info { "Emitting collection status event for collection $collectionId" }
            emitCollectionStatusEvent(collection)

            logger.info { "Initiating document parsing for document: $documentId" }
            processDocument(tenantId, collectionId, documentId, fileName)
        } catch (e: Exception) {
            logger.error(e) { "Error processing storage event: ${e.message}" }
            throw e
        }
    }

    suspend fun processDocument(tenantId: String, collectionId: String, documentId: String, fileName: String) {
        try {

            logger.info { "Processing document: $documentId" }

            val downloadableLink = try {
                storageService.getFileUrl(tenantId, collectionId, documentId, fileName)
            } catch (e: FileNotFoundException) {
                logger.error(e) { "Error getting signed url for document: $documentId" }
                eventStreamService.errorStream(CollectionStatusEvent(
                    id = documentId,
                    name = "Document signed url could not be found/created.",
                    status = CollectionStatus.FAILED,
                    type = CollectionType.INVOICE,
                    error = MajorErrorCode.toCollectionError(MajorErrorCode.GEN_MAJ_RES_001)
                ))
                throw e
            }

            logger.info { "Got signed url for document: $downloadableLink" }

            val prompt = "${invoiceParsingPromptTemplate.prompt}\n${invoiceParsingPromptTemplate.template}"

            val task = DocumentProcessingTask(
                id = documentId,
                collectionId = collectionId,
                tenantId = tenantId,
                name = fileName,
                url = downloadableLink,
                type = DocumentType.INVOICE,
                fileType = FileType.PDF,
                prompt = prompt,
                callbackUrl = "https://${applicationName}-${projectNumber}." +
                        "${applicationRegion}.run.app/api/v1/tenants/${tenantId}/collections/" +
                        "${collectionId}/documents/${documentId}/process"
            )

            // Send to cloud tasks queue
            val taskBody = objectMapper.writeValueAsString(task)
            sendTaskToCloudTaskQueue(taskBody, documentParserProperties.process)
            logger.info { "Sent task to cloud tasks queue for document: $documentId" }

            // Update document status
            val batchUpdateResult = FirestoreHelper.batchUpdateDocuments(
                firestore,
                listOf(
                    DocumentUpdate(
                        "tenants/$tenantId/documents",
                        documentId,
                        mapOf(
                            "status" to FieldUpdate.Set(DocumentStatus.IN_PROGRESS)
                        )
                    ),
                    DocumentUpdate(
                        "tenants/$tenantId/collections",
                        collectionId,
                        mapOf(
                            "documents" to FieldUpdate.MapUpdate(documentId, DocumentStatus.IN_PROGRESS),
                        )
                    )
                )
            )

            if (batchUpdateResult.failures.isNotEmpty() || batchUpdateResult.failureCount > 0) {
                logger.error { "Error updating document status" }
                eventStreamService.errorStream(CollectionStatusEvent(
                    id = collectionId,
                    name = "Collection",
                    status = CollectionStatus.FAILED,
                    type = CollectionType.INVOICE,
                    error = MajorErrorCode.toCollectionError(MajorErrorCode.GEN_MAJ_COL_002)
                ))
                throw CollectionCreationError(MajorErrorCode.GEN_MAJ_COL_002, "Error updating collection and document status.")
            }

            // emit event
            eventStreamService.emitEvent(
                CollectionHelper.getCollection(
                    firestore,
                    collectionId,
                    Tenant.fromTenantId(tenantId)
                ).toCollectionStatusEvent()
            )

        } catch (e: Exception) {
            logger.error(e) { "Error processing document $documentId: ${e.message}" }

            // Clean up pending task tracking on error
            pendingTasks.remove("$tenantId:$documentId")

            throw e
        }

    }

    suspend fun receiveProcessedDocument(
        tenant: Tenant,
        collectionId: String,
        processDocumentCallbackRequest: ProcessDocumentCallbackRequest
    ): Document = coroutineScope {
        val documentId = processDocumentCallbackRequest.id
        logger.info { "Processing document callback: $documentId" }

        try {
            // Remove from pending tasks
            pendingTasks.remove("${tenant.tenantId}:$documentId")

            // Get document
            val document = DocumentHelper.getDocument(firestore, documentId, tenant)

            // Handle error case
            if (processDocumentCallbackRequest.error != null) {
                logger.error { "Error parsing document $documentId: ${processDocumentCallbackRequest.error}" }

                document.status = DocumentStatus.ERROR
                document.error = MajorErrorCode.toDocumentError(MajorErrorCode.INV_MAJ_DOC_001)

                // Update document with error status
                val errorUpdateResult = updateDocumentWithErrorStatus(tenant, collectionId, document)

                if (errorUpdateResult.failures.isNotEmpty()) {
                    logger.error { "Failed to update document error status: $documentId" }
                    throw CollectionCreationError(MajorErrorCode.GEN_MAJ_COL_002, "Error updating document error status.")
                }

                // Emit error event
                emitCollectionStatusEvent(
                    CollectionHelper.getCollection(firestore, collectionId, tenant)
                )

                document
            }

            // Process successful parsing
            logger.info { "Processing parsed data for document: $documentId" }

            // 1. First update - mark as PARSED and store raw data
            document.status = DocumentStatus.PARSED
            document.data = StructuredData(
                raw = processDocumentCallbackRequest.parsedData,
                structured = null
            )

            val parsedUpdateResult = updateDocumentWithParsedData(tenant, collectionId, document)

            if (parsedUpdateResult.failures.isNotEmpty()) {
                logger.error { "Failed to update document with parsed data: $documentId" }
                throw CollectionCreationError(MajorErrorCode.GEN_MAJ_COL_002, "Error updating document parsed data.")
            }

            // 2. Process structured data
            try {
                val invoiceWrapperDTO = objectMapper.readValue(
                    processDocumentCallbackRequest.parsedData,
                    InvoiceWrapperDTO::class.java
                )

                document.data?.structured = invoiceWrapperDTO.toInvoiceWrapper()
                document.status = DocumentStatus.STRUCTURED

                val structuredUpdateResult = updateDocumentWithStructuredData(tenant, collectionId, document)

                if (structuredUpdateResult.failures.isNotEmpty()) {
                    logger.error { "Failed to update document with structured data: $documentId" }
                    throw CollectionCreationError(MajorErrorCode.GEN_MAJ_COL_002, "Error updating structured data.")
                }
            } catch (e: Exception) {
                logger.error(e) { "Error processing structured data for document $documentId" }
                document.status = DocumentStatus.PARSED // Keep as parsed since we have the raw data

                // Update status but don't fail the whole process
                FirestoreHelper.batchUpdateDocuments(
                    firestore,
                    listOf(
                        DocumentUpdate(
                            "tenants/${tenant.tenantId}/documents",
                            documentId,
                            mapOf("status" to FieldUpdate.Set(DocumentStatus.PARSED))
                        )
                    )
                )
            }

            logger.info { "Completed processing document: $documentId with status ${document.status}" }

            // 3. Update collection status
            val collection = CollectionHelper.getCollection(firestore, collectionId, tenant)
            checkAndUpdateCollectionStatus(tenant, collection)

            // 4. Validate the document
            validateProcessedDocument(tenant, collection, document)

            document

        } catch (e: Exception) {
            logger.error(e) { "Error processing document callback: ${e.message}" }

            // Emit error event but don't block progress
            eventStreamService.errorStream(CollectionStatusEvent(
                id = documentId,
                name = "Document",
                status = CollectionStatus.FAILED,
                type = CollectionType.INVOICE,
                error = MajorErrorCode.toCollectionError(MajorErrorCode.INV_MAJ_DOC_001)
            ))

            throw e
        }
    }

    private suspend fun checkAndUpdateCollectionStatus(tenant: Tenant, collection: Collection) {
        val areAllDocumentsProcessed = collection.documents.values.all {
            it == DocumentStatus.STRUCTURED || it == DocumentStatus.VALIDATED || it == DocumentStatus.PARSED
        }

        if (areAllDocumentsProcessed) {
            collection.status = CollectionStatus.COMPLETED
            CollectionHelper.saveCollection(firestore, tenant, collection)
            logger.info { "Parsing completed for collection: ${collection.id}" }
        }

        emitCollectionStatusEvent(collection)
    }

    suspend fun validateProcessedDocument(
        tenant: Tenant,
        collection: Collection,
        document: Document
    ) {
        // Validate processed document
        logger.info { "Validating processed document: ${document.id}" }

        try {
            val validationErrors = documentService.validateDocument(document)
            document.data?.errors = validationErrors
            document.status = DocumentStatus.VALIDATED
            collection.documents[document.id] = DocumentStatus.VALIDATED

            // Save document and collection
            DocumentHelper.saveDocument(firestore, tenant, document)
            CollectionHelper.saveCollection(firestore, tenant, collection)

            // Emit event
            eventStreamService.emitEvent(collection.toCollectionStatusEvent())

            // Check if all documents are validated
            val areAllDocumentsValidated = collection.documents.values.all { it == DocumentStatus.VALIDATED }

            // Complete stream if all documents are validated
            if (areAllDocumentsValidated) {
                logger.info { "Completing stream for collection: ${collection.id}" }
                eventStreamService.completeStream(collection.id)
            }

            // Add tags and save document again
            try {
                DocumentHelper.addTagsToInvoiceDocument(document)
                DocumentHelper.saveDocument(firestore, tenant, document)
            } catch (e: Exception) {
                // Log but don't fail if tag addition fails
                logger.error(e) { "Error adding tags to document ${document.id}" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error validating document ${document.id}" }
            // Don't throw - we still want to keep processing

            // Update document with error status
            document.status = DocumentStatus.ERROR
            document.error = MajorErrorCode.toDocumentError(MajorErrorCode.INV_MAJ_DOC_001)
            DocumentHelper.saveDocument(firestore, tenant, document)
        }
    }


    suspend fun getCollection(
        userId: String,
        tenant: Tenant,
        collectionId: String
    ): Collection {
        val collection = CollectionHelper.getCollection(firestore, collectionId, tenant)

        // Security check
        collection.createdBy.let {
            if (it != userId) {
                logger.warn { "User $userId attempted to access collection ${collection.id} created by ${collection.createdBy}" }
                throw CollectionCreationError(MajorErrorCode.GEN_MAJ_COL_001, "User does not have permission to view collection.")
            }
        }

        return collection
    }

    suspend fun getCollectionWithDocuments(
        userId: String,
        tenant: Tenant,
        collectionId: String
    ): GetCollectionWithDocumentsResponse {
        val collection = CollectionHelper.getCollection(firestore, collectionId, tenant)

        // Security check
        collection.createdBy.let {
            if (it != userId) {
                logger.warn { "User $userId attempted to access collection ${collection.id} created by ${collection.createdBy}" }
                throw CollectionCreationError(MajorErrorCode.GEN_MAJ_COL_001, "User does not have permission to view collection.")
            }
        }
        return collection.toGetCollectionWithDocumentsResponse(firestore)
    }

    suspend fun getCollectionDocumentsAsCSV(
        userId: String,
        tenant: Tenant,
        collectionId: String
    ): String {
        try {
            val collection = getCollectionWithDocuments(userId, tenant, collectionId)
            val invoices = collection.documents.values.mapNotNull { it.data?.raw }

            if (invoices.isEmpty()) {
                logger.warn { "No document data found for CSV export in collection $collectionId" }
                return "No data available for export"
            }

            return invoiceExportService.exportJsonToCsv(invoices)

        } catch (e: Exception) {
            logger.error(e) { "Error exporting collection documents to CSV" }
            throw e
        }
    }

    suspend fun getAllCollections(
        userId: String,
        tenant: Tenant,
        orgWide: Boolean
    ): List<Collection> = coroutineScope {
        logger.info { "Fetching collections for user $userId, tenant ${tenant.tenantId}, orgWide=$orgWide" }

        val collectionsRef = firestore
            .collection("tenants")
            .document(tenant.tenantId)
            .collection("collections")

        // Build query based on viewAll parameter and permissions
        val query = when {
            orgWide -> collectionsRef  // User wants to and can view all
            else -> collectionsRef.whereEqualTo("createdBy", userId)  // User sees only their collections
        }

        try {
            // Execute query and map results to Collection objects
            val collections = query
                .get()
                .await()
                .documents
                .mapNotNull { doc ->
                    doc.toCollection()
                }

            logger.info { "Fetched ${collections.size} collections for user $userId" }

            // Return collections
            collections
        } catch (e: Exception) {
            logger.error(e) { "Error fetching collections for user $userId" }
            emptyList()
        }
    }

    // Process multiple documents in parallel for better performance
    suspend fun processDocumentsBatch(
        tenantId: String,
        collectionId: String,
        documentIds: List<Pair<String, String>>
    ) = coroutineScope {
        logger.info { "Processing batch of ${documentIds.size} documents for collection $collectionId" }

        val tasks = documentIds.map { (documentId, fileName) ->
            async {
                try {
                    processDocument(tenantId, collectionId, documentId, fileName)
                } catch (e: Exception) {
                    logger.error(e) { "Error processing document $documentId in batch" }
                    // Continue with other documents
                }
            }
        }

        // Wait for all processing to complete
        tasks.awaitAll()

        logger.info { "Completed batch processing for collection $collectionId" }
    }


    private suspend fun updateDocumentWithErrorStatus(
        tenant: Tenant,
        collectionId: String,
        document: Document
    ): BatchUpdateResult {
        return FirestoreHelper.batchUpdateDocuments(
            firestore,
            listOf(
                DocumentUpdate(
                    "tenants/${tenant.tenantId}/documents",
                    document.id,
                    mapOf(
                        "status" to FieldUpdate.Set(document.status),
                        "error" to FieldUpdate.Set(document.error)
                    )
                ),
                DocumentUpdate(
                    "tenants/${tenant.tenantId}/collections",
                    collectionId,
                    mapOf(
                        "documents" to FieldUpdate.MapUpdate(
                            document.id,
                            document.status
                        )
                    )
                )
            )
        )
    }

    private suspend fun updateDocumentWithParsedData(
        tenant: Tenant,
        collectionId: String,
        document: Document
    ): BatchUpdateResult {
        return FirestoreHelper.batchUpdateDocuments(
            firestore,
            listOf(
                DocumentUpdate(
                    "tenants/${tenant.tenantId}/documents",
                    document.id,
                    mapOf(
                        "status" to FieldUpdate.Set(document.status),
                        "data" to FieldUpdate.Set(document.data)
                    )
                ),
                DocumentUpdate(
                    "tenants/${tenant.tenantId}/collections",
                    collectionId,
                    mapOf(
                        "documents" to FieldUpdate.MapUpdate(
                            document.id,
                            document.status
                        )
                    )
                )
            )
        )
    }

    private suspend fun updateDocumentWithStructuredData(
        tenant: Tenant,
        collectionId: String,
        document: Document
    ): BatchUpdateResult {
        return FirestoreHelper.batchUpdateDocuments(
            firestore,
            listOf(
                DocumentUpdate(
                    "tenants/${tenant.tenantId}/documents",
                    document.id,
                    mapOf(
                        "data" to FieldUpdate.Set(document.data),
                        "status" to FieldUpdate.Set(document.status)
                    )
                ),
                DocumentUpdate(
                    "tenants/${tenant.tenantId}/collections",
                    collectionId,
                    mapOf(
                        "documents" to FieldUpdate.MapUpdate(
                            document.id,
                            document.status
                        )
                    )
                )
            )
        )
    }


}

