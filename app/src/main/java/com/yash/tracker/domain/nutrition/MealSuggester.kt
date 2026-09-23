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
    /**
     * From the bundled catalogue rather than anything the user has eaten. Only ever offered
     * when their own food cannot close the gap, and marked so they know it is a stranger.
     */
    val isNew: Boolean = false,
)

/** A suggested plate: what to eat, what it adds up to, and what is still left afterwards. */
data class Plate(
    val items: List<Serving>,
    val total: Macros,
    val leftover: MacroGap,
    /** Lower is a better fit. Comparable across days because it is scaled by the gap. */
    val score: Double,
    /** How far the macros alone miss, on the same scale — what decides a plate is a poor fit. */
    val fit: Double = score,
    val micros: Micros = Micros.UNKNOWN,
    /** The reasons this plate is on the list, shown so it can be argued with. */
    val tags: Set<PlateTag> = emptySet(),
)

enum class PlateTag { CLOSES_PROTEIN, ADDS_FIBRE, USUAL_FOR_MEAL, NEW_FOOD, HAD_TODAY }

/** How often one food has been eaten at the meal being suggested for, out of all meals. */
data class SlotCount(val atThisMeal: Int, val total: Int)

/**
 * What the rest of the day says about a plate, beyond the four macros of its gap.
 *
 * Every part is optional and neutral when missing, so a suggestion with no context scores
 * exactly as it did before any of this existed.
 */
data class PlateContext(
    /** Fibre this meal should bring, as its share of what the day still needs. */
    val fibreNeedG: Double? = null,
    /** Sodium this meal may spend, as its share of what the day has left. */
    val sodiumRoomMg: Double? = null,
    val affinity: Map<ServingSource, SlotCount> = emptyMap(),
    val eatenToday: Set<ServingSource> = emptySet(),
    val eatenYesterday: Set<ServingSource> = emptySet(),
)

/**
 * Turns a food into the portions worth offering.
 *
 * A small grid of sizes rather than a solved optimum: the search space is one number, the
 * answer has to land on a portion a person would actually serve, and a handful of evaluations of a
 * closed-form cost beat any solver that returns 137 g of paneer.
 */
object Servings {

    private val MULTIPLIERS = listOf(0.5, 0.75, 1.0, 1.5, 2.0)

