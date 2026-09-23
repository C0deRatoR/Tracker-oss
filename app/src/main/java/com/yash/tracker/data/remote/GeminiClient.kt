package com.yash.tracker.data.remote

import android.util.Log
import com.yash.tracker.BuildConfig
import com.yash.tracker.data.local.dao.AiCacheDao
import com.yash.tracker.data.local.entity.AiCacheEntity

import com.yash.tracker.data.remote.dto.Content
import com.yash.tracker.data.remote.dto.GenerateContentRequest
import com.yash.tracker.data.remote.dto.GenerationConfig
import com.yash.tracker.data.remote.dto.InlineData
import com.yash.tracker.data.remote.dto.Part
import com.yash.tracker.data.remote.dto.RecognizedMealDto
import com.yash.tracker.data.remote.dto.ResponseFormat
import com.yash.tracker.data.remote.dto.ResponseFormatText
import com.yash.tracker.data.remote.dto.Tool
import com.yash.tracker.data.remote.prompts.MealRecognition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class FailureKind { NO_AUTH, NO_NETWORK, KEY_REJECTED, RATE_LIMITED, SERVER, MALFORMED, BLOCKED }

sealed interface RecognitionOutcome {
    /**
     * [fromCache] is true whenever the answer arrived without a model call — a replayed reply
     * from [AiCacheEntity], or a meal the food tables could answer on their own.
     */
    data class Success(val meal: RecognizedMealDto, val fromCache: Boolean) : RecognitionOutcome

    /** The call worked but the model found no food — a different thing from an error. */
    data object NoFoodFound : RecognitionOutcome

    data class Failure(val kind: FailureKind, val message: String) : RecognitionOutcome
}

/** What a nutrition label photo came back as. */
sealed interface LabelOutcome {
    data class Success(val label: ParsedLabelDto) : LabelOutcome
    data object NoLabelFound : LabelOutcome
    data class Failure(val kind: FailureKind, val message: String) : LabelOutcome
}

/** One request's reply, before anyone decides what shape of JSON they expected. */
private sealed interface JsonReply {
    data class Ok(val json: String) : JsonReply
    data class Failed(val kind: FailureKind, val message: String) : JsonReply
}

/**
 * Wraps generateContent with the policy from TRD §5.5 and §5.6: cache by prompt hash, one
 * retry with a stricter instruction on a malformed reply, and a plain-language message for
 * every failure. Never throws at the caller, and never saves anything as a side effect.
 */
