package com.muditsahni.documentstore.security

import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import com.muditsahni.documentstore.model.entity.AuthUserDoc
import com.muditsahni.documentstore.model.entity.toAuthUserDoc
import com.muditsahni.documentstore.model.enum.UserRole
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class FirebaseAuthenticationFilter(
    private val firebaseApp: FirebaseApp,
    private val firestore: Firestore

) : OncePerRequestFilter() {

    companion object {
        private val logger = KotlinLogging.logger { FirebaseAuthenticationFilter::class.java.name }
    }
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        logger.debug("Auth header: ${authHeader?.take(20)}...")
        if (authHeader?.startsWith("Bearer ") == true) {
            val token = authHeader.substring(7)
            try {
                // Verify the token using Firebase Admin SDK
                logger.debug("Attempting to verify token")
                val decodedToken = FirebaseAuth.getInstance(firebaseApp)
                    .verifyIdToken(token)
                logger.debug("Token verified for user: ${decodedToken.uid}")

                val authUserDoc = getUserDoc(decodedToken)

                if (authUserDoc == null) {
                    logger.error("User document not found in Firestore for UID: ${decodedToken.uid}")
                    SecurityContextHolder.clearContext()
                    return
                }

                // **Assigning authorities based on token claims or default**
                val authorities = extractAuthoritiesFromFirestore(authUserDoc.role)

                // Create authentication object with user details
                val auth = FirebaseAuthenticationToken(
                    decodedToken.uid,
                    authUserDoc.tenantId,
                    authUserDoc.role,
                    token,
                    decodedToken.claims,
                    authorities
                )
                SecurityContextHolder.getContext().authentication = auth
                logger.debug("Authentication set in SecurityContext")
            } catch (e: Exception) {
                // Token validation failed
                logger.error("Token validation failed", e)
                SecurityContextHolder.clearContext()
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun getUserDoc(decodedToken: FirebaseToken): AuthUserDoc? {
        val db = firestore

        try {
            // Fetch user document from Firestore
            val userDoc = db.collection("users").document(decodedToken.uid)
                .get()
                .get() // Get() is called twice because the first returns ApiFuture

            if (!userDoc.exists()) {
                logger.warn("User document not found in Firestore for UID: ${decodedToken.uid}")
                throw IllegalStateException("User document not found in Firestore for UID: ${decodedToken.uid}")
            }

            try {
                return userDoc.toAuthUserDoc()
            } catch (e: Exception) {
                logger.error("Error converting user document to User object", e)
                throw IllegalStateException("Error converting user document to User object")
            }

        } catch (e: Exception) {
            logger.error("Error fetching user roles from Firestore", e)
            throw IllegalStateException("Error fetching user roles from Firestore")
        }
    }

    private fun extractAuthoritiesFromFirestore(role: UserRole): Collection<SimpleGrantedAuthority> {

        try {
            // Convert roles to authorities
            return listOf(SimpleGrantedAuthority("ROLE_" + role.value.uppercase()))

        } catch (e: Exception) {
            logger.error("Error fetching user roles from Firestore", e)
            return listOf(SimpleGrantedAuthority("ROLE_" + UserRole.UNAUTHORIZED.value)) // Default role on error
        }
    }

    private fun extractAuthorities(decodedToken: FirebaseToken): Collection<SimpleGrantedAuthority> {
        // Extract roles from token claims or assign default roles
        // Example: Assign ROLE_USER by default
        return listOf(SimpleGrantedAuthority("ROLE_USER"))
    }

}

