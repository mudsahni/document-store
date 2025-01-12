package com.muditsahni.documentstore.exception

data class CollectionError(
    val message: String,
    val type: CollectionErrorType
)