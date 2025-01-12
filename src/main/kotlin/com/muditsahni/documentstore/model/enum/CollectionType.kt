package com.muditsahni.documentstore.model.enum

enum class CollectionType(val value: String) {
    INVOICE("invoice");

    companion object {
        fun fromString(collectionType: String): CollectionType {
            return try {
                valueOf(collectionType.uppercase())
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid CollectionType value: $collectionType")
            }
        }
    }

}