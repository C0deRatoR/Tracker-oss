package com.yash.tracker.data.remote

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the ExerciseDB id a catalogue row carries into a current animation URL.
 *
 * Only ids are bundled: the free tier forbids storing anything it returns, and its media URLs
 * rotate every Monday, so the URL is asked for when a row is actually on screen. It is kept in
 * memory for the life of the process — never on disk — so scrolling back up the library does
 * not ask twice, and [forget] drops one that stopped loading after a rotation.
 *
 * The API sits behind a Cloudflare limit that answers a burst of about ten calls with 429 and
 * a Retry-After of a few seconds. Calls therefore go one at a time and wait a 429 out rather
 * than failing, which keeps a screenful of rows filling in; a row scrolled off screen cancels
 * its own wait. Anything else — offline, a missing id, a malformed reply — is null, and the
 * caller keeps showing the equipment mark.
 */
@Singleton
class ExerciseMedia @Inject constructor(
    private val service: ExerciseDbService,
) {
    private val known = ConcurrentHashMap<String, String>()
    private val oneAtATime = Mutex()

    /** A URL already fetched this session, without waiting for anything. */
    fun peek(id: String): String? = known[id]

    suspend fun gifUrl(id: String): String? {
        known[id]?.let { return it }
        return oneAtATime.withLock {
            // Another row may have asked for the same id while this one queued.
            known[id] ?: fetch(id)?.also { known[id] = it }
        }
    }

    fun forget(id: String) {
        known.remove(id)
    }

    private suspend fun fetch(id: String): String? {
        repeat(MAX_ATTEMPTS) {
            val response = try {
                service.exercise("$BASE_URL/$id")
            } catch (e: IOException) {
                return null
            } catch (e: SerializationException) {
                Log.w(TAG, "ExerciseDB $id: unreadable reply", e)
                return null
            }

            if (response.code() == HTTP_TOO_MANY_REQUESTS) {
                val waitSeconds = response.headers()["Retry-After"]?.toLongOrNull()
                    ?: DEFAULT_RETRY_AFTER_SECONDS
                delay(waitSeconds.coerceIn(1, MAX_RETRY_AFTER_SECONDS) * 1_000)
                return@repeat
            }
            if (!response.isSuccessful) {
                Log.w(TAG, "ExerciseDB $id: HTTP ${response.code()}")
                return null
            }

            // The reply is someone else's data; only an https link is handed to the loader.
            val url = response.body()?.takeIf { it.success }?.data?.gifUrl
            return url?.takeIf { it.startsWith("https://") }
        }
        Log.w(TAG, "ExerciseDB $id: still rate limited after $MAX_ATTEMPTS attempts")
        return null
    }

    private companion object {
        const val TAG = "ExerciseMedia"
        const val BASE_URL = "https://oss.exercisedb.dev/api/v1/exercises"
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val MAX_ATTEMPTS = 3
        const val DEFAULT_RETRY_AFTER_SECONDS = 10L

        // Measured at 6-10 s. A longer ask than this is not worth holding a row's slot for.
        const val MAX_RETRY_AFTER_SECONDS = 15L
    }
}
