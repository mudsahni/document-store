package com.muditsahni.documentstore.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.Timestamp
import com.google.cloud.firestore.Firestore
import com.google.cloud.tasks.v2.CloudTasksClient
import com.muditsahni.documentstore.config.documentparser.DocumentParserProperties
import com.muditsahni.documentstore.config.getObjectMapper
import com.muditsahni.documentstore.exception.DocumentError
import com.muditsahni.documentstore.exception.MajorErrorCode
import com.muditsahni.documentstore.exception.MinorErrorCode
import com.muditsahni.documentstore.exception.throwable.CollectionCreationError
import com.muditsahni.documentstore.model.enum.CollectionStatus
import com.muditsahni.documentstore.model.enum.CollectionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import com.muditsahni.documentstore.model.entity.Collection
import com.muditsahni.documentstore.model.entity.PromptTemplate
import com.muditsahni.documentstore.model.entity.SignedUrlResponse
import com.muditsahni.documentstore.model.entity.document.Document
import com.muditsahni.documentstore.model.entity.document.StructuredData
import com.muditsahni.documentstore.model.entity.toCollectionStatusEvent
import com.muditsahni.documentstore.model.enum.DocumentStatus
import com.muditsahni.documentstore.model.enum.DocumentType
import com.muditsahni.documentstore.model.enum.Tenant
import com.muditsahni.documentstore.model.event.CollectionStatusEvent
import com.muditsahni.documentstore.respository.DocumentCreate
import com.muditsahni.documentstore.respository.DocumentUpdate
import com.muditsahni.documentstore.respository.FirestoreHelper
import com.muditsahni.documentstore.util.CloudTasksHelper
import com.muditsahni.documentstore.util.CollectionHelper
import org.springframework.beans.factory.annotation.Qualifier
import java.util.UUID

