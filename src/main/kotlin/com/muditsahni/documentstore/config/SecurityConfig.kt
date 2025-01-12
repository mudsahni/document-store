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
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val firebaseAuthenticationFilter: FirebaseAuthenticationFilter
) {

    companion object {
        const val JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs"
    }

    @Bean
    @Order(1) // Higher priority for specific matcher
    fun callbackSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // Split into two security filter chains
            .securityMatcher("/api/v1/collections/*/callback") // Only for callback endpoints
            .authorizeHttpRequests { auth ->
                auth.anyRequest()
                    .hasAuthority("SCOPE_https://www.googleapis.com/auth/cloud-platform")
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { jwt ->
                    jwt.jwkSetUri(JWK_SET_URI)
                    jwt.jwtAuthenticationConverter(googleJwtAuthenticationConverter())
                }
            }
            .csrf { it.disable() }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }

        return http.build()
    }

    @Bean
    @Order(2) // Lower priority for general API endpoints
    fun apiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/api/**") // For all other API endpoints
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/health").permitAll()
                    .requestMatchers("/dev/token").permitAll()
                    .requestMatchers("/swagger-ui/**", "/api-docs/**").permitAll()
                    .anyRequest()
                    .hasRole(UserRole.ADMIN.value.uppercase())
            }
            .addFilterBefore(firebaseAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .csrf { it.disable() }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }

        return http.build()
    }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        return NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI)
            .build()
    }

    @Bean
    fun googleJwtAuthenticationConverter(): Converter<Jwt, AbstractAuthenticationToken> {
        return JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter { jwt ->
                val scopes = jwt.claims["scope"] as? String ?: ""
                scopes.split(" ")
                    .map { SimpleGrantedAuthority("SCOPE_$it") }
                    .toList()
            }
        }
    }

}