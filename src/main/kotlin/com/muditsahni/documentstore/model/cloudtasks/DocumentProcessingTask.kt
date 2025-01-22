package com.muditsahni.documentstore.model.cloudtasks

import com.fasterxml.jackson.annotation.JsonProperty
import com.muditsahni.documentstore.model.enum.DocumentType
import com.muditsahni.documentstore.model.enum.FileType
import kotlinx.serialization.Serializable

@Serializable
data class DocumentProcessingTask(
    @JsonProperty("tenant_id")
    val tenantId: String,
    @JsonProperty("collection_id")
    val collectionId: String,
    val id: String,
    val type: DocumentType,
    @JsonProperty("file_type")
    val fileType: FileType,
    val url: String,
    val name: String,
    val prompt: String,
    @JsonProperty("callback_url")
    val callbackUrl: String
)