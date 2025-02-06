package com.muditsahni.documentstore.model.entity

import com.google.cloud.Timestamp
import com.muditsahni.documentstore.model.enum.NotificationCode

data class Notification(
    val id: String,
    val title: String,
    val message: String,
    val code: NotificationCode,
    val userId: String,
    val createdAt: Timestamp = Timestamp.now()
)
