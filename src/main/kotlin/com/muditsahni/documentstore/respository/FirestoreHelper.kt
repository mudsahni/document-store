package com.muditsahni.documentstore.respository

import com.google.cloud.firestore.DocumentReference
import com.google.cloud.firestore.FieldValue
import com.google.cloud.firestore.Firestore
import mu.KotlinLogging
import java.sql.BatchUpdateException

/**
 * Represents different types of field updates
 */
sealed class FieldUpdate {
    data class Set(val value: Any?) : FieldUpdate()
    data class ArrayUnion(val elements: List<Any?>) : FieldUpdate()
    data class ArrayRemove(val elements: List<Any?>) : FieldUpdate()
    // For updating specific fields in a map
    data class MapUpdate(val key: String, val value: Any?) : FieldUpdate()
    // For merging multiple fields into a map
    data class MapMerge(val fields: Map<String, Any?>) : FieldUpdate()
}
data class CreatedDocument(
    val collectionPath: String,
    val documentId: String
)


data class DocumentUpdate(
    val collectionPath: String,
    val documentId: String,
    val fields: Map<String, FieldUpdate>
)

data class DocumentUpdateError(
    val collectionPath: String,
    val documentId: String,
    val error: String
)

data class BatchUpdateResult(
    val successCount: Int,
    val failureCount: Int,
    val failures: List<DocumentUpdateError>
)

// Data classes for request/response
data class DocumentCreate(
    val collectionPath: String,
    val documentId: String? = null, // Optional - will auto-generate if null
    val data: Any
)

data class BatchCreateResult(
    val successCount: Int,
    val failureCount: Int,
    val failures: List<DocumentCreateError>,
    val createdDocuments: List<CreatedDocument>
)

data class DocumentCreateError(
    val collectionPath: String,
    val documentId: String,
    val error: String
)

// Custom exceptions
class DocumentAlreadyExistsException(message: String) : RuntimeException(message)
class DocumentCreateException(message: String) : RuntimeException(message)
class BatchOperationException(message: String) : RuntimeException(message)

class DocumentNotFoundException(message: String) : RuntimeException(message)
class DocumentUpdateException(message: String) : RuntimeException(message)
class BatchUpdateException(message: String) : RuntimeException(message)


object FirestoreHelper {

    const val MAX_CHUNK_SIZE = 500
    private val logger = KotlinLogging.logger {
        FirestoreHelper::class.java.name
    }

    private fun convertToFirestoreUpdates(
        updates: Map<String, FieldUpdate>
    ): Map<String, Any?> {
        return updates.flatMap { (fieldPath, update) ->
            when (update) {
                is FieldUpdate.Set -> listOf(fieldPath to update.value)
                is FieldUpdate.ArrayUnion -> listOf(fieldPath to FieldValue.arrayUnion(*update.elements.toTypedArray()))
                is FieldUpdate.ArrayRemove -> listOf(fieldPath to FieldValue.arrayRemove(*update.elements.toTypedArray()))
                is FieldUpdate.MapUpdate -> listOf("$fieldPath.${update.key}" to update.value)
                is FieldUpdate.MapMerge -> update.fields.map { (key, value) ->
                    "$fieldPath.$key" to value
                }
            }
        }.toMap()
    }

    suspend fun batchUpdateDocuments(
        firestore: Firestore,
        updates: List<DocumentUpdate>
    ): BatchUpdateResult = try {
        var successCount = 0
        var failureCount = 0
        val failures = mutableListOf<DocumentUpdateError>()

        updates.chunked(500).forEach { batch ->
            val writeBatch = firestore.batch()

            batch.forEach { update ->
                val docRef = firestore.collection(update.collectionPath)
                    .document(update.documentId)

                try {
                    // Verify document exists
                    val snapshot = docRef.get().get()
                    if (!snapshot.exists()) {
                        failureCount++
                        failures.add(DocumentUpdateError(
                            update.collectionPath,
                            update.documentId,
                            "Document not found"
                        ))
                        return@forEach
                    }

                    // Convert updates to Firestore field updates
                    val updates = convertToFirestoreUpdates(update.fields)
                    writeBatch.update(docRef, updates)
                    successCount++
                } catch (e: Exception) {
                    failureCount++
                    failures.add(DocumentUpdateError(
                        update.collectionPath,
                        update.documentId,
                        e.message ?: "Unknown error"
                    ))
                }
            }

            writeBatch.commit().get()
        }

        BatchUpdateResult(
            successCount = successCount,
            failureCount = failureCount,
            failures = failures
        )
    } catch (e: Exception) {
        logger.error(e) { "Batch update failed" }
        throw BatchUpdateException("Batch update failed: ${e.message}")
    }

