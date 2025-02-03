package com.muditsahni.documentstore.model.dto.response

import com.muditsahni.documentstore.exception.DocumentError
import com.muditsahni.documentstore.model.entity.document.StructuredData
import com.muditsahni.documentstore.model.enum.DocumentRole
import com.muditsahni.documentstore.model.enum.DocumentStatus
import com.muditsahni.documentstore.model.enum.DocumentType

data class GetDocumentResponse(
    val id: String,
    val name: String,
    var path: String? = null,
    val type: DocumentType,
    val collectionId: String,
    var status: DocumentStatus = DocumentStatus.PENDING,
    var data: StructuredData? = null,
    var private: Boolean,
    var error: DocumentError? = null,
    var permissions: MutableMap<String, DocumentRole> = mutableMapOf(),
    val createdBy: String,
    val createdAt: Long,
    var updatedBy: String? = null,
    var updatedAt: Long? = null
)