package com.yash.tracker.domain.progress

import com.yash.tracker.domain.diary.MealType
import com.yash.tracker.domain.nutrition.DayMicroStatus
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs

/** One day's totals, micros included, as the analysis reads them. */
data class DayNutrition(
    val date: LocalDate,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fibreG: Double?,
    val sugarG: Double?,
    val sodiumMg: Double?,
)

/** The day's targets, as far as they are stated. */
data class NutritionTargets(val kcal: Double, val proteinG: Double, val carbsG: Double, val fatG: Double)

/** How often one target was met, of the full days in the window. */
data class HitRate(val label: String, val percent: Int, val rule: String)

/** A food's share of the window's calories. */
data class FoodShare(val name: String, val kcal: Double, val share: Double, val times: Int)

/** One food's total over the window, as the diary query returns it. */
data class FoodTotal(val name: String, val kcal: Double, val times: Int)

data class MicroAverage(val label: String, val average: Double, val unit: String, val target: Double?, val targetIsCap: Boolean, val days: Int)

data class SplitAverages(val first: Double, val second: Double, val firstDays: Int, val secondDays: Int)

data class NutritionReport(
    /** Full days (≥ 800 kcal) — every average and rate below is over these. */
    val fullDays: Int,
    val hitRates: List<HitRate>,
    val proteinPerKg: Double?,
    val micros: List<MicroAverage>,
    /** Weekday average against weekend average. */
    val weekdayVsWeekend: SplitAverages?,
    val mealShares: Map<MealType, Double>,
    val topFoods: List<FoodShare>,
    /** Training days against rest days, calories and protein. */
    val trainingVsRestKcal: SplitAverages?,
    val trainingVsRestProtein: SplitAverages?,
)

/**
 * Patterns in what was eaten, over the window on screen.
 *
 * Every figure is an average over full days only, for the same reason the energy estimate uses
 * them: a day with one meal logged is a gap in the diary, not a day of fasting, and counting it
 * would make every average read low.
 */
object NutritionAnalyst {

    fun analyse(
        days: List<DayNutrition>,
        targets: NutritionTargets?,
        bodyweightKg: Double?,
        mealKcal: Map<LocalDate, Map<MealType, Double>>,
        foods: List<FoodTotal>,
        trainingDays: Set<LocalDate>,
    ): NutritionReport {
        val full = days.filter { it.kcal >= BodyAnalyst.FULL_DAY_KCAL }

        return NutritionReport(
            fullDays = full.size,
            hitRates = targets?.let { hitRates(full, it) }.orEmpty(),
            proteinPerKg = bodyweightKg?.takeIf { it > 0 && full.isNotEmpty() }
                ?.let { kg -> full.sumOf { it.proteinG } / full.size / kg },
            micros = micros(full, targets),
            weekdayVsWeekend = split(full, { it.date.dayOfWeek !in WEEKEND }) { it.kcal },
            mealShares = mealShares(mealKcal.filterKeys { date -> full.any { it.date == date } }),
            topFoods = topFoods(foods, windowKcal = days.sumOf { it.kcal }),
            trainingVsRestKcal = split(full, { it.date in trainingDays }) { it.kcal },
            trainingVsRestProtein = split(full, { it.date in trainingDays }) { it.proteinG },
        )
    }

    /**
     * Calories within 10% either side. Protein at 90% or more, because it is a floor rather than
     * a line to land on. Carbs and fat within 15%, since the day has slack in both.
     */
    private fun hitRates(full: List<DayNutrition>, targets: NutritionTargets): List<HitRate> {
        if (full.isEmpty()) return emptyList()
        fun percent(test: (DayNutrition) -> Boolean) = full.count(test) * 100 / full.size
        return listOfNotNull(
            targets.kcal.takeIf { it > 0 }?.let { t ->
                HitRate("Calories", percent { abs(it.kcal - t) <= t * KCAL_BAND }, "within 10%")
            },
            targets.proteinG.takeIf { it > 0 }?.let { t ->
                HitRate("Protein", percent { it.proteinG >= t * PROTEIN_FLOOR }, "90% or more")
            },
            targets.carbsG.takeIf { it > 0 }?.let { t ->
                HitRate("Carbs", percent { abs(it.carbsG - t) <= t * MACRO_BAND }, "within 15%")
            },
            targets.fatG.takeIf { it > 0 }?.let { t ->
                HitRate("Fat", percent { abs(it.fatG - t) <= t * MACRO_BAND }, "within 15%")
            },
        )
    }

    private fun micros(full: List<DayNutrition>, targets: NutritionTargets?): List<MicroAverage> {
        fun average(label: String, unit: String, target: Double?, cap: Boolean, pick: (DayNutrition) -> Double?): MicroAverage? {
            val known = full.mapNotNull(pick)
            if (known.isEmpty()) return null
            return MicroAverage(label, known.average(), unit, target, cap, known.size)
        }
        val fibreTarget = targets?.kcal?.takeIf { it > 0 }?.let { DayMicroStatus.FIBRE_PER_1000_KCAL * it / 1000 }
        return listOfNotNull(
            average("Fibre", "g", fibreTarget, cap = false) { it.fibreG },
            average("Sugar", "g", null, cap = true) { it.sugarG },
            average("Salt (sodium)", "mg", DayMicroStatus.SODIUM_CAP_MG, cap = true) { it.sodiumMg },
        )
    }

    private fun <T> split(
        days: List<T>,
        isFirst: (T) -> Boolean,
        value: (T) -> Double,
    ): SplitAverages? {
        val (first, second) = days.partition(isFirst)
        if (first.size < MIN_SPLIT_DAYS || second.size < MIN_SPLIT_DAYS) return null
        return SplitAverages(first.map(value).average(), second.map(value).average(), first.size, second.size)
    }

    /** Each meal's average share of the day, over the days that were fully logged. */
    private fun mealShares(byDay: Map<LocalDate, Map<MealType, Double>>): Map<MealType, Double> {
        val days = byDay.values.filter { it.values.sum() > 0 }
        if (days.isEmpty()) return emptyMap()
        return MealType.entries.associateWith { meal ->
            days.sumOf { day -> (day[meal] ?: 0.0) / day.values.sum() } / days.size
        }
    }

    /**
     * Shares are of everything eaten in the window, not of the foods listed — the list is only
     * the top of it, and a share of the top would overstate every row.
     */
    private fun topFoods(foods: List<FoodTotal>, windowKcal: Double): List<FoodShare> {
        val total = maxOf(windowKcal, foods.sumOf { it.kcal })
        if (total <= 0) return emptyList()
        return foods.sortedByDescending { it.kcal }
            .take(TOP_FOODS)
            .map { FoodShare(it.name, it.kcal, it.kcal / total, it.times) }
    }

    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    private const val KCAL_BAND = 0.10
    private const val PROTEIN_FLOOR = 0.90
    private const val MACRO_BAND = 0.15

    /** Fewer days than this on either side, and the comparison is one odd day. */
    private const val MIN_SPLIT_DAYS = 3

    private const val TOP_FOODS = 6
}
