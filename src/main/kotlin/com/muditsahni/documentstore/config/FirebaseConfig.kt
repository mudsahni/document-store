package com.muditsahni.documentstore.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import jakarta.annotation.PostConstruct
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream
import java.io.IOException

@Configuration
class FirebaseConfig {

    companion object {
        private val logger = KotlinLogging.logger {
            FirebaseConfig::class.java.name
        }
    }

    @Bean
    fun firebaseApp(): FirebaseApp {
        return try {
            FirebaseApp.getInstance()
        } catch (e: IllegalStateException) {
            val credentials = try {
                // Try Cloud Run mounted secret first
                GoogleCredentials.fromStream(FileInputStream("/secrets/firebase/firebase.json"))
            } catch (e: IOException) {
                logger.info { "Could not find secret at /secrets/firebase.json, falling back to local development path" }
                // Local development - use file
                GoogleCredentials.fromStream(FileInputStream("secrets/firebase-sa-key.json"))
            }

            val options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build()

            FirebaseApp.initializeApp(options)
        }
    }

    @Bean
    fun firestore(firebaseApp: FirebaseApp): Firestore {
        return FirestoreClient.getFirestore(firebaseApp)
    }

    @Bean
    fun firebaseAuth(firebaseApp: FirebaseApp): FirebaseAuth {
        return FirebaseAuth.getInstance(firebaseApp)
    }

//    @Bean
//    fun firebaseApp(): FirebaseApp {
//        return try {
//            FirebaseApp.getInstance()
//        } catch (e: IllegalStateException) {
//            val options = FirebaseOptions.builder()
//                .setCredentials(GoogleCredentials.getApplicationDefault())
//                .build()
//            FirebaseApp.initializeApp(options)
//        }
//    }

}