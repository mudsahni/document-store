package com.muditsahni.documentstore.security

import com.muditsahni.documentstore.model.enum.Tenant
import com.muditsahni.documentstore.model.enum.UserRole
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

// Custom Authentication Token class
class FirebaseAuthenticationToken(
    private val uid: String,
    private val tenant: Tenant,
    private val role: UserRole,
    private val token: String,
    private val claims: Map<String, Any>,
    authorities: Collection<GrantedAuthority> = listOf(SimpleGrantedAuthority("ROLE_USER")) // Assign default role
) : AbstractAuthenticationToken(authorities) {
    init {
        isAuthenticated = true
    }

    override fun getCredentials() = token
    override fun getPrincipal() = FirebaseUserDetails(uid, claims, tenant, role, authorities.map { it.authority })
}
