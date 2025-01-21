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
        private val logger = KotlinLogging.logger {}
    }

    @PostConstruct
    fun configureStorageNotification() {
        try {
            val topicPath = "projects/$projectId/topics/$topic"

            // Check existing notifications
            val existingNotifications = storageClient.listNotifications(bucketName)
            val hasMatchingNotification = existingNotifications.any { notification ->
                notification.topic == topicPath &&
                        notification.eventTypes.contains(NotificationInfo.EventType.OBJECT_FINALIZE)
            }

            if (hasMatchingNotification) {
                logger.info { "Notification already exists for bucket: $bucketName with topic: $topicPath" }
                return
            }

            // Create new notification only if none exists
            logger.info { "Creating new notification for bucket: $bucketName with topic: $topicPath" }
            storageClient.createNotification(
                bucketName,
                Notification.newBuilder(topicPath)
                    .setEventTypes(NotificationInfo.EventType.OBJECT_FINALIZE)
                    .build()
            )
            logger.info { "Successfully created notification" }

        } catch (e: Exception) {
            logger.error(e) {
                """
                Failed to configure notification:
                Project ID: $projectId
                Topic: $topic
                Bucket: $bucketName
                """.trimIndent()
            }
        }
    }
}