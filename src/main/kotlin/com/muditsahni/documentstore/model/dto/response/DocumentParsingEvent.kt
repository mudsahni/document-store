package com.muditsahni.documentstore.model.dto.response

import com.muditsahni.documentstore.model.enum.DocumentStatus

data class DocumentParsingEvent(
    val status: DocumentStatus,
    val progress: Int? = null,
    val documentId: String,
    val timestamp: Long? = System.currentTimeMillis()
)