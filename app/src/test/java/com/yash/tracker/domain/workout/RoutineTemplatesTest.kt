package com.yash.tracker.domain.workout

import com.yash.tracker.domain.model.ActivityLevel
import com.yash.tracker.domain.model.Goal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoutineTemplatesTest {

    private fun plans(age: Int = 27, goal: Goal = Goal.MAINTAIN, activity: ActivityLevel = ActivityLevel.MODERATELY) =
        RoutineTemplates.plansFor(TemplateProfile(age, goal, activity))

    @Test
    fun `how active someone is picks the split`() {
        assertEquals(Split.FULL_BODY, plans(activity = ActivityLevel.LIGHTLY).first().split)
        assertEquals(Split.UPPER_LOWER, plans(activity = ActivityLevel.MODERATELY).first().split)
        assertEquals(Split.PUSH_PULL_LEGS, plans(activity = ActivityLevel.VERY).first().split)
    }

    @Test
    fun `only the recommended plan is marked and explained, and it comes first`() {
        val all = plans()
        assertEquals(Split.entries.size, all.size)
        assertTrue(all.first().isRecommended && all.first().reason != null)
        assertTrue(all.drop(1).none { it.isRecommended || it.reason != null })
    }

    @Test
    fun `a surplus carries more sets than a deficit`() {
        val gain = plans(goal = Goal.GAIN).first().routines.first().exercises.first()
        val lose = plans(goal = Goal.LOSE).first().routines.first().exercises.first()
        assertTrue(gain.sets > lose.sets)
    }

    @Test
    fun `older or sedentary starts get the gentler lifts`() {
        val names = { age: Int -> plans(age = age).flatMap { it.routines }.flatMap { it.exercises }.map { it.name } }
        assertTrue("Bench Press (Barbell)" in names(27))
        assertTrue("Bench Press (Barbell)" !in names(55))
        assertTrue("Chest Press (Machine)" in names(55))
    }

    @Test
    fun `push pull legs has the three days`() {
        val ppl = plans(activity = ActivityLevel.ATHLETE).first()
        assertEquals(listOf("Push", "Pull", "Legs"), ppl.routines.map { it.name })
    }

    @Test
    fun `every template exercise is in the shipped catalogue`() {
        val seed = Json.parseToJsonElement(File("src/main/assets/seed/exercises.json").readText()).jsonObject
        val catalogue = seed.values.first { runCatching { it.jsonArray }.isSuccess }.jsonArray
            .map { it.jsonObject.getValue("name").jsonPrimitive.content }
            .toSet()

        val used = listOf(27, 60).flatMap { age ->
            Goal.entries.flatMap { goal -> plans(age = age, goal = goal) }
        }.flatMap { it.routines }.flatMap { it.exercises }.map { it.name }.toSet()

        assertEquals(emptySet<String>(), used - catalogue)
    }
}
