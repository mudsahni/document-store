package com.muditsahni.documentstore.security

data class FirebaseUserDetails(
    val uid: String,
    val claims: Map<String, Any>
) {
//    // Helper properties to easily access common claims
//    val role: String? get() = claims["role"] as? String
//    val tenantId: String? get() = claims["tenantId"] as? String
}