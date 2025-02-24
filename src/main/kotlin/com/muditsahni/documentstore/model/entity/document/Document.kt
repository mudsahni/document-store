package com.muditsahni.documentstore.model.entity.document

import com.google.cloud.firestore.DocumentSnapshot
import com.muditsahni.documentstore.config.getObjectMapper
import com.muditsahni.documentstore.exception.DocumentError
import com.muditsahni.documentstore.exception.ValidationError
import com.muditsahni.documentstore.model.dto.response.GetDocumentResponse
import com.muditsahni.documentstore.model.entity.document.type.InvoiceWrapper
import com.muditsahni.documentstore.model.enum.AIClient
import com.muditsahni.documentstore.model.enum.DocumentRole
import com.muditsahni.documentstore.model.enum.DocumentStatus
import com.muditsahni.documentstore.model.enum.DocumentType
import java.lang.IllegalStateException


data class Document(
    val id: String,
    val name: String,
    var path: String? = null,
    val type: DocumentType,
    val collectionId: String,
    var status: DocumentStatus = DocumentStatus.PENDING,
    var data: StructuredData? = null,
    var private: Boolean,
    var error: DocumentError? = null,
    var permissions: MutableMap<String, DocumentRole> = mutableMapOf(),
    val createdBy: String,
    val createdAt: Long,
    var updatedBy: String? = null,
    var updatedAt: Long? = null,
    var tags: Map<String, String> = emptyMap()
)

data class ClientDetails(
    val model: String,
    val client: AIClient
)

data class StructuredData(
    var raw: String? = null,
    var structured: InvoiceWrapper? = null,
    var errors: Map<String, ValidationError> = emptyMap()
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

fun Document.toGetDocumentResponse(): GetDocumentResponse {
    return GetDocumentResponse(
        id = id,
        name = name,
        path = path,
        type = type,
        collectionId = collectionId,
        status = status,
        data = data,
        private = private,
        permissions = permissions,
        createdBy = createdBy,
        createdAt = createdAt,
        updatedBy = updatedBy,
        updatedAt = updatedAt,
        tags = tags
    )
}

fun DocumentSnapshot.toDocument(): Document {
    val objectMapper = getObjectMapper()
    val dataMap = get("data") as? HashMap<String, Any?>
    val structuredData = dataMap?.let { map ->
        try {
            // Convert HashMap to JSON string first
            val jsonString = objectMapper.writeValueAsString(map)
            // Then parse JSON to StructuredData
            objectMapper.readValue(jsonString, StructuredData::class.java)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to parse structured data: ${e.message}")
        }
    }

    return Document(
        id = id,
        name = getString("name") ?: throw IllegalStateException("Document name not found"),
        path = getString("path"),
        type = DocumentType.fromString(getString("type") ?: throw IllegalStateException("Document type not found")),
        collectionId = getString("collectionId") ?: throw IllegalStateException("Document collectionId not found"),
        status = DocumentStatus.fromString(
            getString("status") ?: throw IllegalStateException("Document status not found")
        ),
        data = structuredData,
//        parsedData = get("parsedData") as ParsedData?,
//        parsedData = getString("parsedData"),
        private = getBoolean("private") ?: throw IllegalStateException("Document private not found"),
        // TODO: Find a good way to do this
        permissions = get("permissions") as MutableMap<String, DocumentRole>,
        createdBy = getString("createdBy") ?: throw IllegalStateException("Document createdBy not found"),
        createdAt = getLong("createdAt") ?: throw IllegalStateException("Document createdAt not found"),
        updatedBy = getString("updatedBy"),
        updatedAt = getLong("updatedAt"),
        tags = get("tags") as? Map<String, String> ?: mapOf()
    )
}