    /** A counted food is served in whole numbers of it, and three or four roti is a real meal. */
    private val COUNTED_EXTRA = listOf(3.0, 4.0)

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
        isNew: Boolean = false,
    ): List<Serving> {
        if (defaultPortionG <= 0 || per100g.kcal100g <= 0) return emptyList()
        val measure = portionLabel?.removePrefix("1")?.trim()?.takeIf { it.isNotBlank() }

        val multipliers = if (measure != null) MULTIPLIERS + COUNTED_EXTRA else MULTIPLIERS
        return multipliers.mapNotNull { multiplier ->
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
                isNew = isNew,
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
        val micros: DayMicroStatus? = null,
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
 * The whole algorithm is a cost function and a small beam search. Nothing here is a model: the
 * arithmetic of closing a macro gap is deterministic, and a number the user can check beats a
 * sentence they have to trust.
 */
object MealSuggester {

    /**
     * The best few plates for [gap], built out of [pool].
     *
     * Every plate of up to three items is reachable, but only the best [BEAM_WIDTH] of each
     * size are carried forward — a greedy walk commits to its first pairing and never finds
     * the plate where two middling items beat one good one and a bad side. The final pick
     * takes the best plates whose main item differs, so the list is a choice rather than one
     * dish at five sizes.
     */
    fun suggest(
        gap: MacroGap,
        pool: List<Serving>,
        context: PlateContext = PlateContext(),
        limit: Int = DEFAULT_LIMIT,
    ): List<Plate> {
        if (gap.isSpent || pool.isEmpty()) return emptyList()

        val cap = gap.kcal * KCAL_TOLERANCE
        val affordable = pool.filter { it.macros.kcal > 0 && it.macros.kcal <= cap }
        if (affordable.isEmpty()) return emptyList()

        var beam = affordable
            .map { plateOf(gap, context, listOf(it)) }
            .sortedBy { it.score }
            .take(BEAM_WIDTH)
        val candidates = beam.toMutableList()

        repeat(MAX_ITEMS - 1) {
            val grown = beam
                .filter { it.items.first().combinable && it.leftover.kcal >= MIN_TOP_UP_KCAL }
                .flatMap { plate ->
                    val used = plate.items.map { it.source }.toSet()
                    affordable
                        .filter { it.combinable && it.source !in used }
                        .filter { plate.total.kcal + it.macros.kcal <= cap }
                        .map { plateOf(gap, context, plate.items + it) }
                }
                .distinctBy { plate -> plate.items.map { it.source to it.portionLabel }.toSet() }
                .sortedBy { it.score }
                .take(BEAM_WIDTH)
            if (grown.isEmpty()) return@repeat
            candidates += grown
            beam = grown
        }

        // Best first, one plate per main item, never the same set of foods twice. A padded
        // plate that fits worse than its parent loses to it here, which is what keeps a good
        // single answer single.
        val leads = mutableSetOf<ServingSource>()
        val seen = mutableSetOf<Set<ServingSource>>()
        val picked = mutableListOf<Plate>()
        for (plate in candidates.sortedBy { it.score }) {
            if (picked.size == limit) break
            val lead = plate.items.first().source
            val sources = plate.items.map { it.source }.toSet()
            if (lead in leads || sources in seen) continue
            leads += lead
            seen += sources
            picked += plate
        }
        return picked
    }

    /**
     * The foods in [pool] that best answer [gap] on their own, one per source, best first.
     *
     * How the catalogue fallback picks its few dozen candidates out of hundreds: by the same
     * cost the plates are judged on, rather than by a column the database could sort.
     */
    fun bestSources(
        gap: MacroGap,
        pool: List<Serving>,
        context: PlateContext = PlateContext(),
        limit: Int,
    ): List<ServingSource> {
        val cap = gap.kcal * KCAL_TOLERANCE
        return pool
            .filter { it.macros.kcal > 0 && it.macros.kcal <= cap }
            .map { it.source to plateOf(gap, context, listOf(it)).score }
            .sortedBy { it.second }
            .map { it.first }
            .distinct()
            .take(limit)
    }

    private fun plateOf(gap: MacroGap, context: PlateContext, items: List<Serving>): Plate {
        val total = items.fold(Macros.ZERO) { sum, it -> sum + it.macros }
        val micros = items.fold(Micros.UNKNOWN) { sum, it -> sum + it.micros }
        val fit = macroMiss(gap, total)
        val score = (fit + MISS_FLOOR) *
            microFactor(items, micros, context) *
            familiarityFactor(items) *
            affinityFactor(items, context) *
            varietyFactor(items, context) *
            noveltyFactor(items)

        return Plate(
            items = items,
            total = total,
            leftover = gap - total,
            score = score,
            fit = fit,
            micros = micros,
            tags = tagsFor(gap, total, items, micros, context),
        )
    }

    /**
     * How badly a plate's macros miss the gap, in calorie-equivalents.
     *
     * Every miss is converted to the calories it stands for (4/4/9) so a gram of protein and a
     * gram of fat are comparable in one number, then squared, so one large miss costs more
     * than the same shortfall spread across three macros. Protein is weighted double because
     * it is the target that actually binds — carbs and fat are where a day has slack.
     *
     * Dividing by the gap makes the number mean the same thing on a 400 kcal evening as on a
     * 1,200 kcal one, which is what lets the UI say "close" rather than just rank.
     */
    private fun macroMiss(gap: MacroGap, eaten: Macros): Double {
        val protein = penalty((gap.proteinG - eaten.proteinG) * KCAL_PER_G_PROTEIN)
        val carbs = penalty((gap.carbsG - eaten.carbsG) * KCAL_PER_G_CARB)
        val fat = penalty((gap.fatG - eaten.fatG) * KCAL_PER_G_FAT)

        val scale = max(gap.kcal, MIN_SCALE_KCAL)
        return (PROTEIN_WEIGHT * protein + carbs + fat) / (scale * scale)
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
     * What the rest of the panel says, as at most a quarter on top of the macro miss.
     *
     * Multiplied rather than added, so it can separate plates that fit similarly but never
     * rescue one that does not: a high-fibre plate that leaves forty grams of protein on the
     * table is still the wrong answer. A micro only counts when every item on the plate
     * states it — a partial figure would read as a low one and punish the plate for a gap in
     * the catalogue.
     */
    private fun microFactor(items: List<Serving>, micros: Micros, context: PlateContext): Double {
        var charge = 0.0

        val need = context.fibreNeedG
        if (need != null && need >= MIN_FIBRE_NEED_G && items.all { it.micros.fibreG != null }) {
            val short = ((need - (micros.fibreG ?: 0.0)) / need).coerceIn(0.0, 1.0)
            charge += FIBRE_WEIGHT * short * short
        }

        val room = context.sodiumRoomMg
        if (room != null && items.all { it.micros.sodiumMg != null }) {
            val over = ((micros.sodiumMg ?: 0.0) - room) / max(room, MIN_SODIUM_SCALE_MG)
            charge += SODIUM_WEIGHT * over.coerceIn(0.0, 1.0)
        }

        // The same sugar-to-fibre test the meal review uses: a banana and a barfi both carry
        // sugar, and only one of them carries anything with it.
        val sugar = micros.sugarG
        if (sugar != null && sugar > MIN_SUGAR_G && items.all { it.micros.sugarG != null && it.micros.fibreG != null }) {
            val ratio = sugar / max(micros.fibreG ?: 0.0, MIN_FIBRE_DIVISOR_G)
            charge += SUGAR_WEIGHT * ((ratio - SUGAR_FIBRE_CLEAN) / (SUGAR_FIBRE_BAD - SUGAR_FIBRE_CLEAN)).coerceIn(0.0, 1.0)
        }

        return 1.0 + charge
    }

    /**
     * A discount for food the user already eats.
     *
     * Applied to the whole plate rather than to each item, so it can only separate options
     * that already fit similarly well. It is not allowed to make a worse-fitting plate win by
     * much: at most fifteen percent, which is roughly the width of a tie.
     */
    private fun familiarityFactor(items: List<Serving>): Double {
        val average = items.sumOf { min(it.familiarity, FAMILIARITY_CAP).toDouble() } / items.size
        return 1.0 - FAMILIARITY_DISCOUNT * (average / FAMILIARITY_CAP)
    }

    /**
     * Whether this is what the user eats at this meal.
     *
     * Laplace-smoothed over the four meals, so a food eaten once is barely moved and one
     * eaten forty times at breakfast is firmly a breakfast. Even-handed is 0.25; the factor
     * runs from a fifth off for a food always eaten here to a fifth on for one never eaten
     * here. A food with no history stays neutral, and nothing is ever excluded — only ranked.
     */
    private fun affinityFactor(items: List<Serving>, context: PlateContext): Double {
        if (context.affinity.isEmpty()) return 1.0
        return items.sumOf { serving ->
            val count = context.affinity[serving.source] ?: return@sumOf 1.0
            val p = (count.atThisMeal + 1.0) / (count.total + SLOTS)
            if (p >= EVEN_SLOT) {
                1.0 - AFFINITY_SWING * (p - EVEN_SLOT) / (1.0 - EVEN_SLOT)
            } else {
                1.0 + AFFINITY_SWING * (EVEN_SLOT - p) / EVEN_SLOT
            }
        } / items.size
    }

    /** A little against eating the same thing again today, less against yesterday's. */
    private fun varietyFactor(items: List<Serving>, context: PlateContext): Double =
        items.sumOf { serving ->
            when (serving.source) {
                in context.eatenToday -> REPEAT_TODAY
                in context.eatenYesterday -> REPEAT_YESTERDAY
                else -> 1.0
            }
        } / items.size

    /** A stranger from the catalogue has to fit clearly better to beat something familiar. */
    private fun noveltyFactor(items: List<Serving>): Double =
        items.sumOf { if (it.isNew) NOVELTY_PENALTY else 1.0 } / items.size

    private fun tagsFor(
        gap: MacroGap,
        total: Macros,
        items: List<Serving>,
        micros: Micros,
        context: PlateContext,
    ): Set<PlateTag> = buildSet {
        if (gap.proteinG >= TAG_MIN_PROTEIN_G && total.proteinG >= gap.proteinG * TAG_PROTEIN_SHARE) {
            add(PlateTag.CLOSES_PROTEIN)
        }
        val need = context.fibreNeedG
        val fibre = micros.fibreG
        if (need != null && fibre != null && need >= MIN_FIBRE_NEED_G && fibre >= need * TAG_FIBRE_SHARE) {
            add(PlateTag.ADDS_FIBRE)
        }
        val usual = items.all { serving ->
            val count = context.affinity[serving.source] ?: return@all false
            count.total > 0 && count.atThisMeal * 2 >= count.total
        }
        if (usual) add(PlateTag.USUAL_FOR_MEAL)
        if (items.any { it.isNew }) add(PlateTag.NEW_FOOD)
        if (items.any { it.source in context.eatenToday }) add(PlateTag.HAD_TODAY)
    }

    const val DEFAULT_LIMIT = 4

    /** Plates kept at each size. Wide enough that the right pairing is never pruned early. */
    private const val BEAM_WIDTH = 24

    /** A plate may run five percent over the gap; demanding an exact fit returns nothing. */
    private const val KCAL_TOLERANCE = 1.05
    private const val MAX_ITEMS = 3

    /** Below this there is nothing left to top up with. */
    private const val MIN_TOP_UP_KCAL = 80.0

    /** Keeps the multipliers meaningful on a plate that happens to fit exactly. */
    private const val MISS_FLOOR = 0.002

    private const val OVERSHOOT_PENALTY = 2.0
    private const val PROTEIN_WEIGHT = 2.0
    private const val FAMILIARITY_DISCOUNT = 0.15
    private const val FAMILIARITY_CAP = 20

    /** Keeps the scale from collapsing when barely any allowance is left. */
    private const val MIN_SCALE_KCAL = 200.0

    private const val FIBRE_WEIGHT = 0.10
    private const val SODIUM_WEIGHT = 0.10
    private const val SUGAR_WEIGHT = 0.05
    private const val MIN_FIBRE_NEED_G = 2.0
    private const val MIN_SODIUM_SCALE_MG = 200.0
    private const val MIN_SUGAR_G = 5.0
    private const val MIN_FIBRE_DIVISOR_G = 0.5
    private const val SUGAR_FIBRE_CLEAN = 5.0
    private const val SUGAR_FIBRE_BAD = 20.0

    private const val SLOTS = 4.0
    private const val EVEN_SLOT = 0.25
    private const val AFFINITY_SWING = 0.2

    private const val REPEAT_TODAY = 1.25
    private const val REPEAT_YESTERDAY = 1.1
    private const val NOVELTY_PENALTY = 1.2

    private const val TAG_MIN_PROTEIN_G = 15.0
    private const val TAG_PROTEIN_SHARE = 0.7
    private const val TAG_FIBRE_SHARE = 0.5

    private const val KCAL_PER_G_PROTEIN = 4.0
    private const val KCAL_PER_G_CARB = 4.0
    private const val KCAL_PER_G_FAT = 9.0
}
