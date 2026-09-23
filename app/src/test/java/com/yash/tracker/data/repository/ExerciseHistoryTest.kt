package com.yash.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.entity.ExerciseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * The exercise detail screen asks one question the rest of the app never has: "show me every
 * session that included *this* movement". These cover what that query has to get right.
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseHistoryTest {

    private lateinit var db: AppDatabase
    private lateinit var workouts: WorkoutRepository
    private lateinit var profiles: ProfileRepository

    private var bench = 0L
    private var squat = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        profiles = ProfileRepository(db.profileDao(), db.weightDao(), Dispatchers.Unconfined)
        workouts = WorkoutRepository(db.workoutDao(), profiles, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedExercises() {
        bench = db.workoutDao().insertExercise(
            ExerciseEntity(
                name = "Barbell Bench Press",
                muscleGroup = "CHEST",
                equipment = "BARBELL",
                type = "STRENGTH",
                notes = null,
            ),
        )
        squat = db.workoutDao().insertExercise(
            ExerciseEntity(
                name = "Barbell Back Squat",
                muscleGroup = "LEGS",
                equipment = "BARBELL",
                type = "STRENGTH",
                notes = null,
            ),
        )
    }

    /** A finished session containing one completed set of each exercise given. */
    private suspend fun finishedSession(vararg exerciseIds: Long): Long {
        val sessionId = workouts.startSession(routineId = null, date = LocalDate.now())
        exerciseIds.forEach { id ->
            workouts.addExerciseToSession(sessionId, id)
        }
        val session = workouts.session(sessionId)!!
        session.sets.forEach { set ->
            workouts.updateSet(set.copy(weightKg = 60.0, reps = 8, isCompleted = true))
        }
        workouts.finishSession(sessionId)
        return sessionId
    }

    @Test
    fun `history returns only the sessions that included the exercise`() = runTest {
        seedExercises()
        finishedSession(bench)
        finishedSession(squat)
        finishedSession(bench, squat)

        val benchHistory = workouts.observeHistoryFor(bench).first()

        assertEquals(2, benchHistory.size)
    }

    @Test
    fun `a session's other exercises are not counted as this one's sets`() = runTest {
        seedExercises()
        finishedSession(bench, squat)

        val sets = workouts.observeHistoryFor(bench).first().single().sets

        // The query returns whole sessions; the screen filters. Both exercises are present here,
        // which is exactly why the filter in ExerciseDetailViewModel has to exist.
        assertTrue(sets.any { it.exerciseId == bench })
        assertEquals(1, sets.count { it.exerciseId == bench })
    }

    @Test
    fun `an unfinished session is not history yet`() = runTest {
        seedExercises()
        val sessionId = workouts.startSession(routineId = null, date = LocalDate.now())
        workouts.addExerciseToSession(sessionId, bench)

        assertTrue(workouts.observeHistoryFor(bench).first().isEmpty())
    }

    @Test
    fun `the completed set count ignores sets that were never ticked`() = runTest {
        seedExercises()
        val sessionId = workouts.startSession(routineId = null, date = LocalDate.now())
        workouts.addExerciseToSession(sessionId, bench)
        workouts.addSet(sessionId, bench)

        val session = workouts.session(sessionId)!!
        workouts.updateSet(session.sets.first().copy(weightKg = 60.0, reps = 8, isCompleted = true))

        assertEquals(1, workouts.observeCompletedSetCount(bench).first())
    }

    @Test
    fun `a set ticked but never filled in does not count as performed`() = runTest {
        seedExercises()
        val sessionId = workouts.startSession(routineId = null, date = LocalDate.now())
        workouts.addExerciseToSession(sessionId, bench)

        // A routine prefills its rows, so this is what "finished the session without actually
        // doing that exercise" looks like in the database: completed, but with nothing in it.
        val set = workouts.session(sessionId)!!.sets.first()
        workouts.updateSet(set.copy(isCompleted = true, weightKg = null, reps = null))
        workouts.finishSession(sessionId)

        assertEquals(0, workouts.observeCompletedSetCount(bench).first())
    }

    @Test
    fun `a single rep with no weight still counts`() = runTest {
        seedExercises()
        val sessionId = workouts.startSession(routineId = null, date = LocalDate.now())
        workouts.addExerciseToSession(sessionId, bench)

        val set = workouts.session(sessionId)!!.sets.first()
        workouts.updateSet(set.copy(isCompleted = true, weightKg = null, reps = 12))
        workouts.finishSession(sessionId)

        // Bodyweight work has no weight, and it is still work.
        assertEquals(1, workouts.observeCompletedSetCount(bench).first())
    }

    @Test
    fun `an exercise never performed has no history and no sets`() = runTest {
        seedExercises()
        finishedSession(bench)

        assertTrue(workouts.observeHistoryFor(squat).first().isEmpty())
        assertEquals(0, workouts.observeCompletedSetCount(squat).first())
    }

    @Test
    fun `history is newest first`() = runTest {
        seedExercises()
        val first = finishedSession(bench)
        Thread.sleep(5)
        val second = finishedSession(bench)

        val ids = workouts.observeHistoryFor(bench).first().map { it.session.id }

        assertEquals(listOf(second, first), ids)
    }
}
