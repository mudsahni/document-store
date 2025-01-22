package com.muditsahni.documentstore.model.entity

import com.google.cloud.firestore.DocumentSnapshot
import com.muditsahni.documentstore.exception.DocumentError
import com.muditsahni.documentstore.model.enum.AIClient
import com.muditsahni.documentstore.model.enum.DocumentRole
import com.muditsahni.documentstore.model.enum.DocumentStatus
import com.muditsahni.documentstore.model.enum.DocumentType

data class Document(
    val id: String,
    val name: String,
    var path: String? = null,
    val type: DocumentType,
    val collectionId: String,
    var status: DocumentStatus = DocumentStatus.PENDING,
    var parsedData: String? = null,
    var private: Boolean,
    var error: DocumentError? = null,
    var permissions: MutableMap<String, DocumentRole> = mutableMapOf(),
    val createdBy: String,
    val createdAt: Long,
    var updatedBy: String? = null,
    var updatedAt: Long? = null
)

data class ClientDetails(
    val model: String,
    val client: AIClient
)

data class ParsedDataMetadata(
    val manual: Boolean,
    val image: Boolean,
    val multiPage: Boolean,
    val validation: Map<String, Any>,
    val clientDetails: List<ClientDetails>,
    var retryCount: Int = 0
)
data class ParsedData(
    val data: MutableMap<String, Any> = mutableMapOf(),
    val metadata: ParsedDataMetadata
)

fun DocumentSnapshot.toDocument(): Document {
    return Document(
        id = id,
        name = getString("name") ?: throw IllegalStateException("Document name not found"),
        path = getString("path"),
        type = DocumentType.fromString(getString("type") ?: throw IllegalStateException("Document type not found")),
        collectionId = getString("collectionId") ?: throw IllegalStateException("Document collectionId not found"),
        status = DocumentStatus.fromString(getString("status") ?: throw IllegalStateException("Document status not found")),
//        parsedData = get("parsedData") as ParsedData?,
        parsedData = getString("parsedData"),
        private = getBoolean("private") ?: throw IllegalStateException("Document private not found"),
        // TODO: Find a good way to do this
        permissions = get("permissions") as MutableMap<String, DocumentRole>,
        createdBy = getString("createdBy") ?: throw IllegalStateException("Document createdBy not found"),
        createdAt = getLong("createdAt") ?: throw IllegalStateException("Document createdAt not found"),
        updatedBy = getString("updatedBy"),
        updatedAt = getLong("updatedAt")
    )
}