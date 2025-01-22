package com.muditsahni.documentstore.model.entity

import kotlinx.serialization.Serializable

@Serializable
data class PromptTemplate(
    val prompt: String,
    val template: String
)
