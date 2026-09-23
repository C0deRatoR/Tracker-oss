package com.yash.tracker.domain.progress

import com.yash.tracker.domain.diary.MealType
import com.yash.tracker.domain.model.Goal
import com.yash.tracker.domain.workout.HistorySet
import com.yash.tracker.domain.workout.Muscle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsTest {

    @Test
    fun `a perfect line correlates at one`() {
        val r = Stats.pearson((1..6).map { it.toDouble() to it * 2.0 })!!
        assertEquals(1.0, r.r, 1e-9)
        assertEquals(Correlation.Strength.STRONG, r.strength)
    }

    @Test
    fun `too few pairs says nothing rather than something wrong`() {
        assertNull(Stats.pearson(listOf(1.0 to 2.0, 2.0 to 3.0, 3.0 to 5.0)))
    }

    @Test
    fun `a flat series has nothing to correlate with`() {
        assertNull(Stats.pearson((1..6).map { it.toDouble() to 4.0 }))
    }

    @Test
    fun `slope of a line is its gradient`() {
        assertEquals(0.5, Stats.slope(listOf(0.0 to 1.0, 2.0 to 2.0, 4.0 to 3.0))!!, 1e-9)
    }
}

class BodyAnalystTest {

    private val today = LocalDate.of(2026, 9, 30)

    private fun series(days: Int, start: Double, perDay: Double, every: Int = 1) =
        (0 until days step every).map { DayPoint(today.minusDays((days - 1 - it).toLong()), start + perDay * it) }

    @Test
    fun `the trend moves a tenth of the way per day, and further across a gap`() {
        val daily = BodyAnalyst.trend(listOf(DayPoint(today, 80.0), DayPoint(today.plusDays(1), 90.0)))
        assertEquals(81.0, daily.last().value, 1e-9)

        val gapped = BodyAnalyst.trend(listOf(DayPoint(today, 80.0), DayPoint(today.plusDays(7), 90.0)))
        // 1 − 0.9⁷ ≈ 0.52 of the way.
        assertEquals(85.217, gapped.last().value, 0.01)
    }

    @Test
    fun `a steady loss reads as that rate per week and projects a goal date`() {
        // Losing 0.1 kg a day for four weeks.
        val weights = series(28, start = 85.0, perDay = -0.1)
        val report = BodyAnalyst.analyse(weights, emptyList(), today, Goal.LOSE, goalWeightKg = 75.0, planRateLbPerWeek = 1.5, planTdee = null)

        assertEquals(-0.7, report.ratePerWeekKg!!, 0.01)
        assertEquals(PaceVerdict.ON_PACE, report.pace)
        assertTrue(report.projectedGoalDate!!.isAfter(today))
    }

    @Test
    fun `gaining while the plan says lose is the wrong way, with no arrival date`() {
        val weights = series(28, start = 80.0, perDay = 0.05)
        val report = BodyAnalyst.analyse(weights, emptyList(), today, Goal.LOSE, 75.0, 1.0, null)

        assertEquals(PaceVerdict.WRONG_WAY, report.pace)
        assertNull(report.projectedGoalDate)
    }

    @Test
    fun `pace verdicts against a target`() {
        assertEquals(PaceVerdict.AHEAD, BodyAnalyst.pace(rate = -1.0, target = -0.5))
        assertEquals(PaceVerdict.BEHIND, BodyAnalyst.pace(rate = -0.2, target = -0.7))
        assertEquals(PaceVerdict.ON_PACE, BodyAnalyst.pace(rate = 0.05, target = 0.0))
        assertEquals(PaceVerdict.WRONG_WAY, BodyAnalyst.pace(rate = 0.4, target = 0.0))
    }

    @Test
    fun `a stable weight on a steady intake puts spend at the intake`() {
        val weights = series(28, start = 80.0, perDay = 0.0, every = 2)
        val intake = series(28, start = 2400.0, perDay = 0.0)
        val tdee = BodyAnalyst.analyse(weights, intake, today, Goal.MAINTAIN, null, null, planTdee = 2600).tdee!!

        assertEquals(2400, tdee.kcal)
        assertEquals(2600, tdee.planKcal)
        assertEquals(Confidence.HIGH, tdee.confidence)
    }

