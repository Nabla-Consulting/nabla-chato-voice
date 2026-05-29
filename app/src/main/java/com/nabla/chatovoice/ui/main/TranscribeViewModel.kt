package com.nabla.chatovoice.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nabla.chatovoice.data.remote.ChatoGatewayRepository
import com.nabla.chatovoice.data.remote.MsalRepository
import com.nabla.chatovoice.domain.repository.GatewayRepository
import com.nabla.chatovoice.service.TranscriptionService
import com.nabla.chatovoice.service.TranscriptionServiceConnection
import com.nabla.chatovoice.util.DebugLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** A single transcribed utterance with diarization info. */
data class TranscriptEntry(
    val timestamp: String,   // HH:mm:ss
    val speakerId: String,   // GUEST_1, GUEST_2, etc. or "Unknown"
    val text: String,
)

sealed class TranscribeState {
    object Idle : TranscribeState()
    object Recording : TranscribeState()
    object Stopping : TranscribeState()
    data class Error(val message: String) : TranscribeState()
}

@HiltViewModel
class TranscribeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gatewayRepository: GatewayRepository,
    private val chatoGatewayRepository: ChatoGatewayRepository,
    private val msalRepository: MsalRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<TranscribeState>(TranscribeState.Idle)
    val state: StateFlow<TranscribeState> = _state.asStateFlow()

    private val _transcriptEntries = MutableStateFlow<List<TranscriptEntry>>(emptyList())
    val transcriptEntries: StateFlow<List<TranscriptEntry>> = _transcriptEntries.asStateFlow()

    private val _summaryText = MutableStateFlow<String?>(null)
    val summaryText: StateFlow<String?> = _summaryText.asStateFlow()

    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing.asStateFlow()

    private val _saveStatus = MutableStateFlow<String?>(null)
    val saveStatus: StateFlow<String?> = _saveStatus.asStateFlow()

    // Fix Bug 3: weak reference to Activity for MSAL interactive sign-in
    private var activityRef: WeakReference<Activity>? = null

    // Fix Bug 1: track collector job to cancel before starting a new one (prevents duplicate utterances)
    private var utteranceCollectorJob: Job? = null

    private val serviceConnection = TranscriptionServiceConnection()
    private var serviceIntent: Intent? = null

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    init {
        // Restore persisted state across app restarts
        _transcriptEntries.update { chatoGatewayRepository.transcriptEntries }
        val savedSummary = chatoGatewayRepository.summaryText
        if (savedSummary.isNotBlank()) _summaryText.update { savedSummary }
    }

    /** Persisted context notes — read by the UI on first composition. */
    val savedContextNotes: String
        get() = chatoGatewayRepository.contextNotes

    /** Persist context notes immediately on every change. */
    fun saveContextNotes(notes: String) {
        chatoGatewayRepository.contextNotes = notes
    }

    /** Persisted default Obsidian folder. */
    val defaultFolder: String
        get() = chatoGatewayRepository.defaultObsidianFolder

    /** Save default Obsidian folder selection. */
    fun saveDefaultFolder(folder: String) {
        chatoGatewayRepository.defaultObsidianFolder = folder
    }

    /** Add a transcript entry and auto-save to prefs. */
    private fun addTranscriptEntry(entry: TranscriptEntry) {
        _transcriptEntries.update { current ->
            val updated = current + entry
            chatoGatewayRepository.transcriptEntries = updated  // persist
            updated
        }
    }

    /** Call from MainActivity so MSAL interactive sign-in has an Activity reference. */
    fun setActivity(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    fun startTranscription(contextNotes: String = "") {
        val azureKey = chatoGatewayRepository.azureSpeechKey
        val azureRegion = chatoGatewayRepository.azureSpeechRegion

        if (azureKey.isBlank()) {
            _state.update { TranscribeState.Error("Azure Speech Key not configured. Open Settings.") }
            return
        }
        if (azureRegion.isBlank()) {
            _state.update { TranscribeState.Error("Azure Speech Region not configured. Open Settings.") }
            return
        }

        // Step 1: Immediate UI feedback
        _state.update { TranscribeState.Recording }

        // Step 2: Reset connection to clear any stale service reference (Bug 1 fix)
        // onServiceDisconnected is NOT called on clean unbind, so we must reset manually.
        serviceConnection.reset()

        // Step 3: Cancel any stale collector before starting a new one
        utteranceCollectorJob?.cancel()
        utteranceCollectorJob = null

        val intent = Intent(context, TranscriptionService::class.java).apply {
            putExtra(TranscriptionService.EXTRA_AZURE_KEY, azureKey)
            putExtra(TranscriptionService.EXTRA_AZURE_REGION, azureRegion)
            // Language is now autodetected (es-US/en-US); EXTRA_LANGUAGE not needed
            putExtra(TranscriptionService.EXTRA_CONTEXT_NOTES, contextNotes)
        }
        serviceIntent = intent

        // Step 4: Start collector BEFORE binding so it is already waiting when
        // onServiceConnected fires and emits the fresh service reference (Bug 3 fix)
        utteranceCollectorJob = viewModelScope.launch {
            val svc = serviceConnection.service.filterNotNull().first()
            DebugLogger.log("TRANSCRIBE", "service connected, collecting utterances")

            launch {
                svc.utterances.collect { utterance ->
                    val entry = TranscriptEntry(
                        timestamp = timeFormat.format(Date()),
                        speakerId = utterance.speakerId,
                        text = utterance.text,
                    )
                    DebugLogger.log("TRANSCRIBE", "[${utterance.speakerId}] ${utterance.text}")
                    addTranscriptEntry(entry)
                }
            }

            launch {
                svc.error.collect { msg ->
                    _state.update { TranscribeState.Error(msg) }
                }
            }

            // Sync recording state from service (e.g., service killed externally).
            // IMPORTANT: skip the initial value (false) — only react to true→false transitions.
            // isRecording is a StateFlow initialized to false. When the collector first
            // subscribes it immediately emits false, which would reset Recording→Idle
            // causing the double-tap bug. Only react after we've seen it go true first.
            launch {
                var seenTrue = false
                svc.isRecording.collect { recording ->
                    if (recording) {
                        seenTrue = true
                    } else if (seenTrue && _state.value is TranscribeState.Recording) {
                        _state.update { TranscribeState.Idle }
                    }
                }
            }
        }

        // Step 5: Bind service — triggers onServiceConnected → sets serviceConnection.service
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        // Step 6: Start the foreground service
        context.startForegroundService(intent)
    }

    fun stopTranscription() {
        // Cancel collector to prevent stacking utterances on reconnect (Bug 1)
        utteranceCollectorJob?.cancel()
        utteranceCollectorJob = null

        _state.update { TranscribeState.Stopping }

        // Tell the service to stop transcribing; it will call stopSelf() via cleanup()
        serviceConnection.service.value?.stopTranscription()

        try {
            context.unbindService(serviceConnection)
        } catch (_: IllegalArgumentException) {
            // Not bound -- ignore
        }

        // Bug 2 fix: do NOT call stopService() here — the service stops itself via
        // stopSelf() inside cleanup() after the async stopTranscribingAsync() completes.
        // Calling stopService() here races with the async coroutine and may kill the
        // service before it finishes cleanup.
        serviceIntent = null

        // Bug 1 fix: clear stale reference after clean unbind (onServiceDisconnected
        // is NOT called for clean unbinds, so we reset manually)
        serviceConnection.reset()

        _state.update { TranscribeState.Idle }
    }

    fun summarize(contextNotes: String) {
        val entries = _transcriptEntries.value
        if (entries.isEmpty()) return

        viewModelScope.launch {
            _isSummarizing.update { true }
            // Keep old summary visible until new one arrives (do NOT clear here)

            // Soft limit: truncate to last N entries to stay within reasonable context size
            val workingEntries = if (entries.size > SUMMARY_SOFT_LIMIT_ENTRIES) {
                DebugLogger.log("TRANSCRIBE", "WARNING: transcript truncated to last $SUMMARY_SOFT_LIMIT_ENTRIES entries for summarization")
                entries.takeLast(SUMMARY_SOFT_LIMIT_ENTRIES)
            } else {
                entries
            }

            val transcriptText = workingEntries.joinToString("\n") { "[${it.timestamp}] ${it.speakerId}: ${it.text}" }
            val prompt = buildString {
                if (contextNotes.isNotBlank()) {
                    appendLine("[Context: $contextNotes]")
                    appendLine()
                }
                appendLine("Transcript:")
                appendLine(transcriptText)
                appendLine()
                // Fix Bug 2: plain ASCII to avoid encoding artifacts
                append("Resume esta conversacion. Incluye: resumen general, puntos clave, y proximos pasos si los hay.")
            }

            val result = gatewayRepository.chat(prompt, null)
            result.fold(
                onSuccess = { response ->
                    // Strip agent prefix like "\uD83E\uDD18 [Chato] " before storing
                    val cleaned = response.content
                        .trimStart()
                        .replace(Regex("^[\\p{So}\\p{Sk}\\p{Sm}\\p{Sc}\\p{Ps}\\p{Pe}]?\\s*\\[\\w+\\]\\s*"), "")
                        .trimStart()
                    _summaryText.update { cleaned }
                    chatoGatewayRepository.summaryText = cleaned
                },
                onFailure = { error ->
                    _summaryText.update { "Error al resumir: ${error.message}" }
                    DebugLogger.log("TRANSCRIBE", "summarize error: ${error.message}")
                }
            )
            _isSummarizing.update { false }
        }
    }

    fun clearTranscript() {
        _transcriptEntries.update { emptyList() }
        _summaryText.update { null }
        chatoGatewayRepository.clearTranscriptData()
    }

    fun dismissError() {
        _state.update { TranscribeState.Idle }
    }

    fun clearSaveStatus() {
        _saveStatus.update { null }
    }

    fun saveSummaryToObsidian(folderPath: String, title: String) {
        viewModelScope.launch {
            try {
                saveToObsidian(folderPath, title, _summaryText.value ?: return@launch)
            } catch (e: Throwable) {
                DebugLogger.log("SAVE", "saveSummary CRASH: ${e::class.simpleName}: ${e.message}\n${e.stackTraceToString().take(500)}")
                _saveStatus.update { "CRASH: ${e::class.simpleName}: ${e.message}" }
            }
        }
    }

    fun saveTranscriptToObsidian(folderPath: String, title: String) {
        val entries = _transcriptEntries.value
        if (entries.isEmpty()) return
        val transcriptText = entries.joinToString("\n") { "[${it.timestamp}] ${it.speakerId}: ${it.text}" }
        viewModelScope.launch {
            try {
                saveToObsidian(folderPath, title, transcriptText)
            } catch (e: Throwable) {
                DebugLogger.log("SAVE", "saveTranscript CRASH: ${e::class.simpleName}: ${e.message}\n${e.stackTraceToString().take(500)}")
                _saveStatus.update { "CRASH: ${e::class.simpleName}: ${e.message}" }
            }
        }
    }

    fun saveBothToObsidian(folderPath: String, title: String) {
        val entries = _transcriptEntries.value
        val summary = _summaryText.value
        viewModelScope.launch {
            try {
                val combined = buildString {
                    if (summary != null) {
                        appendLine("## Summary")
                        appendLine(summary)
                        appendLine()
                    }
                    if (entries.isNotEmpty()) {
                        appendLine("## Transcript")
                        appendLine(entries.joinToString("\n") { "[${it.timestamp}] ${it.speakerId}: ${it.text}" })
                    }
                }
                saveToObsidian(folderPath, title, combined)
            } catch (e: Throwable) {
                DebugLogger.log("SAVE", "saveBoth CRASH: ${e::class.simpleName}: ${e.message}\n${e.stackTraceToString().take(500)}")
                _saveStatus.update { "CRASH: ${e::class.simpleName}: ${e.message}" }
            }
        }
    }

    /**
     * Writes a markdown file to the Obsidian vault on OneDrive via Graph API.
     * Fix Bug 3: tries silent MSAL token first; falls back to interactive sign-in if needed.
     * Path: aeNotes/<folderPath>/<title>.md
     */
    private suspend fun saveToObsidian(folderPath: String, title: String, content: String): Result<Unit> {
        return try {
            DebugLogger.log("SAVE", "saveToObsidian called: folder=$folderPath title=$title contentLen=${content.length}")

            // Log activity ref status
            val activity = activityRef?.get()
            DebugLogger.log("SAVE", "activityRef=${if (activity != null) "OK (${activity::class.simpleName})" else "NULL"}")

            // Log MSAL state
            DebugLogger.log("SAVE", "msalRepository=${msalRepository::class.simpleName}")

            val graphToken = withContext(Dispatchers.Main) {
                DebugLogger.log("SAVE", "calling getGraphToken on Main thread")
                msalRepository.getGraphToken(activity)
            } ?: run {
                DebugLogger.log("SAVE", "MSAL token null, trying settings fallback")
                chatoGatewayRepository.graphToken.takeIf { it.isNotBlank() }
            }
            DebugLogger.log("SAVE", "graphToken=${if (graphToken != null) "OK (${graphToken.length} chars)" else "NULL"}")

            if (graphToken.isNullOrBlank()) {
                // Fix Bug 2: no emoji prefix in error message (avoids encoding artifacts)
                _saveStatus.update { "Sign in with Microsoft required. Tap Save again after signing in." }
                return Result.failure(IllegalStateException("Sign-in required"))
            }

            val isoDate = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val markdownBody = buildString {
                appendLine("---")
                appendLine("created: $isoDate")
                appendLine("tags: [transcript, meeting]")
                appendLine("---")
                appendLine()
                append(content)
            }

            // Sanitize title for use as filename (replace special chars)
            val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "-").trim()
            val encodedPath = "aeNotes/$folderPath/$safeTitle.md"
            val url = "https://graph.microsoft.com/v1.0/me/drive/root:/$encodedPath:/content"

            withContext(Dispatchers.IO) {
                runCatching {
                    val requestBody = markdownBody.toRequestBody("text/plain; charset=utf-8".toMediaType())
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $graphToken")
                        .addHeader("Content-Type", "text/plain")
                        .put(requestBody)
                        .build()

                    val httpClient = OkHttpClient()
                    val response = httpClient.newCall(request).execute()
                    DebugLogger.log("OBSIDIAN", "PUT $url -> ${response.code}")

                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: ""
                        throw RuntimeException("Graph API error ${response.code}: $errorBody")
                    }

                    // Fix Bug 2: plain ASCII status messages (no emoji encoding issues)
                    _saveStatus.update { "Saved to Obsidian: $folderPath/$safeTitle.md" }
                }.onFailure { e ->
                    DebugLogger.log("OBSIDIAN", "save error: ${e.message}")
                    _saveStatus.update { "Save failed: ${e.message}" }
                }
            }
        } catch (e: Throwable) {
            DebugLogger.log("SAVE", "saveToObsidian CRASH: ${e::class.simpleName}: ${e.message}\n${e.stackTraceToString().take(800)}")
            _saveStatus.update { "Error: ${e::class.simpleName}: ${e.message?.take(100)}" }
            Result.failure(e)
        }
    }

    companion object {
        // Azure Speech SDK session limit: 60 minutes (SDK auto-cancels)
        // Workaround: restart session before 60min if needed (not yet implemented)
        const val AZURE_SESSION_TIMEOUT_MS = 60 * 60 * 1000L

        // Soft limit before summarization: warn user if transcript is very long
        // Claude context: ~200k tokens; at ~5 chars/token and ~10 chars/word,
        // ~40k words is safe. At ~6 words/utterance avg, ~6600 utterances.
        const val SUMMARY_SOFT_LIMIT_ENTRIES = 500
    }

    override fun onCleared() {
        super.onCleared()
        // Fix Bug 1: cancel collector job on ViewModel destruction
        utteranceCollectorJob?.cancel()
        utteranceCollectorJob = null
        // Unbind safely if still bound when ViewModel is destroyed
        try {
            context.unbindService(serviceConnection)
        } catch (_: IllegalArgumentException) {
            // Not bound -- ignore
        }
    }
}
