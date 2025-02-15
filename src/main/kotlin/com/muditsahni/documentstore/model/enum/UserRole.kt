package com.muditsahni.documentstore.model.enum

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

        fun evaluateRole(givenRole: UserRole, wantedRole: UserRole): Boolean {
            return givenRole == wantedRole || givenRole == ADMIN
        }
    }

}