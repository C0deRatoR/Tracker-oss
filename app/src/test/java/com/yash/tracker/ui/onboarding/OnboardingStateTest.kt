package com.yash.tracker.ui.onboarding

import com.yash.tracker.domain.model.ActivityLevel
import com.yash.tracker.domain.model.EatingStyle
import com.yash.tracker.domain.model.Goal
import com.yash.tracker.domain.model.HeightUnit
import com.yash.tracker.domain.model.Pace
import com.yash.tracker.domain.model.Sex
import com.yash.tracker.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingStateTest {

    private fun complete() = OnboardingAnswers(
        name = "Yash",
        age = "27",
        sex = Sex.MALE,
        heightCmText = "178",
        weightText = "78",
        goal = Goal.LOSE,
        goalWeightText = "72",
        activity = ActivityLevel.MODERATELY,
        pace = Pace.STEADY,
        eatingStyle = EatingStyle.HIGH_PROTEIN,
    )

    @Test
    fun `goal weight is skipped entirely when maintaining`() {
        val maintaining = complete().copy(goal = Goal.MAINTAIN)

        assertEquals(
            OnboardingStep.ACTIVITY,
            Onboarding.next(OnboardingStep.GOAL, maintaining),
        )
        assertEquals(
            OnboardingStep.GOAL,
            Onboarding.previous(OnboardingStep.ACTIVITY, maintaining),
        )
    }

    @Test
    fun `goal weight is asked when losing`() {
        assertEquals(
            OnboardingStep.GOAL_WEIGHT,
            Onboarding.next(OnboardingStep.GOAL, complete()),
        )
    }

    @Test
    fun `the progress bar shrinks by one when goal weight is skipped`() {
        assertEquals(11, Onboarding.progress(OnboardingStep.NAME, complete()).second)
        assertEquals(
            10,
            Onboarding.progress(OnboardingStep.NAME, complete().copy(goal = Goal.MAINTAIN)).second,
        )
    }

    @Test
    fun `a goal weight above current weight is rejected when losing`() {
        val answers = complete().copy(goalWeightText = "85")
        assertNotNull(Onboarding.validate(OnboardingStep.GOAL_WEIGHT, answers))
    }

    @Test
    fun `a goal weight below current weight is rejected when gaining`() {
        val answers = complete().copy(goal = Goal.GAIN, goalWeightText = "70")
        assertNotNull(Onboarding.validate(OnboardingStep.GOAL_WEIGHT, answers))
    }

    @Test
    fun `a sensible goal weight passes`() {
        assertNull(Onboarding.validate(OnboardingStep.GOAL_WEIGHT, complete()))
    }

    @Test
    fun `age must be between 13 and 100`() {
        assertNotNull(Onboarding.validate(OnboardingStep.AGE, complete().copy(age = "11")))
        assertNotNull(Onboarding.validate(OnboardingStep.AGE, complete().copy(age = "120")))
        assertNotNull(Onboarding.validate(OnboardingStep.AGE, complete().copy(age = "")))
        assertNull(Onboarding.validate(OnboardingStep.AGE, complete().copy(age = "13")))
    }

    @Test
    fun `notes are optional`() {
        assertNull(Onboarding.validate(OnboardingStep.NOTES, complete().copy(notes = "")))
    }

    @Test
    fun `height entered in feet and inches converts to centimetres`() {
        val answers = complete().copy(
            heightUnit = HeightUnit.FT_IN,
            heightFeetText = "5",
            heightInchesText = "10",
        )
        assertEquals(177.8, answers.heightCm!!, 0.1)
        assertNull(Onboarding.validate(OnboardingStep.HEIGHT, answers))
    }

    @Test
    fun `weight entered in pounds converts to kilograms`() {
        val answers = complete().copy(weightUnit = WeightUnit.LB, weightText = "176.4")
        assertEquals(80.0, answers.weightKg!!, 0.05)
    }

    @Test
    fun `goal weight follows whichever weight unit was chosen`() {
        val answers = complete().copy(
            weightUnit = WeightUnit.LB,
            weightText = "176.4",
            goalWeightText = "160",
        )
        assertEquals(72.6, answers.goalWeightKg!!, 0.1)
        assertNull(Onboarding.validate(OnboardingStep.GOAL_WEIGHT, answers))
    }

    @Test
    fun `a complete set of answers becomes a plan input`() {
        val input = complete().toPlanInput()
        assertNotNull(input)
        assertEquals(Sex.MALE, input!!.sex)
        assertEquals(27, input.age)
        assertEquals(72.0, input.goalWeightKg!!, 0.01)
    }

    @Test
    fun `maintaining carries no goal weight into the plan`() {
        val input = complete().copy(goal = Goal.MAINTAIN).toPlanInput()
        assertNull(input!!.goalWeightKg)
    }

    @Test
    fun `an incomplete set of answers yields no plan input`() {
        assertNull(complete().copy(sex = null).toPlanInput())
        assertNull(complete().copy(weightText = "").toPlanInput())
    }
}
