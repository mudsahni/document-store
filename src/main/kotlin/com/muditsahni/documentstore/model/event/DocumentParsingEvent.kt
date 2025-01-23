package com.muditsahni.documentstore.model.event

import com.google.cloud.Timestamp
import com.muditsahni.documentstore.model.enum.DocumentStatus

data class DocumentParsingEvent(
    val status: DocumentStatus,
    val progress: Int? = null,
    val documentId: String,
    val timestamp: Timestamp? = Timestamp.now()
)