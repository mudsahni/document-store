package com.muditsahni.documentstore.util

import com.google.cloud.Timestamp
import com.google.cloud.firestore.Firestore
import com.muditsahni.documentstore.model.enum.Tenant
import com.muditsahni.documentstore.model.entity.Collection
import com.muditsahni.documentstore.model.entity.toCollection
import com.muditsahni.documentstore.model.enum.CollectionStatus
import com.muditsahni.documentstore.model.enum.DocumentStatus

import mu.KotlinLogging

object CollectionHelper {

    private val logger = KotlinLogging.logger {
        CollectionHelper::class.java.name
    }

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

        logger.info("Collection fetched from Firestore")
        val collection = collectionRef.toCollection()
        logger.info("Collection object fetched and converted to collection class")
        return collection
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
        collection.updatedBy = collection.createdBy

        logger.info("Collection object updated with ${documentIds.size} new documents")
        // update collection in firestore
        saveCollection(firestore, tenant, collection)
    }

    suspend fun updateCollectionDocuments(
        firestore: Firestore,
        tenant: Tenant,
        collectionId: String,
        documentId: String,
        documentStatus: DocumentStatus
    ) {

        val collection = getCollection(firestore, collectionId, tenant)

        if (collection.status == CollectionStatus.RECIEVED) {
            collection.status = CollectionStatus.IN_PROGRESS
        }

        collection.documents[documentId] = documentStatus
        collection.updatedAt = Timestamp.now()
        collection.updatedBy = collection.createdBy

        logger.info("Collection object updated with new document: $documentId")
        // update collection in firestore
        saveCollection(firestore, tenant, collection)
    }


}