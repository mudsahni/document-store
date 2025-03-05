package com.muditsahni.documentstore.service.impl

import com.google.cloud.firestore.Firestore
import com.muditsahni.documentstore.exception.MajorErrorCode
import com.muditsahni.documentstore.exception.MinorErrorCode
import com.muditsahni.documentstore.exception.ValidationError
import com.muditsahni.documentstore.exception.throwable.CollectionCreationError
import com.muditsahni.documentstore.exception.throwable.DocumentValidationError
import com.muditsahni.documentstore.model.dto.response.DocumentDownloadResponse
import com.muditsahni.documentstore.model.entity.document.Document
import com.muditsahni.documentstore.model.entity.document.StructuredData
import com.muditsahni.documentstore.model.enum.DocumentType
import com.muditsahni.documentstore.model.enum.Tenant
import com.muditsahni.documentstore.service.StorageService
import com.muditsahni.documentstore.util.DocumentHelper
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class DefaultDocumentService(
    val firestore: Firestore,
    val storageService: StorageService
    ) {

    companion object {
        private val logger = KotlinLogging.logger {}

        // Cache for validation results to avoid repeated validations for unchanged documents
        private val validationCache = ConcurrentHashMap<String, Pair<Map<String, ValidationError>, Long>>()

        // Cache expiration time (10 minutes)
        private const val VALIDATION_CACHE_EXPIRY_MS = 600000L

        // Firestore operation timeout (10 seconds)
        private const val FIRESTORE_TIMEOUT_MS = 10000L
    }

    suspend fun getDocument(
        userId: String,
        tenant: Tenant,
        documentId: String
    ): Document {
        val document = DocumentHelper.getDocument(firestore, documentId, tenant)

        // Security check
        document.permissions[userId]?.let {
            return document
        } ?: document.createdBy.let {
            if (it != userId) {
                logger.warn { "User $userId attempted to access document ${document.id} created by ${document.createdBy}" }
                throw CollectionCreationError(
                    MajorErrorCode.GEN_MAJ_COL_001,
                    "User does not have permission to view document."
                )
            }
            return document
        }
    }

    suspend fun downloadDocument(
        userId: String,
        tenant: Tenant,
        documentId: String
    ): DocumentDownloadResponse {
        try {
            val document = getDocument(userId, tenant, documentId)
            logger.info { "Generating download link for document: ${document.name}, type: ${document.type.value}" }

            val documentDownloadLink = storageService.getFileUrl(
                tenant.tenantId,
                document.collectionId,
                documentId,
                document.name
            )

            logger.info { "Document download link generated for $documentId" }

            return DocumentDownloadResponse(
                documentId = documentId,
                collectionId = document.collectionId,
                tenantId = tenant.tenantId,
                fileName = document.name,
                fileType = document.type.value,
                ttl = 1, // TTL in hours
                downloadUrl = documentDownloadLink
            )
        } catch (e: Exception) {
            logger.error(e) { "Error generating download link for document $documentId" }
            throw e
        }
    }

    private suspend fun validateDocumentContent(
        document: Document
    ): Map<String, ValidationError> {
        val cacheKey = "${document.id}:${document.updatedAt}"
        val now = System.currentTimeMillis()

        // Try validation cache first if document hasn't changed
        validationCache[cacheKey]?.let { (cachedErrors, timestamp) ->
            if (now - timestamp < VALIDATION_CACHE_EXPIRY_MS) {
                logger.debug { "Validation cache hit for document ${document.id}" }
                return cachedErrors
            }
            // Remove expired entry
            validationCache.remove(cacheKey)
        }

        logger.info { "Validating document content: ${document.id}" }

        if (document.type == DocumentType.INVOICE) {
            if (document.data != null) {
                if (document.data?.structured != null) {
                    try {
                        logger.info { "Document contains structured content, validating invoice data" }
                        val validationErrors = validateInvoiceWrapper(document.data?.structured!!)

                        // Cache validation results
                        validationCache[cacheKey] = validationErrors to now

                        return validationErrors
                    } catch (e: DocumentValidationError) {
                        logger.info { "Document validation failed: ${e.message}" }
                        throw e
                    } catch (e: Exception) {
                        logger.error(e) { "Document validation failed with unexpected error" }
                        throw DocumentValidationError(
                            MinorErrorCode.INV_MIN_DOC_001,
                            "Validation failed: ${e.message ?: MinorErrorCode.INV_MIN_DOC_001.message}"
                        )
                    }
                }
                logger.warn { "Document data exists but no structured content found" }
                throw DocumentValidationError(
                    MinorErrorCode.VAL_MIN_DOC_001,
                    "Document has data but no structured content"
                )
            }
            logger.warn { "Document has no data to validate" }
            throw DocumentValidationError(
                MinorErrorCode.VAL_MIN_DOC_002,
                "Document has no data to validate"
            )
        }

        logger.warn { "Unsupported document type for validation: ${document.type}" }
        throw DocumentValidationError(
            MinorErrorCode.VAL_MIN_DOC_003,
            "Unsupported document type: ${document.type}"
        )
    }


    suspend fun validateDocument(
        document: Document
    ): Map<String, ValidationError> {
        return validateDocumentContent(document)
    }

    suspend fun validateDocument(
        tenant: Tenant,
        documentId: String
    ): Map<String, ValidationError> {
        val document = DocumentHelper.getDocument(firestore, documentId, tenant)

        logger.info { "Validating document: $documentId" }
        return validateDocument(document)
    }

    suspend fun updateDocumentContent(
        userId: String,
        tenant: Tenant,
        documentId: String,
        data: StructuredData
    ): Document {
        try {
            // Check if user has permission first
            val document = getDocument(userId, tenant, documentId)

            // Update document content
            document.data = data
            document.updatedAt = System.currentTimeMillis()
            document.updatedBy = userId

            // Validate updated document
            document.data?.errors = validateDocument(document)

            // Clear validation cache since document has changed
            val cacheKey = "${document.id}:${document.updatedAt}"
            validationCache.remove(cacheKey)

            // Save updated document
            DocumentHelper.saveDocument(firestore, tenant, document)

            logger.info { "Updated document content for $documentId" }
            return document
        } catch (e: Exception) {
            logger.error(e) { "Error updating document content for $documentId" }
            throw e
        }
    }

    fun clearValidationCache() {
        validationCache.clear()
        logger.info { "Validation cache cleared" }
    }



}