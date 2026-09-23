package com.yash.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.domain.workout.NextWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
 * The query behind "what next": which groups have been worked, when, and how much lately.
 *
 * Time is passed in rather than read from the clock, so a test about a four-day gap is not
 * really a test about what time the suite happened to run.
 */
@RunWith(RobolectricTestRunner::class)
class NextWorkoutRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var workouts: WorkoutRepository

    private val day = 24L * 60 * 60 * 1000
    private val now = 30 * day
    private val monday = LocalDate.of(2026, 9, 14)

    private var benchId = 0L
    private var rowId = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        val profiles = ProfileRepository(db.profileDao(), db.weightDao(), Dispatchers.Unconfined)
        workouts = WorkoutRepository(db.workoutDao(), profiles, Dispatchers.Unconfined)

        benchId = db.workoutDao().insertExercise(exercise("Barbell Bench Press", "CHEST"))
        rowId = db.workoutDao().insertExercise(exercise("Barbell Row", "BACK"))
        // Compounds the user has never done, for the untrained-group fallback. The machine is
        // the easier of the two, so only an ordering that leads on equipment gets this right.
        db.workoutDao().insertExercise(
            exercise("Barbell Back Squat", "LEGS")
                .copy(mechanic = "compound", level = "intermediate"),
        )
        db.workoutDao().insertExercise(
            exercise("Leg Press", "LEGS")
                .copy(equipment = "MACHINE", mechanic = "compound", level = "beginner"),
        )
    }

    @After
    fun tearDown() = db.close()

    private fun exercise(name: String, group: String) = ExerciseEntity(
        name = name,
        muscleGroup = group,
        equipment = "BARBELL",
        type = "STRENGTH",
        notes = null,
    )

    private suspend fun perform(exerciseId: Long, startedAt: Long, sets: Int = 3) {
        val routineId = workouts.createRoutine("Session $startedAt", listOf(exerciseId))
        val sessionId = workouts.startSession(routineId, monday)
        val loaded = db.workoutDao().session(sessionId)!!
        db.workoutDao().updateSession(loaded.session.copy(startedAt = startedAt))

        loaded.sets.take(sets).forEach { set ->
            workouts.updateSet(set.copy(weightKg = 60.0, reps = 8, isCompleted = true))
        }
        workouts.finishSession(sessionId)
    }

    @Test
    fun `an untrained group comes first and is offered the catalogue's compounds`() = runTest {
        perform(benchId, startedAt = now - 3 * day)
        perform(rowId, startedAt = now - 3 * day)

        val advice = workouts.observeNextWorkout(now).first()
        val due = advice.plan as NextWorkout.Train

        assertEquals(0, due.setsLastWeek)
        assertNull("nothing has ever been logged for it", due.daysSince)
        assertEquals("LEGS", due.group)
        assertEquals(
            "never trained, so the catalogue starts you on the barbell rather than the machine",
            listOf("Barbell Back Squat", "Leg Press"),
            advice.movements.map { it.name },
        )
        assertEquals(listOf("SHOULDERS", "ARMS"), due.alsoDue)
    }

    @Test
    fun `a group worked today is not suggested`() = runTest {
        // Everything trained inside the recovery window except back, which is four days rested.
        listOf("CHEST", "LEGS", "SHOULDERS", "ARMS", "CORE").forEach { group ->
            val id = db.workoutDao().insertExercise(exercise("Move $group", group))
            perform(id, startedAt = now)
        }
        perform(rowId, startedAt = now - 4 * day)

        val due = workouts.observeNextWorkout(now).first().plan as NextWorkout.Train
        assertEquals("BACK", due.group)
        assertEquals(4, due.daysSince)
    }

    @Test
    fun `everything worked today means recovering, not a suggestion`() = runTest {
        listOf("CHEST", "BACK", "LEGS", "SHOULDERS", "ARMS", "CORE").forEach { group ->
            val id = db.workoutDao().insertExercise(exercise("Move $group", group))
            perform(id, startedAt = now)
        }

        assertEquals(
            NextWorkout.Recovering(readyInDays = 2),
            workouts.observeNextWorkout(now).first().plan,
        )
    }

    @Test
    fun `nothing logged means nothing to say`() = runTest {
        assertEquals(NextWorkout.NoHistory, workouts.observeNextWorkout(now).first().plan)
    }

    @Test
    fun `the movements offered are the ones this user actually trains`() = runTest {
        val flyId = db.workoutDao().insertExercise(exercise("Cable Fly", "CHEST"))
        perform(benchId, startedAt = now - 9 * day)
        perform(flyId, startedAt = now - 9 * day, sets = 1)
        // Every other group worked recently, so chest is the one that comes due.
        listOf("BACK", "LEGS", "SHOULDERS", "ARMS", "CORE").forEach { group ->
            val id = db.workoutDao().insertExercise(exercise("Move $group", group))
            perform(id, startedAt = now - 3 * day)
        }

        val advice = workouts.observeNextWorkout(now).first()
        assertEquals("CHEST", (advice.plan as NextWorkout.Train).group)
        assertEquals(
            "most-used first, and nothing from another group",
            listOf("Barbell Bench Press", "Cable Fly"),
            advice.movements.map { it.name },
        )
    }
}
