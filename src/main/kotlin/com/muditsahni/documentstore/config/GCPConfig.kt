package com.muditsahni.documentstore.config

import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.Credentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.tasks.v2.CloudTasksClient
import com.google.cloud.tasks.v2.CloudTasksSettings
import mu.KotlinLogging
import okio.IOException
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream

@Configuration
class GCPConfig(
    private val googleCredentials: GoogleCredentials,
) {

    companion object {
        private val logger = KotlinLogging.logger {
            GCPConfig::class.java.name
        }
    }

    @Bean
    fun cloudTasksClient(): CloudTasksClient {
//        val credentials = gcpCredentials()
        return CloudTasksClient.create(
            CloudTasksSettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(googleCredentials))
            .build())
    }

    private fun gcpCredentials(): Credentials {
        return try {
                GoogleCredentials.fromStream(FileInputStream("/secrets/gcp/gcp.json"))
            }
            catch (e: IOException) {
                logger.info { "Could not find secret at /secrets/gcp/gcp.json, falling back to local development path" }
                GoogleCredentials.fromStream(FileInputStream("secrets/gcp-sa-key.json"))
            }
    }

}