package com.muditsahni.documentstore.config

import com.muditsahni.documentstore.model.enum.UserRole
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import com.muditsahni.documentstore.security.FirebaseAuthenticationFilter
import org.springframework.core.annotation.Order
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher
import reactor.core.publisher.Mono

@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val firebaseAuthenticationFilter: FirebaseAuthenticationFilter
) {

    companion object {
        const val JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs"
    }

    @Bean
    @Order(1) // Higher priority for specific matcher
    fun callbackSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            // Split into two security filter chains
            .securityMatcher(PathPatternParserServerWebExchangeMatcher("/api/v1/collections/*/callback")) // Only for callback endpoints
            .authorizeExchange { auth ->
                auth.anyExchange()
                    .hasAuthority("SCOPE_${GoogleCredentialsConfig.GOOGLE_CREDENTIALS_SCOPE}")
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwkSetUri(JWK_SET_URI)
                    jwt.jwtAuthenticationConverter(googleJwtAuthenticationConverter())
                }
            }
            .csrf { it.disable() }

        return http.build()
    }

    @Bean
    @Order(2) // Lower priority for general API endpoints
    fun apiSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        http
            .securityMatcher(PathPatternParserServerWebExchangeMatcher("/api/**")) // For all other API endpoints
            .authorizeExchange { auth ->
                auth
                    .pathMatchers("/health").permitAll()
                    .pathMatchers("/dev/token").permitAll()
                    .pathMatchers("/swagger-ui/**", "/api-docs/**").permitAll()
                    .anyExchange()
                    .hasRole(UserRole.ADMIN.value.uppercase())
            }
            .addFilterBefore(firebaseAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .csrf { it.disable() }

        return http.build()
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        return NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI)
            .build()
    }

    @Bean
    fun googleJwtAuthenticationConverter(): Converter<Jwt, Mono<AbstractAuthenticationToken>> {
        return ReactiveJwtAuthenticationConverterAdapter(
            JwtAuthenticationConverter().apply {
                setJwtGrantedAuthoritiesConverter { jwt ->
                    val scopes = jwt.claims["scope"] as? String ?: ""
                    scopes.split(" ")
                        .map { SimpleGrantedAuthority("SCOPE_$it") }
                        .toList()
                }
            }
        )
    }

}