package com.alazab.assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_tasks")
data class SmsTask(
    @PrimaryKey
    val taskId: String, // Sent by server, unique
    val phoneNumber: String,
    val messageText: String,
    val status: String, // PENDING, SENT, DELIVERED, FAILED
    val retryCount: Int = 0,
    val createdAt: String,
    val lastAttemptAt: String? = null,
    val errorMessage: String? = null
)
