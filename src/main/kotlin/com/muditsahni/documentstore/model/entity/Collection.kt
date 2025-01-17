package com.muditsahni.documentstore.model.entity

import com.google.cloud.Timestamp
import com.google.cloud.firestore.DocumentSnapshot
import com.muditsahni.documentstore.exception.CollectionError
import com.muditsahni.documentstore.model.dto.request.CollectionCreationStatus
import com.muditsahni.documentstore.model.dto.request.GetCollectionResponse
import com.muditsahni.documentstore.model.enum.CollectionStatus
import com.muditsahni.documentstore.model.enum.CollectionType
import com.muditsahni.documentstore.model.enum.DocumentStatus

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
    return Collection(
        id = id,
        name = getString("name") ?: throw IllegalStateException("Collection name not found"),
        type = CollectionType.fromString(getString("type") ?: throw IllegalStateException("Collection type not found")),
        status = CollectionStatus.fromString(getString("status") ?: throw IllegalStateException("Collection status not found")),
        // TODO: Find a good way to do this
        documents = get("documents") as MutableMap<String, DocumentStatus>,
        createdBy = getString("createdBy") ?: throw IllegalStateException("Collection createdBy not found"),
        createdAt = getTimestamp("createdAt") ?: throw IllegalStateException("Collection createdAt not found"),
        updatedBy = getString("updatedBy"),
        updatedAt = getTimestamp("updatedAt")
    )
}

fun Collection.toCollectionStatus(): CollectionCreationStatus {
    return CollectionCreationStatus(
        id = this.id,
        name = this.name,
        status = this.status,
        type = this.type,
        documents = this.documents,
        error = this.error,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}

fun Collection.toGetCollectionResponse(): GetCollectionResponse {
    return GetCollectionResponse(
        id = this.id,
        name = this.name,
        type = this.type,
        documents = this.documents.keys.toList(),
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )
}