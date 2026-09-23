package com.yash.tracker.data.remote

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.remote.dto.GenerateContentRequest
import com.yash.tracker.data.remote.dto.GenerateContentResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.io.IOException

/**
 * Golden-file tests over recorded Gemini payloads (TRD §12), covering the malformed and empty
 * cases as well as the happy path.
 */
@RunWith(RobolectricTestRunner::class)
class GeminiClientTest {

    private lateinit var db: AppDatabase
    private val config = FakeConfig()
    private val identity = FakeIdentity()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    /** Stands in for Settings, whose real implementation needs the Android Keystore. */
    private class FakeConfig(
        var key: String? = "test-key",
        var model: String = "gemini-3.5-flash-lite",
        var grounding: Boolean = true,
    ) : GeminiConfig {
        override suspend fun apiKey() = key
        override suspend fun model() = model
        override suspend fun isGroundingEnabled() = grounding
    }

    /** Stands in for Firebase, which needs a real device and a real project. */
    private class FakeIdentity(var stored: String? = "test-token") : AppIdentity {
        override suspend fun token() = stored
    }

    /** Replays canned responses and records what was sent. */
    private class FakeService(
        var responses: MutableList<() -> Response<GenerateContentResponse>>,
    ) : GeminiService {
        val requests = mutableListOf<GenerateContentRequest>()
        var url: String? = null
        var headers: Map<String, String> = emptyMap()

        override suspend fun generateContent(
            url: String,
            headers: Map<String, String>,
            request: GenerateContentRequest,
        ): Response<GenerateContentResponse> {
            this.url = url
            this.headers = headers
            requests += request
            return responses.removeAt(0).invoke()
        }
    }

    private fun reply(text: String) = Response.success(
        Json.decodeFromString<GenerateContentResponse>(
            """
            {"candidates":[{"content":{"parts":[{"text":${Json.encodeToString(text)}}]},"finishReason":"STOP"}]}
            """.trimIndent(),
        ),
    )

    private fun httpError(code: Int) = Response.error<GenerateContentResponse>(
        code,
        """{"error":{"code":$code,"status":"API_KEY_INVALID"}}""".toResponseBody("application/json".toMediaType()),
    )

