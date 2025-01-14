package com.muditsahni.documentstore.config

import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.Credentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import com.google.cloud.tasks.v2.CloudTasksClient
import com.google.cloud.tasks.v2.CloudTasksSettings
import mu.KotlinLogging
import okio.IOException
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream

@Configuration
class GCPConfig {

    companion object {
        private val logger = KotlinLogging.logger {
            GCPConfig::class.java.name
        }
    }
    @Bean
    fun storageClient(): Storage {
        val credentials = gcpCredentials()
        return StorageOptions.newBuilder()
            .setCredentials(credentials)
            .build()
            .service
    }


    @Bean
    fun cloudTasksClient(): CloudTasksClient {
        val credentials = gcpCredentials()
        return CloudTasksClient.create(
            CloudTasksSettings.newBuilder()
            .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
            .build())
    }

    private fun gcpCredentials(): Credentials {
        return try {
                GoogleCredentials.fromStream(FileInputStream("/secrets/gcp/gcp.json"))
            }
            catch (e: IOException) {
                logger.info { "Could not find secret at /secrets/firebase.json, falling back to local development path" }
                GoogleCredentials.fromStream(FileInputStream("secrets/gcp-sa-key.json"))
            }
    }

}