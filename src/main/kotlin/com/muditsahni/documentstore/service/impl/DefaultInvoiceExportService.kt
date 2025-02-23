package com.muditsahni.documentstore.service.impl

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class DefaultInvoiceExportService {
    companion object {
        private val logger = KotlinLogging.logger {
            DefaultInvoiceExportService::class.java.name
        }
    }

    fun exportJsonToCsv(jsonStrings: List<String>): String {
        try {
            logger.info { "Exporting JSON strings: $jsonStrings" }
            if (jsonStrings.isEmpty()) return ""

            val mapper = ObjectMapper()
            val csvBuilder = StringBuilder()

            // Get all possible paths from first json to create headers
            val firstJson = mapper.readTree(jsonStrings.first())
            val headers = getAllPaths(firstJson)

            // Write headers
            csvBuilder.appendLine(headers.joinToString(",") { "\"$it\"" })

            // Process each JSON string
            jsonStrings.forEach { jsonString ->
                val json = mapper.readTree(jsonString)
                val values = headers.map { path ->
                    val value = getValueFromJsonPath(json, path)
                    "\"${value?.toString()?.replace("\"", "\"\"") ?: ""}\""
                }
                csvBuilder.appendLine(values.joinToString(","))
            }

            val response = csvBuilder.toString()
            logger.info { "Exported JSON to CSV: $response" }
            return response

        } catch (e: Exception) {
            throw RuntimeException("Error exporting JSON to CSV", e)
        }
    }

    private fun getAllPaths(node: JsonNode, prefix: String = ""): List<String> {
        val paths = mutableListOf<String>()

        when {
            node.isObject -> {
                node.fields().forEach { (key, value) ->
                    val newPrefix = if (prefix.isEmpty()) key else "$prefix.$key"
                    when {
                        value.isObject || value.isArray -> paths.addAll(getAllPaths(value, newPrefix))
                        else -> paths.add(newPrefix)
                    }
                }
            }
            node.isArray -> {
                // For arrays, add indexed paths (e.g., lineItems.0.description)
                node.forEachIndexed { index, item ->
                    paths.addAll(getAllPaths(item, "$prefix.$index"))
                }
            }
            else -> paths.add(prefix)
        }

        return paths
    }

    private fun getValueFromJsonPath(node: JsonNode, path: String): Any? {
        var current = node
        for (part in path.split(".")) {
            current = when {
                part.toIntOrNull() != null -> current[part.toInt()]
                else -> current[part]
            } ?: return null
        }
        return when {
            current.isNull -> null
            current.isTextual -> current.asText()
            current.isNumber -> current.numberValue()
            current.isBoolean -> current.asBoolean()
            else -> current.toString()
        }
    }
}