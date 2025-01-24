package com.muditsahni.documentstore.config

import com.muditsahni.documentstore.model.event.CollectionStatusEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsWebFilter
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

    @Bean
    fun corsWebFilter(): CorsWebFilter {
        val corsConfig = CorsConfiguration()
        corsConfig.apply {
            // Use allowedOriginPatterns instead of allowedOrigins
            allowedOriginPatterns = listOf("https://*.asia-south2.run.app")

            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
            )
            allowCredentials = true
            maxAge = 3600L
        }

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", corsConfig)

        return CorsWebFilter(source)
    }

}
