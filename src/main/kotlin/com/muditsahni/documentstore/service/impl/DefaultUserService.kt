package com.muditsahni.documentstore.service.impl

import com.google.cloud.firestore.Firestore
import com.muditsahni.documentstore.model.entity.User
import com.muditsahni.documentstore.model.enum.Tenant
import com.muditsahni.documentstore.util.UserHelper
import org.springframework.stereotype.Service

@Service
class DefaultUserService(val firestore: Firestore) {

    suspend fun getUser(
        tenant: Tenant,
        userId: String
    ): User {
        val user = UserHelper.getUser(firestore, userId, tenant)
        return user
    }

}