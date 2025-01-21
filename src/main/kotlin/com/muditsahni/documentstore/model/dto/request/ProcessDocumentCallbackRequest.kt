package com.muditsahni.documentstore.model.dto.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.muditsahni.documentstore.exception.DocumentError
import com.muditsahni.documentstore.model.enum.DocumentType
import kotlinx.serialization.Serializable

@Serializable
data class ProcessDocumentCallbackRequest(
    val id: String,
    val name: String,
    val type: DocumentType,
    @JsonProperty("parsed_data")
    val parsedData: String? = null,
    val metadata: Map<String, String>,
    val error: DocumentError? = null,
)
