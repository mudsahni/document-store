package com.muditsahni.documentstore.util

import com.google.cloud.Timestamp
import com.google.cloud.firestore.Firestore
import com.muditsahni.documentstore.model.entity.User
import com.muditsahni.documentstore.model.entity.toUser
import com.muditsahni.documentstore.model.enum.Tenant
import mu.KotlinLogging

object UserHelper {

    private val logger = KotlinLogging.logger {
        UserHelper::class.java.name
    }

    suspend fun getUser(
        firestore: Firestore,
        userId: String,
        tenant: Tenant
    ): User {

        val userRef = firestore
            .collection("tenants")
            .document(tenant.tenantId)
            .collection("users")
            .document(userId)
            .get()
            .await()

        logger.info("User fetched from Firestore")
        val user = userRef.toUser()
        logger.info("User object fetched and converted to user class")
        return user
    }

    suspend fun saveUser(
        firestore: Firestore,
        tenant: Tenant,
        user: User
    ) {
        firestore
            .collection("tenants")
            .document(tenant.tenantId)
            .collection("users")
            .document(user.id)
            .set(user)
            .await()

        logger.info("User updated in Firestore")
    }

    suspend fun updateUserCollections(
        firestore: Firestore,
        userId: String,
        tenant: Tenant,
        collectionId: String
    ) {
        val user = getUser(firestore, userId, tenant)

        user.collections.add(collectionId)
        user.updatedAt = Timestamp.now()
        user.updatedBy = userId

        logger.info("User object updated with new collection: ${collectionId}")
        // update user in firestore
        saveUser(firestore, tenant, user)
    }

    suspend fun updateUserDocuments(
        firestore: Firestore,
        userId: String,
        tenant: Tenant,
        documentId: String
    ) {
        val user = getUser(firestore, userId, tenant)

        user.documents.add(documentId)
        user.updatedAt = Timestamp.now()
        user.updatedBy = userId

        logger.info("User object updated with new document: ${documentId}")
        // update user in firestore
        saveUser(firestore, tenant, user)
    }

    suspend fun updateUserCollections(
        firestore: Firestore,
        userId: String,
        tenant: Tenant,
        collectionIds: List<String>
    ) {
        val user = getUser(firestore, userId, tenant)

        user.collections.addAll(collectionIds)
        user.updatedAt = Timestamp.now()
        user.updatedBy = userId

        logger.info("User object updated with ${collectionIds.size} new collections.")

        // update user in firestore
        saveUser(firestore, tenant, user)
    }

    suspend fun updateUserDocuments(
        firestore: Firestore,
        userId: String,
        tenant: Tenant,
        documentIds: List<String>
    ) {
        val user = getUser(firestore, userId, tenant)

        user.documents.addAll(documentIds)
        user.updatedAt = Timestamp.now()
        user.updatedBy = userId

        logger.info("User object updated with ${documentIds.size} new documents")
        // update user in firestore
        saveUser(firestore, tenant, user)
    }
}