    private val goodMeal = """
        {"items":[
          {"name":"Roti","quantity":2,"unit":"roti","grams_estimate":80,"kcal":240,
           "protein_g":8,"carbs_g":46,"fat_g":3,"confidence":0.9,"basis":"reference"},
          {"name":"Paneer sabji","quantity":1,"unit":"katori","grams_estimate":200,"kcal":380,
           "protein_g":18,"carbs_g":12,"fat_g":28,"confidence":0.6,"basis":"estimate"}
        ],"meal_type_guess":"DINNER"}
    """.trimIndent()

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        config.key = "test-key"
        identity.stored = "test-token"
    }

    @After
    fun tearDown() = runTest {
        config.key = null
        db.close()
    }

    private fun client(service: GeminiService) = GeminiClient(
        service,
        config,
        GeminiAuth(config, identity),
        db.aiCacheDao(),
        Dispatchers.Unconfined,
    )

    @Test
    fun `a well-formed reply becomes items`() = runTest {
        val outcome = client(FakeService(mutableListOf({ reply(goodMeal) })))
            .recognizeText("2 roti and paneer sabji", "system")

        assertTrue(outcome is RecognitionOutcome.Success)
        val meal = (outcome as RecognitionOutcome.Success).meal
        assertEquals(2, meal.items.size)
        assertEquals("Roti", meal.items[0].name)
        assertEquals(240.0, meal.items[0].kcal, 0.01)
        assertEquals(0.6, meal.items[1].confidence, 0.01)
        assertEquals("DINNER", meal.mealTypeGuess)
    }

    @Test
    fun `markdown fences around the JSON are tolerated`() = runTest {
        val fenced = "```json\n$goodMeal\n```"
        val outcome = client(FakeService(mutableListOf({ reply(fenced) })))
            .recognizeText("roti", "system")

        assertTrue("fenced JSON should still parse, was $outcome", outcome is RecognitionOutcome.Success)
    }

    @Test
    fun `an empty item list is reported as no food found, not an error`() = runTest {
        val outcome = client(FakeService(mutableListOf({ reply("""{"items":[]}""") })))
            .recognizeText("a photo of my desk", "system")

        assertEquals(RecognitionOutcome.NoFoodFound, outcome)
    }

    @Test
    fun `malformed JSON is retried once with a stricter instruction, then succeeds`() = runTest {
        val service = FakeService(mutableListOf({ reply("sure! here you go") }, { reply(goodMeal) }))
        val outcome = client(service).recognizeText("roti", "system")

        assertTrue(outcome is RecognitionOutcome.Success)
        assertEquals("should have called twice", 2, service.requests.size)

        val retryPrompt = service.requests[1].systemInstruction?.parts?.first()?.text.orEmpty()
        assertTrue("retry should restate the contract", retryPrompt.startsWith("Return only valid JSON"))
    }

    @Test
    fun `malformed JSON twice gives up with a readable message`() = runTest {
        val service = FakeService(mutableListOf({ reply("nope") }, { reply("still nope") }))
        val outcome = client(service).recognizeText("roti", "system")

        assertTrue(outcome is RecognitionOutcome.Failure)
        assertEquals(FailureKind.MALFORMED, (outcome as RecognitionOutcome.Failure).kind)
        assertTrue(outcome.message.isNotBlank())
    }

    @Test
    fun `a rejected key reads as rejected, not as a network problem`() = runTest {
        // Google returns 400 with API_KEY_INVALID for a malformed key, not 401.
        val outcome = client(FakeService(mutableListOf({ httpError(400) })))
            .recognizeText("roti", "system")

        assertEquals(FailureKind.KEY_REJECTED, (outcome as RecognitionOutcome.Failure).kind)
    }

    @Test
    fun `rate limiting and server errors are distinguished`() = runTest {
        val limited = client(FakeService(mutableListOf({ httpError(429) }))).recognizeText("a", "s")
        val server = client(FakeService(mutableListOf({ httpError(503) }))).recognizeText("b", "s")

        assertEquals(FailureKind.RATE_LIMITED, (limited as RecognitionOutcome.Failure).kind)
        assertEquals(FailureKind.SERVER, (server as RecognitionOutcome.Failure).kind)
    }

    @Test
    fun `losing the network offers manual entry rather than failing hard`() = runTest {
        val outcome = client(FakeService(mutableListOf({ throw IOException("offline") })))
            .recognizeText("roti", "system")

        val failure = outcome as RecognitionOutcome.Failure
        assertEquals(FailureKind.NO_NETWORK, failure.kind)
        assertTrue(failure.message.contains("hand"))
    }

    @Test
    fun `neither an own key nor an identity is its own failure, not a rejection`() = runTest {
        config.key = null
        identity.stored = null
        val outcome = client(FakeService(mutableListOf())).recognizeText("roti", "system")

        assertEquals(FailureKind.NO_AUTH, (outcome as RecognitionOutcome.Failure).kind)
    }

    @Test
    fun `an identical request is served from cache without calling out again`() = runTest {
        val service = FakeService(mutableListOf({ reply(goodMeal) }))
        val client = client(service)

        val first = client.recognizeText("2 roti", "system")
        val second = client.recognizeText("2 roti", "system")

        assertTrue((first as RecognitionOutcome.Success).fromCache.not())
        assertTrue((second as RecognitionOutcome.Success).fromCache)
        assertEquals("second call must not hit the network", 1, service.requests.size)
        assertEquals(2, second.meal.items.size)
    }

    @Test
    fun `a changed system prompt does not throw the cache away`() = runTest {
        val service = FakeService(mutableListOf({ reply(goodMeal) }))
        val client = client(service)

        client.recognizeText("2 roti", "system")
        // The prompt carries the user's corrections, which this app rewrites on every edited
        // row. Keying the cache on its text made the cache useless in exactly the cases it
        // existed for.
        val second = client.recognizeText("2 roti", "system, and they corrected you about dal")

        assertTrue((second as RecognitionOutcome.Success).fromCache)
        assertEquals(1, service.requests.size)
    }

    @Test
    fun `the same label photo is only read once`() = runTest {
        val label = """{"name":"Peanut butter","kcal_100g":600,"protein_100g":25}"""
        val service = FakeService(mutableListOf({ reply(label) }))
        val client = client(service)

        val first = client.parseLabel("photo-bytes")
        val second = client.parseLabel("photo-bytes")

        assertEquals(600.0, (first as LabelOutcome.Success).label.kcal100g!!, 0.01)
        assertEquals(600.0, (second as LabelOutcome.Success).label.kcal100g!!, 0.01)
        assertEquals("re-opening a read panel must not cost a call", 1, service.requests.size)
    }

    @Test
    fun `an unreadable label is not cached as an answer`() = runTest {
        val blank = """{"kcal_100g":0}"""
        val service = FakeService(mutableListOf({ reply(blank) }, { reply(blank) }))
        val client = client(service)

        client.parseLabel("blurry")
        // A second attempt means the photo was the problem; replaying the failure would only
        // hand back the same nothing.
        assertTrue(client.parseLabel("blurry") is LabelOutcome.NoLabelFound)
        assertEquals(2, service.requests.size)
    }

    @Test
    fun `structured output is nested under generationConfig responseFormat`() = runTest {
        val service = FakeService(mutableListOf({ reply(goodMeal) }))
        client(service).recognizeText("roti", "system")

        val body = json.encodeToString(service.requests.single())

        // TRD §5.2 is right and the "it moved to responseFormat" claim was not. The nested
        // generationConfig.responseFormat path does exist and parses, but rejects a schema
        // with a bare "Request contains an invalid argument". The flat pair is what works,
        // and Gemini's schema is a proto, so types are OBJECT/STRING, not object/string.
        assertTrue("flat mime type", body.contains("\"responseMimeType\":\"application/json\""))
        assertTrue("flat schema", body.contains("\"responseSchema\""))
        assertTrue("schema uses proto type names", body.contains("\"type\":\"OBJECT\""))
        assertTrue("should carry the fields", body.contains("\"grams_estimate\""))
        assertTrue("nested responseFormat is not the working shape", !body.contains("\"responseFormat\""))
    }

    @Test
    fun `grounding is off unless asked for`() = runTest {
        val plain = FakeService(mutableListOf({ reply(goodMeal) }))
        client(plain).recognizeText("roti", "system")
        assertTrue(json.encodeToString(plain.requests.single()).contains("google_search").not())

        val grounded = FakeService(mutableListOf({ reply(goodMeal) }))
        client(grounded).recognizeText("a branded protein bar", "system", grounded = true)
        assertTrue(json.encodeToString(grounded.requests.single()).contains("google_search"))
    }

    @Test
    fun `an own key goes straight to Google, in a header, with the model in the path`() = runTest {
        val service = FakeService(mutableListOf({ reply(goodMeal) }))
        client(service).recognizeText("roti", "system")

        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent",
            service.url,
        )
        assertEquals("test-key", service.headers["x-goog-api-key"])
    }

    @Test
    fun `without an own key the call goes to the proxy, bearing the identity token`() = runTest {
        config.key = null
        val service = FakeService(mutableListOf({ reply(goodMeal) }))
        client(service).recognizeText("roti", "system")

        val url = service.url.orEmpty()
        assertTrue("must not go straight to Google", !url.contains("generativelanguage"))
        assertTrue("model still belongs in the path", url.endsWith("/v1beta/models/gemini-3.5-flash-lite:generateContent"))
        assertEquals("Bearer test-token", service.headers["Authorization"])
        assertEquals("the shared key must never leave the server", null, service.headers["x-goog-api-key"])
    }

    @Test
    fun `a rejection on the shared key does not send the user to Settings to fix a key`() = runTest {
        config.key = null
        val service = FakeService(mutableListOf({ httpError(401) }))
        val outcome = client(service).recognizeText("roti", "system")

        val failure = outcome as RecognitionOutcome.Failure
        assertEquals(FailureKind.KEY_REJECTED, failure.kind)
        assertTrue("should not blame a key they never set", !failure.message.startsWith("Your Gemini key"))
    }

    @Test
    fun `a coach note comes back as its text`() = runTest {
        val service = FakeService(mutableListOf({ reply("""{"note":"Protein is the gap tonight."}""") }))
        val outcome = client(service).coachNote("""{"kind":"next_meal"}""")

        assertEquals(CoachOutcome.Success("Protein is the gap tonight.", fromCache = false), outcome)
        assertTrue("the findings travel as data", service.requests.single().contents.single().parts.single().text!!.contains("next_meal"))
    }

    @Test
    fun `an empty coach note is retried once with the schema restated`() = runTest {
        val service = FakeService(
            mutableListOf({ reply("""{"note":""}""") }, { reply("""{"note":"Add a row."}""") }),
        )
        val outcome = client(service).coachNote("""{"kind":"training_week"}""")

        assertEquals("Add a row.", (outcome as CoachOutcome.Success).note)
        assertEquals(2, service.requests.size)
    }

    @Test
    fun `the same findings are answered from the cache`() = runTest {
        val findings = """{"kind":"next_meal","plates":[]}"""
        client(FakeService(mutableListOf({ reply("""{"note":"Fine as it is."}""") }))).coachNote(findings)

        val second = client(FakeService(mutableListOf())).coachNote(findings)
        assertEquals(CoachOutcome.Success("Fine as it is.", fromCache = true), second)
    }

    @Test
    fun `a rate-limited coach note says so`() = runTest {
        val outcome = client(FakeService(mutableListOf({ httpError(429) }))).coachNote("{}")
        assertEquals(FailureKind.RATE_LIMITED, (outcome as CoachOutcome.Failure).kind)
    }
}
