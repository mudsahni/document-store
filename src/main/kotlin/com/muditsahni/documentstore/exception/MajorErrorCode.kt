package com.muditsahni.documentstore.exception

enum class MajorErrorCode(
    override val code: String,
    override val message: String,
) : ErrorCode {

    GEN_MAJ_RES_001("GEN_MAJ_RES_001", "Resource could not be found."),

    GEN_MAJ_UNK_001("GEN_MAJ_UNK_001", "An unknown error occurred."),

    GEN_MAJ_REQ_001("GEN_MAJ_REQ_001", "Invalid request."),

    GEN_MAJ_AUTH_001("GEN_MAJ_AUTH_001", "User could not be authenticated."),
    GEN_MAJ_AUTH_002("GEN_MAJ_AUTH_002", "User could not be authorized."),

    GEN_MAJ_COL_001("GEN_MAJ_COL_001", "Collection could not be created."),
    GEN_MAJ_COL_002("GEN_MAJ_COL_002", "Collection could not be updated."),
    GEN_MAJ_COL_003("GEN_MAJ_COL_003", "Collection could not be found."),

    GEN_MAJ_USR_001("GEN_MAJ_USR_001", "User could not be updated."),
    GEN_MAJ_DOC_001("GEN_MAJ_DOC_001", "Document could not be created."),
    GEN_MAJ_DOC_002("GEN_MAJ_DOC_002", "Document could not be updated."),
    GEN_MAJ_DOC_003("GEN_MAJ_DOC_003", "Document could not be found."),

    GEN_MAJ_EVT_001("GEN_MAJ_EVT_001", "Streaming event could not be emitted."),

    INV_MAJ_DOC_001("INV_MAJ_DOC_001", "Document could not be processed.");

    companion object {
        fun fromCode(code: String): MajorErrorCode? {
            return values().find { it.code == code }
        }

        fun toDocumentError(code: MajorErrorCode): DocumentError {
            return DocumentError(code.message, code.code)
        }

        fun toCollectionError(code: MajorErrorCode): CollectionError {
            return CollectionError(code.message, code.code)
        }
    }
}