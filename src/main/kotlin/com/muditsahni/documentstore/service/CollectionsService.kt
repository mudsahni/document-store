package com.muditsahni.documentstore.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.Timestamp
import com.google.cloud.firestore.Firestore
import com.google.cloud.tasks.v2.CloudTasksClient
import com.muditsahni.documentstore.config.documentparser.DocumentParserProperties
import com.muditsahni.documentstore.config.getObjectMapper
import com.muditsahni.documentstore.exception.DocumentError
import com.muditsahni.documentstore.exception.throwable.CollectionNotFoundException
import com.muditsahni.documentstore.model.dto.response.CreateCollectionResponse
import com.muditsahni.documentstore.model.entity.Collection
import com.muditsahni.documentstore.model.entity.SignedUrlResponse
import com.muditsahni.documentstore.model.entity.StorageEvent
import com.muditsahni.documentstore.model.entity.toCreateCollectionReponse
import com.muditsahni.documentstore.model.enum.*
import com.muditsahni.documentstore.util.CollectionHelper
import com.muditsahni.documentstore.util.DocumentHelper
import com.muditsahni.documentstore.util.UserHelper
import com.muditsahni.documentstore.util.await
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class CollectionsService(
    private val googleCredentials: GoogleCredentials,
    private val firestore: Firestore,
    private val documentParserProperties: DocumentParserProperties,
    private val cloudTasksClient: CloudTasksClient,
    private val storageService: StorageService,
    @Value("\${spring.application.name}") private val applicationName: String,
    @Value("\${spring.cloud.gcp.project-number}") private val projectNumber: String,
    @Value("\${gcp.project-id}") private val gcpProjectId: String,
    @Value("\${gcp.region}") private val gcpRegion: String,
    @Value("\${spring.application.region}") private val applicationRegion: String,
    @Value("\${gcp.cloud-tasks.location}") private val cloudTasksRegion: String,
    @Value("\${gcp.cloud-tasks.queue}") private val cloudTasksQueue: String
) {

    companion object {
        private val logger = KotlinLogging.logger {
            CollectionsService::class.java.name
        }
        private val objectMapper = getObjectMapper()
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

    private suspend fun initiateCollectionCreation(
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

    suspend fun processStorageEvent(
        event: StorageEvent
    ) {
        logger.info("Processing storage event for file: ${event.name}")

        // Extract tenant and collection IDs from path
        val pathParts = event.name.split("/")
        val tenantId = pathParts[0]
        val collectionId = pathParts[1]
        val documentId = pathParts[2]
        val fileName = pathParts[3]

        // Process the uploaded file
        updateCollectionAndDocument(
            Tenant.fromTenantId(tenantId),
            collectionId,
            documentId,
            DocumentStatus.UPLOADED
        )
    }

    suspend fun updateCollectionAndDocument(
        tenant: Tenant,
        collectionId: String,
        documentId: String,
        documentStatus: DocumentStatus
    ) {
        logger.info("Updating collection and document")

        // update document status
        DocumentHelper.updateDocumentStatus(
            firestore,
            tenant,
            documentId,
            documentStatus
        )

        logger.info("Document updated with status UPLOADED")

        CollectionHelper.updateCollectionDocuments(
            firestore,
            tenant,
            collectionId,
            documentId,
            documentStatus
        )

        logger.info("Collection updated with document status UPLOADED")

    }

    suspend fun createCollection(
        userId: String,
        tenant: Tenant,
        collectionName: String,
        collectionType: CollectionType,
        documents: Map<String, String>,
    ): CreateCollectionResponse {

        logger.info("Creating collection for user $userId")
        // Create collection
        val collection = initiateCollectionCreation(userId, tenant, collectionName, collectionType)

        // create documents in firestore and signed urls for documents
        val documentIdsWithSignedUrlResponse = createAndSaveDocumentsForUpload(userId, tenant, collection.id, documents)

        // Return collection
        return collection.toCreateCollectionReponse(documentIdsWithSignedUrlResponse)
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

    suspend fun createAndSaveDocumentsForUpload(
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


}

