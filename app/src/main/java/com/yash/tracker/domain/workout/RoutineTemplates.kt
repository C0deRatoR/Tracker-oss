package com.yash.tracker.domain.workout

import com.yash.tracker.domain.model.ActivityLevel
import com.yash.tracker.domain.model.Goal

/** One exercise in a template, already fitted to the user: a catalogue name and a target. */
data class TemplateExercise(val name: String, val sets: Int, val repsLow: Int, val repsHigh: Int)

data class TemplateRoutine(val name: String, val exercises: List<TemplateExercise>)

data class TemplatePlan(
    val split: Split,
    val routines: List<TemplateRoutine>,
    val isRecommended: Boolean,
    /** Why this plan suits this user, in a sentence. Only the recommended one has a reason. */
    val reason: String?,
)

enum class Split(val label: String, val daysPerWeek: String) {
    FULL_BODY("Full body", "2–3 days a week"),
    UPPER_LOWER("Upper / Lower", "4 days a week"),
    PUSH_PULL_LEGS("Push / Pull / Legs", "3 or 6 days a week"),
}

/** What a template is fitted to: the answers from onboarding. */
data class TemplateProfile(val age: Int, val goal: Goal, val activity: ActivityLevel)

/**
 * The standard splits, fitted to what the user said about themselves at the start.
 *
 * Three things from onboarding change the answer. How active they are decides how many days a
 * week they are likely to train, and so which split fits: a split is only as good as the days
 * it assumes. Their goal sets the dose — a surplus can carry more sets, a deficit should keep
 * the weight heavy and trim the volume. Age, or a sedentary start, swaps the barbell lifts for
 * machine and dumbbell versions that are easier to learn and to recover from.
 *
 * Every exercise name is the catalogue's own, so a template becomes a routine by lookup.
 */
object RoutineTemplates {

    fun plansFor(profile: TemplateProfile): List<TemplatePlan> {
        val recommended = splitFor(profile.activity)
        val gentle = profile.age >= GENTLE_AGE || profile.activity == ActivityLevel.SEDENTARY
        val dose = Dose.of(profile.goal)

        return Split.entries
            .sortedBy { if (it == recommended) 0 else 1 }
            .map { split ->
                TemplatePlan(
                    split = split,
                    routines = routinesFor(split).map { it.fit(gentle, dose) },
                    isRecommended = split == recommended,
                    reason = if (split == recommended) reasonFor(profile, split, gentle) else null,
                )
            }
    }

    fun splitFor(activity: ActivityLevel): Split = when (activity) {
        ActivityLevel.SEDENTARY, ActivityLevel.LIGHTLY -> Split.FULL_BODY
        ActivityLevel.MODERATELY -> Split.UPPER_LOWER
        ActivityLevel.VERY, ActivityLevel.ATHLETE -> Split.PUSH_PULL_LEGS
    }

    private fun reasonFor(profile: TemplateProfile, split: Split, gentle: Boolean): String {
        val days = when (split) {
            Split.FULL_BODY -> "Every session trains everything, so two or three a week covers it"
            Split.UPPER_LOWER -> "Four sessions hit every muscle twice a week"
            Split.PUSH_PULL_LEGS -> "Enough days to run it twice through, every muscle twice a week"
        }
        val goal = when (profile.goal) {
            Goal.GAIN -> "an extra set on the big lifts for your surplus"
            Goal.MAINTAIN -> "a moderate dose to hold what you have"
            Goal.LOSE -> "heavy lifts kept, volume trimmed while you're in a deficit"
        }
        val style = if (gentle) ", machine and dumbbell versions to start" else ""
        return "$days — $goal$style."
    }

    /** How much work each role gets, by goal. */
    private data class Dose(
        val compoundSets: Int,
        val compoundReps: IntRange,
        val accessorySets: Int,
        val accessoryReps: IntRange,
    ) {
        companion object {
            fun of(goal: Goal) = when (goal) {
                Goal.GAIN -> Dose(4, 6..10, 3, 10..12)
                Goal.MAINTAIN -> Dose(3, 6..10, 3, 10..15)
                // Heavy stays heavy in a deficit — it is the signal to keep the muscle — but
                // recovery is worse, so the accessory volume is what gives.
                Goal.LOSE -> Dose(3, 5..8, 2, 12..15)
            }
        }
    }

    private enum class Role { COMPOUND, ACCESSORY }

    /** A slot in a template: the standard lift, and the gentler one where they differ. */
    private data class Slot(val standard: String, val role: Role, val gentle: String = standard)

