package com.nabla.chatovoice.ui.main

sealed class UiState {
    object Idle : UiState()
    object Recording : UiState()
    object Thinking : UiState()
    object Processing : UiState()
    object Speaking : UiState()
    data class Error(val message: String) : UiState()
}

enum class MessageSender { USER, CHATO }

data class ChatMessage(
    val text: String,
    val sender: MessageSender,
)

data class MainUiData(
    val state: UiState = UiState.Idle,
    val messages: List<ChatMessage> = emptyList(),
    val isAccessibilityConnected: Boolean = false,
    val hasToken: Boolean = false,
    val gatewayUrl: String = "",
    val gatewayToken: String = "",
    val azureSpeechKey: String = "",
    val azureSpeechRegion: String = "eastus",
    val transcriptionLanguage: String = "en-US",
    val graphToken: String = "",
    val isConversationMode: Boolean = false,
)
