package com.muditsahni.documentstore.exception

data class DocumentError(
    val message: String,
    val type: DocumentErrorType
)