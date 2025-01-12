package com.muditsahni.documentstore.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.cloud.tasks.v2.CloudTasksClient
import com.google.cloud.tasks.v2.HttpMethod
import com.google.cloud.tasks.v2.HttpRequest
import com.google.cloud.tasks.v2.Task
import com.google.protobuf.ByteString
import com.muditsahni.documentstore.config.documentparser.DocumentParserProperties
import com.muditsahni.documentstore.config.getObjectMapper
import com.muditsahni.documentstore.model.dto.request.UploadDocumentTask
import com.muditsahni.documentstore.model.entity.Collection
import com.muditsahni.documentstore.model.entity.User
import com.muditsahni.documentstore.model.enum.*
import com.muditsahni.documentstore.util.await
import mu.KotlinLogging
import org.apache.http.entity.ContentType.APPLICATION_JSON
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders.AUTHORIZATION
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
class CollectionsService(
    private val firestore: Firestore,
    private val storageService: StorageService,
    private val documentParserProperties: DocumentParserProperties,
    private val cloudTasksClient: CloudTasksClient,
    @Value("\${gcp.cloud-tasks.queue}") private val cloudTasksQueue: String
) {

    companion object {
        private val logger = KotlinLogging.logger {
            CollectionsService::class.java.name
        }
        private val objectMapper = getObjectMapper()
        val credentials: GoogleCredentials = GoogleCredentials.getApplicationDefault()
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
        // create collection id
        val collectionId = UUID.randomUUID()

        // Create collection object
        val collection = Collection(
            id = collectionId.toString(),
            name = collectionName,
            type = collectionType,
            createdBy = userId,
            status = CollectionStatus.RECIEVED,
            createdDate = System.currentTimeMillis(),
        )

        // Save collection to Firestore
        firestore
            .collection("tenants")
            .document(tenant.tenantId)
            .collection("collections")
            .document(collectionId.toString())
            .set(collection)
            .await()

        // link it to user
        val userRef = firestore
            .collection("tenants")
            .document(tenant.tenantId)
            .collection("users")
            .document(userId)
            .get()
            .await()

        val user = userRef.toObject(User::class.java) ?: throw IllegalStateException("User not found")

        user.collections.add(collectionId.toString())
        user.updatedDate = System.currentTimeMillis()
        user.updatedBy = userId

        // update user in firestore
        firestore
            .collection("tenants")
            .document(tenant.tenantId)
            .collection("users")
            .document(userId)
            .set(user)
            .await()

        return collection
    }

    private suspend fun uploadDocument(
        tenant: Tenant,
        userId: String,
        collection: Collection,
        document: MultipartFile
    ) {

        // create task for queue
        val task = UploadDocumentTask(
            collectionId = collection.id,
            tenantId = tenant.tenantId,
            userId = userId,
            fileType = FileType.PDF,
            fileSize = 0,
            file = document.bytes,
            fileName = document.originalFilename ?: throw IllegalArgumentException("File name not found"),
            uploadPath = "${tenant.tenantId}/${collection.id}/${document.originalFilename}",
            callbackUrl = ""
        )


        val taskBody = objectMapper.writeValueAsString(task)

        // Get auth token
        credentials.refreshIfExpired()
        val token = credentials.accessToken.tokenValue

        val documentParserUploadEndpoint = "${documentParserProperties.uri}/${documentParserProperties.version}/" +
                documentParserProperties.upload

        val uploadDocumentTask = Task.newBuilder()
            .setHttpRequest(
                HttpRequest.newBuilder()
                    .setHttpMethod(HttpMethod.POST)
                    .setUrl(documentParserUploadEndpoint)
                    .putHeaders(AUTHORIZATION, "Bearer $token")
                    .putHeaders(CONTENT_TYPE, APPLICATION_JSON.toString())
                    .setBody(ByteString.copyFromUtf8(taskBody))
                    .build()
            )
            .build()

        cloudTasksClient.createTask(cloudTasksQueue, uploadDocumentTask)

    }

    private suspend fun uploadDocuments(
        tenant: Tenant,
        userId: String,
        collection: Collection,
        documents: List<MultipartFile>
    ): List<String> {

        // Upload documents to storage
        val uploadPaths = storageService.uploadFiles(
            tenantId = tenant.tenantId,
            jobId = collection.id,
            files = documents
        )

        // Update collection with document paths
        val updatedCollection = collection.copy(
            documents = uploadPaths.associateWith { DocumentStatus.UPLOADED }.toMutableMap(),
            updatedBy = userId,
            updatedDate = System.currentTimeMillis()
        )

        // Update collection in firestore
        firestore
            .collection("tenants")
            .document(tenant.tenantId)
            .collection("collections")
            .document(collection.id)
            .set(updatedCollection)
            .await()

        return uploadPaths
    }

    suspend fun createCollection(
        userId: String,
        tenant: Tenant,
        collectionName: String,
        collectionType: CollectionType,
        documents: List<MultipartFile>
    ): Collection {

        // Create collection
        val collection = initiateCollectionCreation(userId, tenant, collectionName, collectionType)

        // upload documents
        val documentPaths = uploadDocuments(tenant, userId, collection, documents)


        // Return collection
        return collection
    }
}