package com.muditsahni.documentstore.util

import com.google.cloud.firestore.Firestore
import com.muditsahni.documentstore.model.entity.Document
import com.muditsahni.documentstore.model.entity.toDocument
import com.muditsahni.documentstore.model.enum.DocumentRole
import com.muditsahni.documentstore.model.enum.DocumentStatus
import com.muditsahni.documentstore.model.enum.DocumentType
import com.muditsahni.documentstore.model.enum.Tenant
import mu.KotlinLogging
import java.util.UUID

object DocumentHelper {

    private val logger = KotlinLogging.logger {
        DocumentHelper::class.java.name
    }

    suspend fun getDocument(
        firestore: Firestore,
        documentId: String,
        tenant: Tenant
    ): Document {

        val documentRef = firestore
            .collection("tenants")
            .document(tenant.tenantId)
            .collection("documents")
            .document(documentId)
            .get()
            .await()

        if (!documentRef.exists()) {
            throw IllegalStateException("Document with id $documentId not found")
        }

        logger.info("Document fetched from Firestore")
        val document = documentRef.toDocument()
        logger.info("Document object fetched and converted to document class")
        return document
    }

    suspend fun saveDocument(
        firestore: Firestore,
        tenant: Tenant,
        document: Document
    ) {
        firestore
            .collection("tenants")
            .document(tenant.tenantId)
            .collection("documents")
            .document(document.id)
            .set(document)
            .await()

        logger.info("Document with ${document.id} saved in Firestore")
    }

    suspend fun createDocumentObject(
        name: String,
        userId: String,
        collectionId: String,
        tenant: Tenant,
        filePath: String? = null,
        type: DocumentType = DocumentType.INVOICE,
        status: DocumentStatus = DocumentStatus.PENDING
    ): Document {

        logger.info("Creating document for collection $collectionId")
        val documentId = UUID.randomUUID().toString()
        return Document(
            id = documentId,
            name = name,
            path = filePath,
            type = type,
            status = status,
            collectionId = collectionId,
            parsedData = null,
            private = false,
            createdBy = tenant.tenantId,
            createdAt = System.currentTimeMillis(),
            permissions = mutableMapOf(
                userId to DocumentRole.OWNER
            )
        )
    }


}