package com.muditsahni.documentstore.model.entity

import com.google.cloud.Timestamp
import com.google.cloud.firestore.DocumentSnapshot
import com.muditsahni.documentstore.exception.CollectionError
import com.muditsahni.documentstore.model.dto.response.CreateCollectionResponse
import com.muditsahni.documentstore.model.dto.response.GetCollectionResponse
import com.muditsahni.documentstore.model.enum.CollectionStatus
import com.muditsahni.documentstore.model.enum.CollectionType
import com.muditsahni.documentstore.model.enum.DocumentStatus
import com.muditsahni.documentstore.model.event.CollectionStatusEvent

data class Collection(

    val id: String,
    val name: String,
    val type: CollectionType,
    var status: CollectionStatus,
    var documents: MutableMap<String, DocumentStatus> = mutableMapOf(),
    var error: CollectionError? = null,
    val createdBy: String,
    val createdAt: Timestamp = Timestamp.now(),
    var updatedBy: String? = null,
    var updatedAt: Timestamp? = null

)

fun DocumentSnapshot.toCollection(): Collection {
    val rawDocuments = get("documents") as? Map<String, String>
        ?: throw IllegalStateException("Collection documents not found")
    val convertedDocuments = rawDocuments.mapValues { (_, status) ->
        DocumentStatus.fromString(status)
    }.toMutableMap()

    return Collection(
        id = id,
        name = getString("name") ?: throw IllegalStateException("Collection name not found"),
        type = CollectionType.fromString(getString("type") ?: throw IllegalStateException("Collection type not found")),
        status = CollectionStatus.fromString(getString("status") ?: throw IllegalStateException("Collection status not found")),
        // TODO: Find a good way to do this
        documents = convertedDocuments,
        createdBy = getString("createdBy") ?: throw IllegalStateException("Collection createdBy not found"),
        createdAt = getTimestamp("createdAt") ?: throw IllegalStateException("Collection createdAt not found"),
        updatedBy = getString("updatedBy"),
        updatedAt = getTimestamp("updatedAt")
    )
}

fun Collection.toCollectionStatusEvent(): CollectionStatusEvent {
    return CollectionStatusEvent(
        id = this.id,
        name = this.name,
        status = this.status,
        type = this.type,
        documents = this.documents,
        error = this.error,
        timestamp = this.updatedAt ?: this.createdAt
    )
}

fun Collection.toCreateCollectionReponse(documents: Map<String, SignedUrlResponse?>): CreateCollectionResponse {
    return CreateCollectionResponse(
        id = this.id,
        name = this.name,
        status = this.status,
        type = this.type,
        documents = documents,
        error = this.error,
    )
}


fun Collection.toGetCollectionResponse(): GetCollectionResponse {
    return GetCollectionResponse(
        id = this.id,
        name = this.name,
        type = this.type,
        documents = this.documents,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        updatedBy = this.updatedBy
    )
}