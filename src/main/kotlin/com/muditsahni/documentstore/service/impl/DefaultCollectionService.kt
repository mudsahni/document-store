package com.muditsahni.documentstore.service.impl

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.Timestamp
import com.google.cloud.firestore.Firestore
import com.google.cloud.tasks.v2.CloudTasksClient
import com.muditsahni.documentstore.config.documentparser.DocumentParserProperties
import com.muditsahni.documentstore.config.getObjectMapper
import com.muditsahni.documentstore.exception.CollectionError
import com.muditsahni.documentstore.exception.CollectionErrorType
import com.muditsahni.documentstore.exception.DocumentError
import com.muditsahni.documentstore.exception.DocumentErrorType
import com.muditsahni.documentstore.exception.MajorErrorCode
import com.muditsahni.documentstore.exception.MinorErrorCode
import com.muditsahni.documentstore.exception.throwable.CollectionCreationError
import com.muditsahni.documentstore.model.cloudtasks.DocumentProcessingTask
import com.muditsahni.documentstore.model.dto.request.ProcessDocumentCallbackRequest
import com.muditsahni.documentstore.model.dto.response.CreateCollectionResponse
import com.muditsahni.documentstore.model.entity.Collection
import com.muditsahni.documentstore.model.entity.PromptTemplate
import com.muditsahni.documentstore.model.entity.SignedUrlResponse
import com.muditsahni.documentstore.model.entity.StorageEvent
import com.muditsahni.documentstore.model.entity.document.Document
import com.muditsahni.documentstore.model.entity.document.StructuredData
import com.muditsahni.documentstore.model.entity.document.type.InvoiceWrapper
import com.muditsahni.documentstore.model.entity.toCollectionStatusEvent
import com.muditsahni.documentstore.model.entity.toCreateCollectionReponse
import com.muditsahni.documentstore.model.enum.*
import com.muditsahni.documentstore.model.event.CollectionStatusEvent
import com.muditsahni.documentstore.respository.BatchCreateResult
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
import com.muditsahni.documentstore.util.UserHelper
import com.muditsahni.documentstore.util.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.FileNotFoundException
import java.util.UUID
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
        private val logger = KotlinLogging.logger {
            DefaultCollectionService::class.java.name
        }

        private val objectMapper = getObjectMapper()

    }

//    suspend fun emitFailureEvent(
//        batchUpdateResult: BatchUpdateResult
//    ) {
//        logger.error("Error updating collection and documents")
//        eventStreamService.errorStream(CollectionStatusEvent(
//            id = batchUpdateResult.failures.first().collectionPath,
//            name = "Collection",
//            status = CollectionStatus.FAILED,
//            type = CollectionType.INVOICE,
//            error = CollectionError(
//                batchUpdateResult.failures.joinToString(", ") { it.error.toString() },
//                CollectionErrorType.COLLECTION_UPDATE_ERROR
//            )
//        ))
//    }

