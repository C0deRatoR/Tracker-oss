package com.yash.tracker.domain.nutrition

import com.yash.tracker.domain.model.ActivityLevel
import com.yash.tracker.domain.model.EatingStyle
import com.yash.tracker.domain.model.Goal
import com.yash.tracker.domain.model.Plan
import com.yash.tracker.domain.model.PlanInput
import com.yash.tracker.domain.model.Sex
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Implements the plan formulas in PRD §5.2 exactly: BMR via Mifflin-St Jeor, TDEE via the
 * standard activity multipliers, ~3,500 kcal per pound for the timeline, and protein/fat
 * targets from common sports-nutrition guidelines. No alternative formulas live anywhere else.
 */
object PlanCalculator {

    private const val LB_PER_KG = 2.20462262
    private const val ML_PER_FL_OZ = 29.5735
    private const val KCAL_PER_LB_FAT = 3500.0

    private const val MALE_KCAL_FLOOR = 1500
    private const val FEMALE_KCAL_FLOOR = 1200

    /** Caps the deficit so weekly loss stays within ~1% of bodyweight. */
    private const val MAX_DEFICIT_KCAL_PER_LB = 5.0

    private const val PROTEIN_G_PER_LB_DEFAULT = 0.8
    private const val PROTEIN_G_PER_LB_HIGH = 1.0
    private const val FAT_G_PER_LB = 0.35
    private const val FAT_FLOOR_G_PER_LB = 0.2
    private const val CARB_FLOOR_G = 60.0
    private const val KETO_CARB_G = 30.0
    private const val LOW_CARB_MAX_G = 100.0

    private const val KCAL_PER_G_PROTEIN = 4.0
    private const val KCAL_PER_G_CARB = 4.0
    private const val KCAL_PER_G_FAT = 9.0

    fun calculate(input: PlanInput): Plan {
        val lb = input.weightKg * LB_PER_KG

        val bmr = basalMetabolicRate(input)
        val tdee = (bmr * input.activity.tdeeFactor).roundToInt()

        var deficitCapped = false
        var floorApplied = false

        val rawTarget: Int = when (input.goal) {
            Goal.MAINTAIN -> tdee

            Goal.LOSE -> {
                val requested = input.pace.loseDeficitKcal
                val allowed = (MAX_DEFICIT_KCAL_PER_LB * lb).roundToInt()
                val deficit = min(requested, allowed)
                deficitCapped = deficit < requested

                val floor = if (input.sex == Sex.MALE) MALE_KCAL_FLOOR else FEMALE_KCAL_FLOOR
                val afterDeficit = tdee - deficit
                floorApplied = afterDeficit < floor
                max(afterDeficit, floor)
            }

            Goal.GAIN -> tdee + input.pace.gainSurplusKcal
        }

        val kcal = roundToNearestTen(rawTarget)
        val macros = macros(kcal, lb, input)
        val timeline = timeline(input, lb, tdee, kcal)

        return Plan(
            bmr = bmr,
            tdee = tdee,
            activityFactor = input.activity.tdeeFactor,
            kcal = kcal,
            proteinG = macros.protein,
            carbsG = macros.carbs,
            fatG = macros.fat,
            waterMl = (input.activity.waterOzPerLb * lb * ML_PER_FL_OZ).roundToInt(),
            weeksToGoal = timeline?.weeks,
            rateLbPerWeek = timeline?.rateLbPerWeek,
            deficitCapped = deficitCapped,
            floorApplied = floorApplied,
        )
    }

    private fun basalMetabolicRate(input: PlanInput): Int {
        val sexOffset = if (input.sex == Sex.MALE) 5.0 else -161.0
        val raw = 10 * input.weightKg + 6.25 * input.heightCm - 5 * input.age + sexOffset
        return raw.roundToInt()
    }

    private data class Macros(val protein: Double, val carbs: Double, val fat: Double)

    private fun macros(kcal: Int, lb: Double, input: PlanInput): Macros {
        val proteinPerLb = if (
            input.goal == Goal.LOSE ||
            input.activity == ActivityLevel.ATHLETE ||
            input.eatingStyle == EatingStyle.HIGH_PROTEIN
        ) PROTEIN_G_PER_LB_HIGH else PROTEIN_G_PER_LB_DEFAULT

        // Protein is fixed: every floor below is paid for out of carbs or fat, never protein.
        val protein = proteinPerLb * lb
        var fat = FAT_G_PER_LB * lb
        var carbs = carbsFromRemainder(kcal, protein, fat)

        when (input.eatingStyle) {
            EatingStyle.KETO -> {
                carbs = KETO_CARB_G
                fat = fatFromRemainder(kcal, protein, carbs)
            }

            EatingStyle.LOW_CARB -> if (carbs > LOW_CARB_MAX_G) {
                carbs = LOW_CARB_MAX_G
                fat = fatFromRemainder(kcal, protein, carbs)
            }

            else -> if (carbs < CARB_FLOOR_G) {
                carbs = CARB_FLOOR_G
                fat = fatFromRemainder(kcal, protein, carbs)
            }
        }

        // Keto deliberately pins carbs at 30 g, so the fat floor cannot claw back from them.
        if (input.eatingStyle != EatingStyle.KETO) {
            val fatFloor = FAT_FLOOR_G_PER_LB * lb
            if (fat < fatFloor) {
                fat = fatFloor
                carbs = carbsFromRemainder(kcal, protein, fat)
            }
        }

        return Macros(
            protein = round1(protein),
            carbs = round1(max(carbs, 0.0)),
            fat = round1(max(fat, 0.0)),
        )
    }

    private fun carbsFromRemainder(kcal: Int, protein: Double, fat: Double): Double =
        (kcal - protein * KCAL_PER_G_PROTEIN - fat * KCAL_PER_G_FAT) / KCAL_PER_G_CARB

    private fun fatFromRemainder(kcal: Int, protein: Double, carbs: Double): Double =
        (kcal - protein * KCAL_PER_G_PROTEIN - carbs * KCAL_PER_G_CARB) / KCAL_PER_G_FAT

    private data class Timeline(val weeks: Int, val rateLbPerWeek: Double)

    private fun timeline(input: PlanInput, lb: Double, tdee: Int, kcal: Int): Timeline? {
        if (input.goal == Goal.MAINTAIN) return null
        val goalKg = input.goalWeightKg ?: return null

        val weeklyGapKcal = abs(tdee - kcal) * 7.0
        val rate = weeklyGapKcal / KCAL_PER_LB_FAT
        if (rate <= 0.0) return null

        val goalLb = goalKg * LB_PER_KG
        val weeks = (abs(lb - goalLb) / rate).roundToInt()
        return Timeline(weeks = weeks, rateLbPerWeek = round2(rate))
    }

    private fun roundToNearestTen(value: Int): Int = ((value / 10.0).roundToInt()) * 10

    private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0

    private fun round2(value: Double): Double = (value * 100).roundToInt() / 100.0
}
