package com.muditsahni.documentstore.service

import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class StorageService(
    private val storage: Storage,
    @Value("\${gcp.storage.bucket}") private val bucketName: String
) {

    fun uploadFile(
        tenantId: String,
        jobId: String,
        file: MultipartFile
    ): String {
        val blobName = "$tenantId/$jobId/${file.originalFilename}"
        val blobInfo = BlobInfo.newBuilder(bucketName, blobName)
            .setContentType(file.contentType)
            .build()

        storage.create(blobInfo, file.bytes)
        return blobName
    }

    fun uploadFiles(tenantId: String, jobId: String, files: List<MultipartFile>): List<String> {
        return files.map { uploadFile(tenantId, jobId, it) }
    }

}