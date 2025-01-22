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
import com.muditsahni.documentstore.model.cloudtasks.DocumentProcessingTask
import com.muditsahni.documentstore.model.dto.request.ProcessDocumentCallbackRequest
import com.muditsahni.documentstore.model.dto.response.CreateCollectionResponse
import com.muditsahni.documentstore.model.entity.Collection
import com.muditsahni.documentstore.model.entity.ParsedData
import com.muditsahni.documentstore.model.entity.ParsedDataMetadata
import com.muditsahni.documentstore.model.entity.PromptTemplate
import com.muditsahni.documentstore.model.entity.StorageEvent
import com.muditsahni.documentstore.model.entity.toCollectionStatusEvent
import com.muditsahni.documentstore.model.entity.toCreateCollectionReponse
import com.muditsahni.documentstore.model.enum.*
import com.muditsahni.documentstore.model.event.CollectionStatusEvent
import com.muditsahni.documentstore.service.CollectionService
import com.muditsahni.documentstore.service.EventStreamService
import com.muditsahni.documentstore.service.StorageService
import com.muditsahni.documentstore.util.CloudTasksHelper
import com.muditsahni.documentstore.util.CollectionHelper
import com.muditsahni.documentstore.util.DocumentHelper
import com.muditsahni.documentstore.util.UserHelper
import com.muditsahni.documentstore.util.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
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

        logger.info("Creating collection status events stream")

        eventStreamService.createEventStream(collection.id)
        emitCollectionStatusEvent(collection)

        // create documents in firestore and generate signed urls for documents
        val documentIdsWithSignedUrlResponse = createAndSaveDocumentsWithSignedUrlsForUpload(
            userId,
            tenant,
            collection.id,
            documents
        )

        // Return collection
        return collection.toCreateCollectionReponse(documentIdsWithSignedUrlResponse)
    }


    suspend fun processStorageEvent(event: StorageEvent) {
        logger.info("Processing storage event for file: ${event.name}")

        // Extract tenant and collection IDs from path
        val pathParts = event.name.split("/")
        val tenantId = pathParts[0]
        val collectionId = pathParts[1]
        val documentId = pathParts[2]
        val fileName = pathParts[3]

        // Process the uploaded file
        val collection = updateCollectionAndDocument(
            Tenant.fromTenantId(tenantId),
            collectionId,
            documentId,
            DocumentStatus.UPLOADED
        )

        logger.info("Emitting collection status event for collection $collectionId")
        emitCollectionStatusEvent(collection)

        logger.info("Processing document: ${documentId}")
        processDocument(tenantId, collectionId, documentId, fileName)

    }

    suspend fun processDocument(tenantId: String, collectionId: String, documentId: String, fileName: String) {

        // get signed url link for document

        logger.info("Processing document: $documentId")
        val downloadableLink = storageService.getFileUrl(tenantId, collectionId, documentId, fileName)
        // create a cloud task for the python api to process the document through openai/anthropic/gemini/llama

        logger.info("Got signed url for document: $downloadableLink")

        val prompt = "${invoiceParsingPromptTemplate.prompt}\n${invoiceParsingPromptTemplate.template}"
        logger.info("Prompt for document: $prompt")

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

        // update collection status
        DocumentHelper.updateDocumentStatus(
            firestore,
            Tenant.fromTenantId(tenantId),
            documentId,
            DocumentStatus.IN_PROGRESS
        )
        // update collection status
        val collection = CollectionHelper.updateCollectionDocuments(
            firestore,
            Tenant.fromTenantId(tenantId),
            collectionId,
            documentId,
            DocumentStatus.IN_PROGRESS
        )

        // emit event
        eventStreamService.emitEvent(collection.toCollectionStatusEvent())
    }

    suspend fun receiveProcessedDocument(
        tenant: Tenant,
        collectionId: String,
        processDocumentCallbackRequest: ProcessDocumentCallbackRequest
    ) {
        // get processed document

        // update document
        val document = DocumentHelper.getDocument(firestore, processDocumentCallbackRequest.id, tenant)
        if (processDocumentCallbackRequest.error != null) {
            document.status = DocumentStatus.ERROR
            document.error = DocumentError(
                processDocumentCallbackRequest.error.message,
                DocumentErrorType.DOCUMENT_PARSING_ERROR,
            )
        } else {
            document.status = DocumentStatus.PARSED
            document.parsedData = processDocumentCallbackRequest.parsedData
        }
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
            processDocumentCallbackRequest.id,
            document.status
        )
        // emit event
        eventStreamService.completeStream(collectionId)
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