@Singleton
class GeminiClient @Inject constructor(
    private val service: GeminiService,
    private val config: GeminiConfig,
    private val auth: GeminiAuth,
    private val cache: AiCacheDao,
    private val io: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun recognizeText(
        text: String,
        systemPrompt: String,
        grounded: Boolean = false,
    ): RecognitionOutcome = recognize(
        parts = listOf(Part(text = MealRecognition.TEXT_PREFIX + text)),
        systemPrompt = systemPrompt,
        cacheKeySource = "text:$text",
        grounded = grounded,
    )

    suspend fun recognizePhoto(
        jpegBase64: String,
        hint: String?,
        systemPrompt: String,
        grounded: Boolean = false,
    ): RecognitionOutcome {
        val parts = buildList {
            add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = jpegBase64)))
            if (!hint.isNullOrBlank()) add(Part(text = MealRecognition.photoHint(hint)))
        }
        return recognize(
            parts = parts,
            systemPrompt = systemPrompt,
            cacheKeySource = "photo:${sha256(jpegBase64)}:${hint.orEmpty()}",
            grounded = grounded,
        )
    }

    /**
     * Keeps the reading for [text] past the thirty days, because it was saved to the diary.
     *
     * Whether that reading was grounded is not known out here — it was one or the other — so
     * both keys are pinned and the one that was never written updates nothing.
     */
    suspend fun keepTextReading(text: String) = withContext(io) {
        val model = config.model()
        for (grounded in listOf(true, false)) {
            cache.pin(model, sha256("${MealRecognition.PROMPT_VERSION}|text:$text|$grounded"))
        }
    }

    private suspend fun recognize(
        parts: List<Part>,
        systemPrompt: String,
        cacheKeySource: String,
        grounded: Boolean,
    ): RecognitionOutcome = withContext(io) {
        val model = config.model()
        val route = auth.route(model)
            ?: return@withContext RecognitionOutcome.Failure(
                FailureKind.NO_AUTH,
                "Couldn't reach the photo service. Check your connection.",
            )
        // Keyed on the prompt's version rather than its text: the text carries the user's
        // profile and their recent corrections, and hashing that emptied the cache every time
        // a row was edited — which is exactly when the app writes a correction.
        val hash = sha256("${MealRecognition.PROMPT_VERSION}|$cacheKeySource|$grounded")

        cache.find(model, hash, System.currentTimeMillis() - CACHE_TTL_MS)?.let { hit ->
            parse(hit.responseJson)?.let {
                return@withContext RecognitionOutcome.Success(it, fromCache = true)
            }
        }

        val first = call(route, parts, systemPrompt, grounded, stricter = false)
        // One retry, with the schema restated — a malformed reply is usually recoverable.
        val outcome = if (first is RecognitionOutcome.Failure && first.kind == FailureKind.MALFORMED) {
            call(route, parts, systemPrompt, grounded, stricter = true)
        } else {
            first
        }

        if (outcome is RecognitionOutcome.Success) {
            cache.put(
                AiCacheEntity(
                    model = model,
                    promptHash = hash,
                    responseJson = json.encodeToString(outcome.meal),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
        outcome
    }

    private suspend fun call(
        route: GeminiRoute,
        parts: List<Part>,
        systemPrompt: String,
        grounded: Boolean,
        stricter: Boolean,
    ): RecognitionOutcome {
        val instruction = if (stricter) MealRecognition.STRICTER_RETRY + systemPrompt else systemPrompt

        val reply = requestJson(route, parts, instruction, MealRecognition.SCHEMA, grounded)
        if (reply is JsonReply.Failed) return RecognitionOutcome.Failure(reply.kind, reply.message)

        val meal = parse((reply as JsonReply.Ok).json)
            ?: return RecognitionOutcome.Failure(
                FailureKind.MALFORMED,
                "Gemini sent something that wasn't valid JSON.",
            )

        return if (meal.items.isEmpty()) {
            RecognitionOutcome.NoFoodFound
        } else {
            RecognitionOutcome.Success(meal, fromCache = false)
        }
    }

    /**
     * Reads a packaged food's nutrition panel.
     *
     * Only readings are cached, never the failures. A second attempt at a label almost always
     * means the first photo was unreadable, and replaying that would hand back the same
     * unreadable answer — but the same bytes that did parse describe the same packet, so
     * re-opening the scanner on a photo already read costs nothing.
     */
    suspend fun parseLabel(jpegBase64: String): LabelOutcome = withContext(io) {
        val model = config.model()
        val route = auth.route(model) ?: return@withContext LabelOutcome.Failure(
            FailureKind.NO_AUTH,
            "Couldn't reach the label service. Check your connection.",
        )

        val hash = sha256("${LabelParsing.PROMPT_VERSION}|label:${sha256(jpegBase64)}")

        cache.find(model, hash, System.currentTimeMillis() - CACHE_TTL_MS)?.let { hit ->
            runCatching { json.decodeFromString<ParsedLabelDto>(hit.responseJson) }
                .getOrNull()
                ?.let { return@withContext LabelOutcome.Success(it) }
        }

        val reply = requestJson(
            route = route,
            parts = listOf(Part(inlineData = InlineData(mimeType = "image/jpeg", data = jpegBase64))),
            instruction = LabelParsing.SYSTEM_PROMPT,
            schema = LabelParsing.SCHEMA,
            grounded = false,
        )
        if (reply is JsonReply.Failed) {
            return@withContext LabelOutcome.Failure(reply.kind, reply.message)
        }

        val parsed = runCatching {
            json.decodeFromString<ParsedLabelDto>((reply as JsonReply.Ok).json)
        }.getOrNull() ?: return@withContext LabelOutcome.Failure(
            FailureKind.MALFORMED,
            "Gemini sent something that wasn't valid JSON.",
        )

        // A panel without calories is not a panel. Models asked to transcribe a blank frame
        // will happily return zeroes and the literal string "null" rather than admit it.
        if (parsed.hasNoReading()) return@withContext LabelOutcome.NoLabelFound

        val label = parsed.cleaned()
        cache.put(
            AiCacheEntity(
                model = model,
                promptHash = hash,
                responseJson = json.encodeToString(label),
                createdAt = System.currentTimeMillis(),
            ),
        )
        LabelOutcome.Success(label)
    }

    /** The shared half: send the request, survive the failure matrix, hand back the text. */
    private suspend fun requestJson(
        route: GeminiRoute,
        parts: List<Part>,
        instruction: String,
        schema: kotlinx.serialization.json.JsonElement,
        grounded: Boolean,
    ): JsonReply {
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = parts, role = "user")),
            systemInstruction = Content(parts = listOf(Part(text = instruction))),
            generationConfig = GenerationConfig(
                temperature = 0.2,
                responseMimeType = "application/json",
                responseSchema = schema,
            ),
            // Grounding is opt-in per call: it costs more and is only worth it for branded
            // or genuinely uncertain items (TRD §5.5).
            tools = if (grounded) listOf(Tool()) else null,
        )

        val response = try {
            service.generateContent(route.url, route.headers, request)
        } catch (e: IOException) {
            return JsonReply.Failed(
                FailureKind.NO_NETWORK,
                "No connection. You can still log this by hand.",
            )
        } catch (e: Exception) {
            return JsonReply.Failed(FailureKind.MALFORMED, "Gemini sent something unexpected.")
        }

        if (!response.isSuccessful) {
            val failure = httpFailure(response.code(), response.errorBody()?.string().orEmpty())
            return JsonReply.Failed(failure.kind, failure.message)
        }

        val body = response.body()
        body?.promptFeedback?.blockReason?.let {
            return JsonReply.Failed(
                FailureKind.BLOCKED,
                "Gemini declined to answer for that image.",
            )
        }

        val text = body?.candidates
            ?.firstOrNull()
            ?.content
            ?.parts
            ?.firstNotNullOfOrNull { it.text }
            ?: return JsonReply.Failed(FailureKind.MALFORMED, "Gemini sent an empty reply.")

        return JsonReply.Ok(text)
    }

    private fun httpFailure(code: Int, body: String): RecognitionOutcome.Failure = when {
        // Always our problem rather than the user's: an expired sign-in, or a proxy whose key
        // has gone bad. There is nothing for them to fix, so the advice is simply to retry.
        code == 401 || code == 403 || (code == 400 && body.contains("API_KEY_INVALID")) ->
            RecognitionOutcome.Failure(
                FailureKind.KEY_REJECTED,
                "Couldn't authenticate with the photo service. Try again in a moment.",
            )

        code == 400 -> {
            if (BuildConfig.DEBUG) Log.d("GeminiClient", "rejected request: $body")
            RecognitionOutcome.Failure(
                FailureKind.MALFORMED,
                "Gemini couldn't read that request. ${googleMessage(body)}",
            )
        }

        code == 429 -> RecognitionOutcome.Failure(
            FailureKind.RATE_LIMITED,
            "Rate limited. Try again in a minute, or log this by hand.",
        )

        code >= 500 -> RecognitionOutcome.Failure(
            FailureKind.SERVER,
            "Google's side had a problem. Try again shortly.",
        )

        else -> RecognitionOutcome.Failure(
            FailureKind.SERVER,
            "Couldn't reach Gemini (HTTP $code).",
        )
    }

    /** Google's own explanation, which is far more use than a status code. */
    private fun googleMessage(body: String): String = runCatching {
        json.parseToJsonElement(body)
            .jsonObject["error"]
            ?.jsonObject
            ?.get("message")
            ?.jsonPrimitive
            ?.content
            .orEmpty()
    }.getOrDefault("")

    /** Models sometimes wrap JSON in markdown fences despite being told not to. */
    private fun parse(raw: String): RecognizedMealDto? = runCatching {
        val cleaned = raw.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        json.decodeFromString<RecognizedMealDto>(cleaned)
    }.getOrNull()

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        val CACHE_TTL_MS = TimeUnit.DAYS.toMillis(30)
    }
}
