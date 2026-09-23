package com.yash.tracker.data.remote

import com.yash.tracker.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where one generateContent call goes, and what proves it may be made.
 *
 * [ownKey] because a rejected credential has to be explained differently depending on whose
 * it was: telling someone to go and fix a key they never entered sends them nowhere.
 */
data class GeminiRoute(
    val url: String,
    val headers: Map<String, String>,
    val ownKey: Boolean,
)

/**
 * Picks between the user's own key and the one the app lends out.
 *
 * Two hosts rather than one because a key set in Settings has no business travelling through
 * our server: it is the user's quota, and sending it to us would mean trusting us with it for
 * no gain. Routing it straight to Google also leaves photo logging working on a day the proxy
 * is down, which is the only reason the Settings field is still there.
 */
@Singleton
class GeminiAuth @Inject constructor(
    private val config: GeminiConfig,
    private val identity: AppIdentity,
) {
    /** Null when the app has no identity yet and the user has set no key of their own. */
    suspend fun route(model: String): GeminiRoute? {
        val path = "v1beta/models/$model:generateContent"

        config.apiKey()?.let { key ->
            return GeminiRoute("$GOOGLE_ORIGIN/$path", mapOf("x-goog-api-key" to key), ownKey = true)
        }

        val token = identity.token() ?: return null
        return GeminiRoute(
            url = "${BuildConfig.PROXY_URL}/$path",
            headers = mapOf("Authorization" to "Bearer $token"),
            ownKey = false,
        )
    }

    private companion object {
        const val GOOGLE_ORIGIN = "https://generativelanguage.googleapis.com"
    }
}
