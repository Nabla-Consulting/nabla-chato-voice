package com.nabla.chatovoice.service

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.microsoft.cognitiveservices.speech.*
import com.microsoft.cognitiveservices.speech.ResultReason
import com.microsoft.cognitiveservices.speech.audio.AudioConfig
import com.microsoft.cognitiveservices.speech.transcription.ConversationTranscriber
import com.microsoft.cognitiveservices.speech.AutoDetectSourceLanguageConfig
import com.nabla.chatovoice.ui.main.MainActivity
import com.nabla.chatovoice.util.DebugLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class TranscriptionService : Service() {

    inner class TranscriptionBinder : Binder() {
        fun getService(): TranscriptionService = this@TranscriptionService
    }

    private val binder = TranscriptionBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Flows that the ViewModel observes
    val utterances = MutableSharedFlow<TranscriptUtterance>(extraBufferCapacity = 64)

    // Debounce per speaker: Azure emits individual speaker utterances then a consolidated
    // final within a burst. We debounce PER SPEAKER so that:
    // - Multiple utterances from the same speaker within 300ms → only the last emits
    // - Utterances from different speakers are independent and both emit
    private val pendingBySpeaker = mutableMapOf<String, String>()   // speakerId → latest text
    private val debounceJobBySpeaker = mutableMapOf<String, kotlinx.coroutines.Job>()
    private val debounceLock = Any()
    val isRecording = MutableStateFlow(false)
    val error = MutableSharedFlow<String>(extraBufferCapacity = 8)

    private var transcriber: ConversationTranscriber? = null
    private var wakeLock: PowerManager.WakeLock? = null
    // Fix Bug 2: flag so service knows to stop itself after async cleanup
    private var stopRequested = false

    companion object {
        const val CHANNEL_ID = "transcription_channel"
        const val NOTIF_ID = 101
        const val EXTRA_AZURE_KEY = "azure_key"
        const val EXTRA_AZURE_REGION = "azure_region"
        // EXTRA_LANGUAGE kept for backward compat but ignored — language is autodetected
        const val EXTRA_LANGUAGE = "language"
        const val EXTRA_CONTEXT_NOTES = "context_notes"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        startForeground(NOTIF_ID, notification)

        val azureKey = intent?.getStringExtra(EXTRA_AZURE_KEY) ?: ""
        val azureRegion = intent?.getStringExtra(EXTRA_AZURE_REGION) ?: ""
        val contextNotes = intent?.getStringExtra(EXTRA_CONTEXT_NOTES) ?: ""

        serviceScope.launch {
            startTranscribing(azureKey, azureRegion, contextNotes)
        }
        return START_NOT_STICKY
    }

    private suspend fun startTranscribing(
        azureKey: String,
        azureRegion: String,
        contextNotes: String,
    ) {
        try {
            val speechConfig = SpeechConfig.fromSubscription(azureKey, azureRegion).apply {
                // Semantic segmentation: splits by meaning, not silence.
                // Produces more complete utterances with better speaker attribution
                // vs Default (silence-based) which can split mid-sentence and misattribute speakers.
                setProperty(PropertyId.Speech_SegmentationStrategy, "Semantic")
            }
            val autoDetectConfig = AutoDetectSourceLanguageConfig.fromLanguages(
                listOf("es-US", "en-US")
            )
            val audioConfig = AudioConfig.fromDefaultMicrophoneInput()
            val ct = ConversationTranscriber(speechConfig, autoDetectConfig, audioConfig)
            transcriber = ct

            if (contextNotes.isNotBlank()) {
                val phraseList = PhraseListGrammar.fromRecognizer(ct)
                contextNotes.split(",", "\n", ";")
                    .map { it.trim() }.filter { it.isNotBlank() }
                    .forEach { phraseList.addPhrase(it) }
                DebugLogger.log("SVC", "PhraseList: ${contextNotes.take(80)}")
            }

            ct.transcribed.addEventListener { _, e ->
                val r = e.result
                DebugLogger.log("SVC", "transcribed event: reason=${r.reason} speakerId=${r.speakerId} text=${r.text.take(80)}")
                // Only process fully recognized speech — skip NoMatch, Canceled, partials
                if (r.reason != ResultReason.RecognizedSpeech) {
                    DebugLogger.log("SVC", "SKIPPED reason=${r.reason}")
                    return@addEventListener
                }
                if (r.text.isNotBlank()) {
                    val speakerId = normalizeSpeakerId(r.speakerId ?: "Unknown")
                    // Debounce 300ms PER SPEAKER: cancel+replace only within same speaker.
                    // Different speakers get independent debounce jobs so neither gets dropped.
                    synchronized(debounceLock) {
                        pendingBySpeaker[speakerId] = r.text
                        debounceJobBySpeaker[speakerId]?.cancel()
                        debounceJobBySpeaker[speakerId] = serviceScope.launch {
                            kotlinx.coroutines.delay(300)
                            val text = synchronized(debounceLock) { pendingBySpeaker.remove(speakerId) }
                            if (text != null) {
                                DebugLogger.log("SVC", "emitting [$speakerId]: ${text.take(60)}")
                                utterances.emit(TranscriptUtterance(speakerId, text))
                            }
                        }
                    }
                }
            }

            ct.canceled.addEventListener { _, e ->
                DebugLogger.log("SVC", "canceled: ${e.errorDetails}")
                serviceScope.launch {
                    error.emit("Transcription canceled: ${e.errorDetails}")
                    isRecording.update { false }
                }
            }

            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChatoVoice::TranscriptionLock")
                .also { it.acquire(4 * 60 * 60 * 1000L) } // max 4h timeout safety net
            DebugLogger.log("SVC", "WakeLock acquired")
            ct.startTranscribingAsync().get()
            isRecording.update { true }
            DebugLogger.log("SVC", "transcription started, autodetect: es-US,en-US")
        } catch (e: Exception) {
            DebugLogger.log("SVC", "start error: ${e.message}")
            error.emit(e.message ?: "Failed to start")
            isRecording.update { false }
            stopSelf()
        }
    }

    fun stopTranscription() {
        stopRequested = true
        serviceScope.launch {
            try {
                transcriber?.stopTranscribingAsync()?.get()
            } catch (e: Exception) {
                DebugLogger.log("SVC", "stop error: ${e.message}")
            } finally {
                cleanup()
            }
        }
    }

    private fun cleanup() {
        synchronized(debounceLock) {
            debounceJobBySpeaker.values.forEach { it.cancel() }
            debounceJobBySpeaker.clear()
            pendingBySpeaker.clear()
        }
        try { transcriber?.close() } catch (_: Exception) {}
        transcriber = null
        isRecording.update { false }
        stopForeground(STOP_FOREGROUND_REMOVE)
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (e: Exception) {
            DebugLogger.log("SVC", "WakeLock release error: ${e.message}")
        }
        wakeLock = null
        DebugLogger.log("SVC", "WakeLock released")
        DebugLogger.log("SVC", "cleanup: calling stopSelf()")
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Transcription", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Active transcription session" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Transcribing...")
            .setContentText("Tap to return to Chato Voice")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun normalizeSpeakerId(raw: String): String {
        if (raw == "Unknown" || raw.isBlank()) return "Unknown"
        val match = Regex("(?i)guest[-_]?(\\d+)").find(raw)
        return if (match != null) "GUEST_${match.groupValues[1]}" else raw
    }
}

data class TranscriptUtterance(val speakerId: String, val text: String)
