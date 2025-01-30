package com.muditsahni.documentstore.exception


enum class ErrorType(val value: String) {
    COLLECTION("COL"),
    DOCUMENT("DOC"),
    VALIDATION("VAL"),
}