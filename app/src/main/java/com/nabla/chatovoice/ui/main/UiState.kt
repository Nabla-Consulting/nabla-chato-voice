package com.nabla.chatovoice.ui.main

sealed class UiState {
    object Idle : UiState()
    object Recording : UiState()
    object Processing : UiState()
    object Speaking : UiState()
    data class Error(val message: String) : UiState()
}

data class MainUiData(
    val state: UiState = UiState.Idle,
    val lastTranscription: String = "",
    val lastResponse: String = "",
    val isAccessibilityConnected: Boolean = false,
    val hasToken: Boolean = false,
    val gatewayUrl: String = "",
    val gatewayToken: String = "",
)
