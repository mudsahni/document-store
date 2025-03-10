package com.muditsahni.documentstore.util

import com.google.cloud.Timestamp
import com.google.cloud.firestore.Firestore
import com.muditsahni.documentstore.exception.MajorErrorCode
import com.muditsahni.documentstore.exception.throwable.CollectionNotFoundException
import com.muditsahni.documentstore.model.dto.response.GetDocumentResponse
import com.muditsahni.documentstore.model.enum.Tenant
import com.muditsahni.documentstore.model.entity.Collection
import com.muditsahni.documentstore.model.entity.SYSTEM_USER
import com.muditsahni.documentstore.model.entity.SignedUrlResponse
import com.muditsahni.documentstore.model.entity.document.toDocument
import com.muditsahni.documentstore.model.entity.document.toGetDocumentResponse
import com.muditsahni.documentstore.model.entity.toCollection
import com.muditsahni.documentstore.model.enum.CollectionStatus
import com.muditsahni.documentstore.model.enum.DocumentStatus
import com.muditsahni.documentstore.model.enum.DocumentType
import com.muditsahni.documentstore.service.EventStreamService
import com.muditsahni.documentstore.service.StorageService

import mu.KotlinLogging

object CollectionHelper {

    private val logger = KotlinLogging.logger {}

    suspend fun getCollection(
        firestore: Firestore,
        collectionId: String,
        tenant: Tenant
    ): Collection {

        val collectionRef = firestore
            .collection("tenants")
            .document(tenant.tenantId)
            .collection("collections")
            .document(collectionId)
            .get()
            .await()

        if (!collectionRef.exists()) {
            throw CollectionNotFoundException(MajorErrorCode.GEN_MAJ_COL_003.code, MajorErrorCode.GEN_MAJ_COL_003.message)
        }

        logger.info("Collection fetched from Firestore")
        val collection = collectionRef.toCollection()
        logger.info("Collection object fetched and converted to collection class")
        return collection
    }

    suspend fun getCollectionDocuments(
        firestore: Firestore,
        collectionId: String,
        tenant: Tenant
    ): Map<String, GetDocumentResponse> {

        val documents = firestore
            .collection("tenants")
            .document(tenant.tenantId)
            .collection("documents")
            .whereEqualTo("collectionId", collectionId)
            .get()
            .await()
            .documents
            .map { it.toDocument() }

        val documentMap = documents.associateBy({ it.id }, { it.toGetDocumentResponse() })

        logger.info("Collection fetched with documents from Firestore")
        return documentMap
    }

    suspend fun saveCollection(
        firestore: Firestore,
        tenant: Tenant,
        collection: Collection
    ) {
        firestore
            .collection("tenants")
            .document(tenant.tenantId)
            .collection("collections")
            .document(collection.id)
            .set(collection)
            .await()

        logger.info("Collection updated in Firestore")
    }

    suspend fun updateCollectionDocuments(
        firestore: Firestore,
        tenant: Tenant,
        collectionId: String,
        documentIds: Map<String, DocumentStatus>
    ) {

        val collection = getCollection(firestore, collectionId, tenant)

        collection.documents.putAll(documentIds)
        collection.updatedAt = Timestamp.now()
        collection.updatedBy = SYSTEM_USER

        logger.info("Collection object updated with ${documentIds.size} new documents")
        // update collection in firestore
        saveCollection(firestore, tenant, collection)
    }

    suspend fun updateCollectionDocuments(
        firestore: Firestore,
        tenant: Tenant,
        collectionId: String,
        documentId: String,
        documentStatus: DocumentStatus,
        eventService: EventStreamService? = null
    ): Collection {

        val collection = getCollection(firestore, collectionId, tenant)

        if (collection.status == CollectionStatus.RECEIVED) {
            collection.status = CollectionStatus.IN_PROGRESS
        }

        collection.documents[documentId] = documentStatus

        if (collection.status == CollectionStatus.IN_PROGRESS) {
            val inProgressDocuments = collection.documents.count { it.value == DocumentStatus.IN_PROGRESS }
            if (inProgressDocuments == collection.documents.size) {
                eventService?.completeStream(collectionId)
            }
            val notPendingDocuments = collection.documents.count { it.value != DocumentStatus.PENDING }
            if (notPendingDocuments == 0 || notPendingDocuments+1 == collection.documents.size) {
                collection.status = CollectionStatus.DOCUMENTS_UPLOAD_COMPLETE
            }
        }

        collection.updatedAt = Timestamp.now()
        collection.updatedBy = SYSTEM_USER

        logger.info("Collection object updated with new document: $documentId")
        // update collection in firestore
        saveCollection(firestore, tenant, collection)

        return collection
    }


    suspend fun updateCollectionStatus(
        firestore: Firestore,
        tenant: Tenant,
        collectionId: String,
        status: CollectionStatus
    ) {

        val collection = getCollection(firestore, collectionId, tenant)

        collection.status = status
        collection.updatedAt = Timestamp.now()
        collection.updatedBy = SYSTEM_USER

        logger.info("Collection object updated with new status: $status")
        // update collection in firestore
        saveCollection(firestore, tenant, collection)
    }

    suspend fun createAndSaveDocumentsForUpload(
        firestore: Firestore,
        storageService: StorageService,
        userId: String,
        tenant: Tenant,
        collectionId: String,
        documents: Map<String, String>
    ): Map<String, SignedUrlResponse> {

        val documentIdsWithSignedUrls = mutableMapOf<String, SignedUrlResponse>()
        val documentIdsWithStatus = mutableMapOf<String, DocumentStatus>()

        documents.forEach {

            // create document object
            val document = DocumentHelper.createDocumentObject(
                userId = userId,
                name = it.key, // document name
                collectionId = collectionId,
                tenant = tenant,
                filePath = "${tenant.tenantId}/${collectionId}/${it.key}",
                type = DocumentType.INVOICE,
                status = DocumentStatus.PENDING
            )

            // save document
            DocumentHelper.saveDocument(firestore, tenant, document)
            documentIdsWithStatus[document.id] = DocumentStatus.PENDING
            documentIdsWithSignedUrls[document.id] = storageService.getSignedUrlForDocumentUpload(
                document.id,
                "${tenant.tenantId}/${collectionId}/${document.id}",
                it.key,
                it.value
            )
        }

        // update collection
        updateCollectionDocuments(
            firestore,
            tenant,
            collectionId,
            documentIdsWithStatus
        )

        // update user
        UserHelper.updateUserDocuments(
            firestore,
            userId,
            tenant,
            documentIdsWithStatus.keys.toList()
        )

        return documentIdsWithSignedUrls

    }

}