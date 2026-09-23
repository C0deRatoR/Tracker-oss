package com.yash.tracker.domain.nutrition

import com.yash.tracker.domain.diary.MealType

/**
 * What is still owed against the day's targets.
 *
 * Deliberately not [Macros]: macros are always a quantity of food and cannot sensibly be
 * negative, whereas a gap is a debt and goes negative the moment a target is passed. Keeping
 * them as separate types is what stops a leftover being logged as if it were a portion.
 */
data class MacroGap(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
) {
    operator fun minus(eaten: Macros): MacroGap = MacroGap(
        kcal = kcal - eaten.kcal,
        proteinG = proteinG - eaten.proteinG,
        carbsG = carbsG - eaten.carbsG,
        fatG = fatG - eaten.fatG,
    )

    fun scaledBy(fraction: Double): MacroGap = MacroGap(
        kcal = kcal * fraction,
        proteinG = proteinG * fraction,
        carbsG = carbsG * fraction,
        fatG = fatG * fraction,
    )

    /**
     * Too little left to be worth suggesting anything for. A hundred calories is a glass of
     * milk, and offering a meal plan for it reads as nagging rather than help.
     */
    val isSpent: Boolean get() = kcal < MIN_MEAL_KCAL

    companion object {
        const val MIN_MEAL_KCAL = 100.0

        /**
         * The gap between a target and what has been eaten. Macro debts are floored at zero
         * independently of calories: having overshot fat by lunchtime does not mean the day
         * now owes negative fat, it means fat is finished and the rest should come from
         * somewhere else.
         */
        fun between(target: Macros, eaten: Macros): MacroGap = MacroGap(
            kcal = target.kcal - eaten.kcal,
            proteinG = (target.proteinG - eaten.proteinG).coerceAtLeast(0.0),
            carbsG = (target.carbsG - eaten.carbsG).coerceAtLeast(0.0),
            fatG = (target.fatG - eaten.fatG).coerceAtLeast(0.0),
        )
    }
}

/**
 * How the day's remaining allowance is shared across the meals still to come.
 *
 * Suggesting food against the whole remaining gap is the obvious way to get this wrong: at
 * four in the afternoon with 900 kcal left, dinner is not a 900 kcal plate, because there is
 * still a snack after it. Each slot carries the share of a day that meal usually is,
 * renormalised over whatever is actually left.
 */
object MealBudget {

    /** The day in order. Snack sits between lunch and dinner, where it is actually eaten. */
    private val ORDER = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.SNACK, MealType.DINNER)

    /** The hour each slot opens, matching the windows MealWindows defaults a new entry to. */
    private val OPENS = mapOf(
        MealType.BREAKFAST to 4,
        MealType.LUNCH to 11,
        MealType.SNACK to 16,
        MealType.DINNER to 18,
    )

    private val SHARE = mapOf(
        MealType.BREAKFAST to 0.25,
        MealType.LUNCH to 0.35,
        MealType.SNACK to 0.10,
        MealType.DINNER to 0.30,
    )

    /**
     * The meals still ahead at [hour] that have not already been logged.
     *
     * A slot is gone once the next one has opened — nobody is eating breakfast at two in the
     * afternoon. Hours before the first slot opens belong to the tail of the night before, so
     * only a snack is left. If everything has been logged the answer is still a snack, because
     * you can always eat again, and because the alternative is dividing by zero.
     */
    fun slotsLeft(hour: Int, alreadyLogged: Set<MealType> = emptySet()): List<MealType> {
        if (hour < OPENS.getValue(MealType.BREAKFAST)) return listOf(MealType.SNACK)

        val open = ORDER.filterIndexed { index, _ ->
            val closesAt = ORDER.getOrNull(index + 1)?.let { OPENS.getValue(it) } ?: 24
            hour < closesAt
        }
        return open.filterNot { it in alreadyLogged }.ifEmpty { listOf(MealType.SNACK) }
    }

    /** The slot a suggestion is being made for, and the share of [gap] it should take. */
    fun shareFor(gap: MacroGap, hour: Int, alreadyLogged: Set<MealType> = emptySet()): MealShare {
        val slots = slotsLeft(hour, alreadyLogged)
        val next = slots.first()
        val total = slots.sumOf { SHARE.getValue(it) }
        return MealShare(meal = next, gap = gap.scaledBy(SHARE.getValue(next) / total))
    }
}

/** Which meal a suggestion is for, and the part of the day's remainder it may spend. */
data class MealShare(val meal: MealType, val gap: MacroGap)