    @Test
    fun `losing weight on an intake means spending more than it`() {
        val weights = series(28, start = 85.0, perDay = -0.1)
        val intake = series(28, start = 2000.0, perDay = 0.0)
        val tdee = BodyAnalyst.analyse(weights, intake, today, Goal.LOSE, 75.0, 1.5, null).tdee!!

        assertTrue("a deficit means spending above intake", tdee.kcal > 2000)
    }

    @Test
    fun `half-logged days are not counted as intake`() {
        val weights = series(28, start = 80.0, perDay = 0.0, every = 2)
        val sparse = series(28, start = 600.0, perDay = 0.0)
        assertNull(BodyAnalyst.analyse(weights, sparse, today, null, null, null, null).tdee)
    }
}

class NutritionAnalystTest {

    private val monday = LocalDate.of(2026, 9, 7)
    private val targets = NutritionTargets(kcal = 2000.0, proteinG = 150.0, carbsG = 200.0, fatG = 60.0)

    private fun day(offset: Int, kcal: Double, protein: Double = 150.0, fibre: Double? = 20.0) = DayNutrition(
        date = monday.plusDays(offset.toLong()),
        kcal = kcal,
        proteinG = protein,
        carbsG = 200.0,
        fatG = 60.0,
        fibreG = fibre,
        sugarG = 40.0,
        sodiumMg = 2000.0,
    )

    private fun analyse(days: List<DayNutrition>, training: Set<LocalDate> = emptySet()) =
        NutritionAnalyst.analyse(days, targets, bodyweightKg = 75.0, mealKcal = emptyMap(), foods = emptyList(), trainingDays = training)

    @Test
    fun `hit rates count only full days`() {
        val report = analyse(listOf(day(0, 2000.0), day(1, 2500.0), day(2, 400.0)))

        assertEquals(2, report.fullDays)
        assertEquals(50, report.hitRates.first { it.label == "Calories" }.percent)
    }

    @Test
    fun `protein is a floor, and read per kilo of bodyweight`() {
        val report = analyse(listOf(day(0, 2000.0, protein = 140.0), day(1, 2000.0, protein = 100.0)))

        assertEquals(50, report.hitRates.first { it.label == "Protein" }.percent)
        assertEquals(120.0 / 75.0, report.proteinPerKg!!, 1e-9)
    }

    @Test
    fun `weekends are compared with weekdays once each side has three days`() {
        val days = (0..13).map { day(it, if ((monday.plusDays(it.toLong()).dayOfWeek.value) >= 6) 2600.0 else 2000.0) }
        val split = analyse(days).weekdayVsWeekend!!

        assertEquals(2000.0, split.first, 1e-9)
        assertEquals(2600.0, split.second, 1e-9)
    }

    @Test
    fun `training days are compared with rest days`() {
        val days = (0..7).map { day(it, if (it % 2 == 0) 2400.0 else 1900.0) }
        val training = days.filterIndexed { i, _ -> i % 2 == 0 }.map { it.date }.toSet()
        val split = analyse(days, training).trainingVsRestKcal!!

        assertEquals(2400.0, split.first, 1e-9)
        assertEquals(1900.0, split.second, 1e-9)
    }

    @Test
    fun `fibre is judged against the target density and unknown days are left out`() {
        val report = analyse(listOf(day(0, 2000.0, fibre = 10.0), day(1, 2000.0, fibre = null)))
        val fibre = report.micros.first { it.label == "Fibre" }

        assertEquals(10.0, fibre.average, 1e-9)
        assertEquals(28.0, fibre.target!!, 1e-9)
        assertEquals(1, fibre.days)
    }

