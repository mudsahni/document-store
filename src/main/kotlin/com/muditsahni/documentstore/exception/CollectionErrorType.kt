package com.muditsahni.documentstore.exception

enum class CollectionErrorType(val value: String) {

    COLLECTION_CREATION_ERROR("collection_creation_error"),
    FILE_UPLOAD_TASK_CREATION_ERROR("file_upload_task_creation_error"),
    FILE_UPLOAD_ERROR("file_upload_error"),
}