abstract class CollectionService(
    protected val eventStreamService: EventStreamService,
    protected val scope: CoroutineScope,
    protected val googleCredentials: GoogleCredentials,
    protected val firestore: Firestore,
    protected val documentParserProperties: DocumentParserProperties,
    protected val cloudTasksClient: CloudTasksClient,
    protected val storageService: StorageService,
    @Qualifier("InvoicePromptTemplate") protected val invoiceParsingPromptTemplate: PromptTemplate,
    @Value("\${spring.application.name}") protected val applicationName: String,
    @Value("\${spring.cloud.gcp.project-number}") protected val projectNumber: String,
    @Value("\${gcp.project-id}") protected val gcpProjectId: String,
    @Value("\${gcp.region}") protected val gcpRegion: String,
    @Value("\${spring.application.region}") protected val applicationRegion: String,
    @Value("\${gcp.cloud-tasks.location}") protected val cloudTasksRegion: String,
    @Value("\${gcp.cloud-tasks.queue}") protected val cloudTasksQueue: String

) {
    companion object {
        protected val logger = KotlinLogging.logger {
            CollectionService::class.java.name
        }
        protected val objectMapper = getObjectMapper()

    }

    fun emitCollectionStatusEvent(collection: Collection) {
        try {
            eventStreamService.emitEvent(collection.toCollectionStatusEvent())
        } catch (e: Exception) {
            eventStreamService.errorStream(
                CollectionStatusEvent(
                    id = collection.id,
                    name = collection.name,
                    status = CollectionStatus.FAILED,
                    type = collection.type,
                    error = MajorErrorCode.toCollectionError(MajorErrorCode.GEN_MAJ_EVT_001)
                )
            )
            logger.error("Error while creating collection status events stream", e)
        }
    }

    fun sendTaskToCloudTaskQueue(task: String, endpoint: String) {
        googleCredentials.refreshIfExpired()
        logger.info("Token fetched")

        val documentParserEndpoint = "https://" +
                "${documentParserProperties.name}-${documentParserProperties.projectNumber}." +
                "${documentParserProperties.region}.run.app/" +
                "${documentParserProperties.uri}/${documentParserProperties.version}/" +
                endpoint + "?ai=GEMINI"

        logger.info("Document parser upload endpoint fetched: $documentParserEndpoint")

        CloudTasksHelper.createNewTask(
            cloudTasksClient,
            gcpProjectId,
            gcpRegion,
            cloudTasksQueue,
            documentParserEndpoint,
            task
        )
        logger.info("Task created successfully")
    }

    protected suspend fun createCollectionAndDocumentsUpdateUserAndEmitEvent(
        collection: Collection,
        createBatch: List<DocumentCreate>,
        updateBatch: List<DocumentUpdate>,
    ) {
        val batchCreationResponse = FirestoreHelper.batchCreateDocuments(firestore, createBatch)

        logger.info(
            "Batch creation response: ${batchCreationResponse}"
        )

        if (batchCreationResponse.failures.isNotEmpty() || batchCreationResponse.failureCount > 0) {
            logger.error("Error creating collection and documents")
            eventStreamService.errorStream(CollectionStatusEvent(
                id = batchCreationResponse.failures.first().collectionPath,
                name = "Collection",
                status = CollectionStatus.FAILED,
                type = CollectionType.INVOICE,
                error = MajorErrorCode.toCollectionError(MajorErrorCode.GEN_MAJ_COL_001)
            ))
            throw CollectionCreationError(MajorErrorCode.GEN_MAJ_COL_001, "Error creating collection and documents.")
        }

        logger.info("Created collection and documents batch.")

        val batchUpdateResponse = FirestoreHelper.batchUpdateDocuments(firestore, updateBatch)

        if (batchUpdateResponse.failures.isNotEmpty() || batchUpdateResponse.failureCount > 0) {
            logger.error("Error updating user")
            eventStreamService.errorStream(CollectionStatusEvent(
                id = updateBatch.first().documentId,
                name = "User",
                status = CollectionStatus.FAILED,
                type = CollectionType.INVOICE,
                error = MajorErrorCode.toCollectionError(MajorErrorCode.GEN_MAJ_USR_001)
            ))
            throw CollectionCreationError(MajorErrorCode.GEN_MAJ_COL_001, "Error creating collection and documents. User could not be updated.")
        }

        eventStreamService.createEventStream(collection.id)
        emitCollectionStatusEvent(collection)
    }

    protected suspend fun createSignedUrlsAndAddDocumentsToBatch(
        userId: String,
        collectionId: String,
        tenant: Tenant,
        batch: MutableList<DocumentCreate>,
        documents: Map<String, String>,
    ): Map<String, Pair<DocumentStatus, SignedUrlResponse?>> {
        val documentMapWithSignedUrls = mutableMapOf<String, Pair<DocumentStatus, SignedUrlResponse?>>()

        documents.forEach {
            val documentId = UUID.randomUUID().toString()
            var error: DocumentError? = null
            var signedUrl: SignedUrlResponse? = null
            try {
                signedUrl = storageService.getSignedUrlForDocumentUpload(
                    documentId,
                    "${tenant.tenantId}/${collectionId}/${documentId}",
                    it.key,
                    it.value
                )
            } catch (e: Exception) {
                logger.error(e) { "Error generating signed URL for document: ${it.key}" }
                error = MinorErrorCode.toDocumentError(MinorErrorCode.GEN_MIN_DOC_001)
            }

            val document = Document(
                id = documentId,
                name = it.key,
                path = it.value,
                type = DocumentType.INVOICE,
                collectionId = collectionId,
                status = DocumentStatus.PENDING,
                error = error,
                data = StructuredData(
                    raw = null,
                    structured = null
                ),
                private = false,
                createdBy = userId,
                createdAt = Timestamp.now().seconds,
                updatedBy = null,
                updatedAt = null
            )

            documentMapWithSignedUrls[documentId] = Pair(document.status, signedUrl)

            batch.add(DocumentCreate("tenants/${tenant.tenantId}/documents", document.id, document))
        }

        return documentMapWithSignedUrls
    }

    protected suspend fun updateCollectionForUploadedDocument(
        tenantId: String,
        collectionId: String,
        documentId: String
    ): Collection {
        val collection = CollectionHelper.getCollection(firestore, collectionId, Tenant.fromTenantId(tenantId))


        if (collection.status == CollectionStatus.RECEIVED) {
            collection.status = CollectionStatus.IN_PROGRESS
        }

        // Update document status
        if (!collection.documents.contains(documentId)) {
            throw CollectionCreationError(MajorErrorCode.GEN_MAJ_COL_002, "Error updating collection with uploaded document. Document not found.")
        }

        collection.documents[documentId] = DocumentStatus.UPLOADED

        val uploadedDocumentCount = collection.documents.count { it.value == DocumentStatus.UPLOADED }
        if (uploadedDocumentCount == collection.documents.size) {
            logger.info("All documents uploaded for collection: $collectionId")
            collection.status = CollectionStatus.DOCUMENTS_UPLOAD_COMPLETE
        }

        return collection
    }


}