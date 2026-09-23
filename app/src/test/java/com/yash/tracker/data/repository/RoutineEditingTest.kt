package com.yash.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.domain.workout.TemplateExercise
import com.yash.tracker.domain.workout.TemplateRoutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Editing a routine rewrites its exercises wholesale, so order and identity both need covering. */
@RunWith(RobolectricTestRunner::class)
class RoutineEditingTest {

    private lateinit var db: AppDatabase
    private lateinit var workouts: WorkoutRepository

    private var bench = 0L
    private var press = 0L
    private var fly = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        workouts = WorkoutRepository(
            db.workoutDao(),
            ProfileRepository(db.profileDao(), db.weightDao(), Dispatchers.Unconfined),
            Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed() {
        bench = db.workoutDao().insertExercise(exercise("Bench Press"))
        press = db.workoutDao().insertExercise(exercise("Overhead Press"))
        fly = db.workoutDao().insertExercise(exercise("Cable Fly"))
    }

    private fun exercise(name: String) = ExerciseEntity(
        name = name,
        muscleGroup = "CHEST",
        equipment = "BARBELL",
        type = "STRENGTH",
        notes = null,
    )

    private suspend fun exerciseIdsOf(routineId: Long): List<Long> =
        db.workoutDao().routine(routineId)!!.exercises.sortedBy { it.position }.map { it.exerciseId }

    @Test
    fun `editing replaces the exercises and keeps the routine id`() = runTest {
        seed()
        val id = workouts.createRoutine("Push", listOf(bench, press))

        workouts.updateRoutine(id, "Push A", listOf(bench, fly))

        assertEquals(listOf(bench, fly), exerciseIdsOf(id))
        assertEquals("Push A", db.workoutDao().routine(id)!!.routine.name)
    }

    @Test
    fun `reordering is saved as the new positions`() = runTest {
        seed()
        val id = workouts.createRoutine("Push", listOf(bench, press, fly))

        workouts.updateRoutine(id, "Push", listOf(fly, bench, press))

        assertEquals(listOf(fly, bench, press), exerciseIdsOf(id))
    }

    @Test
    fun `editing does not leave the old rows behind`() = runTest {
        seed()
        val id = workouts.createRoutine("Push", listOf(bench, press, fly))

        workouts.updateRoutine(id, "Push", listOf(bench))

        assertEquals(1, db.workoutDao().routine(id)!!.exercises.size)
    }

    @Test
    fun `duplicating copies the exercises in order under a new id`() = runTest {
        seed()
        val id = workouts.createRoutine("Push", listOf(fly, bench, press))

        val copyId = workouts.duplicateRoutine(id)!!

        assertNotEquals(id, copyId)
        assertEquals(exerciseIdsOf(id), exerciseIdsOf(copyId))
        assertTrue(db.workoutDao().routine(copyId)!!.routine.name.startsWith("Push"))
    }

    @Test
    fun `editing a duplicate leaves the original alone`() = runTest {
        seed()
        val id = workouts.createRoutine("Push", listOf(bench, press))
        val copyId = workouts.duplicateRoutine(id)!!

        workouts.updateRoutine(copyId, "Push B", listOf(fly))

        assertEquals(listOf(bench, press), exerciseIdsOf(id))
        assertEquals(listOf(fly), exerciseIdsOf(copyId))
    }

    @Test
    fun `duplicating something that is not there returns null`() = runTest {
        assertEquals(null, workouts.duplicateRoutine(404))
    }

    @Test
    fun `the rest an exercise defaults to can be changed and is clamped`() = runTest {
        seed()

        workouts.setDefaultRest(bench, 240)
        assertEquals(240, workouts.exercise(bench)!!.defaultRestSec)

        workouts.setDefaultRest(bench, 0)
        assertEquals(REST_RANGE.first, workouts.exercise(bench)!!.defaultRestSec)

        workouts.setDefaultRest(bench, 9_999)
        assertEquals(REST_RANGE.last, workouts.exercise(bench)!!.defaultRestSec)
    }

    @Test
    fun `deleting a set renumbers the ones that remain`() = runTest {
        seed()
        val sessionId = workouts.startSession(routineId = null, date = java.time.LocalDate.now())
        workouts.addExerciseToSession(sessionId, bench)
        workouts.addSet(sessionId, bench)
        workouts.addSet(sessionId, bench)

        val sets = workouts.session(sessionId)!!.sets.sortedBy { it.setIndex }
        workouts.deleteSet(sets[1])

        val after = workouts.session(sessionId)!!.sets.sortedBy { it.setIndex }
        // No hole: set_index is the label and the key that pairs a row with last time's.
        assertEquals(List(after.size) { it }, after.map { it.setIndex })
    }

    @Test
    fun `deleting a set removes it from the session`() = runTest {
        seed()
        val sessionId = workouts.startSession(routineId = null, date = java.time.LocalDate.now())
        workouts.addExerciseToSession(sessionId, bench)
        workouts.addSet(sessionId, bench)

        val before = workouts.session(sessionId)!!.sets
        workouts.deleteSet(before.first())

        assertEquals(before.size - 1, workouts.session(sessionId)!!.sets.size)
    }

    @Test
    fun `a template becomes one routine per day with its targets, skipping unknown names`() = runTest {
        seed()
        val ids = workouts.addTemplate(
            listOf(
                TemplateRoutine(
                    "Push",
                    listOf(
                        TemplateExercise("bench press", sets = 4, repsLow = 6, repsHigh = 10),
                        TemplateExercise("Not In The Catalogue", sets = 3, repsLow = 8, repsHigh = 12),
                        TemplateExercise("Cable Fly", sets = 3, repsLow = 10, repsHigh = 15),
                    ),
                ),
            ),
        )

        val routine = db.workoutDao().routine(ids.single())!!
        assertEquals("Push", routine.routine.name)
        assertEquals(listOf(bench, fly), exerciseIdsOf(routine.routine.id))
        val first = routine.exercises.minBy { it.position }
        assertEquals(4, first.targetSets)
        assertEquals(6, first.targetRepsLow)
        assertEquals(10, first.targetRepsHigh)
    }
}
