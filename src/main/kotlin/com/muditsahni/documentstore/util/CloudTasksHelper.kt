package com.muditsahni.documentstore.util

import com.google.cloud.tasks.v2.CloudTasksClient
import com.google.cloud.tasks.v2.HttpMethod
import com.google.cloud.tasks.v2.HttpRequest
import com.google.cloud.tasks.v2.OidcToken
import com.google.cloud.tasks.v2.Task
import com.google.protobuf.ByteString
import mu.KotlinLogging
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON

object CloudTasksHelper {

    private val logger = KotlinLogging.logger {
        CloudTasksHelper::class.java.name
    }

    fun createNewTask(
        cloudTasksClient: CloudTasksClient,
        gcpProjectId: String,
        gcpRegion: String,
        cloudTasksQueue: String,
        endpoint: String,
        content: String?,
    ) {
        val newTask = Task.newBuilder()
            .setHttpRequest(
                HttpRequest.newBuilder()
                    .setHttpMethod(HttpMethod.POST)
                    .setUrl(endpoint)
                    .putHeaders(CONTENT_TYPE, APPLICATION_JSON.toString())
                    .setOidcToken(
                        OidcToken.newBuilder()
                            .setServiceAccountEmail("document-store-api@muditsahni-bb2eb.iam.gserviceaccount.com")
                            .setAudience(endpoint)
                            .build()
                    )
                    .setBody(ByteString.copyFromUtf8(content))
                    .build()
            )
            .build()

        logger.info("Task created")

        val createdTask = cloudTasksClient
            .createTask("projects/${gcpProjectId}/locations/${gcpRegion}/queues/${cloudTasksQueue}",
                newTask
            )
        logger.info("Task created with name: ${createdTask.name} at ${createdTask.createTime}")

    }
}