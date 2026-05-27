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
                tts?.setSpeechRate(1.25f)
                val targetVoice = tts?.voices?.find { it.name == "es-us-x-esd-local" }
                if (targetVoice != null) {
                    tts?.voice = targetVoice
                    DebugLogger.log("TTS", "voice set to es-us-x-esd-local")
                } else {
                    DebugLogger.log("TTS", "es-us-x-esd-local not found, using es-US fallback")
                    tts?.setLanguage(Locale("es", "US"))
                }
                isReady = true
                DebugLogger.log("TTS", "ready")
                onReady()
            }
        }
    }

    private fun stripEmojis(text: String): String {
        // Remove emoji unicode ranges: emoticons, misc symbols, transport, supplemental
        return text.replace(Regex("[\\p{So}\\p{Cn}\\uFE00-\\uFE0F\\u200D\\u20E3\\uFE4F]"), "")
            .replace(Regex("[\ud83c-\udbff][\udc00-\udfff]"), "") // surrogate pairs (emoji)
            .trim()
    }
    suspend fun speak(text: String) = suspendCancellableCoroutine { cont ->
        val cleanText = stripEmojis(text)
        DebugLogger.log("TTS", "speaking: $cleanText")
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

        val result = engine.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
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
