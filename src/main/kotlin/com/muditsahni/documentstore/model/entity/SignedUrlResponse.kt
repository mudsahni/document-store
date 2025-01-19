package com.muditsahni.documentstore.model.entity

data class SignedUrlResponse(
    val uploadUrl: String,
    val fileName: String,
    val documentId: String
)