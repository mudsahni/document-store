package com.muditsahni.documentstore.model.enum

enum class CollectionStatus(val value: String) {
    RECEIVED("received"),
    PENDING("pending"),
    IN_PROGRESS("in_progress"),
    DOCUMENT_UPLOADING_TASKS_CREATED("document_uploading_tasks_created"),
    DOCUMENTS_UPLOAD_COMPLETE("documents_upload_complete"),
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