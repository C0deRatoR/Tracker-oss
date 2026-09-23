package com.yash.tracker.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule is rest first, then neglect. The tests that matter are the ones where those two
 * pull in different directions.
 */
class NextWorkoutTest {

    private fun standing(group: String, daysSince: Int?, sets: Int) =
        MuscleStanding(group, daysSince, sets)

    private fun train(plan: NextWorkout): NextWorkout.Train =
        plan as? NextWorkout.Train ?: error("expected a suggestion, got $plan")

    @Test
    fun `an empty history suggests nothing rather than guessing`() {
        assertEquals(NextWorkout.NoHistory, NextWorkoutPlanner.plan(emptyList()))
    }

    @Test
    fun `a group never trained outranks one merely rested`() {
        val plan = train(
            NextWorkoutPlanner.plan(
                listOf(
                    standing("CHEST", daysSince = 6, sets = 9),
                    standing("BACK", daysSince = 4, sets = 12),
                ),
            ),
        )

        assertTrue("legs, shoulders, arms and core are all untouched", plan.setsLastWeek == 0)
        assertEquals(null, plan.daysSince)
    }

    @Test
    fun `yesterday's muscle is not suggested however little it has had`() {
        val plan = train(
            NextWorkoutPlanner.plan(
                listOf(
                    standing("CHEST", daysSince = 0, sets = 3),
                    standing("BACK", daysSince = 4, sets = 20),
                    standing("LEGS", daysSince = 5, sets = 18),
                    standing("SHOULDERS", daysSince = 3, sets = 15),
                    standing("ARMS", daysSince = 3, sets = 14),
                    standing("CORE", daysSince = 3, sets = 10),
                ),
            ),
        )

        assertTrue("chest had the least work but is not recovered", plan.group != "CHEST")
        assertEquals("CORE", plan.group)
    }

    @Test
    fun `everything worked in the last two days means rest, with a date`() {
        val plan = NextWorkoutPlanner.plan(
            listOf(
                standing("CHEST", 0, 6),
                standing("BACK", 1, 6),
                standing("LEGS", 1, 6),
                standing("SHOULDERS", 0, 6),
                standing("ARMS", 1, 6),
                standing("CORE", 0, 6),
            ),
        )

        assertEquals(NextWorkout.Recovering(readyInDays = 2), plan)
    }

    @Test
    fun `among rested groups the least worked one wins`() {
        val plan = train(
            NextWorkoutPlanner.plan(
                listOf(
                    standing("CHEST", 3, 4),
                    standing("BACK", 9, 12),
                    standing("LEGS", 2, 18),
                    standing("SHOULDERS", 4, 9),
                    standing("ARMS", 3, 6),
                    standing("CORE", 2, 8),
                ),
            ),
        )

        assertEquals("CHEST", plan.group)
        assertTrue(plan.reason.contains("4 sets this week"))
    }

    @Test
    fun `groups with an equal claim are named rather than picked between`() {
        val plan = train(
            NextWorkoutPlanner.plan(
                listOf(
                    standing("CHEST", 3, 4),
                    standing("BACK", 5, 4),
                    standing("LEGS", 3, 20),
                    standing("SHOULDERS", 3, 20),
                    standing("ARMS", 3, 20),
                    standing("CORE", 3, 20),
                ),
            ),
        )

        // Longest rested of the two least-worked wins, and the other is named alongside it.
        assertEquals("BACK", plan.group)
        assertEquals(listOf("CHEST"), plan.alsoDue)
    }

    @Test
    fun `cardio is history, not a recommendation`() {
        val plan = train(
            NextWorkoutPlanner.plan(
                listOf(
                    standing("CARDIO", daysSince = 8, sets = 0),
                    standing("CHEST", daysSince = 3, sets = 2),
                    standing("BACK", daysSince = 3, sets = 9),
                    standing("LEGS", daysSince = 3, sets = 9),
                    standing("SHOULDERS", daysSince = 3, sets = 9),
                    standing("ARMS", daysSince = 3, sets = 9),
                    standing("CORE", daysSince = 3, sets = 9),
                ),
            ),
        )

        assertEquals("CHEST", plan.group)
    }
}
