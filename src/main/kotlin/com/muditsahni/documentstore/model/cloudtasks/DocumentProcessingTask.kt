package com.muditsahni.documentstore.model.cloudtasks

import com.muditsahni.documentstore.model.enum.DocumentType
import com.muditsahni.documentstore.model.enum.FileType
import kotlinx.serialization.Serializable

@Serializable
data class DocumentProcessingTask(
    val tenantId: String,
    val collectionId: String,
    val id: String,
    val type: DocumentType,
    val fileType: FileType,
    val url: String,
    val name: String,
    val callbackUrl: String
)