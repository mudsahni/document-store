package com.muditsahni.documentstore.model.enum

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

class UploadStatusDeserializer : JsonDeserializer<UploadStatus>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): UploadStatus {
        return when (p.text.uppercase()) {
            "SUCCESS" -> UploadStatus.SUCCESS
            "ERROR" -> UploadStatus.FAILURE
            else -> throw JsonParseException(p, "Invalid status value")
        }
    }
}

@JsonDeserialize(using = UploadStatusDeserializer::class)
enum class UploadStatus(val status: String) {
    SUCCESS("success"),
    FAILURE("failed")
}
