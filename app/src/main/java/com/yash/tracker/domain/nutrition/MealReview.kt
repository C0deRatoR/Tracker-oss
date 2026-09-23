package com.yash.tracker.domain.nutrition

import kotlin.math.roundToInt

/**
 * How a meal reads once it is more than four numbers.
 *
 * Not a model, for the same reason [MealSuggester] is not one: a sentence you have to trust is
 * worse than a number you can check. Every line below is arithmetic over figures already on the
 * plate, and every threshold is written down where it can be argued with.
 */
sealed interface MealReview {

    data class Rated(
        /** 0–100. The weighted average of whichever parts could be judged. */
        val score: Int,
        val verdict: Verdict,
        val notes: List<ReviewNote>,
        /** What share of the meal's calories the figures actually speak for. */
        val coverage: Double,
    ) : MealReview {
        /** Worth saying out loud when the judgement rests on part of the plate. */
        val isPartial: Boolean get() = coverage < FULL_COVERAGE
    }

    /**
     * Too little of the plate carries a panel to say anything.
     *
     * A separate case rather than a low score: "we do not know" and "this is poor" are
     * different answers, and showing the second when we mean the first is how an app starts
     * lying to its owner.
     */
    data class Unrated(val coverage: Double) : MealReview

    /**
     * Whether there is anything worth drawing.
     *
     * A plate where no item states anything — every entry logged before these columns existed
     * — has nothing to say and nothing the user could do about it. Saying "not enough to judge
     * this" there is a card that appears for ever and can never be acted on; saying it on a
     * partly measured plate is a prompt to swap a row, which is why that case still shows.
     */
    val isWorthShowing: Boolean
        get() = this is Rated || (this is Unrated && coverage > 0)

    companion object {
        const val FULL_COVERAGE = 0.995
    }
}

enum class Verdict { GOOD, FAIR, POOR }

/** Whether a note is something to act on, something fine, or just worth knowing. */
enum class ReviewTone { CONCERN, FINE, NOTE }

/**
 * One nutrient as the card shows it: how much of it was on the plate, and whether that is a
 * good amount.
 *
 * The amount is absolute — grams, milligrams — because that is the question anyone actually
 * asks of a meal. The judgement behind [verdict] is not absolute at all: it comes from the
 * densities and ratios below, which is what makes it mean the same thing on a 200 kcal snack
 * as on a 900 kcal dinner. Showing the working turned the card into arithmetic homework.
 */
data class ReviewNote(
    val label: String,
    /** "37 g", "410 mg". */
    val amount: String,
    /** One word: Good, Fair or Poor. Never a quantity — see [MealReviewer.noteFor]. */
    val verdict: String,
    val tone: ReviewTone,
)

/**
 * Scores a meal on what its composition says about it.
 *
 * Four parts, each scored from 0 to 1 and then averaged by weight. Sugar counts double: it is
 * the thing that most often makes a plausible-looking plate a bad one, and it is the question
 * this was built to answer.
 *
 * The card shows plain amounts, but nothing here is *judged* on one. Every part is scored as a
 * density or a ratio, so a meal is not marked down for being bigger, and a verdict means the
 * same thing on a snack as on a dinner — judging against a daily allowance would make breakfast
 * look virtuous and dinner look reckless for no reason but the clock.
 */
object MealReviewer {

    fun review(macros: Macros, micros: Micros, microKcal: Double): MealReview {
        val coverage = if (macros.kcal > 0) (microKcal / macros.kcal).coerceIn(0.0, 1.0) else 0.0
        if (macros.kcal <= 0 || coverage < MIN_COVERAGE) return MealReview.Unrated(coverage)

        val parts = listOfNotNull(
            sugar(macros, micros),
            fibre(macros, micros),
            protein(macros),
            sodium(macros, micros),
        )
        // Protein always scores, so this only trips on a meal with no calories at all.
        if (parts.isEmpty()) return MealReview.Unrated(coverage)

        val weighted = parts.sumOf { it.points * it.weight } / parts.sumOf { it.weight }
        val score = (weighted * 100).roundToInt()

        return MealReview.Rated(
            score = score,
            verdict = when {
                score >= GOOD_FROM -> Verdict.GOOD
                score >= FAIR_FROM -> Verdict.FAIR
                else -> Verdict.POOR
            },
            // Worst first: the reason a meal scored badly is the line worth reading.
            notes = parts.sortedBy { it.points }.map { it.note },
            coverage = coverage,
        )
    }

    private class Part(val points: Double, val weight: Double, val note: ReviewNote)

    /**
     * One nutrient's line: the amount, and a verdict on it.
     *
     * The verdict is always Good, Fair or Poor — never Low, Some or High. Those describe a
     * quantity, and printed beside one they are read as describing *that* quantity: "38 g —
     * Low" says thirty-eight grams is a small amount of sugar, which is false. What the score
     * actually says is that this meal's sugar is in good company, and Good says so without
     * claiming anything about the number next to it.
     */
    private fun noteFor(label: String, amount: String, points: Double) = ReviewNote(
        label = label,
        amount = amount,
        verdict = when {
            points >= FINE_FROM -> "Good"
            points >= NOTE_FROM -> "Fair"
            else -> "Poor"
        },
        tone = toneFor(points),
    )

