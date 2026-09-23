package com.yash.tracker.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMathTest {

    private fun set(
        exerciseId: Long = 1,
        reps: Int? = 10,
        weightKg: Double? = 60.0,
        warmup: Boolean = false,
        completed: Boolean = true,
        durationSec: Int? = null,
    ) = ScoredSet(exerciseId, reps, weightKg, durationSec, warmup, completed)

    @Test
    fun `Epley matches the formula in TRD 6`() {
        // 100 kg for 10 reps -> 100 × (1 + 10/30) = 133.3
        assertEquals(133.33, OneRepMax.epley(100.0, 10), 0.01)
        assertEquals(120.0, OneRepMax.epley(100.0, 6), 0.01)
    }

    @Test
    fun `a single rep is already the one-rep max`() {
        assertEquals(140.0, OneRepMax.epley(140.0, 1), 0.001)
        assertEquals(140.0, OneRepMax.epley(140.0, 0), 0.001)
    }

    @Test
    fun `volume is weight times reps across completed sets`() {
        val volume = VolumeCalculator.totalKg(
            listOf(set(weightKg = 60.0, reps = 10), set(weightKg = 70.0, reps = 8)),
        )
        assertEquals(600.0 + 560.0, volume, 0.01)
    }

    @Test
    fun `warmups and unfinished sets are left out of volume`() {
        val volume = VolumeCalculator.totalKg(
            listOf(
                set(weightKg = 60.0, reps = 10),
                set(weightKg = 20.0, reps = 15, warmup = true),
                set(weightKg = 80.0, reps = 5, completed = false),
            ),
        )
        assertEquals(600.0, volume, 0.01)
    }

    @Test
    fun `bodyweight sets contribute no volume but still count as done`() {
        assertEquals(0.0, VolumeCalculator.totalKg(listOf(set(weightKg = null, reps = 12))), 0.01)
    }

    @Test
    fun `MET calories follow met times bodyweight times hours`() {
        // 8.3 MET, 78 kg, 30 minutes -> 8.3 × 78 × 0.5 = 323.7
        assertEquals(324, MetCalories.burned(8.3, 78.0, seconds = 1800))
        assertEquals(0, MetCalories.burned(8.3, 78.0, seconds = 0))
    }

    @Test
    fun `a session's bests are found per exercise`() {
        val bests = PersonalRecords.bestsIn(
            listOf(
                set(exerciseId = 1, weightKg = 60.0, reps = 10),
                set(exerciseId = 1, weightKg = 80.0, reps = 5),
                set(exerciseId = 2, weightKg = 40.0, reps = 12),
            ),
        )

        val squatMax = bests.first { it.exerciseId == 1L && it.type == RecordType.MAX_WEIGHT }
        assertEquals(80.0, squatMax.value, 0.01)

        // 60×10 is 600 kg of volume against 80×5's 400, so volume and weight disagree — which
        // is the point of tracking both.
        val squatVolume = bests.first { it.exerciseId == 1L && it.type == RecordType.MAX_VOLUME }
        assertEquals(600.0, squatVolume.value, 0.01)

        assertTrue(bests.any { it.exerciseId == 2L })
    }

    @Test
    fun `estimated 1RM picks the best set, not the heaviest`() {
        val bests = PersonalRecords.bestsIn(
            listOf(
                set(weightKg = 100.0, reps = 5),   // 1RM 116.7
                set(weightKg = 90.0, reps = 12),   // 1RM 126.0
            ),
        )
        assertEquals(126.0, bests.first { it.type == RecordType.EST_1RM }.value, 0.1)
    }

    @Test
    fun `warmups never set records`() {
        val bests = PersonalRecords.bestsIn(
            listOf(
                set(weightKg = 60.0, reps = 10),
                set(weightKg = 200.0, reps = 20, warmup = true),
            ),
        )
        assertEquals(60.0, bests.first { it.type == RecordType.MAX_WEIGHT }.value, 0.01)
    }

    @Test
    fun `the best set is the one with most volume`() {
        val best = PersonalRecords.bestSet(
            listOf(set(weightKg = 100.0, reps = 5), set(weightKg = 60.0, reps = 12)),
        )
        assertEquals(60.0, best!!.weightKg!!, 0.01)
        assertEquals(12, best.reps)
    }

    @Test
    fun `a session with nothing completed has no bests`() {
        assertTrue(PersonalRecords.bestsIn(listOf(set(completed = false))).isEmpty())
        assertNull(PersonalRecords.bestSet(listOf(set(completed = false))))
    }
}