    /**
     * Creates multiple documents across different collections in batches
     */
    suspend fun batchCreateDocuments(
        firestore: Firestore,
        documents: List<DocumentCreate>
    ): BatchCreateResult = try {
        var successCount = 0
        var failureCount = 0
        val failures = mutableListOf<DocumentCreateError>()
        val createdDocuments = mutableListOf<CreatedDocument>()
        val documentsToVerify = mutableListOf<DocumentReference>()

        logger.info { "Starting batch creation for ${documents.size} documents" }

        // Process in batches of 500 (Firestore limit)
        documents.chunked(MAX_CHUNK_SIZE).forEachIndexed { batchIndex, batch ->
            logger.info { "Processing batch ${batchIndex + 1} with ${batch.size} documents" }

            val writeBatch = firestore.batch()

            batch.forEach { doc ->
                try {
                    val docRef = if (doc.documentId != null) {
                        firestore.collection(doc.collectionPath)
                            .document(doc.documentId)
                    } else {
                        firestore.collection(doc.collectionPath).document()
                    }

                    logger.info { "Adding to batch: Collection: ${doc.collectionPath}, DocID: ${docRef.id}, Data: ${doc.data}" }
                    writeBatch.set(docRef, doc.data)
                    documentsToVerify.add(docRef)
                    successCount++
                } catch (e: Exception) {
                    failureCount++
                    failures.add(DocumentCreateError(
                        doc.collectionPath,
                        doc.documentId ?: "unknown",
                        e.message ?: "Unknown error"
                    ))
                }
            }

            try {
                val commitResult = writeBatch.commit().get()
                logger.info("Commit result for batch ${batchIndex + 1}: $commitResult")
                logger.info { "Batch commit completed. Verifying documents..." }

                // Verify each document after commit
                documentsToVerify.forEach { docRef ->
                    val verificationSnapshot = docRef.get().get()
                    if (verificationSnapshot.exists()) {
                        logger.info { "Verified document created: ${docRef.path}" }
                        successCount++
                        createdDocuments.add(
                            CreatedDocument(
                                collectionPath = docRef.parent.path,
                                documentId = docRef.id
                            )
                        )
                    } else {
                        logger.error { "Failed to verify document: ${docRef.path}" }
                        failureCount++
                        failures.add(
                            DocumentCreateError(
                                docRef.parent.path,
                                docRef.id,
                                "Document not found after batch commit"
                            )
                        )
                    }
                }
                documentsToVerify.clear()

            } catch (e: Exception) {
                logger.error(e) { "Failed to commit batch ${batchIndex + 1}" }
                throw e
            }
        }

        BatchCreateResult(
            successCount = successCount,
            failureCount = failureCount,
            failures = failures,
            createdDocuments = createdDocuments
        )
    } catch (e: Exception) {
        logger.error(e) { "Batch creation failed" }
        throw BatchOperationException("Batch creation failed: ${e.message}")
    }

    /**
     * Flattens nested maps into dot notation for Firestore updates
     */
    private fun flattenUpdateMap(
        updates: Map<String, Any?>,
        prefix: String = ""
    ): MutableMap<String, Any?> {
        val result = mutableMapOf<String, Any?>()

        updates.forEach { (key, value) ->
            val newKey = if (prefix.isEmpty()) key else "$prefix.$key"

            when (value) {
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val nestedMap = value as Map<String, Any?>
                    result.putAll(flattenUpdateMap(nestedMap, newKey))
                }
                else -> result[newKey] = value
            }
        }

        return result
    }
}