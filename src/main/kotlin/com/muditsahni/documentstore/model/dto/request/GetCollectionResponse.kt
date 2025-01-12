package com.muditsahni.documentstore.model.dto.request

import com.muditsahni.documentstore.model.enum.CollectionType

data class GetCollectionResponse(
    val id: String,
    val name: String,
    val type: CollectionType,
    val documents: List<String>,
    val createdAt: Long,
    val updatedAt: Long? = null
)
