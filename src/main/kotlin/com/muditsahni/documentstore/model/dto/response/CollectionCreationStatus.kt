package com.muditsahni.documentstore.model.dto.response

import com.google.cloud.Timestamp
import com.muditsahni.documentstore.exception.CollectionError
import com.muditsahni.documentstore.model.enum.CollectionStatus
import com.muditsahni.documentstore.model.enum.CollectionType
import com.muditsahni.documentstore.model.enum.DocumentStatus

data class CollectionCreationStatus(
    val id: String,
    val name: String,
    val status: CollectionStatus,
    val type: CollectionType,
    val documents: Map<String, DocumentStatus> = mapOf(),
    val error: CollectionError? = null,
    val createdAt: Timestamp,
    val updatedAt: Timestamp? = null
)