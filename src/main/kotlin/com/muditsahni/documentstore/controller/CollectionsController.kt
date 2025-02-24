package com.muditsahni.documentstore.controller

import com.muditsahni.documentstore.config.FileUploadConfig
import com.muditsahni.documentstore.exception.DocumentError
import com.muditsahni.documentstore.exception.DocumentErrorType
import com.muditsahni.documentstore.model.dto.response.GetCollectionsResponse
import com.muditsahni.documentstore.model.dto.request.NewCollectionRequest
import com.muditsahni.documentstore.model.dto.request.ProcessDocumentCallbackRequest
import com.muditsahni.documentstore.model.dto.request.UploadCallbackRequest
import com.muditsahni.documentstore.model.dto.response.CreateCollectionResponse
import com.muditsahni.documentstore.model.dto.response.GetCollectionResponse
import com.muditsahni.documentstore.model.dto.response.GetCollectionWithDocumentsResponse
import com.muditsahni.documentstore.model.entity.toGetCollectionResponse
import com.muditsahni.documentstore.model.enum.DocumentStatus
import com.muditsahni.documentstore.model.enum.Tenant
import com.muditsahni.documentstore.model.enum.UploadStatus
import com.muditsahni.documentstore.model.enum.UserRole
import com.muditsahni.documentstore.model.event.CollectionStatusEvent
import com.muditsahni.documentstore.security.FirebaseUserDetails
import com.muditsahni.documentstore.service.EventStreamService
import com.muditsahni.documentstore.service.impl.DefaultCollectionService
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import mu.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.ServerSentEvent
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Flux


@RestController
@CrossOrigin // Add this annotation
@RequestMapping("/api/v1/tenants/{tenantId}/collections")
@Tag(name = "Document Upload", description = "Endpoints for document upload and processing")
@SecurityRequirement(name = "firebase")
class CollectionsController(
    private val collectionsService: DefaultCollectionService,
    private val eventStreamService: EventStreamService
) {

    companion object {
        private val logger = KotlinLogging.logger {
            CollectionsController::class.java.name
        }
    }

    @GetMapping("/{collectionId}/sse", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun subscribeToCollectionEvents(
        @PathVariable tenantId: String,
        @PathVariable collectionId: String
    ): Flux<ServerSentEvent<CollectionStatusEvent>>  {
        logger.info("SSE connection attempt for collection: $collectionId")

        return eventStreamService.getEventStream(collectionId)
            .doOnSubscribe { logger.info("Client subscribed to collection: $collectionId") }
            .doOnCancel { logger.info("Client cancelled subscription for collection: $collectionId") }
            .doOnError { error ->
                logger.error("Error in SSE stream for collection: $collectionId", error)
            }
    }

    @GetMapping("/{collectionId}")
    suspend fun get(
        @PathVariable tenantId: String,
        @PathVariable collectionId: String,
        @AuthenticationPrincipal userDetails: FirebaseUserDetails
    ): ResponseEntity<GetCollectionResponse> {
        logger.info { "Get collection call received" }

        // Get collection
        val collection = collectionsService.getCollection(userDetails.uid, userDetails.tenant, collectionId)

        logger.info { "Collection fetched successfully" }
        // Return collection
        return ResponseEntity.ok(collection.toGetCollectionResponse())
    }


    @GetMapping("/{collectionId}/documents")
    suspend fun getDocuments(
        @PathVariable tenantId: String,
        @PathVariable collectionId: String,
        @AuthenticationPrincipal userDetails: FirebaseUserDetails
    ): ResponseEntity<GetCollectionWithDocumentsResponse> {
        logger.info { "Get documents call received" }
        try {

            // Get collection
            val collectionWithDocuments =
                collectionsService.getCollectionWithDocuments(userDetails.uid, userDetails.tenant, collectionId)

            logger.info { "Collection fetched successfully" }
            // Return collection
            return ResponseEntity.ok(collectionWithDocuments)
        } catch (e: Exception) {
            logger.error(e) { "Error fetching documents" }
            throw e
        }
    }

    @GetMapping
    suspend fun getAll(
        @PathVariable tenantId: String,
        @RequestParam(required = false, defaultValue = "false")
        @Parameter(description = "If true, returns all collections (requires power-user or admin permission)")
        orgWide: Boolean,
        @AuthenticationPrincipal userDetails: FirebaseUserDetails
    ): ResponseEntity<GetCollectionsResponse> {

        logger.info { "Get all collections call received "}

        // Check if user can view all collections
        val canViewAll = (userDetails.role == UserRole.ADMIN) ||
                (userDetails.role == UserRole.POWER_USER)

        if (orgWide && !canViewAll) {
            logger.error { "User with Id ${userDetails.uid} does not have permission to view all collections" }
            throw IllegalStateException("User with Id ${userDetails.uid} does not have permission to view all collections")
        }

        logger.info { "User with Id ${userDetails.uid} has permission to view all collections" }

        // Get collections
        val collections = collectionsService.getAllCollections(userDetails.uid, userDetails.tenant, orgWide)

        logger.info { "Collections fetched successfully" }
        // Return collections
        return ResponseEntity.ok(GetCollectionsResponse(collections = collections.map { it.toGetCollectionResponse() }))
    }

    @PostMapping
    suspend fun create(
        @PathVariable tenantId: String,
        @RequestBody request: NewCollectionRequest,
        @AuthenticationPrincipal firebaseUserDetails: FirebaseUserDetails
    ): ResponseEntity<CreateCollectionResponse> {

        try {
            logger.info { "Create collection call received" }

            request.files.forEach { it -> validateFile(it.key, it.value) }

            // Create collection
            return ResponseEntity.ok(
                collectionsService.createCollectionAndDocuments(
                    firebaseUserDetails.uid,
                    firebaseUserDetails.tenant,
                    request.name,
                    request.type,
                    request.files,
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Error creating collection" }
            throw e
        }
    }

    @PostMapping("/{collectionId}/documents/{documentId}/process")
    suspend fun processDocument(
        @PathVariable tenantId: String,
        @PathVariable collectionId: String,
        @PathVariable documentId: String,
        @RequestBody processDocumentCallbackRequest: ProcessDocumentCallbackRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<String> {
        try {
            logger.info { "Process document call received" }

            collectionsService.receiveProcessedDocument(
                Tenant.fromTenantId(tenantId),
                collectionId,
                processDocumentCallbackRequest
            )

            return ResponseEntity.ok("Document processing started")
        } catch (e: Exception) {
            logger.error(e) { "Error processing document" }
            throw e
        }
    }

    private fun validateFile(fileName: String, fileType: String) {
        // Check content type
        require(fileType in FileUploadConfig.ALLOWED_CONTENT_TYPES) {
            "File $fileName must be a PDF"
        }
    }

    @GetMapping("/{collectionId}/export")
    suspend fun exportInvoices(
        @PathVariable tenantId: String,
        @PathVariable collectionId: String,
        @AuthenticationPrincipal userDetails: FirebaseUserDetails
    ): ResponseEntity<String> {
        val csv = collectionsService.getCollectionDocumentsAsCSV(userDetails.uid, userDetails.tenant, collectionId)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoices.csv")
            .contentType(MediaType.TEXT_PLAIN)
            .body(csv)
    }


}