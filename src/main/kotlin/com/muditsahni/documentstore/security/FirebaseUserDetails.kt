package com.muditsahni.documentstore.security

import com.muditsahni.documentstore.model.enum.Tenant
import com.muditsahni.documentstore.model.enum.UserRole

data class FirebaseUserDetails(
    val uid: String,
    val claims: Map<String, Any>,
    val tenant: Tenant,
    val role: UserRole,
    val authorities: Collection<String> = listOf("ROLE_USER")
)