package com.nabla.chatovoice.data.remote

import android.content.Context
import android.content.SharedPreferences
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
private const val DEFAULT_GATEWAY_URL = "http://GATEWAY_HOST:18789"
// Token must be configured via Settings dialog at runtime
private const val DEFAULT_GATEWAY_TOKEN = ""
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
        get() = prefs.getString(PREF_GATEWAY_URL, DEFAULT_GATEWAY_URL) ?: DEFAULT_GATEWAY_URL

    val gatewayToken: String
        get() = prefs.getString(PREF_GATEWAY_TOKEN, DEFAULT_GATEWAY_TOKEN) ?: DEFAULT_GATEWAY_TOKEN

    fun saveSettings(url: String, token: String) {
        prefs.edit()
            .putString(PREF_GATEWAY_URL, url.trimEnd('/'))
            .putString(PREF_GATEWAY_TOKEN, token.trim())
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
