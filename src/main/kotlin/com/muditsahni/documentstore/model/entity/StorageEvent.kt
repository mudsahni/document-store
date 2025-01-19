package com.muditsahni.documentstore.model.entity

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class StorageEvent(
    val kind: String,
    val id: String,
    val name: String,
    val bucket: String,
    val generation: String,
//    val contentType: String,
    @JsonProperty("timeCreated")
    val timeCreated: String,
    val updated: String,
    @JsonProperty("mediaLink")
    val mediaLink: String,
    val size: String
)