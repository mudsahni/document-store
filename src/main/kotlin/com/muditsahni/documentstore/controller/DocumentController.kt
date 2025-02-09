package com.muditsahni.documentstore.controller

import com.muditsahni.documentstore.exception.ValidationError
import com.muditsahni.documentstore.model.dto.response.DocumentDownloadResponse
import com.muditsahni.documentstore.model.dto.response.GetDocumentResponse
import com.muditsahni.documentstore.model.entity.document.toGetDocumentResponse
import com.muditsahni.documentstore.security.FirebaseUserDetails
import com.muditsahni.documentstore.service.impl.DefaultDocumentService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@CrossOrigin // Add this annotation
@RequestMapping("/api/v1/tenants/{tenantId}/documents")
@Tag(name = "Document Upload", description = "Endpoints for document upload and processing")
@SecurityRequirement(name = "firebase")
class DocumentController(
    val documentService: DefaultDocumentService
) {

    companion object {
        private val logger = KotlinLogging.logger {
            DocumentController::class.java.name
        }
    }

    @GetMapping("/{documentId}")
    suspend fun get(
        @PathVariable tenantId: String,
        @PathVariable documentId: String,
        @AuthenticationPrincipal userDetails: FirebaseUserDetails
    ): ResponseEntity<GetDocumentResponse> {
        logger.info { "Get collection call received" }

        // Get collection
        val document = documentService.getDocument(userDetails.uid, userDetails.tenant, documentId)

        logger.info { "Document fetched successfully" }
        // Return collection
        return ResponseEntity.ok(document.toGetDocumentResponse())
    }

    @GetMapping("/{documentId}/download")
    suspend fun download(
        @PathVariable tenantId: String,
        @PathVariable documentId: String,
        @AuthenticationPrincipal userDetails: FirebaseUserDetails
    ): ResponseEntity<DocumentDownloadResponse> {
        logger.info { "Download document call received" }

        // Get collection
        val downloadDocumentResponse = documentService.downloadDocument(userDetails.uid, userDetails.tenant, documentId)

        logger.info { "Document download link fetched successfully." }
        // Return collection
        return ResponseEntity.ok(downloadDocumentResponse)
    }

    @GetMapping("/{documentId}/validate")
    suspend fun validate(
        @PathVariable tenantId: String,
        @PathVariable documentId: String,
        @AuthenticationPrincipal userDetails: FirebaseUserDetails
    ): ResponseEntity<Map<String, ValidationError>> {
        logger.info { "Validate document call received" }

        // Get collection

        val validationErrors = documentService.validateDocument(userDetails.tenant, documentId)

        logger.info { "Document validated successfully." }
        // Return collection
        return ResponseEntity.ok(validationErrors)
    }
}