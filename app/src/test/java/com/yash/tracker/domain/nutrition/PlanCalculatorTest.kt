package com.yash.tracker.domain.nutrition

import com.yash.tracker.domain.model.ActivityLevel
import com.yash.tracker.domain.model.EatingStyle
import com.yash.tracker.domain.model.Goal
import com.yash.tracker.domain.model.Pace
import com.yash.tracker.domain.model.PlanInput
import com.yash.tracker.domain.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The assertions in TRD §6 are the acceptance test for the plan math. */
class PlanCalculatorTest {

    private fun input(
        sex: Sex = Sex.MALE,
        age: Int = 30,
        heightCm: Double = 180.0,
        weightKg: Double = 80.0,
        goal: Goal = Goal.MAINTAIN,
        goalWeightKg: Double? = null,
        activity: ActivityLevel = ActivityLevel.MODERATELY,
        pace: Pace = Pace.STEADY,
        eatingStyle: EatingStyle = EatingStyle.NONE,
    ) = PlanInput(sex, age, heightCm, weightKg, goal, goalWeightKg, activity, pace, eatingStyle)

    @Test
    fun `Mifflin-St Jeor for a male`() {
        assertEquals(1780, PlanCalculator.calculate(input()).bmr)
    }

    @Test
    fun `Mifflin-St Jeor for a female`() {
        val plan = PlanCalculator.calculate(
            input(sex = Sex.FEMALE, heightCm = 165.0, weightKg = 60.0),
        )
        assertEquals(1320, plan.bmr)
    }

    @Test
    fun `TDEE applies the activity multiplier`() {
        assertEquals(2759, PlanCalculator.calculate(input()).tdee)
    }

    @Test
    fun `losing at a steady pace takes a 500 kcal deficit and predicts a pound a week`() {
        val plan = PlanCalculator.calculate(
            input(goal = Goal.LOSE, goalWeightKg = 72.0, pace = Pace.STEADY),
        )

        // 2759 − 500 = 2259, rounded to the nearest 10 per PRD §5.2.
        assertEquals(2260, plan.kcal)
        assertEquals(499, plan.tdee - plan.kcal)
        assertEquals(1.0, plan.rateLbPerWeek!!, 0.05)
        assertTrue(!plan.deficitCapped)
        assertTrue(!plan.floorApplied)
    }

    @Test
    fun `an aggressive deficit is capped at 5 kcal per pound of bodyweight`() {
        val plan = PlanCalculator.calculate(
            input(
                sex = Sex.FEMALE,
                heightCm = 160.0,
                weightKg = 50.0,
                goal = Goal.LOSE,
                goalWeightKg = 45.0,
                activity = ActivityLevel.VERY,
                pace = Pace.AGGRESSIVE,
            ),
        )

        assertTrue("deficit should be capped", plan.deficitCapped)
        assertTrue("floor should not be what bit here", !plan.floorApplied)

        // 50 kg is 110.23 lb, so the cap is 551 rather than a round 550, and rounding the
        // target to the nearest 10 moves the effective deficit by up to 5 either way.
        val effectiveDeficit = plan.tdee - plan.kcal
        assertTrue(
            "expected a capped deficit near 551, was $effectiveDeficit",
            effectiveDeficit in 546..556,
        )
    }

    @Test
    fun `a small female on an aggressive pace is floored at 1200 kcal`() {
        val plan = PlanCalculator.calculate(
            input(
                sex = Sex.FEMALE,
                heightCm = 150.0,
                weightKg = 45.0,
                goal = Goal.LOSE,
                goalWeightKg = 42.0,
                activity = ActivityLevel.SEDENTARY,
                pace = Pace.AGGRESSIVE,
            ),
        )

        assertEquals(1200, plan.kcal)
        assertTrue("floor should be applied", plan.floorApplied)
    }

    @Test
    fun `a 400 kcal per day gap predicts between 0_7 and 0_9 lb per week`() {
        // Gain at an aggressive pace is a clean 400 kcal/day gap; the timeline arithmetic is
        // the same in both directions. PRD §5.2 requires this to land near 0.8.
        val plan = PlanCalculator.calculate(
            input(goal = Goal.GAIN, goalWeightKg = 88.0, pace = Pace.AGGRESSIVE),
        )

        // 2759 + 400 = 3159, which rounds to 3160, so the stored gap is 401.
        val gap = plan.kcal - plan.tdee
        assertTrue("expected a ~400 kcal gap, was $gap", gap in 396..404)

        val rate = plan.rateLbPerWeek!!
        assertTrue("expected 0.7–0.9 lb/week, was $rate", rate in 0.7..0.9)
    }

    @Test
    fun `maintaining emits no timeline`() {
        val plan = PlanCalculator.calculate(input(goal = Goal.MAINTAIN))

        // Maintenance targets TDEE, still rounded to the nearest 10: 2759 becomes 2760.
        assertEquals(2759, plan.tdee)
        assertEquals(2760, plan.kcal)
        assertNull(plan.weeksToGoal)
        assertNull(plan.rateLbPerWeek)
    }

    @Test
    fun `keto pins carbs at 30g and lets fat absorb the remainder`() {
        val baseline = PlanCalculator.calculate(input())
        val plan = PlanCalculator.calculate(input(eatingStyle = EatingStyle.KETO))

        assertEquals(30.0, plan.carbsG, 0.001)
        assertEquals("protein must be untouched", baseline.proteinG, plan.proteinG, 0.001)
        assertCaloriesAddUp(plan.kcal, plan.proteinG, plan.carbsG, plan.fatG)
    }

    @Test
    fun `low-carb caps carbs at 100g`() {
        val plan = PlanCalculator.calculate(input(eatingStyle = EatingStyle.LOW_CARB))

        assertTrue("expected carbs <= 100, was ${plan.carbsG}", plan.carbsG <= 100.0)
        assertCaloriesAddUp(plan.kcal, plan.proteinG, plan.carbsG, plan.fatG)
    }

    @Test
    fun `the carb floor holds at 60g and is paid for out of fat, not protein`() {
        val heavyOnABigDeficit = input(
            weightKg = 120.0,
            goal = Goal.LOSE,
            goalWeightKg = 95.0,
            activity = ActivityLevel.SEDENTARY,
            pace = Pace.AGGRESSIVE,
        )
        val plan = PlanCalculator.calculate(heavyOnABigDeficit)

        assertEquals(60.0, plan.carbsG, 0.001)

        // Protein stays at 1.0 g/lb for a weight-loss goal, untouched by the floor.
        val expectedProtein = 120.0 * 2.20462262
        assertEquals(expectedProtein, plan.proteinG, 0.1)
        assertCaloriesAddUp(plan.kcal, plan.proteinG, plan.carbsG, plan.fatG)
    }

    @Test
    fun `water scales with bodyweight and activity`() {
        val sedentary = PlanCalculator.calculate(input(activity = ActivityLevel.SEDENTARY))
        val athlete = PlanCalculator.calculate(input(activity = ActivityLevel.ATHLETE))

        // 80 kg is 176.4 lb; 0.55 oz/lb is 97 fl oz, about 2.87 L.
        assertEquals(2868.0, sedentary.waterMl.toDouble(), 20.0)
        assertTrue(athlete.waterMl > sedentary.waterMl)
    }

    private fun assertCaloriesAddUp(kcal: Int, protein: Double, carbs: Double, fat: Double) {
        val fromMacros = protein * 4 + carbs * 4 + fat * 9
        assertEquals(
            "macros should account for the calorie target",
            kcal.toDouble(),
            fromMacros,
            12.0,
        )
    }
}
