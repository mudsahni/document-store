package com.muditsahni.documentstore.model.dto.response

import com.google.cloud.Timestamp
import com.muditsahni.documentstore.model.enum.CollectionStatus
import com.muditsahni.documentstore.model.enum.CollectionType

data class GetCollectionWithDocumentsResponse(
    val id: String,
    val name: String,
    val type: CollectionType,
    val status: CollectionStatus,
    var documents: Map<String, GetDocumentResponse> = emptyMap(),
    val createdAt: Timestamp,
    val createdBy: String,
    val updatedAt: Timestamp? = null,
    val updatedBy: String? = null,
    val tags: Map<String, String> = emptyMap()
)
