package com.muditsahni.documentstore.model.dto.request

import com.muditsahni.documentstore.exception.DocumentError
import com.muditsahni.documentstore.model.enum.DocumentType
import kotlinx.serialization.Serializable

@Serializable
data class ProcessDocumentCallbackRequest(
    val id: String,
    val name: String,
    val path: String,
    val type: DocumentType,
    val parsedData: String? = null,
    val metadata: Map<String, String>,
    val error: DocumentError? = null,
)
