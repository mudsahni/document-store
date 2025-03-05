package com.muditsahni.documentstore.util

import com.google.cloud.Timestamp
import com.google.cloud.firestore.Firestore
import com.muditsahni.documentstore.exception.MajorErrorCode
import com.muditsahni.documentstore.exception.throwable.CollectionNotFoundException
import com.muditsahni.documentstore.model.dto.response.GetDocumentResponse
import com.muditsahni.documentstore.model.enum.Tenant
import com.muditsahni.documentstore.model.entity.Collection
import com.muditsahni.documentstore.model.entity.document.toDocument
import com.muditsahni.documentstore.model.entity.document.toGetDocumentResponse
import com.muditsahni.documentstore.model.entity.toCollection
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

object CollectionHelper {

    private val logger = KotlinLogging.logger {}

    // Collection cache for better performance
    private val collectionCache = ConcurrentHashMap<String, Pair<Collection, Long>>()
    // Cache expiration time in milliseconds (2 minutes)
    private const val CACHE_EXPIRY_MS = 120000L

    // Timeout for firestore operations (10 seconds)
    private const val FIRESTORE_TIMEOUT_MS = 10000L

    suspend fun getCollection(
        firestore: Firestore,
        collectionId: String,
        tenant: Tenant
    ): Collection {
        val cacheKey = "${tenant.tenantId}:$collectionId"
        val now = System.currentTimeMillis()

        // Try cache first
        collectionCache[cacheKey]?.let { (cachedCollection, timestamp) ->
            if (now - timestamp < CACHE_EXPIRY_MS) {
                logger.debug { "Collection cache hit for $collectionId" }
                return cachedCollection.copy() // Return a copy to prevent modification of cached object
            }
            // Remove expired entry
            collectionCache.remove(cacheKey)
        }

        try {
            return withTimeout(FIRESTORE_TIMEOUT_MS) {

                val collectionRef = firestore
                    .collection("tenants")
                    .document(tenant.tenantId)
                    .collection("collections")
                    .document(collectionId)
                    .get()
                    .await()

                if (!collectionRef.exists()) {
                    logger.warn { "Collection not found: $collectionId for tenant ${tenant.tenantId}" }
                    throw CollectionNotFoundException(
                        MajorErrorCode.GEN_MAJ_COL_003.code,
                        MajorErrorCode.GEN_MAJ_COL_003.message
                    )
                }

                logger.info { "Collection fetched from Firestore: $collectionId" }
                val collection = collectionRef.toCollection()
                logger.info("Collection object fetched and converted to collection class")
                // Store in cache
                collectionCache[cacheKey] = collection to now
                collection
            }
        } catch (e: TimeoutCancellationException) {
            logger.error { "Timeout fetching collection $collectionId from Firestore" }
            throw CollectionNotFoundException(
                MajorErrorCode.GEN_MAJ_COL_003.code,
                "Timeout fetching collection: $collectionId"
            )
        } catch (e: Exception) {
            logger.error(e) { "Error fetching collection $collectionId from Firestore" }
            throw e
        }
    }

    suspend fun getCollectionDocuments(
        firestore: Firestore,
        collectionId: String,
        tenant: Tenant
    ): Map<String, GetDocumentResponse> {
        try {
            return withTimeout(FIRESTORE_TIMEOUT_MS * 2) {
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

                logger.info { "Retrieved ${documents.size} documents for collection $collectionId" }
                documentMap
            }
        } catch (e: TimeoutCancellationException) {
            logger.error { "Timeout fetching documents for collection $collectionId" }
            return emptyMap()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching documents for collection $collectionId" }
            return emptyMap()
        }
    }

    suspend fun saveCollection(
        firestore: Firestore,
        tenant: Tenant,
        collection: Collection
    ) {
        try {
            // Ensure collection has updated timestamp
            collection.updatedAt = Timestamp.now()

            // Update the cache first to ensure immediate consistency for subsequent reads
            val cacheKey = "${tenant.tenantId}:${collection.id}"
            collectionCache[cacheKey] = collection.copy() to System.currentTimeMillis()

            withTimeout(FIRESTORE_TIMEOUT_MS) {
                firestore
                    .collection("tenants")
                    .document(tenant.tenantId)
                    .collection("collections")
                    .document(collection.id)
                    .set(collection)
                    .await()
            }

            logger.info { "Collection ${collection.id} saved in Firestore" }
        } catch (e: TimeoutCancellationException) {
            logger.error { "Timeout saving collection ${collection.id} to Firestore" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error saving collection ${collection.id}" }
            throw e
        }
    }

    fun clearCache() {
        collectionCache.clear()
        logger.info { "Collection cache cleared" }
    }

    fun invalidateCache(tenantId: String, collectionId: String) {
        val cacheKey = "$tenantId:$collectionId"
        collectionCache.remove(cacheKey)
        logger.debug { "Cache invalidated for collection $collectionId" }
    }


}