package com.yash.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.domain.workout.RecordType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class WorkoutSessionTest {

    private lateinit var db: AppDatabase
    private lateinit var workouts: WorkoutRepository
    private lateinit var profiles: ProfileRepository

    private val monday = LocalDate.of(2026, 9, 14)
    private var squatId = 0L
    private var benchId = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        profiles = ProfileRepository(db.profileDao(), db.weightDao(), Dispatchers.Unconfined)
        workouts = WorkoutRepository(db.workoutDao(), profiles, Dispatchers.Unconfined)

        squatId = db.workoutDao().insertExercise(exercise("Barbell Back Squat", "LEGS"))
        benchId = db.workoutDao().insertExercise(exercise("Barbell Bench Press", "CHEST"))
    }

    @After
    fun tearDown() = db.close()

    private fun exercise(name: String, group: String) = ExerciseEntity(
        name = name,
        muscleGroup = group,
        equipment = "BARBELL",
        type = "STRENGTH",
        metValue = 6.0,
        defaultRestSec = 180,
        notes = null,
    )

    /** Runs a whole session: start, fill in each set, finish. */
    private suspend fun performSession(
        routineId: Long,
        date: LocalDate,
        weightKg: Double,
        reps: Int,
    ): FinishedSession? {
        val sessionId = workouts.startSession(routineId, date)
        db.workoutDao().session(sessionId)!!.sets.forEach { set ->
            workouts.updateSet(set.copy(weightKg = weightKg, reps = reps, isCompleted = true))
        }
        return workouts.finishSession(sessionId)
    }

    @Test
    fun `starting a routine lays out a set row per target set`() = runTest {
        val routineId = workouts.createRoutine("Legs", listOf(squatId))
        val sessionId = workouts.startSession(routineId, monday)

        val sets = db.workoutDao().session(sessionId)!!.sets
        assertEquals(3, sets.size)
        assertTrue("nothing is complete before the user says so", sets.none { it.isCompleted })
        assertEquals("Legs", db.workoutDao().session(sessionId)!!.session.name)
    }

    @Test
    fun `the next session prefills from the last one, so a repeat set is one tap`() = runTest {
        val routineId = workouts.createRoutine("Legs", listOf(squatId))
        performSession(routineId, monday, weightKg = 80.0, reps = 5)

        val nextId = workouts.startSession(routineId, monday.plusDays(2))
        val sets = db.workoutDao().session(nextId)!!.sets

        assertTrue("every row should arrive filled in", sets.all { it.weightKg == 80.0 && it.reps == 5 })
        assertTrue("but not yet done", sets.none { it.isCompleted })
    }

    @Test
    fun `an exercise never trained before starts blank`() = runTest {
        val routineId = workouts.createRoutine("Push", listOf(benchId))
        val sets = db.workoutDao().session(workouts.startSession(routineId, monday))!!.sets

        assertTrue(sets.all { it.weightKg == null && it.reps == null })
    }

    @Test
    fun `finishing records volume, completed sets and duration`() = runTest {
        val routineId = workouts.createRoutine("Legs", listOf(squatId))
        val finished = performSession(routineId, monday, weightKg = 80.0, reps = 5)!!

        assertEquals(3, finished.setsCompleted)
        assertEquals(3 * 80.0 * 5, finished.volumeKg, 0.01)
        assertTrue(finished.durationSec >= 0)
    }

    @Test
    fun `the first session of an exercise sets its records`() = runTest {
        val routineId = workouts.createRoutine("Legs", listOf(squatId))
        val finished = performSession(routineId, monday, weightKg = 80.0, reps = 5)!!

        val types = finished.newRecords.map { it.type }.toSet()
        assertTrue(RecordType.MAX_WEIGHT.name in types)
        assertTrue(RecordType.EST_1RM.name in types)
    }

    @Test
    fun `a first session breaks nothing, so the summary announces no PRs`() = runTest {
        val routineId = workouts.createRoutine("Legs", listOf(squatId))
        val finished = performSession(routineId, monday, weightKg = 80.0, reps = 5)!!

        assertTrue("baselines are stored", finished.newRecords.isNotEmpty())
        assertTrue(
            "but a baseline is not a record broken",
            workouts.brokenRecordsForSession(finished.sessionId).isEmpty(),
        )
    }

    @Test
    fun `beating a standing record is announced`() = runTest {
        val routineId = workouts.createRoutine("Legs", listOf(squatId))
        performSession(routineId, monday, weightKg = 80.0, reps = 5)

        val heavier = performSession(routineId, monday.plusDays(2), weightKg = 90.0, reps = 5)!!

        val broken = workouts.brokenRecordsForSession(heavier.sessionId)
        assertTrue(broken.any { it.type == RecordType.MAX_WEIGHT.name })
    }

    @Test
    fun `a lighter session afterwards sets no records`() = runTest {
        val routineId = workouts.createRoutine("Legs", listOf(squatId))
        performSession(routineId, monday, weightKg = 100.0, reps = 5)

        val lighter = performSession(routineId, monday.plusDays(2), weightKg = 60.0, reps = 5)!!

        assertTrue("beating nothing is not a record", lighter.newRecords.isEmpty())
    }

    @Test
    fun `going heavier sets a new record`() = runTest {
        val routineId = workouts.createRoutine("Legs", listOf(squatId))
        performSession(routineId, monday, weightKg = 80.0, reps = 5)

        val heavier = performSession(routineId, monday.plusDays(2), weightKg = 90.0, reps = 5)!!

        assertEquals(
            90.0,
            heavier.newRecords.first { it.type == RecordType.MAX_WEIGHT.name }.value,
            0.01,
        )
    }

    @Test
    fun `an unfinished session is offered for resuming`() = runTest {
        val routineId = workouts.createRoutine("Legs", listOf(squatId))
        val sessionId = workouts.startSession(routineId, monday)

        assertEquals(sessionId, workouts.activeSessionId())

        workouts.finishSession(sessionId)
        assertNull("a finished session is not resumable", workouts.activeSessionId())
    }

    @Test
    fun `discarding a session leaves nothing behind`() = runTest {
        val routineId = workouts.createRoutine("Legs", listOf(squatId))
        val sessionId = workouts.startSession(routineId, monday)

        workouts.discardSession(sessionId)

        assertNull(db.workoutDao().session(sessionId))
        assertTrue(workouts.observeRecentSessions().first().isEmpty())
    }

    @Test
    fun `adding a set copies the one before it`() = runTest {
        val routineId = workouts.createRoutine("Legs", listOf(squatId))
        val sessionId = workouts.startSession(routineId, monday)
        db.workoutDao().session(sessionId)!!.sets.forEach {
            workouts.updateSet(it.copy(weightKg = 70.0, reps = 8, isCompleted = true))
        }

        workouts.addSet(sessionId, squatId)

        val added = db.workoutDao().session(sessionId)!!.sets.maxByOrNull { it.setIndex }!!
        assertEquals(70.0, added.weightKg!!, 0.01)
        assertEquals(8, added.reps)
        assertTrue("a new row still needs confirming", !added.isCompleted)
    }

    @Test
    fun `an exercise can be added to a session in progress`() = runTest {
        val routineId = workouts.createRoutine("Legs", listOf(squatId))
        val sessionId = workouts.startSession(routineId, monday)

        workouts.addExerciseToSession(sessionId, benchId)

        val sets = db.workoutDao().session(sessionId)!!.sets
        assertTrue(sets.any { it.exerciseId == benchId })
    }

    @Test
    fun `finished sessions appear in history, newest first`() = runTest {
        val routineId = workouts.createRoutine("Legs", listOf(squatId))
        performSession(routineId, monday, 80.0, 5)
        performSession(routineId, monday.plusDays(2), 85.0, 5)

        val history = workouts.observeRecentSessions().first()
        assertEquals(2, history.size)
        assertTrue(history.all { it.session.isFinished })
        assertTrue(history.first().session.startedAt >= history.last().session.startedAt)
    }

    @Test
    fun `a routine remembers when it was last performed`() = runTest {
        val routineId = workouts.createRoutine("Legs", listOf(squatId))
        assertNull(workouts.routineLastPerformed(routineId))

        performSession(routineId, monday, 80.0, 5)

        assertNotNull(workouts.routineLastPerformed(routineId))
    }
}
