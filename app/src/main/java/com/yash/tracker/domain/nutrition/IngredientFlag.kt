package com.yash.tracker.domain.nutrition

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * How much a flag wants your attention.
 *
 * Three levels rather than a severity number, because the honest range is small: something you
 * would rather not buy, something the pack is genuinely clean of, and something merely worth
 * knowing. Anything finer would be a judgement the app has no standing to make.
 */
enum class FlagTone { CONCERN, FINE, NOTE;

    companion object {
        /** Model output is untrusted: an unknown tone reads as the neutral one, not a crash. */
        fun parse(raw: String?): FlagTone =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: NOTE
    }
}

/** One thing worth knowing about what is in a packet. */
@Serializable
data class IngredientFlag(
    val label: String,
    val detail: String? = null,
    val tone: FlagTone = FlagTone.NOTE,
)

/**
 * Flags are stored as JSON on the product row rather than a table of their own.
 *
 * They are read only with the product that owns them, never queried across, and rewritten as a
 * set whenever a label is re-read — which is a document, not a relation. The exercise catalogue
 * stores its muscle lists the same way for the same reason.
 */
object IngredientFlags {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(flags: List<IngredientFlag>): String? =
        flags.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) }

    /** A column written by an older build, or by hand, must not take the product down with it. */
    fun decode(stored: String?): List<IngredientFlag> {
        if (stored.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<IngredientFlag>>(stored) }
            .getOrDefault(emptyList())
    }
}
