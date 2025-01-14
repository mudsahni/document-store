package com.muditsahni.documentstore.model.dto.request

import com.google.cloud.Timestamp
import com.muditsahni.documentstore.model.enum.CollectionType

data class GetCollectionResponse(
    val id: String,
    val name: String,
    val type: CollectionType,
    val documents: List<String>,
    val createdAt: Timestamp,
    val updatedAt: Timestamp? = null
)
