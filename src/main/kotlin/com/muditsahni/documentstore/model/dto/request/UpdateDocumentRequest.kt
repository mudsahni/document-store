package com.muditsahni.documentstore.model.dto.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.muditsahni.documentstore.model.entity.document.StructuredData
import jakarta.validation.constraints.NotEmpty

data class UpdateDocumentRequest(
    @JsonProperty("data")
    @field:NotEmpty(message = "Data is required")
    val data: StructuredData
)
