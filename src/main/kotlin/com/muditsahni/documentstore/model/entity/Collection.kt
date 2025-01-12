package com.muditsahni.documentstore.model.entity

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
    val createdDate: Long,
    var updatedBy: String? = null,
    var updatedDate: Long? = null

)

fun Collection.toCollectionStatus(): CollectionCreationStatus {
    return CollectionCreationStatus(
        id = this.id,
        name = this.name,
        status = this.status,
        type = this.type,
        documents = this.documents,
        error = this.error,
        createdAt = this.createdDate,
        updatedAt = this.updatedDate
    )
}

fun Collection.toGetCollectionResponse(): GetCollectionResponse {
    return GetCollectionResponse(
        id = this.id,
        name = this.name,
        type = this.type,
        documents = this.documents.keys.toList(),
        createdAt = this.createdDate,
        updatedAt = this.updatedDate
    )
}