    @Test
    fun `meal shares average each day's split and top foods rank by calories`() {
        val report = NutritionAnalyst.analyse(
            days = listOf(day(0, 2000.0)),
            targets = targets,
            bodyweightKg = null,
            mealKcal = mapOf(monday to mapOf(MealType.LUNCH to 1000.0, MealType.DINNER to 1000.0)),
            foods = listOf(FoodTotal("Rice", 300.0, 3), FoodTotal("Paneer", 700.0, 2)),
            trainingDays = emptySet(),
        )
        assertEquals(0.5, report.mealShares.getValue(MealType.LUNCH), 1e-9)
        assertEquals("Paneer", report.topFoods.first().name)
        // 700 of the 2,000 eaten that day, not of the 1,000 the two listed foods add up to.
        assertEquals(0.35, report.topFoods.first().share, 1e-9)
    }
}

class StrengthAnalystTest {

    private val today = LocalDate.of(2026, 9, 30)
    private var session = 0L

    private fun sets(daysAgo: Int, weight: Double, name: String = "Bench Press", id: Long = 1, count: Int = 3) = List(count) {
        HistorySet(
            sessionId = daysAgo.toLong() * 100 + id,
            startedAt = today.minusDays(daysAgo.toLong()).atTime(18, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            exerciseId = id,
            exerciseName = name,
            muscleGroup = "CHEST",
            primaryMuscles = listOf("chest"),
            secondaryMuscles = listOf("triceps"),
            force = "push",
            mechanic = "compound",
            equipment = "BARBELL",
            reps = 5,
            weightKg = weight,
            rpe = null,
            durationSec = null,
        )
    }

    private fun analyse(sets: List<HistorySet>, protein: Map<LocalDate, Double> = emptyMap()) =
        StrengthAnalyst.analyse(sets, today, protein, ZoneOffset.UTC)

    @Test
    fun `a lift's line is its best estimated max per session`() {
        val report = analyse(sets(21, 80.0) + sets(14, 85.0) + sets(7, 90.0))
        val bench = report.lifts.single()

        assertEquals(3, bench.points.size)
        assertEquals(OneRepMaxProbe.epley(90.0, 5), bench.best, 1e-9)
        assertEquals(OneRepMaxProbe.epley(90.0, 5) / OneRepMaxProbe.epley(80.0, 5) - 1, bench.changeFraction!!, 1e-9)
    }

    @Test
    fun `the first session is a baseline and later beats are personal bests`() {
        val report = analyse(sets(21, 80.0) + sets(14, 80.0) + sets(7, 90.0))
        assertEquals(1, report.personalBests.size)
        assertEquals(today.minusDays(7), report.personalBests.single().date)
    }

    @Test
    fun `weekly sets per muscle use the shared counting rule`() {
        val report = analyse(sets(0, 80.0, count = 4))
        val chest = report.muscleWeeks.first { it.muscle == Muscle.CHEST }
        val triceps = report.muscleWeeks.first { it.muscle == Muscle.TRICEPS }

        assertEquals(StrengthAnalyst.WEEKS, chest.sets.size)
        assertEquals(4.0, chest.sets.last(), 1e-9)
        assertEquals(2.0, triceps.sets.last(), 1e-9)
    }

    @Test
    fun `consistency counts sessions and the longest gap`() {
        val report = analyse(sets(20, 80.0) + sets(10, 80.0) + sets(9, 80.0))
        assertEquals(10, report.longestGapDays)
        assertEquals(3.0 / StrengthAnalyst.WEEKS, report.sessionsPerWeek, 1e-9)
    }

    @Test
    fun `protein and strength are only compared with enough weeks`() {
        val few = analyse(sets(14, 80.0) + sets(7, 85.0), protein = (0..20).associate { today.minusDays(it.toLong()) to 150.0 })
        assertNull(few.proteinVsStrength)

        // Eight weekly sessions, heavier weeks after more protein.
        val history = (0 until 8).flatMap { week -> sets(56 - week * 7, 80.0 + week * (if (week % 2 == 0) 3 else 1)) }
        val protein = (0..60).associate { d ->
            val date = today.minusDays(d.toLong())
            date to 120.0 + (d % 14)
        }
        assertNotNull(analyse(history, protein).proteinVsStrength)
    }
}

/** Epley, restated so the test does not lean on the code it is checking. */
private object OneRepMaxProbe {
    fun epley(weight: Double, reps: Int) = weight * (1 + reps / 30.0)
}
