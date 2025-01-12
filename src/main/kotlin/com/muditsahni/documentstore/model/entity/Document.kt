package com.muditsahni.documentstore.model.entity

import com.muditsahni.documentstore.exception.DocumentError
import com.muditsahni.documentstore.model.enum.AIClient
import com.muditsahni.documentstore.model.enum.DocumentRole
import com.muditsahni.documentstore.model.enum.DocumentType

data class Document(
    val id: String,
    val name: String,
    val path: String,
    val size: Long,
    val type: DocumentType,
    val collectionId: String,
    val collectionName: String,
    var metadata: DocumentMetadata,
    var data: MutableMap<String, Any> = mutableMapOf(),
    var private: Boolean,
    var error: DocumentError? = null,
    var permissions: MutableMap<String, DocumentRole> = mutableMapOf(),
    val createdBy: String,
    val createdDate: Long,
    var updatedBy: String? = null,
    var updatedDate: Long? = null
)

data class ClientDetails(
    val model: String,
    val client: AIClient
)
data class DocumentMetadata(
    val manual: Boolean,
    val image: Boolean,
    val multiPage: Boolean,
    val validation: Map<String, Any>,
    val clientDetails: List<ClientDetails>,
    var retryCount: Int = 0
)