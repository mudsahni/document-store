package com.muditsahni.documentstore.model.enum

import com.google.cloud.firestore.DocumentSnapshot

enum class UserRole(val value: String) {
    ADMIN("admin"),
    POWER_USER("power_user"),
    USER("user"),
    VIEWER("viewer"),
    UNAUTHORIZED("unauthorized");

    companion object {
        fun fromValue(value: String): UserRole {
            return entries.firstOrNull {
                it.value.equals(value, ignoreCase = true) ||
                        it.name.equals(value, ignoreCase = true)
            } ?: UNAUTHORIZED
        }
    }

}