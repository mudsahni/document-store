package com.muditsahni.documentstore.model.dto.response

data class DocumentDownloadResponse(
    val documentId: String,
    val collectionId: String,
    val tenantId: String,
    val fileName: String,
    val fileType: String,
    val ttl: Int,
    val downloadUrl: String
)
