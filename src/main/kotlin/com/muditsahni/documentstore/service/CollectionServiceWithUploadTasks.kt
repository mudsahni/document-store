package com.muditsahni.documentstore.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.Timestamp
import com.google.cloud.firestore.Firestore
import com.google.cloud.tasks.v2.CloudTasksClient
import com.muditsahni.documentstore.config.documentparser.DocumentParserProperties
import com.muditsahni.documentstore.config.getObjectMapper
import com.muditsahni.documentstore.exception.CollectionError
import com.muditsahni.documentstore.exception.CollectionErrorType
import com.muditsahni.documentstore.exception.DocumentError
import com.muditsahni.documentstore.exception.throwable.CollectionUploadException
import com.muditsahni.documentstore.model.dto.request.UploadDocumentTask
import com.muditsahni.documentstore.model.dto.response.CreateCollectionResponse
import com.muditsahni.documentstore.model.entity.Collection
import com.muditsahni.documentstore.model.entity.SignedUrlResponse
import com.muditsahni.documentstore.model.entity.toCreateCollectionReponse
import com.muditsahni.documentstore.model.enum.*
import com.muditsahni.documentstore.util.CloudTasksHelper
import com.muditsahni.documentstore.util.CollectionHelper
import com.muditsahni.documentstore.util.DocumentHelper
import com.muditsahni.documentstore.util.UserHelper
import com.muditsahni.documentstore.util.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.awaitSingle
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.codec.multipart.FilePart
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID

@Service
class CollectionsServiceWIthUploadTasks(
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

    private suspend fun createUploadDocumentTask(
        tenantId: String,
        userId: String,
        collectionId: String,
        documentId: String,
        document: Pair<FilePart, ByteArray>
    ) {

        logger.info("Uploading document ${document.first.filename()} for user $userId")

        // Encode file content to Base64
        val fileContentBase64 = Base64.getEncoder().encodeToString(document.second)

        // create task for queue
        val task = UploadDocumentTask(
            collectionId = collectionId,
            documentId = documentId,
            tenantId = tenantId,
            userId = userId,
            fileType = FileType.PDF,
            fileSize = document.second.size.toLong(),
            file = fileContentBase64,     // Send Base64 encoded string
            fileName = document.first.filename(),
            uploadPath = "${tenantId}/${collectionId}",
            callbackUrl = "https://${applicationName}-${projectNumber}.${applicationRegion}.run.app/api/v1/tenants/${tenantId}/collections/${collectionId}/upload"
        )

        val taskBody = objectMapper.writeValueAsString(task)

        logger.info("Task body created")

        googleCredentials.refreshIfExpired()

        logger.info("Token fetched")

        val documentParserUploadEndpoint = "https://${documentParserProperties.name}-${documentParserProperties.projectNumber}.${documentParserProperties.region}.run.app/${documentParserProperties.uri}/${documentParserProperties.version}/" +
                documentParserProperties.upload

        logger.info("Document parser upload endpoint fetched: $documentParserUploadEndpoint")

        CloudTasksHelper.createNewTask(
            cloudTasksClient,
            gcpProjectId,
            gcpRegion,
            cloudTasksQueue,
            documentParserUploadEndpoint,
            taskBody
        )

        logger.info("Task created successfully")

        // update collection status
        CollectionHelper.updateCollectionStatus(
            firestore,
            Tenant.fromTenantId(tenantId),
            collectionId,
            CollectionStatus.DOCUMENT_UPLOADING_TASKS_CREATED
        )

    }

    private suspend fun createUploadDocumentTasks(
        tenant: Tenant,
        userId: String,
        collection: Collection,
        documents: List<Pair<FilePart, ByteArray>>
    ): List<String> {

        logger.info("Creating uploading tasks for user $userId")

        // create document objects and save them to firestore
        // link them to collection and user too
        val documentIds = createAndSaveDocumentsForUpload(
            userId,
            tenant,
            collection.id,
            documents.map { it.first.filename() }
        )
        // create upload document tasks
        val uploadPaths = mutableListOf<String>()
        documents.forEachIndexed { index, document ->
            logger.info("Creating document upload task for ${document.first.filename()}")
            createUploadDocumentTask(tenant.tenantId, userId, collection.id, documentIds[index], document)
            uploadPaths.add("${tenant.tenantId}/${collection.id}/${document.first.filename()}")
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
        // Read all file contents first before launching coroutine
        val fileContents = documents.map { filePart ->
            filePart.content()
                .collectList()
                .awaitSingle()
                .fold(ByteArrayOutputStream()) { baos, dataBuffer ->
                    baos.write(dataBuffer.asInputStream().readBytes())
                    baos
                }
                .toByteArray()
        }

        // Launch the document upload in a new coroutine
        CoroutineScope(Dispatchers.IO).launch {
            try {
                createUploadDocumentTasks(tenant, userId, collection, documents.zip(fileContents))
                logger.info("Document upload tasks created successfully")
            } catch (e: Exception) {
                logger.error("Failed to upload documents: ${e.message}", e)
                // Handle error - maybe update collection status
                // update collection to failed
                collection.status = CollectionStatus.FAILED
                collection.error = CollectionError(
                    "Failed to create tasks for uploading documents",
                    CollectionErrorType.DOCUMENT_UPLOAD_TASK_CREATION_ERROR
                )
                collection.updatedBy = userId
                collection.updatedAt = Timestamp.now()
                CollectionHelper.saveCollection(firestore, tenant, collection)
                logger.info("Collection updated with error")
                // throw collection upload exception
                throw CollectionUploadException("Failed to upload documents")
            }
        }

        // Return collection
        return collection
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
        documents: List<String>
    ): List<String> {

        val documentIds = mutableMapOf<String, DocumentStatus>()

        documents.forEach {

            // create document object
            val document = DocumentHelper.createDocumentObject(
                userId = userId,
                name = it,
                collectionId = collectionId,
                tenant = tenant,
                filePath = "${tenant.tenantId}/${collectionId}/${it}",
                type = DocumentType.INVOICE,
                status = DocumentStatus.PENDING
            )

            // save document
            DocumentHelper.saveDocument(firestore, tenant, document)
            documentIds[document.id] = DocumentStatus.PENDING

        }

        // update collection
        CollectionHelper.updateCollectionDocuments(
            firestore,
            tenant,
            collectionId,
            documentIds
        )

        // update user
        UserHelper.updateUserDocuments(
            firestore,
            userId,
            tenant,
            documentIds.keys.toList()
        )

        return documentIds.keys.toList()

    }


}

