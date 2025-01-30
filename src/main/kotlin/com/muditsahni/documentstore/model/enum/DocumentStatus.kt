package com.muditsahni.documentstore.model.enum

enum class DocumentStatus(val value: String) {
    // pending -> uploaded -> in_progress -> parsed -> validated -> approved

    UPLOADED("uploaded"),
    IN_PROGRESS("in_progress"),
    PENDING("pending"),
    PARSED("parsed"),
    STRUCTURED("structured"),
    VALIDATED("validated"),
    ERROR("error"),
    APPROVED("approved");

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