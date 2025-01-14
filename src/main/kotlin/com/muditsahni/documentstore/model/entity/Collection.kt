package com.muditsahni.documentstore.model.entity

import com.google.cloud.Timestamp
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