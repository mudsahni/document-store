package com.muditsahni.documentstore.controller

import com.muditsahni.documentstore.config.FileUploadConfig
import com.muditsahni.documentstore.model.dto.request.CollectionCreationStatus
import com.muditsahni.documentstore.model.dto.request.GetCollectionsResponse
import com.muditsahni.documentstore.model.dto.request.NewCollectionRequest
import com.muditsahni.documentstore.model.entity.toCollectionStatus
import com.muditsahni.documentstore.model.entity.toGetCollectionResponse
import com.muditsahni.documentstore.model.enum.UserRole
import com.muditsahni.documentstore.security.FirebaseUserDetails
import com.muditsahni.documentstore.service.CollectionsService
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/collections")
@Tag(name = "Document Upload", description = "Endpoints for document upload and processing")
@SecurityRequirement(name = "firebase")
class CollectionsController(
    private val collectionsService: CollectionsService
) {

    companion object {
        private val logger = KotlinLogging.logger {
            CollectionsController::class.java.name
        }
    }

    @GetMapping("/hello")
    fun helloWorld(
        @AuthenticationPrincipal firebaseUserDetails: FirebaseUserDetails
    ): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("message" to "Hello ${firebaseUserDetails.uid}"))
    }

    @GetMapping
    suspend fun getAll(
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
        return ResponseEntity.ok(GetCollectionsResponse(collections = collections.map { it.toGetCollectionResponse()}))
    }

//    @PostMapping
//    suspend fun uploadCallback(
//        @RequestBody request: UploadCallbackRequest,
//    ): {
//        // Optional: Additional verification if needed
//        val jwt = authentication.credentials as Jwt
//        val audience = jwt.claims["aud"] as String
//
//    }

    @PostMapping
    suspend fun create(
        @ModelAttribute request: NewCollectionRequest,
        @AuthenticationPrincipal firebaseUserDetails: FirebaseUserDetails
    ): ResponseEntity<CollectionCreationStatus> {

        logger.info { "Create collection call received" }

        // Create collection
        val collection = collectionsService.createCollection(
            firebaseUserDetails.uid,
            firebaseUserDetails.tenant,
            request.name,
            request.type,
            request.files,
        )
        return ResponseEntity.ok(collection.toCollectionStatus())
    }

    private fun validateFile(file: MultipartFile) {
        // Check content type
        require(file.contentType in FileUploadConfig.ALLOWED_CONTENT_TYPES) {
            "File ${file.originalFilename} must be a PDF"
        }

        // Check file size
        require(file.size <= FileUploadConfig.MAX_FILE_SIZE) {
            "File ${file.originalFilename} exceeds maximum size of ${FileUploadConfig.MAX_FILE_SIZE / 1024 / 1024}MB"
        }
    }


}