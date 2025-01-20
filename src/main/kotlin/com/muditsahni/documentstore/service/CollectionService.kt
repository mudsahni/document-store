package com.muditsahni.documentstore.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.Timestamp
import com.google.cloud.firestore.Firestore
import com.google.cloud.tasks.v2.CloudTasksClient
import com.muditsahni.documentstore.config.documentparser.DocumentParserProperties
import com.muditsahni.documentstore.config.getObjectMapper
import com.muditsahni.documentstore.exception.CollectionError
import com.muditsahni.documentstore.exception.CollectionErrorType
import com.muditsahni.documentstore.model.entity.SignedUrlResponse
import com.muditsahni.documentstore.model.enum.CollectionStatus
import com.muditsahni.documentstore.model.enum.CollectionType
import com.muditsahni.documentstore.model.enum.DocumentStatus
import com.muditsahni.documentstore.model.enum.DocumentType
import com.muditsahni.documentstore.model.enum.Tenant
import com.muditsahni.documentstore.util.CollectionHelper
import com.muditsahni.documentstore.util.DocumentHelper
import com.muditsahni.documentstore.util.UserHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import java.util.UUID
import com.muditsahni.documentstore.model.entity.Collection
import com.muditsahni.documentstore.model.entity.toCollectionStatusEvent
import com.muditsahni.documentstore.model.event.CollectionStatusEvent
import com.muditsahni.documentstore.util.CloudTasksHelper

abstract class CollectionService(
    protected val eventStreamService: EventStreamService,
    protected val scope: CoroutineScope,
    protected val googleCredentials: GoogleCredentials,
    protected val firestore: Firestore,
    protected val documentParserProperties: DocumentParserProperties,
    protected val cloudTasksClient: CloudTasksClient,
    protected val storageService: StorageService,
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

    protected suspend fun initiateCollectionCreation(
        userId: String,
        tenant: Tenant,
        collectionName: String,
        collectionType: CollectionType
    ): Collection {
        logger.info("Initiating collection creation for user $userId")
        // create collection id
        val collectionId = UUID.randomUUID()

        // Create collection object
        val collection = Collection(
            id = collectionId.toString(),
            name = collectionName,
            type = collectionType,
            createdBy = userId,
            status = CollectionStatus.RECEIVED,
            createdAt = Timestamp.now(),
        )

        logger.info("Collection object created")

        // Save collection to Firestore
        CollectionHelper.saveCollection(firestore, tenant, collection)

        // link it to user
        // add collection id to user collections
        UserHelper.updateUserCollections(
            firestore,
            userId,
            tenant,
            collectionId.toString()
        )

        return collection
    }

    protected suspend fun createAndSaveDocumentsWithSignedUrlsForUpload(
        userId: String,
        tenant: Tenant,
        collectionId: String,
        documents: Map<String, String>
    ): Map<String, SignedUrlResponse> {

        val documentIdsWithSignedUrls = mutableMapOf<String, SignedUrlResponse>()
        val documentIdsWithStatus = mutableMapOf<String, DocumentStatus>()

        documents.forEach {

            // create document object
            val document = DocumentHelper.createDocumentObject(
                userId = userId,
                name = it.key, // document name
                collectionId = collectionId,
                tenant = tenant,
                filePath = "${tenant.tenantId}/${collectionId}/${it.key}",
                type = DocumentType.INVOICE,
                status = DocumentStatus.PENDING
            )

            // save document
            DocumentHelper.saveDocument(firestore, tenant, document)
            documentIdsWithStatus[document.id] = DocumentStatus.PENDING
            documentIdsWithSignedUrls[document.id] = storageService.getSignedUrlForDocumentUpload(
                document.id,
                "${tenant.tenantId}/${collectionId}/${document.id}",
                it.key,
                it.value
            )
        }

        // update collection
        CollectionHelper.updateCollectionDocuments(
            firestore,
            tenant,
            collectionId,
            documentIdsWithStatus
        )

        // update user
        UserHelper.updateUserDocuments(
            firestore,
            userId,
            tenant,
            documentIdsWithStatus.keys.toList()
        )

        return documentIdsWithSignedUrls

    }

    suspend fun updateCollectionAndDocument(
        tenant: Tenant,
        collectionId: String,
        documentId: String,
        documentStatus: DocumentStatus
    ): Collection {
        logger.info("Updating collection and document")

        // update document status
        DocumentHelper.updateDocumentStatus(
            firestore,
            tenant,
            documentId,
            documentStatus
        )

        logger.info("Document updated with status UPLOADED")

        // update document status in collection
        val collection = CollectionHelper.updateCollectionDocuments(
            firestore,
            tenant,
            collectionId,
            documentId,
            documentStatus
        )

        logger.info("Collection updated with document status UPLOADED")

        return collection
    }

    fun emitCollectionStatusEvent(collection: Collection) {
        scope.launch {
            try {
                eventStreamService.emitEvent(collection.toCollectionStatusEvent())
            } catch (e: Exception) {
                eventStreamService.emitEvent(
                    CollectionStatusEvent(
                        id = collection.id,
                        name = collection.name,
                        status = CollectionStatus.FAILED,
                        type = collection.type,
                        error = CollectionError(
                            "Error while creating collection status events stream",
                            CollectionErrorType.EVENT_STREAM_ERROR
                        )
                    )
                )
                logger.error("Error while creating collection status events stream", e)
            }
        }
    }

    fun sendTaskToCloudTaskQueue(task: String, endpoint: String) {
        googleCredentials.refreshIfExpired()
        logger.info("Token fetched")

        val documentParserEndpoint = "https://" +
                "${documentParserProperties.name}-${documentParserProperties.projectNumber}." +
                "${documentParserProperties.region}.run.app/" +
                "${documentParserProperties.uri}/${documentParserProperties.version}/" +
                endpoint

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


}