package com.alazab.assistant.ai

import java.util.UUID

/**
 * Data model for messages displayed in the AI Chat conversation history.
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)
