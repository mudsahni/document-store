package com.muditsahni.documentstore.controller

import com.muditsahni.documentstore.security.FirebaseUserDetails
import com.muditsahni.documentstore.service.CollectionsService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*

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

    @GetMapping
    fun helloWorld(
        @AuthenticationPrincipal userDetails: FirebaseUserDetails
    ): ResponseEntity<Map<String, String>> {
        val authentication = SecurityContextHolder.getContext().authentication
        val userDetails = authentication.principal as FirebaseUserDetails
        logger.debug("Authentication: $authentication")
        logger.debug("Authorities: ${authentication.authorities}")

        logger.info("User with id ${userDetails.uid} validated")
        logger.info("These are the user claims: ${userDetails.claims}")
        return ResponseEntity.ok(mapOf("message" to "Hello ${userDetails.uid}"))
    }

//    private fun validateUser(authentication: Authentication): FirebaseUserDetails {
//        val userDetails = authentication.principal as FirebaseUserDetails
//        userDetails.tenantId
//            ?: throw IllegalStateException("Tenant ID not found in user claims")
//        userDetails.role
//            ?: throw IllegalStateException("Role not found in user claims")
//        userDetails.uid
//        return userDetails
//    }

//    @GetMapping
//    suspend fun getAll(
//        @RequestParam(required = false, defaultValue = "false")
//        @Parameter(description = "If true, returns all collections (requires power-user or admin permission)")
//        orgWide: Boolean,
//        authentication: Authentication
//    ): ResponseEntity<GetCollectionsResponse> {
//
//        logger.info { "Get all collections call received "}
//        val userDetails = validateUser(authentication)
//        logger.info { "User with id ${userDetails.uid} validated"}
//
//        val tenant = validateTenantId(userDetails.tenantId)
//        logger.info { "Tenant ${tenant.tenantId} validated" }
//
//        // Check if user can view all collections
//        val canViewAll = userDetails.role?.uppercase() == UserRole.ADMIN.value ||
//                userDetails.role?.uppercase() == UserRole.POWER_USER.value
//        if (orgWide && !canViewAll) {
//            logger.error { "User with Id ${userDetails.uid} does not have permission to view all collections" }
//            throw IllegalStateException("User with Id ${userDetails.uid} does not have permission to view all collections")
//        }
//
//        logger.info { "User with Id ${userDetails.uid} has permission to view all collections" }
//
//        // Get collections
//        val collections = collectionsService.getAllCollections(userDetails.uid, tenant, orgWide)
//
//        logger.info { "Collections fetched successfully" }
//        // Return collections
//        return ResponseEntity.ok(GetCollectionsResponse(collections = collections.map { it.toGetCollectionResponse()}))
//    }

//    @PostMapping
//    suspend fun uploadCallback(
//        @RequestBody request: UploadCallbackRequest,
//        authentication: Authentication  // Spring Security will inject this
//    ): {
//        // Optional: Additional verification if needed
//        val jwt = authentication.credentials as Jwt
//        val audience = jwt.claims["aud"] as String
//
//    }
//
//    @PostMapping
//    suspend fun create(
//        @ModelAttribute request: NewCollectionRequest,
//        authentication: Authentication
//    ): ResponseEntity<CollectionCreationStatus> {
//
//        // Validate files
//        require(request.files.size <= FileUploadConfig.MAX_FILES) {
//            "Maximum ${FileUploadConfig.MAX_FILES} files allowed per request"
//        }
//
//
//        return ResponseEntity.ok("Document uploaded successfully")
//    }

//    private fun validateTenantId(tenantId: String?): Tenant {
//        require(tenantId != null) { "Tenant ID not found in user claims" }
//        require(Tenant.isValidTenantId(tenantId)) { "Invalid tenant ID: $tenantId" }
//        return Tenant.fromTenantId(tenantId)
//    }
//
//    private fun validateFile(file: MultipartFile) {
//        // Check content type
//        require(file.contentType in FileUploadConfig.ALLOWED_CONTENT_TYPES) {
//            "File ${file.originalFilename} must be a PDF"
//        }
//
//        // Check file size
//        require(file.size <= FileUploadConfig.MAX_FILE_SIZE) {
//            "File ${file.originalFilename} exceeds maximum size of ${FileUploadConfig.MAX_FILE_SIZE / 1024 / 1024}MB"
//        }
//    }


}