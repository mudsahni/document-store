package com.muditsahni.documentstore.model.entity

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
    val createdDate: Long,
    var updatedDate: Long? = null,
    var updatedBy: String? = null
)