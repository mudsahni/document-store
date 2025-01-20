package com.muditsahni.documentstore.config

import com.google.cloud.pubsub.v1.MessageReceiver
import com.google.cloud.pubsub.v1.Publisher
import com.google.cloud.pubsub.v1.Subscriber
import com.google.pubsub.v1.ProjectSubscriptionName
import com.google.pubsub.v1.TopicName
import com.muditsahni.documentstore.model.entity.StorageEvent
import com.muditsahni.documentstore.service.impl.DefaultCollectionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PubSubConfig(
    private val scope: CoroutineScope // Inject a CoroutineScope
) {

    @Autowired
    private lateinit var collectionsService: DefaultCollectionService


    companion object {
        private val logger = KotlinLogging.logger {
            PubSubConfig::class.java.name
        }

        private val objectMapper = getObjectMapper()
    }
    @Bean
    fun pubSubPublisher(
        @Value("\${gcp.project-id}") projectId: String,
        @Value("\${gcp.pubsub.uploads.topic}") topic: String
    ): Publisher {
        return Publisher.newBuilder(
            TopicName.of(projectId, topic)
        ).build()
    }

    @Bean
    fun pubSubSubscriber(
        @Value("\${gcp.project-id}") projectId: String,
        @Value("\${gcp.pubsub.uploads.subscription}") subscription: String
    ): Subscriber {
        val subscriptionName = ProjectSubscriptionName.of(projectId, subscription)
        val subscriber = Subscriber.newBuilder(subscriptionName, MessageReceiver { message, consumer ->
            // Launch the processing in a coroutine
            scope.launch {
                try {
                    val messageData = message.data.toStringUtf8()
                    logger.info("This is the message: ${messageData}")
                    val event = objectMapper.readValue(messageData, StorageEvent::class.java)

                    logger.info("Received storage notification for file: ${event.name}")
                    // Extract tenant and collection IDs from path

                    // Process the uploaded file
                    collectionsService.processStorageEvent(event)
                    consumer.ack()
                } catch (e: Exception) {
                    logger.error("Error processing message: ${e.message}")
                    consumer.nack()
                }
            }
        }).build()

        // IMPORTANT: Start the subscriber, otherwise no messages will be pulled!
        subscriber.startAsync().awaitRunning()

        return subscriber
    }
}