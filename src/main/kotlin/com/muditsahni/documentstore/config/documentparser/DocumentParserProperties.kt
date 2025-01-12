package com.muditsahni.documentstore.config.documentparser

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "document-parser")
data class DocumentParserProperties(
    val uri: String,
    val version: String,
    val upload: String
)