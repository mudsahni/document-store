package com.muditsahni.documentstore.exception

data class ValidationError(
    val field: String,
    val message: String,
    val severity: ErrorSeverity = ErrorSeverity.MINOR
)