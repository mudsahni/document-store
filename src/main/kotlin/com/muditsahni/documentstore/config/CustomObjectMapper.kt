package com.muditsahni.documentstore.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

fun getObjectMapper(): ObjectMapper {
    return ObjectMapper().apply {
        // Register Kotlin module for proper Kotlin data class support
        registerKotlinModule()
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

//        // Set the naming strategy to SnakeCase for global field naming
//        setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    }
}