package com.muditsahni.documentstore.config

import com.muditsahni.documentstore.model.event.CollectionStatusEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource
import reactor.core.publisher.Sinks

@Configuration
class WebFluxConfig(
    @Value("\${cors.allowed-origins:http://localhost:3000}")
    private val allowedOrigins: List<String>
) {

    @Bean
    fun eventSink(): Sinks.Many<ServerSentEvent<CollectionStatusEvent>> {
        return Sinks.many().multicast().onBackpressureBuffer()
    }

    @Configuration
    class CorsConfig {
        @Bean
        fun corsConfigurationSource(): CorsConfigurationSource {
            val configuration = CorsConfiguration().apply {
                allowedOriginPatterns = listOf("https://*.asia-south2.run.app", "http://localhost:3000")
                allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
                allowedHeaders = listOf(
                    "Authorization",
                    "Content-Type",
                    "Accept",
                    "Origin",
                    "Access-Control-Request-Method",
                    "Access-Control-Request-Headers",
                    "Access-Control-Allow-Origin",
                    "Cache-Control",
                    "Connection"
                )
                allowCredentials = true
                maxAge = 3600L
            }

            val source = UrlBasedCorsConfigurationSource()
            source.registerCorsConfiguration("/**", configuration)
            return source
        }
    }


}
