package com.muditsahni.documentstore.model.enum

enum class DocumentStatus {

    UPLOADED,
    PENDING,
    PARSED,
    VALIDATED,
    ERROR,
    APPROVED;

    companion object {
        fun fromString(documentStatus: String): DocumentStatus {
            return try {
                valueOf(documentStatus.uppercase())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid DocumentStatus value: $documentStatus")
            }
        }
    }
}