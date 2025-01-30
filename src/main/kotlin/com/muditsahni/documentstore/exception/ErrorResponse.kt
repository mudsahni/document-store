package com.muditsahni.documentstore.exception

// Response models
data class ErrorResponse(
    val message: String,
    val code: String,
    val timestamp: Long = System.currentTimeMillis()
)
