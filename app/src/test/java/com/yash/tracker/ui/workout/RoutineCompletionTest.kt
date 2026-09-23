package com.yash.tracker.ui.workout

import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.data.local.entity.WorkoutSetEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Auto-finishing ends a session for good, so the condition that triggers it gets its own tests.
 * The costly mistake is firing early, not firing late.
 */
class RoutineCompletionTest {

    private fun set(index: Int, completed: Boolean, warmup: Boolean = false) = WorkoutSetEntity(
        id = index.toLong() + 1,
        sessionId = 1,
        exerciseId = 1,
        position = 0,
        setIndex = index,
        reps = 8,
        weightKg = 60.0,
        rpe = null,
        distanceM = null,
        durationSec = null,
        isWarmup = warmup,
        isCompleted = completed,
    )

    private fun block(vararg sets: WorkoutSetEntity) = ExerciseBlock(
        exercise = ExerciseEntity(
            id = 1,
            name = "Barbell Bench Press",
            muscleGroup = "CHEST",
            equipment = "BARBELL",
            type = "STRENGTH",
            notes = null,
        ),
        sets = sets.toList(),
        previous = emptyList(),
    )

    private fun state(routineId: Long?, vararg blocks: ExerciseBlock) =
        LiveSessionUiState(sessionId = 1, routineId = routineId, blocks = blocks.toList())

    @Test
    fun `a routine with every set ticked is complete`() {
        val state = state(routineId = 7, block(set(0, true), set(1, true)))

        assertTrue(state.isRoutineComplete)
    }

    @Test
    fun `one unticked set is not complete`() {
        val state = state(routineId = 7, block(set(0, true), set(1, false)))

        assertFalse(state.isRoutineComplete)
    }

    @Test
    fun `an empty workout never auto-finishes, however many sets are ticked`() {
        val state = state(routineId = null, block(set(0, true), set(1, true)))

        // No routine means no plan to have completed — the first ticked set would end it.
        assertFalse(state.isRoutineComplete)
    }

    @Test
    fun `a routine with no sets at all is not complete`() {
        val state = state(routineId = 7)

        assertFalse(state.isRoutineComplete)
    }

    @Test
    fun `a warmup still has to be ticked to count`() {
        val state = state(routineId = 7, block(set(0, true, warmup = true), set(1, true)))
        val pending = state(routineId = 7, block(set(0, false, warmup = true), set(1, true)))

        assertTrue(state.isRoutineComplete)
        assertFalse(pending.isRoutineComplete)
    }

    @Test
    fun `every exercise has to be done, not just the first`() {
        val done = block(set(0, true))
        val notDone = ExerciseBlock(
            exercise = ExerciseEntity(
                id = 2,
                name = "Overhead Press",
                muscleGroup = "SHOULDERS",
                equipment = "BARBELL",
                type = "STRENGTH",
                notes = null,
            ),
            sets = listOf(set(0, false)),
            previous = emptyList(),
        )

        assertFalse(state(routineId = 7, done, notDone).isRoutineComplete)
        assertTrue(state(routineId = 7, done).isRoutineComplete)
    }

    @Test
    fun `adding a set to a finished routine makes it incomplete again`() {
        val complete = state(routineId = 7, block(set(0, true)))
        val afterAdding = state(routineId = 7, block(set(0, true), set(1, false)))

        assertTrue(complete.isRoutineComplete)
        assertFalse(afterAdding.isRoutineComplete)
    }
}
