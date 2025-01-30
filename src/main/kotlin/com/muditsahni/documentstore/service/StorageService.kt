package com.muditsahni.documentstore.service

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Bucket
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import com.muditsahni.documentstore.model.entity.SignedUrlResponse
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit

@Service
class StorageService(
    private val storageClient: Storage,
    @Value("\${gcp.bucket.name}") private val bucketName: String
) {

    companion object {
        private val logger = KotlinLogging.logger {
            StorageService::class.java.name
        }
    }

    suspend fun getSignedUrlForDocumentUpload(
        documentId: String,
        path: String,
        filename: String,
        contentType: String,
    ): SignedUrlResponse {
        logger.info("Generating signed URL for document upload")
        val bucket = storageClient.get(bucketName)
        val blobId = BlobId.of(bucketName, "${path}/${filename}")
        val blobInfo = BlobInfo.newBuilder(blobId).setContentType(contentType).build()

        val signedUrl = storageClient.signUrl(
            blobInfo,
            15, // URL valid for 15 minutes
            TimeUnit.MINUTES,
            Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
            Storage.SignUrlOption.withV4Signature()
        )

        return SignedUrlResponse(
            uploadUrl = signedUrl.toString(),
            fileName = filename,
            documentId = documentId
        )
    }

    suspend fun getFileUrl(tenantId: String, collectionId: String, documentId: String, fileName: String): String {
        val blobId = BlobId.of(bucketName, "${tenantId}/${collectionId}/${documentId}/${fileName}")
        val blob = storageClient.get(blobId)
        return blob?.signUrl(1, TimeUnit.HOURS)?.toString()
            ?: throw FileNotFoundException("File not found: $fileName")
    }

    suspend fun deleteFile(tenantId: String, collectionId: String, fileName: String) {
        val blobId = BlobId.of(bucketName, "${tenantId}/${collectionId}/${fileName}")
        if (!storageClient.delete(blobId)) {
            throw FileNotFoundException("File not found: $fileName")
        }
    }

    suspend fun fileExists(tenantId: String, collectionId: String, fileName: String): Boolean {
        val blobId = BlobId.of(bucketName, "${tenantId}/${collectionId}/${fileName}")
        return storageClient.get(blobId)?.exists() ?: false
    }

}