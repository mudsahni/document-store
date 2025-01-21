package com.muditsahni.documentstore.security

import com.muditsahni.documentstore.model.enum.UserRole
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher
import reactor.core.publisher.Mono
import kotlin.apply
import kotlin.collections.toTypedArray
import kotlin.jvm.java
import kotlin.let
import kotlin.text.uppercase

@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val firebaseAuthenticationFilter: FirebaseAuthenticationFilter,
    @Value("\${gcp.service-account}") private val serviceAccount: String,
    @Value("\${gcp.project-id}") private val projectId: String
) {

    companion object {
        private val logger = KotlinLogging.logger {
            SecurityConfig::class.java.name
        }
        const val JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs"

        private val SERVICE_ACCOUNT_AUTHORITY = "ROLE_SERVICE_ACCOUNT"
        private val EMAIL_PREFIX = "EMAIL_"
    }

    @Bean
    @Order(1) // Higher priority for specific matcher
    fun callbackSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {

        // Create a matcher that combines all callback paths
        val jwtAuthMatchers = SecurityPath.jwtAuthPaths()
            .map { PathPatternParserServerWebExchangeMatcher(it) }

        val combinedMatcher = if (jwtAuthMatchers.size == 1) {
            jwtAuthMatchers.first()
        } else {
            OrServerWebExchangeMatcher(jwtAuthMatchers)
        }

        return http
            // Split into two security filter chains
//            .securityMatcher(PathPatternParserServerWebExchangeMatcher("/api/v1/tenants/*/collections/*/documents/*/process")) // Only for callback endpoints
            .securityMatcher(combinedMatcher)
            .authorizeExchange { auth ->
                auth.anyExchange()
                    .hasAnyAuthority(
                        SERVICE_ACCOUNT_AUTHORITY,
                        EMAIL_PREFIX + serviceAccount + "@${projectId}.iam.gserviceaccount.com"
                    )
            }
           .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwkSetUri(JWK_SET_URI)
                    jwt.jwtAuthenticationConverter(googleJwtAuthenticationConverter())
                }
            }
            .csrf { it.disable() }
           .build()
    }

    @Bean
    @Order(2) // Lower priority for general API endpoints
    fun apiSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {

        val apiMatcher = PathPatternParserServerWebExchangeMatcher(SecurityPath.API.pattern)

        // Create a matcher that combines all callback paths for exclusion
        val jwtAuthMatchers = SecurityPath.jwtAuthPaths()
            .map { PathPatternParserServerWebExchangeMatcher(it) }
        val combinedMatchers = if (jwtAuthMatchers.size == 1) {
            jwtAuthMatchers.first()
        } else {
            OrServerWebExchangeMatcher(jwtAuthMatchers)
        }

        http
            .securityMatcher(
                AndServerWebExchangeMatcher(
                    apiMatcher,
                    NegatedServerWebExchangeMatcher(combinedMatchers)
                )
            )
            .authorizeExchange { auth ->
                auth
                    .pathMatchers(*SecurityPath.publicPaths().toTypedArray()).permitAll()
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

    private fun extractAuthorities(jwt: Jwt): Collection<GrantedAuthority> =
        buildList {
            add(SimpleGrantedAuthority(SERVICE_ACCOUNT_AUTHORITY))

            jwt.claims["email"]?.toString()?.let { email ->
                logger.debug { "Adding email authority for: $email" }
                add(SimpleGrantedAuthority("${EMAIL_PREFIX}$email"))
            } ?: logger.warn { "No email claim found in JWT" }
        }

    // Optional: Custom JWT validator if needed
    class DefaultJwtValidator : OAuth2TokenValidator<Jwt> {
        override fun validate(jwt: Jwt): OAuth2TokenValidatorResult {
            // Add custom validation logic here
            return OAuth2TokenValidatorResult.success()
        }
    }

    @Bean
    fun googleJwtAuthenticationConverter(): Converter<Jwt, Mono<AbstractAuthenticationToken>> {
        return ReactiveJwtAuthenticationConverterAdapter(
            JwtAuthenticationConverter().apply {
                setJwtGrantedAuthoritiesConverter(::extractAuthorities)
            }
        )
    }
}