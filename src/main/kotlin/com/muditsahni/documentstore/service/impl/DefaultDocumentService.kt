package com.muditsahni.documentstore.service.impl

import com.google.cloud.firestore.Firestore
import com.muditsahni.documentstore.exception.MajorErrorCode
import com.muditsahni.documentstore.exception.throwable.CollectionCreationError
import com.muditsahni.documentstore.model.entity.document.Document
import com.muditsahni.documentstore.model.enum.Tenant
import com.muditsahni.documentstore.util.DocumentHelper
import org.springframework.stereotype.Service

@Service
class DefaultDocumentService(
    val firestore: Firestore,
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

}