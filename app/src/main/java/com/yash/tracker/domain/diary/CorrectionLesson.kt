package com.yash.tracker.domain.diary

import kotlin.math.abs

/** Something the user taught the app by editing a row before saving it. */
data class CorrectionLesson(
    val aiName: String,
    val correctedName: String,
    /** The user's own portion, or null when they left the estimate alone. */
    val grams: Double?,
)

/**
 * What can be learned from one edited row.
 *
 * Two different lessons come off the confirm screen and they used to be collapsed into one. A
 * rename says the dish was misidentified. A changed weight says the dish was right but this
 * user's portion is not the standard one — which, for someone eating the same roti every day,
 * is the more useful of the two and was previously thrown away.
 *
 * The weight is only ever remembered when the user actually changed it. Recording the model's
 * own estimate would feed it back its own guess on the next photo and call that learning.
 */
object CorrectionLessons {

    /** Portions are estimates on both sides, so a gram either way is the same number. */
    private const val GRAM_TOLERANCE = 1.0

    fun from(
        aiName: String,
        finalName: String,
        aiGrams: Double,
        finalGrams: Double,
    ): CorrectionLesson? {
        val original = aiName.trim()
        val corrected = finalName.trim()
        // A row the user added themselves has no model guess behind it, so there is nothing to
        // correct and nothing to learn.
        if (original.isBlank() || corrected.isBlank()) return null

        val renamed = !corrected.equals(original, ignoreCase = true)
        val reweighed = finalGrams > 0 && abs(finalGrams - aiGrams) >= GRAM_TOLERANCE

        return when {
            !renamed && !reweighed -> null
            else -> CorrectionLesson(original, corrected, finalGrams.takeIf { reweighed })
        }
    }
}
