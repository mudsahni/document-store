package com.muditsahni.documentstore.model.dto.request

import com.muditsahni.documentstore.model.enum.UploadStatus

data class UploadCallbackRequest(
    val tenantId: String,
    val userId: String,
    val collectionId: String,
    val documentId: String,
    val uploadPath: String? = null,
    val status: UploadStatus,
    val error: String? = null,
)