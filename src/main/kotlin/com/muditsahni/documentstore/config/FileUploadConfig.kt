package com.muditsahni.documentstore.config

import org.springframework.context.annotation.Configuration

@Configuration
class FileUploadConfig {
    companion object {
        const val MAX_FILES = 25
        const val MAX_FILE_SIZE = 10 * 1024 * 1024L // 10MB in bytes
        val ALLOWED_CONTENT_TYPES = setOf("application/pdf")
    }
}
