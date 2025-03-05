package com.muditsahni.documentstore.util

import com.google.cloud.firestore.Firestore
import com.muditsahni.documentstore.exception.MajorErrorCode
import com.muditsahni.documentstore.exception.throwable.DocumentNotFoundException
import com.muditsahni.documentstore.model.entity.document.Document
import com.muditsahni.documentstore.model.entity.document.toDocument
import com.muditsahni.documentstore.model.enum.DocumentRole
import com.muditsahni.documentstore.model.enum.DocumentStatus
import com.muditsahni.documentstore.model.enum.DocumentType
import com.muditsahni.documentstore.model.enum.Tenant
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object DocumentHelper {

    private val logger = KotlinLogging.logger {}
    // Cache to avoid repeated Firestore fetches for the same document
    private val documentCache = ConcurrentHashMap<String, Pair<Document, Long>>()

    // Cache expiration time in milliseconds (5 minutes)
    private const val CACHE_EXPIRY_MS = 300000L

    // Timeout for firestore operations (10 seconds)
    private const val FIRESTORE_TIMEOUT_MS = 10000L

    suspend fun getDocument(
        firestore: Firestore,
        documentId: String,
        tenant: Tenant
    ): Document {
        val cacheKey = "${tenant.tenantId}:$documentId"
        val now = System.currentTimeMillis()

        // Try to get from cache first
        documentCache[cacheKey]?.let { (cachedDoc, timestamp) ->
            if (now - timestamp < CACHE_EXPIRY_MS) {
                logger.debug { "Document cache hit for $documentId" }
                return cachedDoc.copy() // Return a copy to prevent modification of cached object
            }
            // Remove expired entry
            documentCache.remove(cacheKey)
        }

        try {
            // Fetch with timeout to avoid hanging operations
            return withTimeout(FIRESTORE_TIMEOUT_MS) {
                val documentRef = firestore
                    .collection("tenants")
                    .document(tenant.tenantId)
                    .collection("documents")
                    .document(documentId)
                    .get()
                    .await()

                if (!documentRef.exists()) {
                    logger.warn { "Document not found: $documentId for tenant ${tenant.tenantId}" }
                    throw DocumentNotFoundException(
                        MajorErrorCode.GEN_MAJ_DOC_003.code,
                        "Document not found: $documentId"
                    )
                }

                logger.info { "Document fetched from Firestore: $documentId" }
                val document = documentRef.toDocument()

                // Store in cache
                documentCache[cacheKey] = document to now

                document
            }
        } catch (e: TimeoutCancellationException) {
            logger.error { "Timeout fetching document $documentId from Firestore" }
            throw DocumentNotFoundException(
                MajorErrorCode.GEN_MAJ_DOC_003.code,
                "Timeout fetching document: $documentId"
            )
        } catch (e: Exception) {
            logger.error(e) { "Error fetching document $documentId from Firestore" }
            throw e
        }
    }


    suspend fun saveDocument(
        firestore: Firestore,
        tenant: Tenant,
        document: Document
    ) {
        try {
            // Ensure document has updated timestamp if not set
            if (document.updatedAt == null) {
                document.updatedAt = System.currentTimeMillis()
            }

            // Update the cache with the new document
            val cacheKey = "${tenant.tenantId}:${document.id}"
            documentCache[cacheKey] = document.copy() to System.currentTimeMillis()

            withTimeout(FIRESTORE_TIMEOUT_MS) {
                firestore
                    .collection("tenants")
                    .document(tenant.tenantId)
                    .collection("documents")
                    .document(document.id)
                    .set(document)
                    .await()
            }

            logger.info { "Document ${document.id} saved in Firestore" }
        } catch (e: TimeoutCancellationException) {
            logger.error { "Timeout saving document ${document.id} to Firestore" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Error saving document ${document.id}" }
            throw e
        }
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
        logger.info { "Creating document for collection $collectionId" }

        val documentId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        return Document(
            id = documentId,
            name = name,
            path = filePath,
            type = type,
            status = status,
            collectionId = collectionId,
            data = null,
            private = false,
            createdBy = userId, // Note: Changed from tenant.tenantId to userId for better tracking
            createdAt = now,
            updatedAt = now,
            updatedBy = userId,
            permissions = mutableMapOf(
                userId to DocumentRole.OWNER
            ),
            tags = mutableMapOf() // Initialize empty tags map
        )
    }


    fun addTagsToInvoiceDocument(
        document: Document
    ): Document {
        if (document.type != DocumentType.INVOICE) {
            logger.warn { "Cannot add invoice tags to non-invoice document: ${document.id}" }
            return document
        }

        if (document.data?.structured == null) {
            logger.warn { "Cannot add tags to document without structured data: ${document.id}" }
            return document
        }

        val NA = "N/A"
        try {
            val invoice = document.data?.structured?.invoice
            val tags = document.tags as? MutableMap ?: mutableMapOf()

            // Add basic invoice information
            tags["vendor"] = invoice?.vendor?.name ?: NA
            tags["customer"] = invoice?.customer?.name ?: NA
            tags["billing_date"] = invoice?.billingDate ?: NA
            tags["invoice_number"] = invoice?.invoiceNumber ?: NA
            tags["amount"] = invoice?.billedAmount?.total?.toString() ?: NA

            // Add line item information
            invoice?.lineItems?.forEachIndexed { index, item ->
                tags["line_item_${index}_hsn_sac"] = item.hsnSac ?: NA
                tags["line_item_${index}_description"] = item.description?.take(50) ?: NA
            }

            document.tags = tags
            return document
        } catch (e: Exception) {
            logger.error(e) { "Error adding tags to document ${document.id}" }
            // Return document without failing if tag addition has issues
            return document
        }
    }

    fun clearCache() {
        documentCache.clear()
        logger.info { "Document cache cleared" }
    }

    fun invalidateCache(tenantId: String, documentId: String) {
        val cacheKey = "$tenantId:$documentId"
        documentCache.remove(cacheKey)
        logger.debug { "Cache invalidated for document $documentId" }
    }
    suspend fun batchCreateDocuments(
        firestore: Firestore,
        tenant: Tenant,
        documents: List<Document>
    ) {
        if (documents.isEmpty()) return

        try {
            val batch = firestore.batch()

            documents.forEach { document ->
                val docRef = firestore
                    .collection("tenants")
                    .document(tenant.tenantId)
                    .collection("documents")
                    .document(document.id)

                batch.set(docRef, document)

                // Update cache
                val cacheKey = "${tenant.tenantId}:${document.id}"
                documentCache[cacheKey] = document.copy() to System.currentTimeMillis()
            }

            withTimeout(FIRESTORE_TIMEOUT_MS * 2) { // Double timeout for batch operations
                batch.commit().await()
            }

            logger.info { "Batch created ${documents.size} documents" }
        } catch (e: Exception) {
            logger.error(e) { "Error in batch document creation" }
            throw e
        }
    }

}