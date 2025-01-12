package com.muditsahni.documentstore.model.entity

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.cloud.firestore.DocumentSnapshot
import com.muditsahni.documentstore.model.enum.Tenant
import com.muditsahni.documentstore.model.enum.UserRole

data class AuthUserDoc(
    val id: String,
    val role: UserRole,

    @JsonProperty("tenant_id")
    val tenantId: Tenant
) {
    constructor(): this("", UserRole.UNAUTHORIZED, Tenant.PERFECT_ACCOUNTING_AND_SHARED_SERVICES)

}

fun DocumentSnapshot.toAuthUserDoc(): AuthUserDoc {
    return AuthUserDoc(
        id = id,
        role = UserRole.fromValue(getString("role") ?: UserRole.UNAUTHORIZED.value),
        tenantId = Tenant.fromTenantId(getString("tenant_id") ?: Tenant.INVALID_TENANT.tenantId)
    )
}