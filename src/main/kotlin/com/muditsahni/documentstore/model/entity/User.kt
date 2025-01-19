package com.muditsahni.documentstore.model.entity

import com.google.cloud.Timestamp
import com.google.cloud.firestore.DocumentSnapshot
import com.muditsahni.documentstore.model.enum.UserRole

data class User(

    val id: String,
    var firstName: String,
    var lastName: String,
    var email: String,
    var role: UserRole,
    var collections: MutableList<String> = mutableListOf(),
    var documents: MutableList<String> = mutableListOf(),
    val createdBy: String,
    val createdAt: Timestamp,
    var updatedAt: Timestamp? = null,
    var updatedBy: String? = null
)


val SYSTEM_USER = "SYSTEM"


fun DocumentSnapshot.toUser(): User {
    return User(
        id = id,
        firstName = getString("firstName") ?: "",
        lastName = getString("lastName") ?: "",
        email = getString("email") ?: "",
        role = UserRole.fromValue(getString("role") ?: UserRole.UNAUTHORIZED.value),
        collections = get("collections") as MutableList<String>,
        documents = get("documents") as MutableList<String>,
        createdBy = getString("createdBy") ?: "",
        createdAt = getTimestamp("createdAt") ?: Timestamp.now(),
        updatedAt = getTimestamp("updatedAt"),
        updatedBy = getString("updatedBy")
    )
}