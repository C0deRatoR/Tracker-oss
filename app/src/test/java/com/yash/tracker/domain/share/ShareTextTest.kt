package com.yash.tracker.domain.share

import com.yash.tracker.data.local.dao.DayTotals
import com.yash.tracker.data.local.entity.TargetEntity
import com.yash.tracker.data.local.entity.WorkoutSessionEntity
import com.yash.tracker.data.local.entity.WorkoutSetEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Shared text leaves the phone, so what it does and does not contain is worth pinning down. */
class ShareTextTest {

    private val target = TargetEntity(
        id = 1,
        computedAt = 0,
        basisWeightKg = 78.0,
        bmr = 1740,
        tdee = 2697,
        activityFactor = 1.55,
        kcal = 2230,
        proteinG = 172.0,
        carbsG = 250.0,
        fatG = 60.0,
        waterMl = 3300,
        weeksToGoal = null,
        rateLbPerWeek = null,
    )

    @Test
    fun `a day under target says what is left`() {
        val text = ShareText.forDay(
            date = LocalDate.of(2026, 9, 13),
            totals = DayTotals(kcal = 660.0, proteinG = 29.0, carbsG = 87.0, fatG = 22.0),
            target = target,
            waterMl = 0,
        )

        assertTrue(text, text.contains("660 / 2230 kcal"))
        assertTrue(text, text.contains("1570 left"))
        assertTrue(text, text.contains("Protein 29 / 172 g"))
    }

    @Test
    fun `a day over target says by how much rather than a negative`() {
        val text = ShareText.forDay(
            date = LocalDate.of(2026, 9, 13),
            totals = DayTotals(kcal = 2500.0, proteinG = 0.0, carbsG = 0.0, fatG = 0.0),
            target = target,
            waterMl = 0,
        )

        assertTrue(text, text.contains("270 over"))
        assertFalse(text, text.contains("-270"))
    }

    @Test
    fun `no target still shares the intake`() {
        val text = ShareText.forDay(
            date = LocalDate.of(2026, 9, 13),
            totals = DayTotals(kcal = 660.0, proteinG = 29.0, carbsG = 0.0, fatG = 0.0),
            target = null,
            waterMl = 0,
        )

        assertTrue(text, text.contains("660 kcal"))
        assertTrue(text, text.contains("Protein 29 g"))
    }

    @Test
    fun `water is left out when none was logged`() {
        val text = ShareText.forDay(
            date = LocalDate.of(2026, 9, 13),
            totals = DayTotals(0.0, 0.0, 0.0, 0.0),
            target = target,
            waterMl = 0,
        )

        assertFalse(text, text.contains("Water"))
    }

    private fun session() = WorkoutSessionEntity(
        id = 1,
        routineId = 1,
        name = "Push",
        date = "2026-09-13",
        startedAt = 1_757_000_000_000,
        endedAt = 1_757_000_000_000 + 35 * 60 * 1000,
        totalVolumeKg = 2760.0,
        kcalBurned = 180,
        note = null,
        isFinished = true,
    )

    private fun set(
        index: Int,
        reps: Int? = 8,
        weight: Double? = 60.0,
        warmup: Boolean = false,
        completed: Boolean = true,
    ) = WorkoutSetEntity(
        id = index.toLong() + 1,
        sessionId = 1,
        exerciseId = 1,
        position = 0,
        setIndex = index,
        reps = reps,
        weightKg = weight,
        rpe = null,
        distanceM = null,
        durationSec = null,
        isWarmup = warmup,
        isCompleted = completed,
    )

    @Test
    fun `a session lists each exercise and its sets`() {
        val text = ShareText.forSession(
            session = session(),
            sets = listOf(set(0), set(1, reps = 6)),
            nameOf = { "Barbell Bench Press" },
        )

        assertTrue(text, text.startsWith("Push — "))
        assertTrue(text, text.contains("Barbell Bench Press"))
        assertTrue(text, text.contains("60 kg × 8"))
        assertTrue(text, text.contains("60 kg × 6"))
        assertTrue(text, text.contains("2,760 kg"))
    }

    @Test
    fun `sets ticked but never filled in are not shared as work`() {
        val text = ShareText.forSession(
            session = session(),
            sets = listOf(set(0), set(1, reps = null, weight = null)),
            nameOf = { "Barbell Bench Press" },
        )

        // A routine prefills its rows; sharing them would claim work that never happened.
        assertEquals(1, text.lines().count { it.trim().startsWith("60 kg") })
        // The title line uses an em-dash, so check for an empty *set* line rather than the char.
        assertFalse(text, text.lines().any { it.trim() == "—" })
    }

    @Test
    fun `a warm-up is labelled rather than silently counted`() {
        val text = ShareText.forSession(
            session = session(),
            sets = listOf(set(0, warmup = true), set(1)),
            nameOf = { "Barbell Bench Press" },
        )

        assertTrue(text, text.contains("warm-up"))
        assertTrue(text, text.contains("1 sets"))
    }

    @Test
    fun `an unnamed exercise does not break the text`() {
        val text = ShareText.forSession(session(), listOf(set(0)), nameOf = { null })

        assertTrue(text, text.contains("Exercise"))
    }
}
