package com.muditsahni.documentstore.config

import com.google.auth.oauth2.GoogleCredentials
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream

@Configuration
class GoogleCredentialsConfig {

    @Value("\${spring.profiles.active:dev}")
    private lateinit var activeProfile: String

    @Value("\${spring.cloud.gcp.credentials.location}")
    private lateinit var gcpCredentialsLocation: String

    companion object {
        const val GOOGLE_CREDENTIALS_SCOPE = "https://www.googleapis.com/auth/cloud-platform"
    }

    @Bean
    fun googleCredentials(): GoogleCredentials {
        return when (activeProfile) {
            "dev" -> {
                // Development: use local file
                GoogleCredentials
                    .fromStream(FileInputStream(gcpCredentialsLocation))
                    .createScoped(GOOGLE_CREDENTIALS_SCOPE)
            }
            "prod" -> {
                // Production: use mounted secret from Cloud Run
                GoogleCredentials
                    .getApplicationDefault()
                    .createScoped(GOOGLE_CREDENTIALS_SCOPE)
            }
            else -> throw IllegalStateException("Unknown profile: $activeProfile")
        }
    }
}