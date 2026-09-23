package com.yash.tracker.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement

/**
 * Request and response shapes for generativelanguage.googleapis.com generateContent.
 *
 * TRD §5.2 writes structured output as flat responseMimeType/responseSchema. That moved:
 * both now nest under generationConfig.responseFormat.text. The schema contents are
 * unchanged — only the envelope.
 */

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    @SerialName("systemInstruction") val systemInstruction: Content? = null,
    @SerialName("generationConfig") val generationConfig: GenerationConfig? = null,
    val tools: List<Tool>? = null,
)

@Serializable
data class Content(
    val parts: List<Part>,
    val role: String? = null,
)

@Serializable
data class Part(
    val text: String? = null,
    @SerialName("inlineData") val inlineData: InlineData? = null,
)

@Serializable
data class InlineData(
    @SerialName("mimeType") val mimeType: String,
    val data: String,
)

@Serializable
data class GenerationConfig(
    val temperature: Double? = null,
    @SerialName("responseMimeType") val responseMimeType: String? = null,
    @SerialName("responseSchema") val responseSchema: JsonElement? = null,
    @SerialName("responseFormat") val responseFormat: ResponseFormat? = null,
)

@Serializable
data class ResponseFormat(val text: ResponseFormatText)

/**
 * [mimeType] is an enum (TextResponseFormat.MimeType), not a media-type string. Sending
 * "application/json" is rejected with INVALID_ARGUMENT; the value wanted is APPLICATION_JSON.
 */
@Serializable
data class ResponseFormatText(
    @SerialName("mimeType") val mimeType: String = MIME_TYPE_JSON,
    val schema: JsonElement,
) {
    companion object {
        const val MIME_TYPE_JSON = "APPLICATION_JSON"
    }
}

/** Google Search grounding; the shape is unchanged from TRD §5.4. */
@Serializable
data class Tool(@SerialName("google_search") val googleSearch: GoogleSearch = GoogleSearch())

@Serializable
class GoogleSearch

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate> = emptyList(),
    @SerialName("promptFeedback") val promptFeedback: PromptFeedback? = null,
)

@Serializable
data class Candidate(
    val content: Content? = null,
    @SerialName("finishReason") val finishReason: String? = null,
)

@Serializable
data class PromptFeedback(
    @SerialName("blockReason") val blockReason: String? = null,
)

/** The model's answer, as described by the meal-recognition schema. */
@Serializable
data class RecognizedMealDto(
    val items: List<RecognizedItemDto> = emptyList(),
    @SerialName("meal_type_guess") val mealTypeGuess: String? = null,
    val notes: String? = null,
)

@Serializable
data class RecognizedItemDto(
    val name: String,
    @SerialName("name_local") val nameLocal: String? = null,
    val quantity: Double = 1.0,
    val unit: String = "g",
    @SerialName("grams_estimate") val gramsEstimate: Double = 0.0,
    val kcal: Double = 0.0,
    @SerialName("protein_g") val proteinG: Double = 0.0,
    @SerialName("carbs_g") val carbsG: Double = 0.0,
    @SerialName("fat_g") val fatG: Double = 0.0,
    /**
     * The rest of the panel, which the model is asked for but not required to produce.
     * Null means it did not say, and null is what gets stored — never zero.
     */
    @SerialName("fibre_g") val fibreG: Double? = null,
    @SerialName("sugar_g") val sugarG: Double? = null,
    @SerialName("sodium_mg") val sodiumMg: Double? = null,
    val confidence: Double = 0.0,
    val basis: String = "estimate",
    @SerialName("source_note") val sourceNote: String? = null,
    /**
     * Set only when the row was answered from the user's own tables instead of being read by
     * the model, so the draft can point at the exact row that answered rather than looking the
     * name up a second time and possibly landing somewhere else. Never sent to Gemini and
     * never read back from it.
     */
    @Transient val foodId: Long? = null,
    @Transient val productId: Long? = null,
)
