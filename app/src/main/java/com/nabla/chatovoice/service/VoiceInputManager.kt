package com.nabla.chatovoice.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.nabla.chatovoice.util.DebugLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class VoiceInputManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recognizer: SpeechRecognizer? = null

    /**
     * Starts speech recognition and suspends until a result is available.
     * Caller is responsible for calling [stopListening] to trigger final result
     * (e.g. on button release).
     */
    suspend fun listen(): String = suspendCancellableCoroutine { cont ->
        DebugLogger.log("PTT", "listen() called")
        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speechRecognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-GT")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-GT")
            // Google Speech undocumented multi-language support — auto-detects between primary + additional
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        DebugLogger.log("STT", "startListening lang=es-GT+en-US")
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            private var partialResult = ""

            override fun onReadyForSpeech(params: Bundle?) { DebugLogger.log("STT", "ready for speech") }
            override fun onBeginningOfSpeech() { DebugLogger.log("STT", "speech started") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { DebugLogger.log("STT", "speech ended") }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    partialResult = matches[0]
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = if (!matches.isNullOrEmpty()) matches[0] else partialResult
                DebugLogger.log("STT", "result: $text")
                cleanup()
                resumeSafe(cont, text)
            }

            override fun onError(error: Int) {
                DebugLogger.log("STT", "error code: $error")
                cleanup()
                val msg = speechErrorMessage(error)
                if (cont.isActive) {
                    cont.resumeWithException(RuntimeException("Speech error: $msg"))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}

            private fun cleanup() {
                recognizer?.destroy()
                recognizer = null
            }
        })

        cont.invokeOnCancellation {
            speechRecognizer.cancel()
            speechRecognizer.destroy()
            recognizer = null
        }

        speechRecognizer.startListening(intent)
    }

    /**
     * Listens for a single utterance and returns the transcribed text, or null if no speech
     * was detected (ERROR_NO_MATCH or ERROR_SPEECH_TIMEOUT). Other errors are thrown as exceptions.
     */
    suspend fun listenOnce(): String? = suspendCancellableCoroutine { cont ->
        DebugLogger.log("VIM", "listenOnce() called")
        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speechRecognizer

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-GT")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-GT")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("en-US"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { DebugLogger.log("STT", "listenOnce: ready") }
            override fun onBeginningOfSpeech() { DebugLogger.log("STT", "listenOnce: speech started") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { DebugLogger.log("STT", "listenOnce: speech ended") }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = if (!matches.isNullOrEmpty()) matches[0] else null
                DebugLogger.log("STT", "listenOnce result: $text")
                cleanup()
                if (cont.isActive) cont.resume(text)
            }

            override fun onError(error: Int) {
                DebugLogger.log("STT", "listenOnce error: $error")
                cleanup()
                if (!cont.isActive) return
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> cont.resume(null)
                    else -> cont.resumeWithException(RuntimeException("Speech error: ${speechErrorMessage(error)}"))
                }
            }

            private fun cleanup() {
                recognizer?.destroy()
                recognizer = null
            }
        })

        cont.invokeOnCancellation {
            speechRecognizer.cancel()
            speechRecognizer.destroy()
            recognizer = null
        }

        speechRecognizer.startListening(intent)
    }

    fun stopListening() {
        DebugLogger.log("PTT", "stopListening called")
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun resumeSafe(cont: CancellableContinuation<String>, value: String) {
        if (cont.isActive) cont.resume(value)
    }

    private fun speechErrorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
        SpeechRecognizer.ERROR_CLIENT -> "Client error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
        SpeechRecognizer.ERROR_NETWORK -> "Network error"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech match"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
        SpeechRecognizer.ERROR_SERVER -> "Server error"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
        else -> "Unknown error ($error)"
    }
}
