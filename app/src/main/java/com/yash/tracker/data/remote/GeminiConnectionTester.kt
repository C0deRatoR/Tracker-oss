package com.yash.tracker.data.remote

import com.yash.tracker.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ConnectionResult {
    data class Ok(val modelCount: Int, val modelAvailable: Boolean, val model: String) : ConnectionResult
    data class Failed(val message: String) : ConnectionResult
}

/**
 * Validates the key and the configured model in one cheap call.
 *
 * Listing models costs no tokens, and it answers both questions that matter: whether the key
 * works, and whether the model id in Settings still exists — which matters because Google
 * retires model ids on its own schedule.
 */
@Singleton
class GeminiConnectionTester @Inject constructor(
    private val settings: SettingsRepository,
    private val io: CoroutineDispatcher,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun test(): ConnectionResult = withContext(io) {
        val key = settings.apiKey()
        if (key.isNullOrBlank()) {
            return@withContext ConnectionResult.Failed("No API key saved yet.")
        }
        val model = settings.model()

        val request = Request.Builder()
            .url("$BASE/models")
            // Header, never the URL: query strings end up in logs and crash reports.
            .header("x-goog-api-key", key)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val names = modelNames(response.body.string())
                        ConnectionResult.Ok(
                            modelCount = names.size,
                            modelAvailable = names.any { it.endsWith(model) },
                            model = model,
                        )
                    }

                    // A malformed key comes back as 400 with API_KEY_INVALID, not 401 — so
                    // matching on status alone would report it as a network problem.
                    response.code == 401 || response.code == 403 || response.code == 400 -> {
                        val body = response.body.string()
                        if (response.code != 400 || body.contains("API_KEY_INVALID")) {
                            ConnectionResult.Failed("Your Gemini key was rejected. Check it and try again.")
                        } else {
                            ConnectionResult.Failed("Gemini rejected the request (HTTP 400).")
                        }
                    }

                    response.code == 429 ->
                        ConnectionResult.Failed("Rate limited. Try again in a minute.")

                    response.code >= 500 ->
                        ConnectionResult.Failed("Google's side had a problem. Try again shortly.")

                    else ->
                        ConnectionResult.Failed("Couldn't reach Gemini (HTTP ${response.code}).")
                }
            }
        } catch (e: IOException) {
            ConnectionResult.Failed("No connection. Check your network and try again.")
        }
    }

    private fun modelNames(body: String): List<String> = runCatching {
        json.parseToJsonElement(body)
            .jsonObject["models"]
            ?.jsonArray
            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
            .orEmpty()
    }.getOrDefault(emptyList())

    private companion object {
        const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    }
}
