package com.yash.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.domain.workout.Trend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * The half of the analysis that is SQL: finding what an exercise did *before* this session,
 * rather than what it did in it. Getting that wrong compares every lift against itself and
 * reports a life of perfect plateau.
 */
@RunWith(RobolectricTestRunner::class)
class SessionAnalysisRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var workouts: WorkoutRepository

    private val monday = LocalDate.of(2026, 9, 14)
    private var squatId = 0L
    private var pullUpId = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        val profiles = ProfileRepository(db.profileDao(), db.weightDao(), Dispatchers.Unconfined)
        workouts = WorkoutRepository(db.workoutDao(), profiles, Dispatchers.Unconfined)

        squatId = db.workoutDao().insertExercise(
            ExerciseEntity(
                name = "Barbell Back Squat",
                muscleGroup = "LEGS",
                equipment = "BARBELL",
                type = "STRENGTH",
                notes = null,
            ),
        )
        pullUpId = db.workoutDao().insertExercise(
            ExerciseEntity(
                name = "Pull Up",
                muscleGroup = "BACK",
                equipment = "BODYWEIGHT",
                type = "STRENGTH",
                notes = null,
            ),
        )
    }

    @After
    fun tearDown() = db.close()

    /**
     * Runs a session and pins when it started, so the ordering the queries depend on cannot
     * come down to how fast the test machine got through the previous one.
     */
    private suspend fun perform(
        routineId: Long,
        date: LocalDate,
        weightKg: Double?,
        reps: Int,
        startedAt: Long,
    ): Long {
        val sessionId = workouts.startSession(routineId, date)
        val loaded = db.workoutDao().session(sessionId)!!
        db.workoutDao().updateSession(loaded.session.copy(startedAt = startedAt))

        loaded.sets.forEach { set ->
            workouts.updateSet(set.copy(weightKg = weightKg, reps = reps, isCompleted = true))
        }
        workouts.finishSession(sessionId)
        return sessionId
    }

    @Test
    fun `a lift is compared against the session before, not against itself`() = runTest {
        val routineId = workouts.createRoutine("Legs", listOf(squatId))
        perform(routineId, monday, weightKg = 80.0, reps = 5, startedAt = 1_000L)
        val second = perform(routineId, monday.plusDays(3), weightKg = 90.0, reps = 5, startedAt = 2_000L)

        val analysis = workouts.analyse(second)!!
        val squat = analysis.trends.single { it.exerciseId == squatId }

        assertEquals(Trend.UP, squat.trend)
        assertTrue("90 kg over 80 kg is an eighth more", squat.changeFraction!! > 0.1)
    }

    @Test
    fun `the first ever session has nothing behind it`() = runTest {
        val routineId = workouts.createRoutine("Legs", listOf(squatId))
        val first = perform(routineId, monday, weightKg = 80.0, reps = 5, startedAt = 1_000L)

        val analysis = workouts.analyse(first)!!
        assertEquals(Trend.FIRST, analysis.trends.single().trend)
        assertNull(analysis.change)
    }

    @Test
    fun `a session is compared with the last one of the same name`() = runTest {
        val legs = workouts.createRoutine("Legs", listOf(squatId))
        val back = workouts.createRoutine("Back", listOf(pullUpId))

        perform(legs, monday, weightKg = 80.0, reps = 5, startedAt = 1_000L)
        // A different routine in between must not become the thing "last time" refers to.
        perform(back, monday.plusDays(1), weightKg = null, reps = 8, startedAt = 2_000L)
        val second = perform(legs, monday.plusDays(3), weightKg = 100.0, reps = 5, startedAt = 3_000L)

        val change = workouts.analyse(second)!!.change!!
        assertEquals(3 * 100.0 * 5, change.volumeKg, 0.01)
        assertEquals(3 * 80.0 * 5, change.previousVolumeKg, 0.01)
    }

    @Test
    fun `a bodyweight session still reports where the work went`() = runTest {
        val routineId = workouts.createRoutine("Back", listOf(pullUpId))
        val sessionId = perform(routineId, monday, weightKg = null, reps = 8, startedAt = 1_000L)

        val analysis = workouts.analyse(sessionId)!!
        val back = analysis.muscleSplit.single()

        assertEquals("BACK", back.group)
        assertEquals(3, back.workingSets)
        assertEquals("no load to add up", 0.0, back.volumeKg, 0.001)
    }

    @Test
    fun `analysing a session that does not exist returns nothing`() = runTest {
        assertNull(workouts.analyse(999L))
    }
}
