package com.nabla.chatovoice.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nabla.chatovoice.data.remote.ChatoGatewayRepository
import com.nabla.chatovoice.util.DebugLogger
import com.nabla.chatovoice.domain.repository.GatewayRepository
import com.nabla.chatovoice.service.ChatoAccessibilityService
import com.nabla.chatovoice.service.TextToSpeechManager
import com.nabla.chatovoice.service.VoiceInputManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val gatewayRepository: GatewayRepository,
    private val voiceInputManager: VoiceInputManager,
    private val ttsManager: TextToSpeechManager,
    private val chatoGatewayRepository: ChatoGatewayRepository,
) : ViewModel() {

    private val _uiData = MutableStateFlow(MainUiData())
    val uiData: StateFlow<MainUiData> = _uiData.asStateFlow()

    private var listenJob: Job? = null

    init {
        loadSettings()
    }

    fun initTts() {
        DebugLogger.log("TTS", "init called")
        ttsManager.initialize {
            DebugLogger.log("TTS", "ready")
        }
    }

    private fun loadSettings() {
        _uiData.update {
            it.copy(
                hasToken = chatoGatewayRepository.hasToken(),
                gatewayUrl = chatoGatewayRepository.gatewayUrl,
                gatewayToken = chatoGatewayRepository.gatewayToken,
            )
        }
    }

    fun saveSettings(url: String, token: String) {
        chatoGatewayRepository.saveSettings(url, token)
        loadSettings()
    }

    fun refreshAccessibilityStatus() {
        _uiData.update {
            it.copy(isAccessibilityConnected = ChatoAccessibilityService.isConnected)
        }
    }

    fun onPushToTalkDown() {
        val hasToken = _uiData.value.hasToken
        DebugLogger.log("VM", "PTT down, hasToken=$hasToken")
        if (!hasToken) {
            _uiData.update { it.copy(state = UiState.Error("Gateway token not set. Open Settings.")) }
            return
        }
        _uiData.update { it.copy(state = UiState.Recording) }
        listenJob = viewModelScope.launch {
            try {
                val transcription = voiceInputManager.listen()
                if (transcription.isNotBlank()) {
                    _uiData.update { it.copy(
                        messages = it.messages + ChatMessage(transcription, MessageSender.USER)
                    )}
                }
                sendToGateway(transcription)
            } catch (e: CancellationException) {
                _uiData.update { it.copy(state = UiState.Idle) }
            } catch (e: Exception) {
                _uiData.update { it.copy(state = UiState.Error(e.message ?: "Voice input error")) }
            }
        }
    }

    fun onPushToTalkUp() {
        DebugLogger.log("VM", "PTT up")
        voiceInputManager.stopListening()
    }

    private suspend fun sendToGateway(text: String) {
        if (text.isBlank()) {
            _uiData.update { it.copy(state = UiState.Idle) }
            return
        }
        DebugLogger.log("VM", "sending: $text")
        _uiData.update { it.copy(state = UiState.Processing) }
        val screenContext = ChatoAccessibilityService.screenContext
        val result = gatewayRepository.chat(text, screenContext)
        result.fold(
            onSuccess = { response ->
                DebugLogger.log("VM", "response received")
                _uiData.update { it.copy(
                    messages = it.messages + ChatMessage(response.content, MessageSender.CHATO),
                    state = UiState.Speaking
                )}
                speak(response.content)
            },
            onFailure = { error ->
                DebugLogger.log("VM", "error: ${error.toString()}")
                _uiData.update { it.copy(state = UiState.Error(error.toString())) }
            }
        )
    }

    private suspend fun speak(text: String) {
        try {
            ttsManager.speak(text)
        } catch (e: Exception) {
            // TTS failure is non-fatal — response is already shown in UI
        } finally {
            _uiData.update { it.copy(state = UiState.Idle) }
        }
    }

    fun dismissError() {
        _uiData.update { it.copy(state = UiState.Idle) }
    }

    override fun onCleared() {
        super.onCleared()
        voiceInputManager.destroy()
        ttsManager.destroy()
    }
}
