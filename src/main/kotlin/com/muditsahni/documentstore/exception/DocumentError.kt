package com.muditsahni.documentstore.exception

import kotlinx.serialization.Serializable

@Serializable
data class DocumentError(
    val message: String,
    val code: String
)