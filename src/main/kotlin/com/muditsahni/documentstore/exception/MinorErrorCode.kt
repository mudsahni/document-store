package com.muditsahni.documentstore.exception

enum class MinorErrorCode(
    override val code: String,
    override val message: String
) : ErrorCode {
    GEN_MIN_DOC_001("GEN_MIN_DOC_001", "Document could not be uploaded."),
    GEN_MIN_DOC_002("GEN_MIN_DOC_002", "Document it too large."),
    INV_MIN_DOC_001("INV_MIN_DOC_001", "Document could not be validated.");

    companion object {
        fun fromCode(code: String): MinorErrorCode? {
            return values().find { it.code == code }
        }

        fun toDocumentError(code: MinorErrorCode): DocumentError {
            return DocumentError(code.message, code.code)
        }

        fun toCollectionError(code: MinorErrorCode): CollectionError {
            return CollectionError(code.message, code.code)
        }
    }

}