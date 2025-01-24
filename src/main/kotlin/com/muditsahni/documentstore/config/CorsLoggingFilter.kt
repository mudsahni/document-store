package com.muditsahni.documentstore.config

import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
class CorsLoggingFilter : WebFilter {
    companion object {
        private val logger = KotlinLogging.logger {
            CorsLoggingFilter::class.java.name
        }
    }

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        logger.info("Request: ${exchange.request.method} ${exchange.request.path}")
        logger.info("Origin: ${exchange.request.headers["Origin"]}")
        return chain.filter(exchange)
    }
}
