package com.muditsahni.documentstore.model.enum

enum class Tenant(val tenantId: String, val bucketName: String, val firestoreCollection: String) {
    PERFECT_ACCOUNTING_AND_SHARED_SERVICES(
        "perfect-accounting-and-shared-services",
        "perfect-accounting-and-shared-services",
        "perfect-accounting-and-shared-services"
    ),

    INVALID_TENANT("invalid-tenant", "invalid-tenant", "invalid-tenant");

    companion object {
        fun isValidTenantId(tenantId: String): Boolean {
            return entries.any { it.tenantId == tenantId && it != INVALID_TENANT }
        }

        fun fromTenantId(tenantId: String): Tenant {
            return entries.find { it.tenantId == tenantId && it != INVALID_TENANT }
                ?: throw IllegalArgumentException("Invalid tenant ID: $tenantId")
        }
    }

}