package com.muditsahni.documentstore.model.entity

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import kotlinx.serialization.Serializable

@Serializable
data class PromptTemplate(
    val prompt: String,
    @JsonDeserialize(using = RawJsonDeserializer::class)
    val template: String
)

class RawJsonDeserializer : JsonDeserializer<String>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): String {
        val codec = p.codec
        val node = codec.readTree<JsonNode>(p)
        return node.toString()
    }
}