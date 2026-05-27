package com.nabla.chatovoice.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nabla.chatovoice.data.remote.ChatoGatewayRepository
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
        ttsManager.initialize {
            // TTS ready — no UI update needed
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
        if (!hasToken) {
            _uiData.update { it.copy(state = UiState.Error("Gateway token not set. Open Settings.")) }
            return
        }

        _uiData.update { it.copy(state = UiState.Recording, lastTranscription = "") }

        listenJob = viewModelScope.launch {
            try {
                val transcription = voiceInputManager.listen()
                _uiData.update { it.copy(lastTranscription = transcription) }
                sendToGateway(transcription)
            } catch (e: CancellationException) {
                _uiData.update { it.copy(state = UiState.Idle) }
            } catch (e: Exception) {
                _uiData.update { it.copy(state = UiState.Error(e.message ?: "Voice input error")) }
            }
        }
    }

    fun onPushToTalkUp() {
        voiceInputManager.stopListening()
    }

    private suspend fun sendToGateway(text: String) {
        if (text.isBlank()) {
            _uiData.update { it.copy(state = UiState.Idle) }
            return
        }

        _uiData.update { it.copy(state = UiState.Processing) }

        val screenContext = ChatoAccessibilityService.screenContext
        val result = gatewayRepository.chat(text, screenContext)

        result.fold(
            onSuccess = { response ->
                _uiData.update { it.copy(lastResponse = response.content, state = UiState.Speaking) }
                speak(response.content)
            },
            onFailure = { error ->
                _uiData.update {
                    it.copy(state = UiState.Error(error.message ?: "Gateway error"))
                }
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