    /**
     * Rounded to whole grams once there are enough of them to round.
     *
     * Below ten it keeps a decimal, because "0 g of fibre" next to 37 g of sugar reads as a
     * measurement of nothing when the row actually held a trace of it.
     */
    private fun grams(value: Double): String = when {
        value <= 0 -> "0 g"
        value >= 10 -> "${value.roundToInt()} g"
        else -> "%.1f g".format(value)
    }

    /**
     * Sugar, judged against the fibre it arrives with rather than on its own.
     *
     * A bare sugar figure cannot tell a banana from a barfi — both are mostly sugar by the
     * numbers, and only one of them is a problem. What separates them is what the sugar came
     * wrapped in, so this scores the sugar-to-fibre ratio: whole food carries both, and a
     * sweet carries one. Ten to one is the usual line for a packet; this treats five as clean
     * and twenty as indefensible.
     *
     * With no fibre figure to compare against, it falls back to sugar's share of the carbs,
     * which is the same question asked more crudely.
     */
    private fun sugar(macros: Macros, micros: Micros): Part? {
        val sugar = micros.sugarG ?: return null

        if (sugar <= 0) {
            return Part(1.0, SUGAR_WEIGHT, noteFor("Sugar", grams(0.0), 1.0))
        }

        val fibre = micros.fibreG
        val points = if (fibre != null) {
            // Fibre of zero is the honest extreme, not a division error: all sugar, nothing
            // with it. Scored as the worst case rather than skipped.
            val ratio = if (fibre > 0) sugar / fibre else Double.MAX_VALUE
            band(ratio, best = SUGAR_FIBRE_CLEAN, worst = SUGAR_FIBRE_BAD)
        } else {
            val share = if (macros.carbsG > 0) sugar / macros.carbsG else 1.0
            band(share, best = SUGAR_SHARE_CLEAN, worst = SUGAR_SHARE_BAD)
        }

        return Part(points, SUGAR_WEIGHT, noteFor("Sugar", grams(sugar), points))
    }

    /** Fibre per 1,000 kcal. Fourteen grams is the dietary-guideline density. */
    private fun fibre(macros: Macros, micros: Micros): Part? {
        val fibre = micros.fibreG ?: return null
        val density = fibre / (macros.kcal / KCAL_UNIT)
        val points = band(density, best = FIBRE_GOOD, worst = FIBRE_POOR, higherIsBetter = true)

        return Part(points, 1.0, noteFor("Fibre", grams(fibre), points))
    }

    /** What share of the energy is protein. Always scorable, since macros are never missing. */
    private fun protein(macros: Macros): Part {
        val share = (macros.proteinG * KCAL_PER_G_PROTEIN) / macros.kcal
        val points = band(share, best = PROTEIN_GOOD, worst = PROTEIN_POOR, higherIsBetter = true)

        return Part(points, 1.0, noteFor("Protein", grams(macros.proteinG), points))
    }

    /** Sodium per 1,000 kcal, against a 2,000 mg day spread over a 2,000 kcal one. */
    private fun sodium(macros: Macros, micros: Micros): Part? {
        val sodium = micros.sodiumMg ?: return null
        val density = sodium / (macros.kcal / KCAL_UNIT)
        val points = band(density, best = SODIUM_GOOD, worst = SODIUM_POOR)

        return Part(points, 1.0, noteFor("Salt", "${sodium.roundToInt()} mg", points))
    }

    /**
     * A value placed on a line between two thresholds, clamped at both ends.
     *
     * Linear rather than banded, so a meal one gram the wrong side of a number does not drop a
     * grade. The thresholds are the arguable part and they are all named constants below.
     */
    private fun band(
        value: Double,
        best: Double,
        worst: Double,
        higherIsBetter: Boolean = false,
    ): Double = if (higherIsBetter) {
        ((value - worst) / (best - worst)).coerceIn(0.0, 1.0)
    } else {
        ((worst - value) / (worst - best)).coerceIn(0.0, 1.0)
    }

    private fun toneFor(points: Double): ReviewTone = when {
        points >= FINE_FROM -> ReviewTone.FINE
        points >= NOTE_FROM -> ReviewTone.NOTE
        else -> ReviewTone.CONCERN
    }

    /** Below this, the figures describe too little of the plate to judge it by. */
    private const val MIN_COVERAGE = 0.6

    /** Sugar answers the question this was built for, so it counts for two of the five. */
    private const val SUGAR_WEIGHT = 2.0

    private const val SUGAR_FIBRE_CLEAN = 5.0
    private const val SUGAR_FIBRE_BAD = 20.0
    private const val SUGAR_SHARE_CLEAN = 0.2
    private const val SUGAR_SHARE_BAD = 0.6

    private const val FIBRE_GOOD = 14.0
    private const val FIBRE_POOR = 3.0

    private const val PROTEIN_GOOD = 0.25
    private const val PROTEIN_POOR = 0.08

    private const val SODIUM_GOOD = 600.0
    private const val SODIUM_POOR = 2300.0

    private const val GOOD_FROM = 70
    private const val FAIR_FROM = 45

    private const val FINE_FROM = 0.7
    private const val NOTE_FROM = 0.4

    private const val KCAL_UNIT = 1000.0
    private const val KCAL_PER_G_PROTEIN = 4.0
}
