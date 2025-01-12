package com.muditsahni.documentstore.model.enum

enum class DocumentType(val value: String) {
    INVOICE("invoice");

    companion object {
        private val INVOICE_FILE_TYPES = setOf(FileType.PDF, FileType.JPEG, FileType.JPG, FileType.PNG)
            .map { it.value }

        fun fromString(documentType: String): DocumentType {
            return try {
                valueOf(documentType.uppercase())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid DocumentType value: $documentType")
            }
        }

        private fun isFileTypeForInvoiceAllowed(fileType: String): Boolean {
            return fileType.lowercase() in INVOICE_FILE_TYPES
        }

        fun isFileTypeAllowed(documentType: String, fileType: String): Boolean {
            return when (fromString(documentType)) {
                INVOICE -> isFileTypeForInvoiceAllowed(fileType)
            }
        }
    }
}