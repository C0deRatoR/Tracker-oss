package com.yash.tracker.domain.nutrition

import com.yash.tracker.domain.diary.MealType
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Where a suggested portion came from, so the UI can log it back the way it is stored. */
sealed interface ServingSource {
    data class Food(val foodId: Long) : ServingSource
    data class Product(val productId: Long) : ServingSource
    data class SavedMeal(val mealId: Long) : ServingSource
}

/**
 * One concrete thing the user could eat, with its portion already settled.
 *
 * Nothing downstream scales a serving: every size worth offering is built up front, so what
 * gets scored is exactly what can be logged. A catalogue food contributes several servings at
 * different sizes, a saved meal contributes one.
 */
data class Serving(
    val source: ServingSource,
    val name: String,
    /** How the portion reads on screen: "2 roti", "150 g". */
    val portionLabel: String,
    val quantity: Double,
    val unit: String,
    val grams: Double?,
    val macros: Macros,
    /** Carried so a suggestion logs the same figures any other route to that food would. */
    val micros: Micros = Micros.UNKNOWN,
    /** How often this has been logged before. Breaks ties towards what the user actually eats. */
    val familiarity: Int = 0,
    /**
     * Whether this can share a plate. A saved meal is already a meal — pairing one with a
     * side of rice is not a suggestion anybody asked for, and the two would have to be logged
     * as separate entries anyway.
     */
    val combinable: Boolean = true,
)

/** A suggested plate: what to eat, what it adds up to, and what is still left afterwards. */
data class Plate(
    val items: List<Serving>,
    val total: Macros,
    val leftover: MacroGap,
    /** Lower is a better fit. Comparable across days because it is scaled by the gap. */
    val score: Double,
)

/**
 * Turns a food into the portions worth offering.
 *
 * A grid of five sizes rather than a solved optimum: the search space is one number, the
 * answer has to land on a portion a person would actually serve, and five evaluations of a
 * closed-form cost beat any solver that returns 137 g of paneer.
 */
object Servings {

    private val MULTIPLIERS = listOf(0.5, 0.75, 1.0, 1.5, 2.0)

    /** Below this a portion is a garnish, and offering it is noise. */
    private const val MIN_GRAMS = 5.0
    private const val GRAM_STEP = 5.0

    fun ofFood(
        source: ServingSource,
        name: String,
        per100g: NutritionPer100g,
        defaultPortionG: Double,
        portionLabel: String?,
        familiarity: Int = 0,
    ): List<Serving> {
        if (defaultPortionG <= 0 || per100g.kcal100g <= 0) return emptyList()
        val measure = portionLabel?.removePrefix("1")?.trim()?.takeIf { it.isNotBlank() }

        return MULTIPLIERS.mapNotNull { multiplier ->
            val grams = roundToStep(defaultPortionG * multiplier)
            if (grams < MIN_GRAMS) return@mapNotNull null

            // A count only reads properly on a whole number of them: "0.75 roti" is not a
            // portion anybody serves, so fractional sizes fall back to grams.
            val whole = multiplier == multiplier.roundToInt().toDouble()
            val counted = measure.takeIf { whole }

            Serving(
                source = source,
                name = name,
                portionLabel = if (counted != null) {
                    "${multiplier.roundToInt()} $counted"
                } else {
                    "${grams.roundToInt()} g"
                },
                quantity = if (counted != null) multiplier else grams,
                unit = counted?.uppercase() ?: "G",
                grams = grams,
                macros = PortionResolver.macrosFor(per100g, grams),
                micros = PortionResolver.microsFor(per100g, grams),
                familiarity = familiarity,
            )
        }.distinctBy { it.portionLabel }
    }

    private fun roundToStep(grams: Double): Double = (grams / GRAM_STEP).roundToInt() * GRAM_STEP
}

/**
 * What there is to say about the next meal, including the honest ways of having nothing to say.
 *
 * The empty cases are separate types rather than an empty list because they mean different
 * things to the reader: a day that is finished, a diary with nothing to learn from yet, and a
 * gap that none of their food fits all want different words on screen.
 */
sealed interface MacroSuggestion {

    data class Plates(
        val meal: MealType,
        val gap: MacroGap,
        val plates: List<Plate>,
    ) : MacroSuggestion

    /** The target is met, or close enough that another meal would only overshoot it. */
    data object DayDone : MacroSuggestion

    /** Nothing has been logged yet, so there is no pool to suggest from. */
    data object NoHistoryYet : MacroSuggestion

    /** Room left, but nothing in the pool fits inside it. */
    data class NothingFits(val meal: MealType, val gap: MacroGap) : MacroSuggestion
}

/**
 * Picks what to eat next from what is left of the day.
 *
 * The whole algorithm is a cost function and a greedy walk. Nothing here is a model: the
 * arithmetic of closing a macro gap is deterministic, and a number the user can check beats a
 * sentence they have to trust.
 */
object MealSuggester {

