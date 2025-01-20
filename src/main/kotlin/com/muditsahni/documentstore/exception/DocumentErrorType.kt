package com.muditsahni.documentstore.exception

enum class DocumentErrorType(val value: String) {

    VALIDATION_ERROR("validation_error"),
    DOCUMENT_UPLOAD_ERROR("document_upload_error"),
    DOCUMENT_PARSING_ERROR("document_parsing_error"),
    DOCUMENT_SIZE_ERROR("document_size_error"),
}