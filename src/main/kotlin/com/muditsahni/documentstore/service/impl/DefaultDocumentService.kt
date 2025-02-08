package com.muditsahni.documentstore.service.impl

import com.google.cloud.firestore.Firestore
import com.muditsahni.documentstore.exception.MajorErrorCode
import com.muditsahni.documentstore.exception.throwable.CollectionCreationError
import com.muditsahni.documentstore.model.dto.response.DocumentDownloadResponse
import com.muditsahni.documentstore.model.entity.document.Document
import com.muditsahni.documentstore.model.enum.Tenant
import com.muditsahni.documentstore.service.StorageService
import com.muditsahni.documentstore.util.DocumentHelper
import org.springframework.stereotype.Service

@Service
class DefaultDocumentService(
    val firestore: Firestore,
    val storageService: StorageService
    ) {

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
        val documentDownloadLink = storageService.getFileUrl(tenant.name, document.collectionId, documentId, document.name)
        return DocumentDownloadResponse(
            documentId = documentId,
            collectionId = document.collectionId,
            tenantId = tenant.name,
            fileName = document.name,
            fileType = document.type.value,
            ttl = 1,
            downloadUrl = documentDownloadLink
        )
    }

}