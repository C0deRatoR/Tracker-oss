package com.yash.tracker.domain.model

enum class Sex { MALE, FEMALE }

enum class Goal { LOSE, MAINTAIN, GAIN }

/** Factors per PRD §5.2: TDEE multiplier and the water allowance in fluid ounces per lb. */
enum class ActivityLevel(val tdeeFactor: Double, val waterOzPerLb: Double) {
    SEDENTARY(1.2, 0.55),
    LIGHTLY(1.375, 0.6),
    MODERATELY(1.55, 0.65),
    VERY(1.725, 0.75),
    ATHLETE(1.9, 0.85),
}

enum class Pace(val loseDeficitKcal: Int, val gainSurplusKcal: Int) {
    GENTLE(250, 250),
    STEADY(500, 325),
    AGGRESSIVE(750, 400),
}

enum class EatingStyle { NONE, HIGH_PROTEIN, LOW_CARB, KETO, VEGETARIAN, VEGAN }

data class PlanInput(
    val sex: Sex,
    val age: Int,
    val heightCm: Double,
    val weightKg: Double,
    val goal: Goal,
    val goalWeightKg: Double?,
    val activity: ActivityLevel,
    val pace: Pace,
    val eatingStyle: EatingStyle,
)

data class Plan(
    val bmr: Int,
    val tdee: Int,
    val activityFactor: Double,
    val kcal: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val waterMl: Int,
    val weeksToGoal: Int?,
    val rateLbPerWeek: Double?,
    val deficitCapped: Boolean,
    val floorApplied: Boolean,
)
