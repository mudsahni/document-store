package com.muditsahni.documentstore.model.dto.response

import com.muditsahni.documentstore.exception.CollectionError
import com.muditsahni.documentstore.model.entity.SignedUrlResponse
import com.muditsahni.documentstore.model.enum.CollectionStatus
import com.muditsahni.documentstore.model.enum.CollectionType
import kotlinx.serialization.Serializable

@Serializable
data class CreateCollectionResponse(
    val id: String,
    val name: String,
    val status: CollectionStatus,
    val type: CollectionType,
    val documents: Map<String, SignedUrlResponse> = mapOf(),
    val error: CollectionError? = null,
)