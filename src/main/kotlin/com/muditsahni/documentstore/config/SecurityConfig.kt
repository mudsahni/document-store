package com.muditsahni.documentstore.config

import com.muditsahni.documentstore.model.enum.UserRole
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import com.muditsahni.documentstore.security.FirebaseAuthenticationFilter
import mu.KotlinLogging
import org.springframework.core.annotation.Order
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher
import reactor.core.publisher.Mono

@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val firebaseAuthenticationFilter: FirebaseAuthenticationFilter
) {

    companion object {
        private val logger = KotlinLogging.logger {
            SecurityConfig::class.java.name
        }
        const val JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs"
    }

    @Bean
    @Order(1) // Higher priority for specific matcher
    fun callbackSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
       return http
            // Split into two security filter chains
            .securityMatcher(PathPatternParserServerWebExchangeMatcher("/api/v1/collections/upload/callback")) // Only for callback endpoints
            .authorizeExchange { auth ->
                //auth.pathMatchers("/api/v1/upload/callback").permitAll()  // Temporarily permit all to debug
//                auth.pathMatchers("/api/v1/upload/callback")
//                    .hasAnyAuthority(
//                        "ROLE_SERVICE_ACCOUNT",
//                        "EMAIL_document-store-api@muditsahni-bb2eb.iam.gserviceaccount.com"
//                    )

                auth.anyExchange()
                    .hasAnyAuthority(
                        "ROLE_SERVICE_ACCOUNT",
                        "EMAIL_document-store-api@muditsahni-bb2eb.iam.gserviceaccount.com"
                    )
//                    .hasAuthority("SCOPE_${GoogleCredentialsConfig.GOOGLE_CREDENTIALS_SCOPE}")
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
        val apiPattern = PathPatternParserServerWebExchangeMatcher("/api/**")
        val callbackPattern = PathPatternParserServerWebExchangeMatcher("/api/v1/collections/upload/callback")

        http
            .securityMatcher(AndServerWebExchangeMatcher(
                apiPattern,
                NegatedServerWebExchangeMatcher(callbackPattern)
            ))
            .authorizeExchange { auth ->
                auth
                    .pathMatchers("/api/v1/collections/upload/callback").permitAll()
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
//                    // Log the token claims to debug
//                    logger.info("JWT claims: ${jwt.claims}")

                    // Extract and map all claims to authorities
                    val authorities = mutableListOf<GrantedAuthority>()  // Changed to GrantedAuthority

                    // Add basic authority
                    authorities.add(SimpleGrantedAuthority("ROLE_SERVICE_ACCOUNT"))

//                    // If there's an aud claim, add it as an authority
//                    jwt.claims["aud"]?.toString()?.let {
//                        authorities.add(SimpleGrantedAuthority("AUD_$it"))
//                    }

                    // If there's an email claim, add it as an authority
                    jwt.claims["email"]?.toString()?.let {
                        authorities.add(SimpleGrantedAuthority("EMAIL_$it"))
                    }

                    authorities  // Now returns Collection<GrantedAuthority>
                }
            }
        )
    }
//    @Bean
//    fun googleJwtAuthenticationConverter(): Converter<Jwt, Mono<AbstractAuthenticationToken>> {
//        return ReactiveJwtAuthenticationConverterAdapter(
//            JwtAuthenticationConverter().apply {
//                setJwtGrantedAuthoritiesConverter { jwt ->
//                    val scopes = jwt.claims["scope"] as? String ?: ""
//                    scopes.split(" ")
//                        .map { SimpleGrantedAuthority("SCOPE_$it") }
//                        .toList()
//                }
//            }
//        )
//    }

}