//    suspend fun emitFailureEvent(
//        batchCreateResult: BatchCreateResult
//    ) {
//        logger.error("Error creating collection and documents")
//        eventStreamService.errorStream(CollectionStatusEvent(
//            id = batchCreateResult.failures.first().collectionPath,
//            name = "Collection",
//            status = CollectionStatus.FAILED,
//            type = CollectionType.INVOICE,
//            error = CollectionError(
//                batchCreateResult.failures.joinToString(", ") { it.error.toString() },
//                CollectionErrorType.COLLECTION_CREATION_ERROR
//            )
//        ))
//    }

    suspend fun createCollectionAndDocuments(
        userId: String,
        tenant: Tenant,
        collectionName: String,
        collectionType: CollectionType,
        documents: Map<String, String>
    ): CreateCollectionResponse {

        logger.info("Creating collection and documents for user $userId")

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

        return collection.toCreateCollectionReponse(signedUrls)

    }

    suspend fun processStorageEvent(event: StorageEvent) {
        logger.info("Processing storage event for file: ${event.name}")

        // Extract tenant and collection IDs from path
        val pathParts = event.name.split("/")
        val tenantId = pathParts[0]
        val collectionId = pathParts[1]
        val documentId = pathParts[2]
        val fileName = pathParts[3]

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

        logger.info("Batch update result: $batchUpdateResult")
        if (batchUpdateResult.failures.isNotEmpty() || batchUpdateResult.failureCount > 0) {
            logger.error("Error updating document status")
            eventStreamService.errorStream(CollectionStatusEvent(
                id = collectionId,
                name = collection.name,
                status = CollectionStatus.FAILED,
                type = collection.type,
                error = MajorErrorCode.toCollectionError(MajorErrorCode.GEN_MAJ_COL_002)
            ))
            throw CollectionCreationError(MajorErrorCode.GEN_MAJ_COL_002, "Error updating document status.")
        }

        logger.info("Emitting collection status event for collection $collectionId")
        emitCollectionStatusEvent(collection)

        logger.info("Initiating document parsing for document: ${documentId}")
        processDocument(tenantId, collectionId, documentId, fileName)
    }

    suspend fun processDocument(tenantId: String, collectionId: String, documentId: String, fileName: String) {

        // get signed url link for document

        var downloadableLink: String = ""

        try {
            logger.info("Processing document: $documentId")
            downloadableLink = storageService.getFileUrl(tenantId, collectionId, documentId, fileName)
            logger.info("Got signed url for document: $downloadableLink")
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
        // create a cloud task for the python api to process the document through openai/anthropic/gemini/llama

        try {
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
            // send to cloud tasks queue
            val taskBody = objectMapper.writeValueAsString(task)

            sendTaskToCloudTaskQueue(taskBody, documentParserProperties.process)
            logger.info("Sent task to cloud tasks queue for document: $documentId")

        } catch (e: Exception) {
            logger.error(e) { "Error sending task to cloud tasks queue for document: $documentId" }
            eventStreamService.errorStream(CollectionStatusEvent(
                id = documentId,
                name = "Document could not be processed.",
                status = CollectionStatus.FAILED,
                type = CollectionType.INVOICE,
                error = MajorErrorCode.toCollectionError(MajorErrorCode.INV_MAJ_DOC_001)
            ))
            throw e
        }

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
            logger.error("Error updating document status")
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
    }

    suspend fun receiveProcessedDocument(
        tenant: Tenant,
        collectionId: String,
        processDocumentCallbackRequest: ProcessDocumentCallbackRequest
    ): Document = coroutineScope {
        try {
            // get processed document

            logger.info("Processing document callback: ${processDocumentCallbackRequest.id}")
            // update document
            val document = DocumentHelper.getDocument(firestore, processDocumentCallbackRequest.id, tenant)
            if (processDocumentCallbackRequest.error != null) {
                logger.error("There was an error parsing the document: ${processDocumentCallbackRequest.id}. " +
                        "Error: ${processDocumentCallbackRequest.error}")
                document.status = DocumentStatus.ERROR
                document.error = MajorErrorCode.toDocumentError(MajorErrorCode.INV_MAJ_DOC_001)
            } else {
                logger.info("Updating document with parsed data: ${processDocumentCallbackRequest.id}")
                document.status = DocumentStatus.PARSED
                document.data = StructuredData(
                    raw = processDocumentCallbackRequest.parsedData,
                    structured = null
                )
                logger.debug("Parsed data: ${processDocumentCallbackRequest.parsedData}")
            }

            logger.info("Updating document with parsed data: ${processDocumentCallbackRequest.id}")

            val batchUpdateResponse = FirestoreHelper.batchUpdateDocuments(
                firestore,
                listOf(
                    DocumentUpdate(
                        "tenants/${tenant.tenantId}/documents",
                        processDocumentCallbackRequest.id,
                        mapOf(
                            "status" to FieldUpdate.Set(document.status),
                            "data" to FieldUpdate.Set(document.data),
                            "error" to FieldUpdate.Set(document.error)
                        )
                    ),
                    DocumentUpdate(
                        "tenants/${tenant.tenantId}/collections",
                        collectionId,
                        mapOf(
                            "documents" to FieldUpdate.MapUpdate(
                                processDocumentCallbackRequest.id,
                                document.status
                            )
                        )
                    )
                )
            )

            if (batchUpdateResponse.failures.isNotEmpty() || batchUpdateResponse.failureCount > 0) {
                logger.error("Error updating document status")
                eventStreamService.errorStream(CollectionStatusEvent(
                    id = collectionId,
                    name = "Collection",
                    status = CollectionStatus.FAILED,
                    type = CollectionType.INVOICE,
                    error = MajorErrorCode.toCollectionError(MajorErrorCode.GEN_MAJ_COL_002)
                ))
                throw CollectionCreationError(MajorErrorCode.GEN_MAJ_COL_002, "Error updating document status.")
            }

            logger.info("Updating document with parsed data: ${processDocumentCallbackRequest.id}")


            document.data?.structured = objectMapper.readValue<InvoiceWrapper>(
                processDocumentCallbackRequest.parsedData,
                InvoiceWrapper::class.java
            )
            document.status = DocumentStatus.STRUCTURED
            val batchUpdateResponse2 = FirestoreHelper.batchUpdateDocuments(
                firestore,
                listOf(
                    DocumentUpdate(
                        "tenants/${tenant.tenantId}/documents",
                        processDocumentCallbackRequest.id,
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
                                processDocumentCallbackRequest.id,
                                document.status
                            )
                        )
                    )
                )
            )

            if (batchUpdateResponse2.failures.isNotEmpty() || batchUpdateResponse2.failureCount > 0) {
                logger.error("Error updating document status")
                eventStreamService.errorStream(CollectionStatusEvent(
                    id = collectionId,
                    name = "Collection",
                    status = CollectionStatus.FAILED,
                    type = CollectionType.INVOICE,
                    error = MajorErrorCode.toCollectionError(MajorErrorCode.GEN_MAJ_COL_002)
                ))
                throw CollectionCreationError(MajorErrorCode.GEN_MAJ_COL_002, "Error updating document status.")
            }

            logger.info("Completed processing document: ${processDocumentCallbackRequest.id}")

            val collection: Collection = CollectionHelper.getCollection(firestore, collectionId, tenant)

            logger.info("This is the collection as json: ${objectMapper.writeValueAsString(collection)}")

            val areAllDocumentsStructured = collection.documents.values.all { it == DocumentStatus.STRUCTURED }

            logger.info("Checking if all documents are structured for collection: $collectionId")
            if (areAllDocumentsStructured) {
                collection.status = CollectionStatus.COMPLETED
                CollectionHelper.saveCollection(firestore, tenant, collection)
                logger.info("Stream completed for collection: $collectionId")
                emitCollectionStatusEvent(
                    CollectionHelper.getCollection(firestore, collectionId, tenant)
                )
            }

            logger.info("Emitting collection status event for collection $collectionId")
            emitCollectionStatusEvent(collection)
            eventStreamService.completeStream(collectionId)
            document
        } catch (e: Exception) {
            logger.error(e) { "Error processing document callback. Error: ${e.message}" }
            // You might want to emit an error event here
            eventStreamService.errorStream(CollectionStatusEvent(
                id = processDocumentCallbackRequest.id,
                name = "Document",
                status = CollectionStatus.FAILED,
                type = CollectionType.INVOICE,
                error = MajorErrorCode.toCollectionError(MajorErrorCode.INV_MAJ_DOC_001)
            ))
            throw e
        }
    }

    suspend fun validateProcessedDocument() {

        // validate processed document

        // emit event

        // complete stream
    }

    suspend fun getAllCollections(
        userId: String,
        tenant: Tenant,
        orgWide: Boolean
    ): List<Collection> {

        val collectionsRef = firestore
            .collection("tenants")
            .document(tenant.tenantId)
            .collection("collections")

        logger.info { "Fetched collections ref for tenant ${tenant.tenantId}" }

        // Build query based on viewAll parameter and permissions
        val query = when {
            orgWide -> collectionsRef  // User wants to and can view all
            else -> collectionsRef.whereEqualTo("createdBy", userId)  // User sees only their collections
        }

        // Execute query and map results to Collection objects
        val collections = query
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                doc.toObject(Collection::class.java)
            }

        // Return collections
        return collections
    }


    suspend fun updateDocumentCollectionAndUserWithUploadedDocumentStatus(
        userId: String,
        tenant: Tenant,
        collectionId: String,
        documentFilePath: String?,
        documentId: String,
        status: DocumentStatus,
        error: DocumentError? = null,
    ) {
        logger.info("Updating collection with uploaded document for user $userId")

        // update document

        val document = DocumentHelper.getDocument(firestore, documentId, tenant)
        document.status = status
        document.path = documentFilePath
        document.error = error

        // update document status
        DocumentHelper.saveDocument(
            firestore,
            tenant,
            document
        )

        // update collection
        CollectionHelper.updateCollectionDocuments(
            firestore,
            tenant,
            collectionId,
            documentId,
            status
        )

        // update user
        UserHelper.updateUserDocuments(
            firestore,
            userId,
            tenant,
            documentId
        )
    }
}

