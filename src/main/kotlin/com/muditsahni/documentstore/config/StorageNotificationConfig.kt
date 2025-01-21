package com.muditsahni.documentstore.config

import com.google.cloud.storage.Notification
import com.google.cloud.storage.NotificationInfo
import com.google.cloud.storage.Storage
import com.google.pubsub.v1.TopicName
import jakarta.annotation.PostConstruct
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class StorageNotificationConfig(
    private val storageClient: Storage,
    @Value("\${gcp.project-id}") private val projectId: String,
    @Value("\${gcp.pubsub.uploads.topic}") private val topic: String,
    @Value("\${gcp.bucket.name}") private val bucketName: String
) {

    companion object {
        private val logger = KotlinLogging.logger {
            StorageNotificationConfig::class.java.name
        }
    }

    @PostConstruct
    fun configureStorageNotification(
    ) {
        try {

            val topicPath = "projects/$projectId/topics/$topic"
            logger.info { "Creating notification for bucket: $bucketName with topic: $topicPath" }

            storageClient.createNotification(
                bucketName,
                Notification.newBuilder(topicPath)
                    .setEventTypes(NotificationInfo.EventType.OBJECT_FINALIZE)
                    .build()
            )
            logger.info { "Successfully created notification" }
        } catch (e: Exception) {
            // Notification might already exist
            logger.warn {
                """
                Failed to create notification:
                Project ID: $projectId
                Topic: $topic
                Bucket: $bucketName
                Error: ${e.message}
                """.trimIndent()
            }
        }
    }
}