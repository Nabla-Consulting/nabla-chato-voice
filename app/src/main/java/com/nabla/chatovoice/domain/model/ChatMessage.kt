package com.nabla.chatovoice.domain.model

data class ChatMessage(
    val role: String,
    val content: String,
)

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
)

data class ChatResponse(
    val content: String,
)
