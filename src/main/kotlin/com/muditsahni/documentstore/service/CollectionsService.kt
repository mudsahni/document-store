package com.muditsahni.documentstore.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.Timestamp
import com.google.cloud.firestore.Firestore
import com.google.cloud.tasks.v2.CloudTasksClient
import com.google.cloud.tasks.v2.HttpMethod
import com.google.cloud.tasks.v2.HttpRequest
import com.google.cloud.tasks.v2.OidcToken
import com.google.cloud.tasks.v2.Task
import com.google.protobuf.ByteString
import com.muditsahni.documentstore.config.documentparser.DocumentParserProperties
import com.muditsahni.documentstore.config.getObjectMapper
import com.muditsahni.documentstore.model.dto.request.UploadDocumentTask
import com.muditsahni.documentstore.model.entity.Collection
import com.muditsahni.documentstore.model.entity.User
import com.muditsahni.documentstore.model.entity.toUser
import com.muditsahni.documentstore.model.enum.*
import com.muditsahni.documentstore.util.await
import kotlinx.coroutines.reactive.awaitSingle
import mu.KotlinLogging
import org.apache.http.entity.ContentType.APPLICATION_JSON
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders.AUTHORIZATION
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.util.UUID
import javax.annotation.PostConstruct
import kotlin.math.log

@Service
class CollectionsService(
    private val firestore: Firestore,
    private val storageService: StorageService,
    private val documentParserProperties: DocumentParserProperties,
    private val cloudTasksClient: CloudTasksClient,
    @Value("\${gcp.project-id}") private val gcpProjectId: String,
    @Value("\${gcp.region}") private val gcpRegion: String,
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
        logger.info("Initiating collection creation for user $userId")
        // create collection id
        val collectionId = UUID.randomUUID()

        // Create collection object
        val collection = Collection(
            id = collectionId.toString(),
            name = collectionName,
            type = collectionType,
            createdBy = userId,
            status = CollectionStatus.RECIEVED,
            createdAt = Timestamp.now(),
        )

        logger.info("Collection object created")

        // Save collection to Firestore
        firestore
            .collection("tenants")
            .document(tenant.tenantId)
            .collection("collections")
            .document(collectionId.toString())
            .set(collection)
            .await()

        logger.info("Collection saved to Firestore")

        // link it to user
        val userRef = firestore
            .collection("tenants")
            .document(tenant.tenantId)
            .collection("users")
            .document(userId)
            .get()
            .await()

        logger.info("User fetched from Firestore")
        logger.info("user doc: ${userRef.data}")
        val user = userRef.toUser() ?: throw IllegalStateException("User not found")
        logger.info("User object fetched and converted to user class")

        user.collections.add(collectionId.toString())
        user.updatedAt = Timestamp.now()
        user.updatedBy = userId

        logger.info("User object updated")
        // update user in firestore
        firestore
            .collection("tenants")
            .document(tenant.tenantId)
            .collection("users")
            .document(userId)
            .set(user)
            .await()

        logger.info("User updated in Firestore")
        return collection
    }

    private suspend fun uploadDocument(
        tenant: Tenant,
        userId: String,
        collection: Collection,
        document: FilePart
    ) {

        logger.info("Uploading document ${document.filename()} for user $userId")

        try {

            val fileContent = document.content().awaitSingle().asInputStream().readBytes()

            // create task for queue
            val task = UploadDocumentTask(
                collectionId = collection.id,
                tenantId = tenant.tenantId,
                userId = userId,
                fileType = FileType.PDF,
                fileSize = 0,
                file = fileContent,
                fileName = document.filename(),
                uploadPath = "${tenant.tenantId}/${collection.id}/${document.filename()}",
                callbackUrl = ""
            )

            val taskBody = objectMapper.writeValueAsString(task)

            logger.info("Task body created")

            // Get auth token
            credentials.refreshIfExpired()
            val token = credentials.accessToken.tokenValue

            logger.info("Token fetched")

            val documentParserUploadEndpoint = "${documentParserProperties.uri}/${documentParserProperties.version}/" +
                    documentParserProperties.upload

            logger.info("Document parser upload endpoint fetched: $documentParserUploadEndpoint")
            val uploadDocumentTask = Task.newBuilder()
                .setHttpRequest(
                    HttpRequest.newBuilder()
                        .setHttpMethod(HttpMethod.POST)
                        .setUrl(documentParserUploadEndpoint)
                        .putHeaders(AUTHORIZATION, "Bearer $token")
                        .putHeaders(CONTENT_TYPE, APPLICATION_JSON.toString())
//                        .setOidcToken(
//                            OidcToken.newBuilder()
//                                .setServiceAccountEmail("$")  // Your service account email
//                                .setAudience(documentParserUploadEndpoint)
//                        )
                        .setBody(ByteString.copyFromUtf8(taskBody))
                        .build()
                )
                .build()

            logger.info("Task created")

            val createdTask = cloudTasksClient
                .createTask("projects/${gcpProjectId}/locations/${gcpRegion}/queues/${cloudTasksQueue}",
                    uploadDocumentTask
                )
            logger.info("Task created with name: ${createdTask.name} at ${createdTask.createTime}")

        } catch (e: Exception) {
            logger.error { "Error uploading document to document parser due to error: ${e.cause} ${e.message}" }
            throw e
        }
    }

    private suspend fun uploadDocuments(
        tenant: Tenant,
        userId: String,
        collection: Collection,
        documents: List<FilePart>
    ): List<String> {

        logger.info("Uploading documents for user $userId")

        // create upload document tasks
        val uploadPaths = mutableListOf<String>()
        documents.forEach { document ->
            logger.info("Uploading document ${document.filename()}")
            uploadDocument(tenant, userId, collection, document)
            uploadPaths.add("${tenant.tenantId}/${collection.id}/${document.filename()}")
        }

        return uploadPaths
    }

    suspend fun createCollection(
        userId: String,
        tenant: Tenant,
        collectionName: String,
        collectionType: CollectionType,
        documents: List<FilePart>
    ): Collection {

        logger.info("Creating collection for user $userId")
        // Create collection
        val collection = initiateCollectionCreation(userId, tenant, collectionName, collectionType)

        // upload documents
        val documentPaths = uploadDocuments(tenant, userId, collection, documents)

        logger.info("Documents uploaded successfully")

        // Return collection
        return collection
    }
}