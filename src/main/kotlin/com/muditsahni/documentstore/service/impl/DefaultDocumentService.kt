package com.muditsahni.documentstore.service.impl

import com.google.cloud.firestore.Firestore
import com.muditsahni.documentstore.exception.MajorErrorCode
import com.muditsahni.documentstore.exception.MinorErrorCode
import com.muditsahni.documentstore.exception.ValidationError
import com.muditsahni.documentstore.exception.throwable.CollectionCreationError
import com.muditsahni.documentstore.exception.throwable.DocumentValidationError
import com.muditsahni.documentstore.model.dto.response.DocumentDownloadResponse
import com.muditsahni.documentstore.model.entity.document.Document
import com.muditsahni.documentstore.model.enum.DocumentType
import com.muditsahni.documentstore.model.enum.Tenant
import com.muditsahni.documentstore.service.StorageService
import com.muditsahni.documentstore.util.DocumentHelper
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class DefaultDocumentService(
    val firestore: Firestore,
    val storageService: StorageService
    ) {

    companion object {
        private val logger = KotlinLogging.logger {
            DefaultDocumentService::class.java.name
        }
    }

    suspend fun getDocument(
        userId: String,
        tenant: Tenant,
        documentId: String
    ): Document {

        val collection = DocumentHelper.getDocument(firestore, documentId, tenant)
        collection.createdBy.let {
            if (it != userId) {
                throw CollectionCreationError(MajorErrorCode.GEN_MAJ_COL_001, "User does not have permission to view collection.")
            }
        }

        return collection
    }

    suspend fun downloadDocument(
        userId: String,
        tenant: Tenant,
        documentId: String
    ): DocumentDownloadResponse {
        val document = getDocument(userId, tenant, documentId)
        logger.info("Document details: ${document.name}, ${document.type.value}, ${document.collectionId}, ${documentId}")
        val documentDownloadLink = storageService.getFileUrl(tenant.tenantId, document.collectionId, documentId, document.name)
        logger.info("Document download link generated: $documentDownloadLink")
        return DocumentDownloadResponse(
            documentId = documentId,
            collectionId = document.collectionId,
            tenantId = tenant.tenantId,
            fileName = document.name,
            fileType = document.type.value,
            ttl = 1,
            downloadUrl = documentDownloadLink
        )
    }

    private suspend fun validateDocumentContent(
        document: Document
    ): Map<String, ValidationError> {
        if (document.type == DocumentType.INVOICE) {
            if (document.data != null) {
                if (document.data?.structured != null) {
                    try {
                        logger.info { "Document contains structured content." }
                        return validateInvoiceWrapper(document.data?.structured!!)
                    } catch (e: DocumentValidationError) {
                        logger.info { "Document validation failed. Error: ${e}" }
                        throw e
                    } catch (e: Exception) {
                        logger.error { "Document validation failed. Error: ${e}" }
                        throw DocumentValidationError(MinorErrorCode.INV_MIN_DOC_001, MinorErrorCode.INV_MIN_DOC_001.message)
                    }
                }
                throw DocumentValidationError(MinorErrorCode.VAL_MIN_DOC_001, MinorErrorCode.VAL_MIN_DOC_001.message)
            }
            throw DocumentValidationError(MinorErrorCode.VAL_MIN_DOC_002, MinorErrorCode.VAL_MIN_DOC_002.message)
        }

        throw DocumentValidationError(MinorErrorCode.VAL_MIN_DOC_003, MinorErrorCode.VAL_MIN_DOC_003.message)
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

}