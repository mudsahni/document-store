package com.muditsahni.documentstore.security

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import com.google.common.util.concurrent.MoreExecutors
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import com.muditsahni.documentstore.model.enum.UserRole
import mu.KotlinLogging
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.server.authentication.AuthenticationWebFilter
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import java.util.concurrent.CompletableFuture

@Component
class FirebaseAuthenticationFilter(
    private val firebaseApp: FirebaseApp,
    private val firestore: Firestore

) : AuthenticationWebFilter(DummyReactiveAuthenticationManager()) {

    companion object {
        private val logger = KotlinLogging.logger { FirebaseAuthenticationFilter::class.java.name }
    }

    init {


        this.setServerAuthenticationConverter { exchange ->
            val requestPath = exchange.request.uri.path
            val regex = Regex("/api/v1/tenants/[^/]+/collections/[^/]+/upload")

            if (regex.matches(requestPath)) {
                logger.debug("Skipping authentication for callback endpoint")
                return@setServerAuthenticationConverter Mono.empty()
            }

//            if (exchange.request.uri.path.startsWith("/api/v1/tenants/*/collections/*/upload/callback")) {
//                logger.debug("Skipping authentication for callback endpoint")
//                return@setServerAuthenticationConverter Mono.empty()
//            }

            Mono.defer {
                val authHeader = exchange.request.headers["Authorization"]?.firstOrNull()
                val pathElements = exchange.request.path.elements().toList()
                val tenantId = pathElements
                    .indexOfFirst { it.value() == "tenants" }
                    .let { index ->
                        if (index >= 0 && index < pathElements.size - 2) {
                            pathElements[index + 2].value()
                        } else {
                            null
                        }
                    }

                logger.debug("Auth header: ${authHeader?.take(20)}...")

                if (authHeader?.startsWith("Bearer ") == true) {
                    val token = authHeader.substring(7)
                    try {
                        // Verify the token using Firebase Admin SDK
                        logger.debug("Attempting to verify token")
                        val decodedToken = FirebaseAuth.getInstance(firebaseApp)
                            .verifyIdToken(token)
                        logger.debug("Token verified for user: ${decodedToken.uid}")

                        // Convert ApiFuture to CompletableFuture
                        val completableFuture = CompletableFuture<DocumentSnapshot>()
                        val apiFuture = firestore.collection("users").document(decodedToken.uid).get()

                        apiFuture.addListener({
                            try {
                                completableFuture.complete(apiFuture.get())
                            } catch (e: Exception) {
                                completableFuture.completeExceptionally(e)
                            }
                        }, MoreExecutors.directExecutor())

                        Mono.fromFuture(completableFuture)
                            .flatMap { userDoc ->
                                if (!userDoc.exists()) {
                                    logger.warn("User document not found in Firestore for UID: ${decodedToken.uid}")
                                    Mono.empty()
                                } else {
                                    try {
                                        val authUserDoc = userDoc.toAuthUserDoc()
                                        val authorities = extractAuthoritiesFromFirestore(authUserDoc.role)

                                        if (authUserDoc.tenant.tenantId != tenantId) {
                                            logger.info("User tenant: ${authUserDoc.tenant.tenantId}, Requested tenant: $tenantId")
                                            logger.warn("Tenant mismatch for user: ${decodedToken.uid}")
                                            return@flatMap Mono.empty()
                                        }

                                        Mono.just(
                                            FirebaseAuthenticationToken(
                                                authUserDoc.id,
                                                authUserDoc.tenant,
                                                authUserDoc.role,
                                                token,
                                                decodedToken.claims,
                                                authorities
                                            )
                                        )
                                    } catch (e: Exception) {
                                        logger.error("Error converting user document to User object", e)
                                        Mono.empty()
                                    }
                                }
                            }
                    } catch (e: Exception) {
                        logger.error("Token validation failed", e)
                        Mono.empty()
                    }
                } else {
                    Mono.empty()
                }
            }
        }
    }


    private fun extractAuthoritiesFromFirestore(role: UserRole): Collection<SimpleGrantedAuthority> {
        return try {
            listOf(SimpleGrantedAuthority("ROLE_" + role.value.uppercase()))
        } catch (e: Exception) {
            logger.error("Error fetching user roles from Firestore", e)
            listOf(SimpleGrantedAuthority("ROLE_" + UserRole.UNAUTHORIZED.value))
        }
    }

    private fun extractAuthorities(decodedToken: FirebaseToken): Collection<SimpleGrantedAuthority> {
        // Extract roles from token claims or assign default roles
        // Example: Assign ROLE_USER by default
        return listOf(SimpleGrantedAuthority("ROLE_USER"))
    }

}

class DummyReactiveAuthenticationManager : ReactiveAuthenticationManager {
    override fun authenticate(authentication: Authentication): Mono<Authentication> {
        return Mono.just(authentication)
    }
}

