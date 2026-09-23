package com.yash.tracker.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.yash.tracker.BuildConfig
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Proves to the proxy that a call came from an install of this app.
 *
 * An interface for the same reason [GeminiConfig] is one: the real implementation talks to
 * Firebase, which does not exist off-device.
 */
interface AppIdentity {
    /**
     * Null when no identity can be had right now.
     *
     * The first call needs the network — there is no minting one offline — and every later
     * call is served from the token already held, refreshed in the background as it ages out.
     */
    suspend fun token(): String?
}

/**
 * Anonymous because there is nothing to sign in to: the app has no accounts and wants none.
 *
 * What it buys is a per-install identity the proxy can verify and, if one install ever starts
 * burning the shared key, refuse on its own. A secret baked into the APK could do neither —
 * everyone would share it, and revoking it would lock out everybody at once.
 */
@Singleton
class FirebaseAppIdentity @Inject constructor(
    private val auth: FirebaseAuth,
) : AppIdentity {

    override suspend fun token(): String? = runCatching {
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
        user?.getIdToken(false)?.await()?.token
    }.onFailure {
        // Not user-facing: the caller turns a null into one plain sentence. This is only so a
        // debug build can tell "offline" apart from "Firebase refused us".
        if (BuildConfig.DEBUG) Log.d("AppIdentity", "anonymous sign-in failed", it)
    }.getOrNull()
}
