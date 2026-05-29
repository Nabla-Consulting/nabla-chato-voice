package com.nabla.chatovoice.data.remote

import android.content.Context
import android.content.SharedPreferences
import com.nabla.chatovoice.BuildConfig
import com.nabla.chatovoice.domain.model.ChatResponse
import com.nabla.chatovoice.util.DebugLogger
import com.nabla.chatovoice.domain.repository.GatewayRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val MODEL = "openclaw/main"
private const val SESSION_USER = "alejandro"
private const val PREFS_NAME = "chato_prefs"
private const val PREF_GATEWAY_URL = "gateway_url"
private const val PREF_GATEWAY_TOKEN = "gateway_token"
private const val PREF_AZURE_SPEECH_KEY = "azure_speech_key"
private const val PREF_AZURE_SPEECH_REGION = "azure_speech_region"
private const val PREF_TRANSCRIPTION_LANGUAGE = "transcription_language"
private const val PREF_GRAPH_TOKEN = "graph_token"
private const val PREF_CONTEXT_NOTES = "context_notes"
private const val PREF_SUMMARY_TEXT = "summary_text"
private const val PREF_TRANSCRIPT_JSON = "transcript_json"
private const val PREF_DEFAULT_FOLDER = "default_obsidian_folder"
private const val DEFAULT_GATEWAY_URL = ""
private const val DEFAULT_AZURE_REGION = "eastus"
private const val DEFAULT_TRANSCRIPTION_LANGUAGE = "en-US"
private const val MAX_SCREEN_CONTEXT_CHARS = 500

@Singleton
class ChatoGatewayRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val httpClient: OkHttpClient,
) : GatewayRepository {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    val gatewayUrl: String
        get() = prefs.getString(PREF_GATEWAY_URL, "")
            ?.ifBlank { if (BuildConfig.DEBUG) BuildConfig.DEBUG_GATEWAY_URL else "" }
            ?: ""

    // In debug builds, fall back to BuildConfig values injected from secrets.properties (gitignored).
    // In release builds, BuildConfig fields are empty strings — user must configure via Settings.
    val gatewayToken: String
        get() = prefs.getString(PREF_GATEWAY_TOKEN, "")
            ?.ifBlank { BuildConfig.DEBUG_GATEWAY_TOKEN } ?: BuildConfig.DEBUG_GATEWAY_TOKEN

    val azureSpeechKey: String
        get() = prefs.getString(PREF_AZURE_SPEECH_KEY, "")
            ?.ifBlank { BuildConfig.DEBUG_AZURE_SPEECH_KEY } ?: BuildConfig.DEBUG_AZURE_SPEECH_KEY

    val azureSpeechRegion: String
        get() = prefs.getString(PREF_AZURE_SPEECH_REGION, "")
            ?.ifBlank { BuildConfig.DEBUG_AZURE_SPEECH_REGION.ifBlank { DEFAULT_AZURE_REGION } }
            ?: DEFAULT_AZURE_REGION

    val transcriptionLanguage: String
        get() = prefs.getString(PREF_TRANSCRIPTION_LANGUAGE, DEFAULT_TRANSCRIPTION_LANGUAGE) ?: DEFAULT_TRANSCRIPTION_LANGUAGE

    /** Microsoft Graph API bearer token for OneDrive/Obsidian access. */
    val graphToken: String
        get() = prefs.getString(PREF_GRAPH_TOKEN, "") ?: ""

    fun saveSettings(url: String, token: String) {
        prefs.edit()
            .putString(PREF_GATEWAY_URL, url.trimEnd('/'))
            .putString(PREF_GATEWAY_TOKEN, token.trim())
            .apply()
    }

    fun saveAzureSettings(key: String, region: String) {
        prefs.edit()
            .putString(PREF_AZURE_SPEECH_KEY, key.trim())
            .putString(PREF_AZURE_SPEECH_REGION, region.trim())
            .apply()
    }

    fun saveGraphToken(token: String) {
        prefs.edit()
            .putString(PREF_GRAPH_TOKEN, token.trim())
            .apply()
    }

    // Context notes persistence
    var contextNotes: String
        get() = prefs.getString(PREF_CONTEXT_NOTES, "") ?: ""
        set(value) = prefs.edit().putString(PREF_CONTEXT_NOTES, value).apply()

    // Summary text persistence
    var summaryText: String
        get() = prefs.getString(PREF_SUMMARY_TEXT, "") ?: ""
        set(value) = prefs.edit().putString(PREF_SUMMARY_TEXT, value).apply()

    // Transcript entries persistence — serialized as tab-delimited lines
    var transcriptEntries: List<com.nabla.chatovoice.ui.main.TranscriptEntry>
        get() {
            val raw = prefs.getString(PREF_TRANSCRIPT_JSON, "") ?: ""
            if (raw.isBlank()) return emptyList()
            return raw.lines().mapNotNull { line ->
                val parts = line.split("\t", limit = 3)
                if (parts.size == 3) com.nabla.chatovoice.ui.main.TranscriptEntry(parts[0], parts[1], parts[2]) else null
            }
        }
        set(value) {
            val raw = value.joinToString("\n") { "${it.timestamp}\t${it.speakerId}\t${it.text}" }
            prefs.edit().putString(PREF_TRANSCRIPT_JSON, raw).apply()
        }

    /** Default Obsidian folder persisted across sessions. */
    var defaultObsidianFolder: String
        get() = prefs.getString(PREF_DEFAULT_FOLDER, "Research") ?: "Research"
        set(value) { prefs.edit().putString(PREF_DEFAULT_FOLDER, value).apply() }

    fun clearTranscriptData() {
        prefs.edit()
            .remove(PREF_CONTEXT_NOTES)
            .remove(PREF_SUMMARY_TEXT)
            .remove(PREF_TRANSCRIPT_JSON)
            .apply()
    }

    fun hasToken(): Boolean = gatewayToken.isNotBlank()

    override suspend fun chat(userText: String, screenContext: String?): Result<ChatResponse> {
        DebugLogger.log("GW", "chat() called: $userText")
        val token = gatewayToken
        if (token.isBlank()) {
            return Result.failure(IllegalStateException("Gateway token not configured."))
        }

        val content = buildString {
            append(userText)
            if (!screenContext.isNullOrBlank()) {
                val trimmed = screenContext.take(MAX_SCREEN_CONTEXT_CHARS)
                append("\n\n[Screen context: $trimmed]")
            }
        }

        val bodyJson = JSONObject().apply {
            put("model", MODEL)
            put("user", SESSION_USER)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", content)
                    })
                }
            )
        }.toString()

        val requestBody = bodyJson.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("${gatewayUrl}/v1/chat/completions")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        DebugLogger.log("GW", "POST ${gatewayUrl}/v1/chat/completions")
        return withContext(Dispatchers.IO) { runCatching {
            val response = httpClient.newCall(request).execute()
            DebugLogger.log("GW", "response ${response.code}")
            val responseBody = response.body?.string()
                ?: throw IllegalStateException("Empty response body.")

            if (!response.isSuccessful) {
                DebugLogger.log("GW", "error: Gateway ${response.code}: $responseBody")
                throw RuntimeException("Gateway error ${response.code}: $responseBody")
            }

            val json = JSONObject(responseBody)
            val text = json
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            ChatResponse(content = text)
        }.onFailure { e ->
            DebugLogger.log("GW", "error: ${e.toString()}")
        } }
    }
}
