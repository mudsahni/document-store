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

fun DocumentSnapshot.toUser(): User {
    return User(
        id = id,
        firstName = getString("first_name") ?: "",
        lastName = getString("last_name") ?: "",
        email = getString("email") ?: "",
        role = UserRole.fromValue(getString("role") ?: UserRole.UNAUTHORIZED.value),
        collections = get("collections") as MutableList<String>,
        documents = get("documents") as MutableList<String>,
        createdBy = getString("created_by") ?: "",
        createdAt = getTimestamp("created_at") ?: Timestamp.now(),
        updatedAt = getTimestamp("updated_at"),
        updatedBy = getString("updated_by")
    )
}