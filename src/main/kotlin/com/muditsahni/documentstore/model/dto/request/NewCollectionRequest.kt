package com.muditsahni.documentstore.model.dto.request

import com.muditsahni.documentstore.model.enum.CollectionType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty

data class NewCollectionRequest(
    @field:NotEmpty(message = "Files are required")
    val files: Map<String, String>,
    @field:NotBlank(message = "Name is required")
    val name: String,
    @field:NotBlank(message = "Type is required")
    val type: CollectionType,
)