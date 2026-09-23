package com.yash.tracker.domain.workout

import com.yash.tracker.data.local.entity.ExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingAnalystTest {

    private val day = TrainingAnalyst.DAY_MS
    private val now = 100 * day

    private fun exercise(
        id: Long,
        name: String,
        group: String,
        primary: String,
        secondary: String? = null,
        force: String? = "push",
        mechanic: String? = "compound",
        equipment: String = "BARBELL",
        level: String = "beginner",
    ) = ExerciseEntity(
        id = id,
        name = name,
        muscleGroup = group,
        equipment = equipment,
        type = "STRENGTH",
        notes = null,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        force = force,
        mechanic = mechanic,
        level = level,
    )

    private val bench = exercise(1, "Bench Press", "CHEST", "chest", secondary = "triceps\nshoulders")
    private val squat = exercise(2, "Squat", "LEGS", "quadriceps", secondary = "glutes\nhamstrings")
    private val row = exercise(3, "Bent Over Row", "BACK", "middle back", secondary = "biceps", force = "pull")
    private val pulldown = exercise(4, "Lat Pulldown", "BACK", "lats", force = "pull", equipment = "CABLE")
    private val rdl = exercise(5, "Romanian Deadlift", "LEGS", "hamstrings", force = "pull")
    private val press = exercise(6, "Overhead Press", "SHOULDERS", "shoulders")
    private val plank = exercise(7, "Plank", "CORE", "abdominals", force = "static", mechanic = "isolation", equipment = "BODYWEIGHT")
    private val fly = exercise(8, "Cable Fly", "CHEST", "chest", mechanic = "isolation", equipment = "CABLE")
    private val legCurl = exercise(9, "Leg Curl", "LEGS", "hamstrings", force = "pull", mechanic = "isolation", equipment = "MACHINE")
    private val legExtension = exercise(10, "Leg Extension", "LEGS", "quadriceps", mechanic = "isolation", equipment = "MACHINE")
    private val catalogue = listOf(bench, squat, row, pulldown, rdl, press, plank, fly, legCurl, legExtension)

    private var sessionSeq = 0L

    /** [count] sets of [exercise], in a session [daysAgo] days before now. */
    private fun sets(
        exercise: ExerciseEntity,
        count: Int,
        daysAgo: Int,
        reps: Int = 8,
        weight: Double = 60.0,
        rpe: Double? = null,
        session: Long = ++sessionSeq,
    ): List<HistorySet> = List(count) {
        HistorySet(
            sessionId = session,
            startedAt = now - daysAgo * day,
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            muscleGroup = exercise.muscleGroup,
            primaryMuscles = exercise.primaryMuscleList,
            secondaryMuscles = exercise.secondaryMuscleList,
            force = exercise.force,
            mechanic = exercise.mechanic,
            equipment = exercise.equipment,
            reps = reps,
            weightKg = weight,
            rpe = rpe,
            durationSec = null,
        )
    }

    private fun analyse(sets: List<HistorySet>) =
        TrainingAnalyst.analyse(sets.sortedBy { it.startedAt }, now, catalogue)

    @Test
    fun `secondary muscles earn half a set`() {
        val report = analyse(sets(bench, 4, daysAgo = 1))

        assertEquals(4.0, report.volumeOf(Muscle.CHEST), 1e-9)
        assertEquals(2.0, report.volumeOf(Muscle.TRICEPS), 1e-9)
    }

    @Test
    fun `volume outside the week does not count`() {
        val report = analyse(sets(bench, 12, daysAgo = 9))
        assertEquals(0.0, report.volumeOf(Muscle.CHEST), 1e-9)
    }

    @Test
    fun `volume is graded against the weekly landmarks`() {
        val report = analyse(sets(bench, 12, daysAgo = 1) + sets(squat, 24, daysAgo = 2) + sets(row, 2, daysAgo = 3))
        val grade = report.muscles.associate { it.muscle to it.grade }

        assertEquals(VolumeGrade.PRODUCTIVE, grade[Muscle.CHEST])
        assertEquals(VolumeGrade.HIGH, grade[Muscle.QUADS])
        assertEquals(VolumeGrade.UNDER, grade[Muscle.MIDDLE_BACK])
    }

    @Test
    fun `patterns come from muscle and force, not names`() {
        assertEquals(Pattern.HORIZONTAL_PUSH, TrainingAnalyst.patternOf(sets(bench, 1, 1).single()))
        assertEquals(Pattern.VERTICAL_PULL, TrainingAnalyst.patternOf(sets(pulldown, 1, 1).single()))
        assertEquals(Pattern.HORIZONTAL_PULL, TrainingAnalyst.patternOf(sets(row, 1, 1).single()))
        assertEquals(Pattern.HINGE, TrainingAnalyst.patternOf(sets(rdl, 1, 1).single()))
        assertEquals(Pattern.SQUAT, TrainingAnalyst.patternOf(sets(squat, 1, 1).single()))
        assertEquals(Pattern.VERTICAL_PUSH, TrainingAnalyst.patternOf(sets(press, 1, 1).single()))
        assertNull("a curl is no hinge", TrainingAnalyst.patternOf(sets(legCurl, 1, 1).single()))
    }

    @Test
    fun `pushing far more than pulling is flagged with rows to fix it`() {
        val report = analyse(sets(bench, 12, daysAgo = 1) + sets(press, 6, daysAgo = 3) + sets(pulldown, 4, daysAgo = 2))
        val imbalance = report.suggestions.first { it.kind == SuggestionKind.IMBALANCE }

        assertEquals(18, report.pushSets)
        assertEquals(4, report.pullSets)
        assertTrue(imbalance.title.startsWith("Pull is behind push"))
        assertTrue(imbalance.exercises.any { it.id == row.id })
    }

    @Test
    fun `quads far ahead of hamstrings is flagged`() {
        // Squats credit the hamstrings half a set each; extensions credit them nothing.
        val report = analyse(sets(squat, 4, daysAgo = 2) + sets(legExtension, 8, daysAgo = 2))
        assertTrue(report.suggestions.any { it.kind == SuggestionKind.IMBALANCE && it.title.startsWith("Quads") })
    }

    @Test
    fun `a lift that has not moved in three sessions is stalled`() {
        val history = sets(bench, 3, daysAgo = 20, weight = 80.0) +
            sets(bench, 3, daysAgo = 15, weight = 80.0) +
            sets(bench, 3, daysAgo = 10, weight = 80.0) +
            sets(bench, 3, daysAgo = 5, weight = 80.0)

        val lift = analyse(history).lifts.single()
        assertEquals(LiftStatus.STALLED, lift.status)
    }

    @Test
    fun `a lift still climbing is progressing`() {
        val history = sets(bench, 3, daysAgo = 20, weight = 70.0) +
            sets(bench, 3, daysAgo = 15, weight = 72.5) +
            sets(bench, 3, daysAgo = 10, weight = 75.0) +
            sets(bench, 3, daysAgo = 5, weight = 77.5)

        assertEquals(LiftStatus.PROGRESSING, analyse(history).lifts.single().status)
    }

    @Test
    fun `two sessions well under the best is a regression, and it leads`() {
        val history = sets(bench, 3, daysAgo = 15, weight = 100.0) +
            sets(bench, 3, daysAgo = 10, weight = 90.0) +
            sets(bench, 3, daysAgo = 5, weight = 90.0)

        val report = analyse(history)
        assertEquals(LiftStatus.REGRESSING, report.lifts.single().status)
        assertEquals(SuggestionKind.REGRESSING, report.suggestions.first().kind)
    }

    @Test
    fun `there are several suggestions, most important first`() {
        val report = analyse(sets(bench, 12, daysAgo = 1) + sets(squat, 6, daysAgo = 3))

        assertTrue(report.suggestions.size >= 3)
        assertEquals(report.suggestions.sortedByDescending { it.priority }, report.suggestions)
        // Big muscles with nothing at all outrank a small one.
        val first = report.suggestions.first()
        assertEquals(SuggestionKind.UNDER_TRAINED, first.kind)
    }

    @Test
    fun `an under-trained muscle suggests what the user already does for it first`() {
        // Pulldowns were done a month ago; lats have had nothing this week.
        val report = analyse(sets(pulldown, 3, daysAgo = 20) + sets(bench, 10, daysAgo = 1))
        val lats = report.suggestions.first { it.title.startsWith("Lats") }

        assertEquals(pulldown.id, lats.exercises.first().id)
    }

    @Test
    fun `a muscle trained yesterday is tagged as still recovering`() {
        val report = analyse(sets(rdl, 3, daysAgo = 1) + sets(bench, 10, daysAgo = 3))
        val hamstrings = report.suggestions.first { it.title.startsWith("Hamstrings") }

        assertNotNull(hamstrings.readyInDays)
    }

    @Test
    fun `a missing pattern is named only when its muscle is otherwise covered`() {
        // Chest done entirely with flyes: plenty of volume, no press.
        val report = analyse(sets(fly, 12, daysAgo = 2))

        assertTrue(report.suggestions.any { it.kind == SuggestionKind.MISSING_PATTERN && it.title.contains("horizontal push") })
        assertTrue(
            "hinge is covered by the hamstring line instead",
            report.suggestions.none { it.kind == SuggestionKind.MISSING_PATTERN && it.title.contains("hinge") },
        )
    }

    @Test
    fun `easy sets are called out once enough of them are rated`() {
        val report = analyse(sets(bench, 8, daysAgo = 1, rpe = 5.5))
        assertTrue(report.suggestions.any { it.kind == SuggestionKind.LOW_EFFORT })
    }

    @Test
    fun `rep ranges fall into their bands`() {
        val report = analyse(sets(bench, 2, 1, reps = 3) + sets(bench, 3, 1, reps = 10) + sets(bench, 1, 1, reps = 15))
        assertEquals(RepBands(strength = 2, hypertrophy = 3, endurance = 1), report.repBands)
    }

    @Test
    fun `no history means an empty report with nothing to say`() {
        val report = analyse(emptyList())

        assertTrue(report.isEmpty)
        assertTrue(report.suggestions.isEmpty())
    }
}
