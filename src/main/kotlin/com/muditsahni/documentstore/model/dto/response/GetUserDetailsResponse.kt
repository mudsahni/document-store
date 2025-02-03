package com.muditsahni.documentstore.model.dto.response

data class GetUserDetailsResponse (
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    val createdAt: Long,
    val updatedAt: Long
)