    /**
     * The best few plates for [gap], built out of [pool].
     *
     * Leads are picked first — the best single serving from each distinct food — and each is
     * then completed greedily, so the results differ in their main item rather than being the
     * same dish at five sizes.
     */
    fun suggest(gap: MacroGap, pool: List<Serving>, limit: Int = 3): List<Plate> {
        if (gap.isSpent || pool.isEmpty()) return emptyList()

        val affordable = pool.filter { it.macros.kcal > 0 && it.macros.kcal <= gap.kcal * KCAL_TOLERANCE }
        if (affordable.isEmpty()) return emptyList()

        return affordable
            .sortedBy { score(gap, it.macros, listOf(it)) }
            .distinctBy { it.source }
            .take(limit)
            .map { lead -> complete(gap, affordable, lead) }
            .distinctBy { plate -> plate.items.map { it.source }.toSet() }
            .sortedBy { it.score }
    }

    /**
     * Adds items while they make the plate fit better, up to three.
     *
     * One food almost never closes a gap on its own — the answer to "220 g of protein and 40 g
     * of fat left" is paneer *and* roti, not an absurd serving of either. The loop stops the
     * moment another item stops helping, which is what keeps a good single answer single.
     */
    private fun complete(gap: MacroGap, pool: List<Serving>, lead: Serving): Plate {
        val items = mutableListOf(lead)
        var total = lead.macros
        var best = score(gap, total, items)

        while (lead.combinable && items.size < MAX_ITEMS) {
            val leftover = gap - total
            if (leftover.kcal < MIN_TOP_UP_KCAL) break

            val used = items.map { it.source }.toSet()
            val next = pool
                .filter { it.combinable && it.source !in used }
                .filter { total.kcal + it.macros.kcal <= gap.kcal * KCAL_TOLERANCE }
                .minByOrNull { score(gap, total + it.macros, items + it) }
                ?: break

            val candidate = score(gap, total + next.macros, items + next)
            if (candidate >= best) break

            items += next
            total += next.macros
            best = candidate
        }

        return Plate(items = items.toList(), total = total, leftover = gap - total, score = best)
    }

    /**
     * How badly a plate misses the gap, in calorie-equivalents.
     *
     * Every miss is converted to the calories it stands for (4/4/9) so a gram of protein and a
     * gram of fat are comparable in one number, then squared, so one large miss costs more
     * than the same shortfall spread across three macros. Protein is weighted double because
     * it is the target that actually binds — carbs and fat are where a day has slack.
     *
     * Dividing by the gap makes the number mean the same thing on a 400 kcal evening as on a
     * 1,200 kcal one, which is what lets the UI say "close" rather than just rank.
     */
    private fun score(gap: MacroGap, eaten: Macros, items: List<Serving>): Double {
        val protein = penalty((gap.proteinG - eaten.proteinG) * KCAL_PER_G_PROTEIN)
        val carbs = penalty((gap.carbsG - eaten.carbsG) * KCAL_PER_G_CARB)
        val fat = penalty((gap.fatG - eaten.fatG) * KCAL_PER_G_FAT)

        val scale = max(gap.kcal, MIN_SCALE_KCAL)
        val miss = (PROTEIN_WEIGHT * protein + carbs + fat) / (scale * scale)
        return miss * familiarityFactor(items)
    }

    /**
     * Falling short is forgivable; going over is not.
     *
     * Twenty grams of fat under at bedtime is a rounding error. Twenty over is the thing that
     * quietly cancels a deficit, so it is charged at double before squaring — four times the
     * cost of the same miss in the other direction.
     */
    private fun penalty(missKcal: Double): Double {
        val charged = if (missKcal >= 0) missKcal else OVERSHOOT_PENALTY * missKcal
        return charged * charged
    }

    /**
     * A discount for food the user already eats.
     *
     * Applied to the whole plate rather than to each item, so it can only separate options
     * that already fit similarly well. It is not allowed to make a worse-fitting plate win by
     * much: at most fifteen percent, which is roughly the width of a tie.
     */
    private fun familiarityFactor(items: List<Serving>): Double {
        if (items.isEmpty()) return 1.0
        val average = items.sumOf { min(it.familiarity, FAMILIARITY_CAP).toDouble() } / items.size
        return 1.0 - FAMILIARITY_DISCOUNT * (average / FAMILIARITY_CAP)
    }

    /** A plate may run five percent over the gap; demanding an exact fit returns nothing. */
    private const val KCAL_TOLERANCE = 1.05
    private const val MAX_ITEMS = 3

    /** Below this there is nothing left to top up with. */
    private const val MIN_TOP_UP_KCAL = 80.0

    private const val OVERSHOOT_PENALTY = 2.0
    private const val PROTEIN_WEIGHT = 2.0
    private const val FAMILIARITY_DISCOUNT = 0.15
    private const val FAMILIARITY_CAP = 20

    /** Keeps the scale from collapsing when barely any allowance is left. */
    private const val MIN_SCALE_KCAL = 200.0

    private const val KCAL_PER_G_PROTEIN = 4.0
    private const val KCAL_PER_G_CARB = 4.0
    private const val KCAL_PER_G_FAT = 9.0
}
