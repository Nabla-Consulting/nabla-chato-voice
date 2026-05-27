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
