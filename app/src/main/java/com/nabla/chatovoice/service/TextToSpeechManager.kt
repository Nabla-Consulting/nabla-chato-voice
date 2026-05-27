package com.nabla.chatovoice.service

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.nabla.chatovoice.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val UTTERANCE_ID = "chato_tts"

@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    fun initialize(onReady: () -> Unit) {
        DebugLogger.log("TTS", "init called")
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setSpeechRate(0.85f)
                val locale = Locale("es", "GT")
                val langResult = tts?.setLanguage(locale)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    DebugLogger.log("TTS", "es-GT not supported, falling back to es-US")
                    tts?.setLanguage(Locale("es", "US"))
                } else {
                    DebugLogger.log("TTS", "language set to es-GT")
                }
                val voices = tts?.voices
                if (voices != null) {
                    val esVoices = voices.filter { it.locale.language == "es" }
                        .sortedBy { it.name }
                        .joinToString(", ") { "${it.name}(${it.locale})" }
                    DebugLogger.log("TTS", "es voices: $esVoices")
                    DebugLogger.log("TTS", "total voices: ${voices.size}")
                }
                isReady = true
                DebugLogger.log("TTS", "ready")
                onReady()
            }
        }
    }

    suspend fun speak(text: String) = suspendCancellableCoroutine { cont ->
        DebugLogger.log("TTS", "speaking: $text")
        val engine = tts
        if (engine == null || !isReady) {
            cont.resumeWithException(IllegalStateException("TTS not initialized."))
            return@suspendCancellableCoroutine
        }

        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (utteranceId == UTTERANCE_ID && cont.isActive) {
                    DebugLogger.log("TTS", "done")
                    cont.resume(Unit)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == UTTERANCE_ID && cont.isActive) {
                    cont.resumeWithException(RuntimeException("TTS utterance error."))
                }
            }
        })

        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        if (result == TextToSpeech.ERROR) {
            if (cont.isActive) {
                cont.resumeWithException(RuntimeException("TTS speak() returned ERROR."))
            }
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}
