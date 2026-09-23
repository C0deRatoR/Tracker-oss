package com.yash.tracker.data.remote

import com.yash.tracker.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/** Where one generateContent call goes, and what proves it may be made. */
data class GeminiRoute(val url: String, val headers: Map<String, String>)

/**
 * Every call goes to the proxy, which holds the only Gemini key anyone uses.
 *
 * There was briefly a second route — a key the user pasted into Settings, sent straight to
 * Google on their own quota. It is gone: asking someone to go and fetch an API key is the
 * friction the proxy exists to remove, and keeping the field meant keeping a second path
 * through every failure message for the handful of people who would ever have used it.
 *
 * The cost is honest. If the proxy is down, photo logging is down, with no way for a user to
 * route around it. Everything else in the app — the diary, the gym log, manual entry — is
 * on-device and keeps working.
 */
@Singleton
class GeminiAuth @Inject constructor(private val identity: AppIdentity) {

    /** Null when the app has no identity yet, which needs the network once and then never again. */
    suspend fun route(model: String): GeminiRoute? {
        val token = identity.token() ?: return null
        return GeminiRoute(
            url = "${BuildConfig.PROXY_URL}/v1beta/models/$model:generateContent",
            headers = mapOf("Authorization" to "Bearer $token"),
        )
    }
}