    private data class Day(val name: String, val slots: List<Slot>) {
        fun fit(gentle: Boolean, dose: Dose) = TemplateRoutine(
            name = name,
            exercises = slots.map { slot ->
                val compound = slot.role == Role.COMPOUND
                val reps = if (compound) dose.compoundReps else dose.accessoryReps
                TemplateExercise(
                    name = if (gentle) slot.gentle else slot.standard,
                    sets = if (compound) dose.compoundSets else dose.accessorySets,
                    repsLow = reps.first,
                    repsHigh = reps.last,
                )
            },
        )
    }

    private fun compound(standard: String, gentle: String = standard) = Slot(standard, Role.COMPOUND, gentle)
    private fun accessory(standard: String, gentle: String = standard) = Slot(standard, Role.ACCESSORY, gentle)

    private fun routinesFor(split: Split): List<Day> = when (split) {
        Split.FULL_BODY -> listOf(
            Day(
                "Full body A",
                listOf(
                    compound("Squat (Barbell)", gentle = "Goblet Squat (Kettlebell)"),
                    compound("Bench Press (Barbell)", gentle = "Chest Press (Machine)"),
                    compound("Seated Row (Cable)"),
                    compound("Romanian Deadlift (Dumbbell)"),
                    accessory("Lateral Raise (Dumbbell)"),
                    accessory("Plank"),
                ),
            ),
            Day(
                "Full body B",
                listOf(
                    compound("Leg Press"),
                    compound("Overhead Press (Dumbbell)", gentle = "Shoulder Press (Machine)"),
                    compound("Lat Pulldown (Cable)"),
                    compound("Hip Thrust (Barbell)", gentle = "Hip Thrust (Bodyweight)"),
                    accessory("Bicep Curl (Dumbbell)"),
                    accessory("Triceps Pushdown (Cable - Straight Bar)"),
                ),
            ),
        )

        Split.UPPER_LOWER -> listOf(
            Day(
                "Upper",
                listOf(
                    compound("Bench Press (Barbell)", gentle = "Chest Press (Machine)"),
                    compound("Bent Over Row (Barbell)", gentle = "Seated Row (Cable)"),
                    compound("Overhead Press (Dumbbell)", gentle = "Shoulder Press (Machine)"),
                    compound("Lat Pulldown (Cable)"),
                    accessory("Bicep Curl (Dumbbell)"),
                    accessory("Triceps Pushdown (Cable - Straight Bar)"),
                ),
            ),
            Day(
                "Lower",
                listOf(
                    compound("Squat (Barbell)", gentle = "Leg Press"),
                    compound("Romanian Deadlift (Barbell)", gentle = "Romanian Deadlift (Dumbbell)"),
                    compound("Bulgarian Split Squat", gentle = "Lunge (Dumbbell)"),
                    accessory("Lying Leg Curl (Machine)"),
                    accessory("Standing Calf Raise (Machine)"),
                    accessory("Hanging Knee Raise", gentle = "Crunch"),
                ),
            ),
        )

        Split.PUSH_PULL_LEGS -> listOf(
            Day(
                "Push",
                listOf(
                    compound("Bench Press (Barbell)", gentle = "Chest Press (Machine)"),
                    compound("Overhead Press (Barbell)", gentle = "Shoulder Press (Machine)"),
                    compound("Incline Bench Press (Dumbbell)"),
                    accessory("Lateral Raise (Dumbbell)"),
                    accessory("Triceps Pushdown (Cable - Straight Bar)"),
                    accessory("Triceps Extension (Dumbbell)"),
                ),
            ),
            Day(
                "Pull",
                listOf(
                    compound("Pull Up", gentle = "Lat Pulldown (Cable)"),
                    compound("Bent Over Row (Barbell)", gentle = "Seated Row (Machine)"),
                    compound("Seated Row (Cable)"),
                    accessory("Face Pull (Cable)"),
                    accessory("Bicep Curl (Barbell)", gentle = "Bicep Curl (Dumbbell)"),
                    accessory("Hammer Curl (Dumbbell)"),
                ),
            ),
            Day(
                "Legs",
                listOf(
                    compound("Squat (Barbell)", gentle = "Leg Press"),
                    compound("Romanian Deadlift (Barbell)", gentle = "Romanian Deadlift (Dumbbell)"),
                    accessory("Leg Extension (Machine)"),
                    accessory("Seated Leg Curl (Machine)"),
                    accessory("Standing Calf Raise (Machine)"),
                    accessory("Hanging Leg Raise", gentle = "Crunch"),
                ),
            ),
        )
    }

    /** From here the barbell versions give way to machines and dumbbells. */
    private const val GENTLE_AGE = 50
}
