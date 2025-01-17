package com.muditsahni.documentstore.model.enum

enum class CollectionStatus(val value: String) {
    RECIEVED("received"),
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    FAILED("failed"),
    DELETED("deleted");

    companion object {
        fun fromString(collectionStatus: String): CollectionStatus {
            return try {
                valueOf(collectionStatus.uppercase())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid CollectionStatus value: $collectionStatus")
            }
        }
    }


}