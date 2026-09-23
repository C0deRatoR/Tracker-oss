package com.yash.tracker.data.local.seed

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.entity.AppStateEntity
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.data.local.entity.WorkoutSessionEntity
import com.yash.tracker.data.local.entity.WorkoutSetEntity
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

/**
 * Guards the seam between the tools/ scripts and the app: if a converter changes shape or an
 * asset goes missing, first launch breaks and nothing else would catch it.
 */
@RunWith(RobolectricTestRunner::class)
class SeedImporterTest {

    private lateinit var db: AppDatabase
    private lateinit var importer: SeedImporter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        importer = SeedImporter(context, db, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `import loads every bundled asset`() = runTest {
        val result = importer.import {}

        assertTrue("import failed: $result", result is SeedProgress.Finished)
        result as SeedProgress.Finished

        assertTrue("expected thousands of foods, got ${result.foods}", result.foods > 8000)
        assertTrue("expected a full exercise library, got ${result.exercises}", result.exercises > 200)
        assertTrue(importer.isSeeded())
    }

    @Test
    fun `an install seeded on an older library tops itself up`() = runTest {
        importer.import {}
        val full = db.exerciseDao().count()

        // Rewind to the state a phone seeded before the library grew would be in: 96 rows
        // and the old version number, with a food diary that must survive untouched.
        rewindToOldLibrary()
        val foodsBefore = db.foodDao().count()

        importer.import {}

        assertEquals(full, db.exerciseDao().count())
        assertEquals("the catalogue must not be touched", foodsBefore, db.foodDao().count())
    }

    @Test
    fun `the library carries muscles, force and mechanic`() = runTest {
        importer.import {}

        val bench = db.exerciseDao().getAll().first { it.name == "Bench Press (Barbell)" }

        assertEquals(listOf("chest"), bench.primaryMuscleList)
        assertTrue("triceps" in bench.secondaryMuscleList)
        assertEquals("push", bench.force)
        assertEquals("compound", bench.mechanic)

        val pulldown = db.exerciseDao().getAll().first { it.name == "Lat Pulldown (Cable)" }
        assertEquals("pull", pulldown.force)

        // The heading stays Strong's: free-exercise-db files the squat under quadriceps and
        // the deadlift under lower back, which is right as detail and wrong as a group.
        val deadlift = db.exerciseDao().getAll().first { it.name == "Deadlift (Barbell)" }
        assertEquals("BACK", deadlift.muscleGroup)
        assertEquals(listOf("lower back"), deadlift.primaryMuscleList)
    }

    @Test
    fun `a phone seeded before the muscles shipped gains them in place`() = runTest {
        importer.import {}

        // Strip the five fields the way the Strong rebuild left them, and rewind the version.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE exercise SET primary_muscles = NULL, secondary_muscles = NULL, " +
                "force = NULL, mechanic = NULL, level = NULL",
        )
        db.appStateDao().put(AppStateEntity(AppStateEntity.KEY_EXERCISE_LIBRARY_VERSION, "9"))

        importer.import {}

        val bench = db.exerciseDao().getAll().first { it.name == "Bench Press (Barbell)" }
        assertEquals("push", bench.force)
        assertEquals(listOf("chest"), bench.primaryMuscleList)
    }

    @Test
    fun `topping up twice adds nothing the second time`() = runTest {
        importer.import {}
        rewindToOldLibrary()

        importer.import {}
        val afterFirst = db.exerciseDao().count()
        importer.import {}

        assertEquals(afterFirst, db.exerciseDao().count())
    }

