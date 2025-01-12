package com.muditsahni.documentstore.model.enum

enum class CollectionStatus(val value: String) {
    RECIEVED("recieved"),
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    FAILED("failed"),
    DELETED("deleted")

}