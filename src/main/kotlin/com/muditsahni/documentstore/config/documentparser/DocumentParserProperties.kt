package com.muditsahni.documentstore.config.documentparser

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "document-parser")
data class DocumentParserProperties(
    val name: String,
    val projectNumber: String,
    val region: String,
    val uri: String,
    val version: String,
    val upload: String,
    val process: String
)