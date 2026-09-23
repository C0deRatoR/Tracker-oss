package com.yash.tracker.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The analysis, checked on the cases where a naive version gets it wrong: warmups counted as
 * work, bodyweight lifts that can never register a change, and a first session compared
 * against nothing.
 */
class SessionAnalysisTest {

    private fun set(
        exerciseId: Long,
        name: String,
        group: String,
        reps: Int? = null,
        weightKg: Double? = null,
        durationSec: Int? = null,
        warmup: Boolean = false,
        completed: Boolean = true,
    ) = AnalysedSet(exerciseId, name, group, reps, weightKg, durationSec, warmup, completed)

    @Test
    fun `warmups and unticked rows are not work`() {
        val analysis = SessionAnalyst.analyse(
            sets = listOf(
                set(1, "Bench Press", "CHEST", reps = 10, weightKg = 40.0, warmup = true),
                set(1, "Bench Press", "CHEST", reps = 8, weightKg = 80.0),
                set(1, "Bench Press", "CHEST", reps = 8, weightKg = 80.0, completed = false),
            ),
            previousBests = emptyMap(),
        )

        assertEquals(1, analysis.muscleSplit.single().workingSets)
    }

    @Test
    fun `the split is counted in sets so bodyweight work still appears`() {
        val analysis = SessionAnalyst.analyse(
            sets = listOf(
                set(1, "Squat", "LEGS", reps = 5, weightKg = 100.0),
                set(2, "Pull Up", "BACK", reps = 8),
                set(2, "Pull Up", "BACK", reps = 7),
            ),
            previousBests = emptyMap(),
        )

        val back = analysis.muscleSplit.single { it.group == "BACK" }
        assertEquals(2, back.workingSets)
        assertEquals("no load, but two thirds of the work", 2.0 / 3, back.share, 0.001)
        assertEquals(0.0, back.volumeKg, 0.001)
    }

    @Test
    fun `a bodyweight lift doing more reps counts as progress`() {
        val analysis = SessionAnalyst.analyse(
            sets = listOf(set(2, "Pull Up", "BACK", reps = 10)),
            previousBests = mapOf(2L to Effort.Reps(8)),
        )

        val trend = analysis.trends.single()
        assertEquals(Trend.UP, trend.trend)
        assertEquals(0.25, trend.changeFraction!!, 0.001)
    }

    @Test
    fun `a heavier top set counts as progress`() {
        val analysis = SessionAnalyst.analyse(
            sets = listOf(set(1, "Bench Press", "CHEST", reps = 5, weightKg = 100.0)),
            previousBests = mapOf(1L to Effort.Load(OneRepMax.epley(95.0, 5))),
        )

        assertEquals(Trend.UP, analysis.trends.single().trend)
    }

    @Test
    fun `a lighter session is reported as down rather than hidden`() {
        val analysis = SessionAnalyst.analyse(
            sets = listOf(set(1, "Bench Press", "CHEST", reps = 5, weightKg = 80.0)),
            previousBests = mapOf(1L to Effort.Load(OneRepMax.epley(100.0, 5))),
        )

        assertEquals(Trend.DOWN, analysis.trends.single().trend)
        assertTrue(analysis.notes.any { it.contains("1 down") })
    }

    @Test
    fun `the same numbers twice is level, not a change`() {
        val analysis = SessionAnalyst.analyse(
            sets = listOf(set(1, "Bench Press", "CHEST", reps = 5, weightKg = 100.0)),
            previousBests = mapOf(1L to Effort.Load(OneRepMax.epley(100.0, 5))),
        )

        assertEquals(Trend.LEVEL, analysis.trends.single().trend)
    }

    @Test
    fun `a first session has nothing to compare against and says so`() {
        val analysis = SessionAnalyst.analyse(
            sets = listOf(set(1, "Bench Press", "CHEST", reps = 5, weightKg = 100.0)),
            previousBests = emptyMap(),
        )

        val trend = analysis.trends.single()
        assertEquals(Trend.FIRST, trend.trend)
        assertNull(trend.changeFraction)
        assertTrue(analysis.notes.any { it.contains("previous session") })
    }

    @Test
    fun `an exercise that changed measure is not compared across units`() {
        // Loaded this time, bodyweight last time: the numbers are not in the same currency.
        val analysis = SessionAnalyst.analyse(
            sets = listOf(set(2, "Pull Up", "BACK", reps = 6, weightKg = 10.0)),
            previousBests = mapOf(2L to Effort.Reps(12)),
        )

        assertEquals(Trend.FIRST, analysis.trends.single().trend)
    }

    @Test
    fun `a lopsided session says which muscle took it`() {
        val analysis = SessionAnalyst.analyse(
            sets = listOf(
                set(1, "Bench Press", "CHEST", reps = 8, weightKg = 80.0),
                set(1, "Bench Press", "CHEST", reps = 8, weightKg = 80.0),
                set(1, "Bench Press", "CHEST", reps = 8, weightKg = 80.0),
                set(3, "Curl", "ARMS", reps = 12, weightKg = 15.0),
            ),
            previousBests = emptyMap(),
        )

        assertTrue(analysis.notes.any { it.startsWith("Chest took 75%") })
    }

    @Test
    fun `a big swing in total volume is worth a sentence`() {
        val analysis = SessionAnalyst.analyse(
            sets = listOf(set(1, "Bench Press", "CHEST", reps = 8, weightKg = 80.0)),
            previousBests = emptyMap(),
            previousSession = SessionChange(
                volumeKg = 6400.0,
                previousVolumeKg = 4000.0,
                workingSets = 10,
                previousWorkingSets = 8,
            ),
        )

        assertTrue(analysis.notes.any { it.contains("Total volume up 60%") })
    }

    @Test
    fun `a session with nothing filled in analyses to nothing`() {
        val analysis = SessionAnalyst.analyse(
            sets = listOf(set(1, "Bench Press", "CHEST", completed = true)),
            previousBests = emptyMap(),
        )

        assertTrue(analysis.isEmpty)
    }
}
