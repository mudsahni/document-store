package com.muditsahni.documentstore.model.event

import com.google.cloud.Timestamp
import com.muditsahni.documentstore.exception.CollectionError
import com.muditsahni.documentstore.model.enum.CollectionStatus
import com.muditsahni.documentstore.model.enum.CollectionType
import com.muditsahni.documentstore.model.enum.DocumentStatus

data class CollectionStatusEvent(
    val id: String,
    val name: String,
    val status: CollectionStatus,
    val type: CollectionType,
    val documents: Map<String, DocumentStatus> = mapOf(),
    val error: CollectionError? = null,
    val timestamp: Timestamp = Timestamp.now()
)