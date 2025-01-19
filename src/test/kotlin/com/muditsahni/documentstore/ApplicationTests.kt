package com.muditsahni.documentstore

import com.google.cloud.firestore.Firestore
import com.google.cloud.pubsub.v1.Publisher
import com.google.cloud.pubsub.v1.Subscriber
import com.google.cloud.storage.Storage
import com.google.cloud.tasks.v2.CloudTasksClient
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest
class ApplicationTests {

	@MockitoBean
	lateinit var cloudTasksClient: CloudTasksClient

	@MockitoBean
	lateinit var firebaseApp: FirebaseApp

	@MockitoBean
	lateinit var firestore: Firestore

	@MockitoBean
	lateinit var storageClient: Storage

	@MockitoBean
	lateinit var firebaseAuth: FirebaseAuth

	@MockitoBean
	lateinit var pubSubPublisher: Publisher

	@MockitoBean
	lateinit var pubSubSubscriber: Subscriber

	@MockitoBean
	lateinit var coroutineScope: CoroutineScope

	@Test
	fun contextLoads() {
	}

}
