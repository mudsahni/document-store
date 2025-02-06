package com.muditsahni.documentstore.model.dto.response

import com.google.cloud.Timestamp
import com.muditsahni.documentstore.model.enum.CollectionStatus
import com.muditsahni.documentstore.model.enum.CollectionType
import com.muditsahni.documentstore.model.enum.DocumentStatus

data class GetCollectionResponse(
    val id: String,
    val name: String,
    val type: CollectionType,
    val status: CollectionStatus,
    val documents: Map<String, DocumentStatus>,
    val createdAt: Timestamp,
    val updatedAt: Timestamp? = null,
    val updatedBy: String? = null,
    val tags: Map<String, String> = emptyMap()
)
