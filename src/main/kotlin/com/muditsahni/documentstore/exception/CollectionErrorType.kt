package com.muditsahni.documentstore.exception

enum class CollectionErrorType(val value: String) {

    COLLECTION_CREATION_ERROR("collection_creation_error"),
    DOCUMENT_UPLOAD_TASK_CREATION_ERROR("document_upload_task_creation_error"),
    DOCUMENT_UPLOAD_ERROR("document_upload_error"),
}