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
import kotlinx.coroutines.delay
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
    private var conversationJob: Job? = null

    companion object {
        /** 5 minutes of silence stops the conversation loop. */
        private const val CONVERSATION_SILENCE_TIMEOUT_MS = 300_000L
        /** Brief pause between TTS completion and the next listen cycle. */
        private const val CONVERSATION_LOOP_DELAY_MS = 300L
        /** Retry delay when the recognizer reports busy. */
        private const val RECOGNIZER_BUSY_RETRY_DELAY_MS = 500L
    }

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
                azureSpeechKey = chatoGatewayRepository.azureSpeechKey,
                azureSpeechRegion = chatoGatewayRepository.azureSpeechRegion,
                transcriptionLanguage = chatoGatewayRepository.transcriptionLanguage,
                graphToken = chatoGatewayRepository.graphToken,
            )
        }
    }

    fun saveSettings(url: String, token: String) {
        chatoGatewayRepository.saveSettings(url, token)
        loadSettings()
    }

    fun saveAzureSettings(key: String, region: String) {
        chatoGatewayRepository.saveAzureSettings(key, region)
        loadSettings()
    }

    fun saveGraphToken(token: String) {
        chatoGatewayRepository.saveGraphToken(token)
        loadSettings()
    }

    fun refreshAccessibilityStatus() {
        _uiData.update {
            it.copy(isAccessibilityConnected = ChatoAccessibilityService.isConnected)
        }
    }

    // --- Conversation mode ---

    fun startConversation() {
        if (_uiData.value.isConversationMode) return
        val hasToken = _uiData.value.hasToken
        DebugLogger.log("VM", "startConversation, hasToken=$hasToken")
        if (!hasToken) {
            _uiData.update { it.copy(state = UiState.Error("Gateway token not set. Open Settings.")) }
            return
        }
        _uiData.update { it.copy(isConversationMode = true, state = UiState.Recording) }
        conversationJob = viewModelScope.launch {
            runConversationLoop()
        }
    }

    fun stopConversation() {
        DebugLogger.log("VM", "stopConversation")
        conversationJob?.cancel()
        conversationJob = null
        voiceInputManager.stopListening()
        _uiData.update { it.copy(isConversationMode = false, state = UiState.Idle) }
    }

    private suspend fun runConversationLoop() {
        var lastSpeechMs = System.currentTimeMillis()
        var silenceRetryCount = 0

        try {
            while (_uiData.value.isConversationMode) {
                _uiData.update { it.copy(state = UiState.Recording) }

                val text = try {
                    voiceInputManager.listenOnce()
                } catch (e: RuntimeException) {
                    val msg = e.message ?: ""
                    DebugLogger.log("VM", "listenOnce error in loop: $msg")
                    when {
                        msg.contains("Recognizer busy") -> {
                            delay(RECOGNIZER_BUSY_RETRY_DELAY_MS)
                            continue
                        }
                        else -> {
                            // Non-fatal errors: treat as silence
                            null
                        }
                    }
                }

                if (!_uiData.value.isConversationMode) break

                if (text.isNullOrBlank()) {
                    // No speech detected — check silence timeout
                    val silentMs = System.currentTimeMillis() - lastSpeechMs
                    DebugLogger.log("VM", "conv: no speech, silent=${silentMs}ms")
                    if (silentMs >= CONVERSATION_SILENCE_TIMEOUT_MS) {
                        DebugLogger.log("VM", "conv: 5-min silence timeout, stopping")
                        stopConversation()
                        return
                    }
                    silenceRetryCount++
                    continue
                }

                // Got speech — reset silence tracking
                lastSpeechMs = System.currentTimeMillis()
                silenceRetryCount = 0

                _uiData.update { it.copy(
                    messages = it.messages + ChatMessage(text, MessageSender.USER),
                    state = UiState.Thinking
                )}

                sendToGatewayForConversation(text)

                if (!_uiData.value.isConversationMode) break

                // Brief pause after TTS before next listen cycle
                delay(CONVERSATION_LOOP_DELAY_MS)
            }
        } catch (e: CancellationException) {
            DebugLogger.log("VM", "conv: loop cancelled")
            // Job was cancelled — let caller clean up state
        }
    }

    private suspend fun sendToGatewayForConversation(text: String) {
        if (text.isBlank()) return
        DebugLogger.log("VM", "conv sending: $text")
        _uiData.update { it.copy(state = UiState.Processing) }
        val screenContext = ChatoAccessibilityService.screenContext
        val result = gatewayRepository.chat(text, screenContext)
        result.fold(
            onSuccess = { response ->
                val cleanContent = stripAgentPrefix(response.content)
                DebugLogger.log("VM", "conv response received")
                _uiData.update { it.copy(
                    messages = it.messages + ChatMessage(cleanContent, MessageSender.CHATO),
                    state = UiState.Speaking
                )}
                // Do not restart loop while TTS is speaking
                speak(cleanContent)
                // speak() sets state to Idle when done; restore Recording if still in conversation
                if (_uiData.value.isConversationMode) {
                    _uiData.update { it.copy(state = UiState.Recording) }
                }
            },
            onFailure = { error ->
                DebugLogger.log("VM", "conv error: $error")
                _uiData.update { it.copy(state = UiState.Error(error.toString())) }
                // On gateway error in conversation mode, stop the loop
                stopConversation()
            }
        )
    }

    // --- PTT (existing, preserved) ---

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

    private fun stripAgentPrefix(text: String): String {
        // Matches patterns like "🤘 [Chato] ", "💻 [Elliot] ", etc.
        return text.trimStart()
            .replace(Regex("^[\\p{So}\\p{Cn}\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+\\s*\\[\\w+\\]\\s*"), "")
            .trimStart()
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
                val cleanContent = stripAgentPrefix(response.content)
                DebugLogger.log("VM", "response received")
                _uiData.update { it.copy(
                    messages = it.messages + ChatMessage(cleanContent, MessageSender.CHATO),
                    state = UiState.Speaking
                )}
                speak(cleanContent)
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
        conversationJob?.cancel()
        voiceInputManager.destroy()
        ttsManager.destroy()
    }
}
