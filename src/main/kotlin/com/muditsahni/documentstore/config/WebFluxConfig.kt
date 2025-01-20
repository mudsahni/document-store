package com.muditsahni.documentstore.config

import com.muditsahni.documentstore.model.event.CollectionStatusEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ServerSentEvent
import reactor.core.publisher.Sinks

@Configuration
class WebFluxConfig {

    @Bean
    fun eventSink(): Sinks.Many<ServerSentEvent<CollectionStatusEvent>> {
        return Sinks.many().multicast().onBackpressureBuffer()
    }
}
