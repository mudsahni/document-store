package com.muditsahni.documentstore

import com.google.cloud.firestore.Firestore
import com.google.cloud.storage.Storage
import com.google.cloud.tasks.v2.CloudTasksClient
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
class ApplicationTests {

	@MockitoBean
	lateinit var cloudTasksClient: CloudTasksClient

	@MockitoBean
	lateinit var storage: Storage

	@MockitoBean
	lateinit var firebaseApp: FirebaseApp

	@MockitoBean
	lateinit var firestore: Firestore

	@MockitoBean
	lateinit var firebaseAuth: FirebaseAuth

	@Test
	fun contextLoads() {
	}

}
