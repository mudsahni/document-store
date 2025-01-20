package com.muditsahni.documentstore.exception

import kotlinx.serialization.Serializable

@Serializable
data class CollectionError(
    val message: String,
    val type: CollectionErrorType
)