    /** The state a phone seeded before the library grew is in: 96 rows and the old version. */
    private suspend fun rewindToOldLibrary() {
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM exercise WHERE id NOT IN (SELECT id FROM exercise LIMIT 96)",
        )
        db.appStateDao().put(AppStateEntity(AppStateEntity.KEY_EXERCISE_LIBRARY_VERSION, "1"))
    }

    @Test
    fun `rows the library no longer carries are retired`() = runTest {
        importer.import {}

        rewindToOldLibrary()
        // Two rows from the free-exercise-db catalogue the Strong-based library replaced.
        // Added after the rewind, which keeps only the lowest 96 ids.
        val unused = insertSeeded("3-4 Sit-Up")
        val trained = insertSeeded("Smith Machine Pistol Squat")
        logSetAgainst(trained)

        importer.import {}

        assertNull("nothing pointed at it, so it goes", db.workoutDao().exercise(unused))
        val kept = db.workoutDao().exercise(trained)
        assertTrue("a trained movement is kept, not deleted", kept != null)
        assertTrue("and becomes the user's own row", kept!!.isCustom)
    }

    @Test
    fun `a row the old catalogue spelled differently takes the library's name`() = runTest {
        importer.import {}
        rewindToOldLibrary()
        // What free-exercise-db called it. matchKey folds the hyphen, so the Strong row is
        // never inserted and this is the row the user would be left looking at.
        val old = insertSeeded("Pull-Up")

        importer.import {}

        assertEquals("Pull Up", db.workoutDao().exercise(old)?.name)
        assertEquals(
            "and there must not be two of it",
            1,
            db.exerciseDao().getAll().count { it.name == "Pull Up" },
        )
    }

    @Test
    fun `a renamed movement's history moves onto the library row`() = runTest {
        importer.import {}
        rewindToOldLibrary()
        // What free-exercise-db called the pec dec machine, with a session logged against it.
        val old = insertSeeded("Pec Deck")
        logSetAgainst(old)

        importer.import {}

        assertNull("the old row goes", db.workoutDao().exercise(old))
        val survivor = db.exerciseDao().getAll().first { it.name == "Pec Deck (Machine)" }
        assertEquals(
            "and its set is now the library row's",
            1,
            db.workoutDao().observeCompletedSetCount(survivor.id).first(),
        )
    }

    @Test
    fun `retiring leaves the user's own exercises alone`() = runTest {
        importer.import {}
        rewindToOldLibrary()
        val mine = db.workoutDao().insertExercise(exercise("Yash's Warmup Circuit", custom = true))

        importer.import {}

        assertTrue(db.workoutDao().exercise(mine) != null)
    }

    private suspend fun insertSeeded(name: String): Long =
        db.workoutDao().insertExercise(exercise(name, custom = false))

    private fun exercise(name: String, custom: Boolean) = ExerciseEntity(
        name = name,
        muscleGroup = "CORE",
        equipment = "BODYWEIGHT",
        type = "STRENGTH",
        isCustom = custom,
        notes = null,
    )

    private suspend fun logSetAgainst(exerciseId: Long) {
        val session = db.workoutDao().insertSession(
            WorkoutSessionEntity(
                routineId = null,
                name = "Session",
                date = "2026-01-01",
                startedAt = 0L,
                endedAt = null,
                note = null,
            ),
        )
        db.workoutDao().insertSet(
            WorkoutSetEntity(
                sessionId = session,
                exerciseId = exerciseId,
                position = 0,
                setIndex = 0,
                reps = 8,
                weightKg = 40.0,
                rpe = null,
                distanceM = null,
                durationSec = null,
                isCompleted = true,
            ),
        )
    }

    @Test
    fun `a second import is a no-op rather than a duplicate load`() = runTest {
        val first = importer.import {} as SeedProgress.Finished
        val second = importer.import {} as SeedProgress.Finished

        assertEquals(first.foods, second.foods)
        assertEquals(first.foods, db.foodDao().count())
    }

    @Test
    fun `fried dishes come from USDA, not INDB's oil-inflated rows`() = runTest {
        importer.import {}

        val samosa = db.foodDao().searchByName("Samosa").first { it.name == "Samosa" }
        assertEquals("USDA", samosa.source)
        assertTrue("samosa should be ~310 kcal, was ${samosa.kcal100g}", samosa.kcal100g in 250.0..380.0)
        assertTrue(samosa.isVerified)
    }

    @Test
    fun `household measures and catalogue search work after import`() = runTest {
        importer.import {}

        assertEquals(40.0, db.foodDao().portionsFor(1L).first { it.label == "roti" }.grams, 0.01)
        assertTrue(db.foodDao().search("paneer").isNotEmpty())
        assertTrue(db.foodDao().search("idli").isNotEmpty())
    }
}
