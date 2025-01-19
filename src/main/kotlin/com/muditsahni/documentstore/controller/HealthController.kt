package com.muditsahni.documentstore.controller

import com.google.cloud.pubsub.v1.Subscriber
import com.google.pubsub.v1.ProjectSubscriptionName
import kotlinx.coroutines.reactor.mono
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
class HealthController {
    @GetMapping("/health")
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("status" to "UP"))
    }

    @Value("\${gcp.project-id}")
    private lateinit var projectId: String

    @Value("\${gcp.pubsub.uploads.subscription}")
    private lateinit var subscriptionId: String

    @GetMapping("/pull-messages")
    fun pullMessages(): Mono<String> = mono {
        val subscriptionName = ProjectSubscriptionName.of(projectId, subscriptionId)
        val messageReceiver = { message: com.google.pubsub.v1.PubsubMessage, consumer: com.google.cloud.pubsub.v1.AckReplyConsumer ->
            println("Received message: ${message.data.toStringUtf8()}")
            consumer.ack()
        }

        val subscriber = Subscriber.newBuilder(subscriptionName, messageReceiver).build()
        try {
            subscriber.startAsync().awaitRunning()
            // Wait briefly to allow message processing
            kotlinx.coroutines.delay(10000)
            subscriber.stopAsync().awaitTerminated()
            "Messages pulled and processed successfully."
        } catch (e: Exception) {
            subscriber.stopAsync()
            "Failed to pull messages: ${e.localizedMessage}"
        }
    }

}