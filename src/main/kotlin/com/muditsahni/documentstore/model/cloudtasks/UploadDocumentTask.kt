package com.muditsahni.documentstore.model.cloudtasks

import com.muditsahni.documentstore.model.enum.FileType
import kotlinx.serialization.Serializable
import kotlin.jvm.javaClass
import kotlin.text.contentEquals

@Serializable
data class UploadDocumentTask(
    val uploadPath: String,
    val fileName: String,
    val collectionId: String,
    val documentId: String,
    val tenantId: String,
    val userId: String,
    val fileType: FileType,
    val fileSize: Long,
    val file: String,
    val callbackUrl: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UploadDocumentTask

        if (uploadPath != other.uploadPath) return false
        if (fileName != other.fileName) return false
        if (collectionId != other.collectionId) return false
        if (documentId != other.documentId) return false
        if (tenantId != other.tenantId) return false
        if (userId != other.userId) return false
        if (fileType != other.fileType) return false
        if (fileSize != other.fileSize) return false
        if (!file.contentEquals(other.file)) return false
        if (callbackUrl != other.callbackUrl) return false

        return true
    }

    override fun hashCode(): Int {
        var result = uploadPath.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + collectionId.hashCode()
        result = 31 * result + documentId.hashCode()
        result = 31 * result + tenantId.hashCode()
        result = 31 * result + userId.hashCode()
        result = 31 * result + fileType.hashCode()
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + file.hashCode()
        result = 31 * result + callbackUrl.hashCode()
        return result
    }
}