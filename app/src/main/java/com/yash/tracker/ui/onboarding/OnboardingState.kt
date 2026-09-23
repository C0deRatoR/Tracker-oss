package com.yash.tracker.ui.onboarding

import com.yash.tracker.domain.model.ActivityLevel
import com.yash.tracker.domain.model.EatingStyle
import com.yash.tracker.domain.model.Goal
import com.yash.tracker.domain.model.HeightUnit
import com.yash.tracker.domain.model.Pace
import com.yash.tracker.domain.model.PlanInput
import com.yash.tracker.domain.model.Sex
import com.yash.tracker.domain.model.Units
import com.yash.tracker.domain.model.WeightUnit

enum class OnboardingStep {
    WELCOME,
    NAME,
    AGE,
    SEX,
    HEIGHT,
    WEIGHT,
    GOAL,
    GOAL_WEIGHT,
    ACTIVITY,
    PACE,
    EATING_STYLE,
    NOTES,
    RESULTS,
    ;

    /** Choice questions auto-advance; typed answers wait for Next (PRD §5.1). */
    val isChoice: Boolean
        get() = this in setOf(SEX, GOAL, ACTIVITY, PACE, EATING_STYLE)
}

/**
 * Raw interview answers. Numbers stay as text while the user is typing so a half-entered
 * value is never silently coerced, and are parsed only when the step is validated.
 */
data class OnboardingAnswers(
    val name: String = "",
    val age: String = "",
    val sex: Sex? = null,
    val heightUnit: HeightUnit = HeightUnit.CM,
    val heightCmText: String = "",
    val heightFeetText: String = "",
    val heightInchesText: String = "",
    val weightUnit: WeightUnit = WeightUnit.KG,
    val weightText: String = "",
    val goal: Goal? = null,
    val goalWeightText: String = "",
    val activity: ActivityLevel? = null,
    val pace: Pace? = null,
    val eatingStyle: EatingStyle? = null,
    val notes: String = "",
) {
    val heightCm: Double?
        get() = when (heightUnit) {
            HeightUnit.CM -> heightCmText.trim().toDoubleOrNull()
            HeightUnit.FT_IN -> {
                val feet = heightFeetText.trim().toIntOrNull()
                val inches = heightInchesText.trim().ifBlank { "0" }.toDoubleOrNull()
                if (feet == null || inches == null) null else Units.cmFromFeetInches(feet, inches)
            }
        }

    val weightKg: Double?
        get() = weightText.trim().toDoubleOrNull()?.let {
            if (weightUnit == WeightUnit.KG) it else Units.kgFromLb(it)
        }

    val goalWeightKg: Double?
        get() = goalWeightText.trim().toDoubleOrNull()?.let {
            if (weightUnit == WeightUnit.KG) it else Units.kgFromLb(it)
        }

    fun toPlanInput(): PlanInput? {
        val height = heightCm ?: return null
        val weight = weightKg ?: return null
        return PlanInput(
            sex = sex ?: return null,
            age = age.trim().toIntOrNull() ?: return null,
            heightCm = height,
            weightKg = weight,
            goal = goal ?: return null,
            goalWeightKg = if (goal == Goal.MAINTAIN) null else goalWeightKg,
            activity = activity ?: return null,
            pace = pace ?: return null,
            eatingStyle = eatingStyle ?: return null,
        )
    }
}

object Onboarding {

    /** Goal weight is meaningless when maintaining, so it drops out of the flow entirely. */
    fun steps(answers: OnboardingAnswers): List<OnboardingStep> =
        OnboardingStep.entries.filterNot {
            it == OnboardingStep.GOAL_WEIGHT && answers.goal == Goal.MAINTAIN
        }

    fun next(current: OnboardingStep, answers: OnboardingAnswers): OnboardingStep {
        val order = steps(answers)
        val index = order.indexOf(current)
        return order.getOrElse(index + 1) { OnboardingStep.RESULTS }
    }

    fun previous(current: OnboardingStep, answers: OnboardingAnswers): OnboardingStep {
        val order = steps(answers)
        val index = order.indexOf(current)
        return order.getOrElse(index - 1) { OnboardingStep.WELCOME }
    }

    /** Position within the questions, ignoring the welcome and results bookends. */
    fun progress(current: OnboardingStep, answers: OnboardingAnswers): Pair<Int, Int> {
        val questions = steps(answers).filter {
            it != OnboardingStep.WELCOME && it != OnboardingStep.RESULTS
        }
        return (questions.indexOf(current) + 1) to questions.size
    }

    /** Returns an inline error for the current step, or null when it may be left. */
    fun validate(step: OnboardingStep, answers: OnboardingAnswers): String? = when (step) {
        OnboardingStep.NAME ->
            if (answers.name.isBlank()) "What should I call you?" else null

        OnboardingStep.AGE -> when (answers.age.trim().toIntOrNull()) {
            null -> "Enter your age in years"
            !in 13..100 -> "Age needs to be between 13 and 100"
            else -> null
        }

        OnboardingStep.SEX ->
            if (answers.sex == null) "Pick one to continue" else null

        OnboardingStep.HEIGHT -> when (val cm = answers.heightCm) {
            null -> "Enter your height"
            else -> if (cm < 80 || cm > 250) "That height looks off — check it" else null
        }

        OnboardingStep.WEIGHT -> when (val kg = answers.weightKg) {
            null -> "Enter your current weight"
            else -> if (kg < 25 || kg > 300) "That weight looks off — check it" else null
        }

        OnboardingStep.GOAL ->
            if (answers.goal == null) "Pick a goal to continue" else null

        OnboardingStep.GOAL_WEIGHT -> {
            val goalKg = answers.goalWeightKg
            val currentKg = answers.weightKg
            when {
                goalKg == null -> "Enter your goal weight"
                goalKg < 25 || goalKg > 300 -> "That weight looks off — check it"
                currentKg == null -> null
                answers.goal == Goal.LOSE && goalKg >= currentKg ->
                    "To lose weight, your goal needs to be below your current weight"
                answers.goal == Goal.GAIN && goalKg <= currentKg ->
                    "To gain weight, your goal needs to be above your current weight"
                else -> null
            }
        }

        OnboardingStep.ACTIVITY ->
            if (answers.activity == null) "Pick your activity level" else null

        OnboardingStep.PACE ->
            if (answers.pace == null) "Pick a pace" else null

        OnboardingStep.EATING_STYLE ->
            if (answers.eatingStyle == null) "Pick an eating style" else null

        OnboardingStep.WELCOME, OnboardingStep.NOTES, OnboardingStep.RESULTS -> null
    }
}
