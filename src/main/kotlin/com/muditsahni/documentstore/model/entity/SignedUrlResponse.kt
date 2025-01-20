package com.muditsahni.documentstore.model.entity

import kotlinx.serialization.Serializable

@Serializable
data class SignedUrlResponse(
    val uploadUrl: String,
    val fileName: String,
    val documentId: String
)