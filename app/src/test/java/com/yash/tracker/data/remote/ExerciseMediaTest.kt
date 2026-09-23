package com.yash.tracker.data.remote

import com.yash.tracker.data.remote.dto.ExerciseDbExercise
import com.yash.tracker.data.remote.dto.ExerciseDbReply
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.Headers.Companion.headersOf
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.io.IOException

/** Robolectric only because a failure writes to android.util.Log. */
@RunWith(RobolectricTestRunner::class)
class ExerciseMediaTest {

    /** Replays canned replies in order and records every URL asked for. */
    private class FakeService(vararg replies: () -> Response<ExerciseDbReply>) : ExerciseDbService {
        private val queue = ArrayDeque(replies.toList())
        val asked = mutableListOf<String>()

        override suspend fun exercise(url: String): Response<ExerciseDbReply> {
            asked += url
            return queue.removeFirst().invoke()
        }
    }

    private fun ok(gifUrl: String?) = {
        Response.success(ExerciseDbReply(success = true, data = ExerciseDbExercise("EIeI8Vf", gifUrl)))
    }

    private fun status(code: Int, retryAfter: String? = null) = {
        val headers = retryAfter?.let { headersOf("Retry-After", it) } ?: headersOf()
        Response.error<ExerciseDbReply>(
            "".toResponseBody(),
            okhttp3.Response.Builder()
                .request(okhttp3.Request.Builder().url("https://oss.exercisedb.dev/").build())
                .protocol(okhttp3.Protocol.HTTP_2)
                .code(code)
                .message("")
                .headers(headers)
                .build(),
        )
    }

    private val gif = "https://static.exercisedb.dev/media/EIeI8Vf.gif"

    @Test
    fun `asks for the id and keeps the url for the session`() = runTest {
        val service = FakeService(ok(gif))
        val media = ExerciseMedia(service)

        assertEquals(gif, media.gifUrl("EIeI8Vf"))
        assertEquals(gif, media.gifUrl("EIeI8Vf"))
        assertEquals(gif, media.peek("EIeI8Vf"))
        assertEquals(listOf("https://oss.exercisedb.dev/api/v1/exercises/EIeI8Vf"), service.asked)
    }

    @Test
    fun `waits out a 429 for as long as it is told, then asks again`() = runTest {
        val service = FakeService(status(429, retryAfter = "7"), ok(gif))
        val media = ExerciseMedia(service)

        assertEquals(gif, media.gifUrl("EIeI8Vf"))
        assertEquals(7_000L, currentTime)
        assertEquals(2, service.asked.size)
    }

    @Test
    fun `gives up after three 429s rather than queueing forever`() = runTest {
        val service = FakeService(status(429, "1"), status(429, "1"), status(429, "1"))

        assertNull(ExerciseMedia(service).gifUrl("EIeI8Vf"))
        assertEquals(3, service.asked.size)
    }

    @Test
    fun `an absurd Retry-After is capped`() = runTest {
        val service = FakeService(status(429, retryAfter = "3600"), ok(gif))

        ExerciseMedia(service).gifUrl("EIeI8Vf")
        assertEquals(15_000L, currentTime)
    }

    @Test
    fun `a failure is null and not remembered`() = runTest {
        val service = FakeService(status(404), { throw IOException("offline") }, ok(gif))
        val media = ExerciseMedia(service)

        assertNull(media.gifUrl("EIeI8Vf"))
        assertNull(media.gifUrl("EIeI8Vf"))
        assertEquals(gif, media.gifUrl("EIeI8Vf"))
    }

    @Test
    fun `a malformed or unsafe reply is null`() = runTest {
        val service = FakeService(
            { throw SerializationException("truncated") },
            { Response.success(ExerciseDbReply(success = false, data = null)) },
            ok(null),
            ok("http://static.exercisedb.dev/media/EIeI8Vf.gif"),
            ok("javascript:alert(1)"),
        )
        val media = ExerciseMedia(service)

        repeat(5) { assertNull(media.gifUrl("EIeI8Vf")) }
    }

    @Test
    fun `forgetting a url that stopped loading fetches it fresh`() = runTest {
        val rotated = "https://static.exercisedb.dev/media/rotated.gif"
        val service = FakeService(ok(gif), ok(rotated))
        val media = ExerciseMedia(service)

        media.gifUrl("EIeI8Vf")
        media.forget("EIeI8Vf")
        assertEquals(rotated, media.gifUrl("EIeI8Vf"))
    }
}
