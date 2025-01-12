package com.muditsahni.documentstore.model.dto.request

data class DocumentProcessingTask(
    val documentId: String,
    val documentType: String,
    val documentUrl: String,
    val documentName: String,
    val documentSize: Long,
    val documentContentType: String,
    val documentMetadata: Map<String, String>,
    val documentProcessingType: String,
    val documentProcessingOptions: Map<String, String>
)