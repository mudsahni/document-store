package com.muditsahni.documentstore.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream

@Configuration
class FirebaseConfig(
    @Value("\${FIREBASE_SA_KEY:}")
    private val firebaseKeyJson: String = ""
) {
    @Bean
    fun firebaseApp(): FirebaseApp {
        return try {
            FirebaseApp.getInstance()
        } catch (e: IllegalStateException) {
            val credentials = when {
                firebaseKeyJson.isNotEmpty() -> {
                    // Cloud Run environment - use secret from environment
                    GoogleCredentials.fromStream(firebaseKeyJson.byteInputStream())
                }
                else -> {
                    // Local development - use file
                    GoogleCredentials.fromStream(FileInputStream("secrets/firebase-sa-key.json"))
                }
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