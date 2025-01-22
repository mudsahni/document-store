package com.muditsahni.documentstore.config

import com.muditsahni.documentstore.model.entity.PromptTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import java.io.IOException
import java.lang.RuntimeException
import kotlin.io.use
import kotlin.jvm.java

@Configuration
class PromptConfig {

    @Bean(name = ["InvoicePromptTemplate"])
    fun loadInvoiceParsingPromptTemplate(): PromptTemplate {
        val objectMapper = getObjectMapper()
        val resource = ClassPathResource("parsing-templates/invoice-parsing-template.json")  // Ensure your file is in src/main/resources/

        try {
            resource.inputStream.use {
                return objectMapper.readValue(it, PromptTemplate::class.java)
            }
        } catch (e: IOException) {
            throw RuntimeException("Failed to load configuration from config.json", e)
        }
    }

}