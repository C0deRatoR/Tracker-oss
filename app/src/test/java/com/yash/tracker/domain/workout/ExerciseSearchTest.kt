package com.yash.tracker.domain.workout

import com.yash.tracker.data.local.entity.ExerciseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseSearchTest {

    private fun exercise(
        id: Long,
        name: String,
        muscle: String = "ARMS",
        equipment: String = "BARBELL",
        type: String = "STRENGTH",
    ) = ExerciseEntity(
        id = id,
        name = name,
        muscleGroup = muscle,
        equipment = equipment,
        type = type,
        notes = null,
    )

    private val catalogue = listOf(
        exercise(1, "Barbell Curl", muscle = "ARMS", equipment = "BARBELL"),
        exercise(2, "Alternating Hammer Curl With Rotation", muscle = "ARMS", equipment = "DUMBBELL"),
        exercise(3, "Cable Curl", muscle = "ARMS", equipment = "CABLE"),
        exercise(4, "Barbell Back Squat", muscle = "LEGS", equipment = "BARBELL"),
        exercise(5, "Push-Up", muscle = "CHEST", equipment = "BODYWEIGHT"),
        exercise(6, "Treadmill Run", muscle = "CARDIO", equipment = "MACHINE", type = "CARDIO"),
    )

    @Test
    fun `an empty filter keeps everything`() {
        val result = ExerciseSearch.apply(catalogue, ExerciseFilter())

        assertEquals(catalogue.size, result.size)
    }

    @Test
    fun `a name that starts with the query outranks one that merely contains it`() {
        val result = ExerciseSearch.apply(catalogue, ExerciseFilter(query = "curl"))

        assertEquals(
            listOf("Cable Curl", "Barbell Curl", "Alternating Hammer Curl With Rotation"),
            result.map { it.name },
        )
    }

    @Test
    fun `the shorter name wins when both start with the query`() {
        val rows = listOf(
            exercise(1, "Barbell Curl Against Incline Bench"),
            exercise(2, "Barbell Curl"),
        )

        val result = ExerciseSearch.apply(rows, ExerciseFilter(query = "barbell"))

        assertEquals(listOf("Barbell Curl", "Barbell Curl Against Incline Bench"), result.map { it.name })
    }

    @Test
    fun `every typed word has to appear, in any order`() {
        val result = ExerciseSearch.apply(catalogue, ExerciseFilter(query = "curl barbell"))

        assertEquals(listOf("Barbell Curl"), result.map { it.name })
    }

    @Test
    fun `filtering by muscle group narrows to that group`() {
        val result = ExerciseSearch.apply(
            catalogue,
            ExerciseFilter(muscles = setOf(MuscleGroup.LEGS)),
        )

        assertEquals(listOf("Barbell Back Squat"), result.map { it.name })
    }

    @Test
    fun `two equipment values are an or, not an and`() {
        val result = ExerciseSearch.apply(
            catalogue,
            ExerciseFilter(equipment = setOf(Equipment.CABLE, Equipment.BODYWEIGHT)),
        )

        assertEquals(setOf("Cable Curl", "Push-Up"), result.map { it.name }.toSet())
    }

    @Test
    fun `muscle and equipment filters intersect`() {
        val result = ExerciseSearch.apply(
            catalogue,
            ExerciseFilter(
                muscles = setOf(MuscleGroup.ARMS),
                equipment = setOf(Equipment.BARBELL),
            ),
        )

        assertEquals(listOf("Barbell Curl"), result.map { it.name })
    }

    @Test
    fun `a query combines with the filters rather than replacing them`() {
        val result = ExerciseSearch.apply(
            catalogue,
            ExerciseFilter(query = "curl", equipment = setOf(Equipment.DUMBBELL)),
        )

        assertEquals(listOf("Alternating Hammer Curl With Rotation"), result.map { it.name })
    }

    @Test
    fun `nothing matching is an empty list rather than everything`() {
        val result = ExerciseSearch.apply(catalogue, ExerciseFilter(query = "zercher"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `an alias is what the row matched on, and is reported as such`() {
        val butterfly = exercise(10, "Butterfly", muscle = "CHEST", equipment = "MACHINE")
            .copy(aliases = "pec dec\npec deck")

        assertEquals("pec dec", ExerciseSearch.matchedAlias(butterfly, "pec dec"))
    }

    @Test
    fun `a row that matched on its own name reports no alias`() {
        val butterfly = exercise(10, "Butterfly", muscle = "CHEST", equipment = "MACHINE")
            .copy(aliases = "pec dec")

        // Searching the real name must not claim the row was found under another one.
        assertEquals(null, ExerciseSearch.matchedAlias(butterfly, "butterfly"))
    }

    @Test
    fun `searching a gym name finds the movement it belongs to`() {
        val rows = listOf(
            exercise(10, "Butterfly", muscle = "CHEST", equipment = "MACHINE")
                .copy(aliases = "pec dec\npec deck"),
            exercise(11, "Barbell Curl"),
        )

        val result = ExerciseSearch.apply(rows, ExerciseFilter(query = "pec dec"))

        assertEquals(listOf("Butterfly"), result.map { it.name })
    }

    @Test
    fun `grouping puts every exercise under its muscle group`() {
        val sections = ExerciseSearch.groupByMuscle(catalogue)

        assertEquals(catalogue.size, sections.sumOf { it.second.size })
        assertEquals(
            listOf("Arms", "Cardio", "Chest", "Legs"),
            sections.map { it.first },
        )
    }

    @Test
    fun `an unknown stored value falls back rather than throwing`() {
        val odd = listOf(exercise(9, "Sled Push", muscle = "POSTERIOR_CHAIN", equipment = "SLED"))

        assertEquals("posterior chain", MuscleGroup.label(odd.first().muscleGroup))
        assertEquals("sled", Equipment.label(odd.first().equipment))
        // It has no known group, so a group filter must exclude it rather than match everything.
        assertTrue(
            ExerciseSearch.apply(odd, ExerciseFilter(muscles = setOf(MuscleGroup.LEGS))).isEmpty(),
        )
    }
}
