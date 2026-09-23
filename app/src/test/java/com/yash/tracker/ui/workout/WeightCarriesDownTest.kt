package com.yash.tracker.ui.workout

import com.yash.tracker.data.local.entity.WorkoutSetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Typing a weight once should be enough for the sets under it.
 *
 * The rule has to stop at two things, and both lose data if it does not: a set already ticked
 * is what was actually lifted, and a set above the one being typed was decided earlier.
 */
class WeightCarriesDownTest {

    private fun set(
        index: Int,
        weightKg: Double? = null,
        completed: Boolean = false,
    ) = WorkoutSetEntity(
        id = index.toLong() + 1,
        sessionId = 1,
        exerciseId = 1,
        position = 0,
        setIndex = index,
        reps = 8,
        weightKg = weightKg,
        rpe = null,
        distanceM = null,
        durationSec = null,
        isCompleted = completed,
    )

    @Test
    fun `the sets below take the weight`() {
        val sets = listOf(set(0, 60.0), set(1), set(2))

        val carried = carriesTo(sets, sets[0], 60.0)

        assertEquals(listOf(1, 2), carried.map { it.setIndex })
    }

    @Test
    fun `a set already ticked keeps what was lifted`() {
        val sets = listOf(set(0, 60.0), set(1, 40.0, completed = true), set(2))

        val carried = carriesTo(sets, sets[0], 60.0)

        assertEquals(listOf(2), carried.map { it.setIndex })
    }

    @Test
    fun `sets above are left alone`() {
        val sets = listOf(set(0, 40.0), set(1, 60.0), set(2))

        val carried = carriesTo(sets, sets[1], 60.0)

        assertEquals(listOf(2), carried.map { it.setIndex })
    }

    @Test
    fun `a row that already holds the weight is not written again`() {
        val sets = listOf(set(0, 60.0), set(1, 60.0), set(2))

        val carried = carriesTo(sets, sets[0], 60.0)

        assertEquals(listOf(2), carried.map { it.setIndex })
    }

    @Test
    fun `the last set carries to nothing`() {
        val sets = listOf(set(0, 60.0), set(1, 60.0))

        assertTrue(carriesTo(sets, sets[1], 60.0).isEmpty())
    }
}
