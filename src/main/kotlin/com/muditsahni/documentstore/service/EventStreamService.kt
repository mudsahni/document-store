package com.muditsahni.documentstore.service

import com.muditsahni.documentstore.model.event.CollectionStatusEvent
import com.muditsahni.documentstore.model.event.EmitResult
import mu.KotlinLogging
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.lang.IllegalStateException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class EventStreamService {

    private val processStreams = ConcurrentHashMap<String, Sinks.Many<ServerSentEvent<CollectionStatusEvent>>>()

    companion object {
        private val logger = KotlinLogging.logger {
            EventStreamService::class.java.name
        }
    }

    fun createEventStream(processId: String): String {
        val sink = Sinks.many().multicast().onBackpressureBuffer<ServerSentEvent<CollectionStatusEvent>>()
        processStreams[processId] = sink
        return processId
    }

    fun getEventStream(processId: String): Flux<ServerSentEvent<CollectionStatusEvent>> {
        return processStreams[processId]?.asFlux()
            ?: throw IllegalStateException("No active process found for: $processId")
    }

    fun emitEvent(
        collectionStatusEvent: CollectionStatusEvent
    ): EmitResult {

        val sink = processStreams[collectionStatusEvent.id]

        if (sink == null) {
            logger.warn("Attempted to emit event to non-existent stream: $collectionStatusEvent.id")
            return EmitResult.Closed
        }

        val event = ServerSentEvent.builder<CollectionStatusEvent>()
            .id(UUID.randomUUID().toString())
            .event(collectionStatusEvent.status.name)
            .data(collectionStatusEvent)
            .build()

        val result = sink.tryEmitNext(event)

        return when (result) {
            Sinks.EmitResult.OK -> EmitResult.Success
            Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER -> EmitResult.NoSubscribers
            Sinks.EmitResult.FAIL_CANCELLED,
            Sinks.EmitResult.FAIL_TERMINATED -> {
                processStreams.remove(collectionStatusEvent.id)
                EmitResult.Closed
            }
            Sinks.EmitResult.FAIL_OVERFLOW -> EmitResult.Overflow
            else -> EmitResult.Unknown
        }
    }

    fun completeStream(processId: String): Boolean {
        val sink = processStreams.remove(processId)
        return if (sink != null) {
            sink.tryEmitComplete()
            true
        } else {
            logger.warn("Attempted to complete non-existent stream: $processId")
            false
        }
    }

    fun errorStream(collectionStatusEvent: CollectionStatusEvent): Boolean {
        val result = emitEvent(collectionStatusEvent)
        return when (result) {
            EmitResult.Success -> completeStream(collectionStatusEvent.id)
            else -> false
        }
    }

}