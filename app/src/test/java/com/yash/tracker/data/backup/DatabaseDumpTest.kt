package com.yash.tracker.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.data.local.entity.FoodEntity
import com.yash.tracker.data.local.entity.WeightLogEntity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The dump is generic, so what needs proving is that a real Room schema survives the round
 * trip: every table, foreign keys, nulls, doubles, and the full-text index.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseDumpTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun food(name: String, kcal: Double) = FoodEntity(
        name = name,
        altNames = null,
        category = "DISH",
        source = "INDB",
        sourceRef = "test",
        isComposite = true,
        kcal100g = kcal,
        protein100g = 4.4,
        carbs100g = 20.0,
        fat100g = 1.2,
        fibre100g = null,
        sugar100g = null,
        sodiumMg100g = null,
        defaultPortionG = 40.0,
        portionLabel = "roti",
        isVerified = false,
        createdAt = 1_700_000_000_000,
    )

    private suspend fun seedSomething() {
        db.foodDao().insertAll(listOf(food("Roti", 297.0), food("Dal Tadka", 118.0)))
        db.weightDao().upsert(
            WeightLogEntity(date = "2026-09-13", weightKg = 78.0, note = null, loggedAt = 1L),
        )
        db.workoutDao().insertExercise(
            ExerciseEntity(
                name = "Barbell Bench Press",
                muscleGroup = "CHEST",
                equipment = "BARBELL",
                type = "STRENGTH",
                metValue = 6.0,
                defaultRestSec = 180,
                notes = null,
            ),
        )
    }

    @Test
    fun `every table round-trips through a dump`() = runTest {
        seedSomething()
        val dump = DatabaseDump.write(db.openHelper.writableDatabase, schemaVersion = 1)

        // Wipe it the way a restore onto a different phone would find it.
        db.openHelper.writableDatabase.execSQL("DELETE FROM food")
        db.openHelper.writableDatabase.execSQL("DELETE FROM weight_log")
        db.openHelper.writableDatabase.execSQL("DELETE FROM exercise")
        assertEquals(0, db.foodDao().count())

        DatabaseDump.read(db.openHelper.writableDatabase, dump)

        assertEquals(2, db.foodDao().count())
        assertEquals(1, db.exerciseDao().count())
        assertEquals(78.0, db.weightDao().latest()!!.weightKg, 0.001)
    }

    @Test
    fun `doubles and nulls survive`() = runTest {
        seedSomething()
        val dump = DatabaseDump.write(db.openHelper.writableDatabase, schemaVersion = 1)
        db.openHelper.writableDatabase.execSQL("DELETE FROM food")

        DatabaseDump.read(db.openHelper.writableDatabase, dump)

        val roti = db.foodDao().searchByName("Roti").first { it.name == "Roti" }
        assertEquals(297.0, roti.kcal100g, 0.001)
        assertEquals(4.4, roti.protein100g, 0.001)
        assertEquals("a null column must come back null", null, roti.fibre100g)
        assertEquals("a true flag must not come back as text", true, roti.isComposite)
    }

    @Test
    fun `search still works after a restore, so the text index was rebuilt`() = runTest {
        seedSomething()
        val dump = DatabaseDump.write(db.openHelper.writableDatabase, schemaVersion = 1)
        db.openHelper.writableDatabase.execSQL("DELETE FROM food")

        DatabaseDump.read(db.openHelper.writableDatabase, dump)

        assertTrue("FTS should find the restored row", db.foodDao().search("dal").isNotEmpty())
    }

    @Test
    fun `the dump carries its schema version and no full-text shadow tables`() = runTest {
        seedSomething()
        val dump = DatabaseDump.write(db.openHelper.writableDatabase, schemaVersion = 1)

        assertEquals(1, DatabaseDump.schemaVersionOf(dump))

        val tables = dump["tables"]!!.jsonObject.keys
        assertNotNull(tables.firstOrNull { it == "food" })
        assertTrue("shadow tables bloat the file and cannot be restored directly",
            tables.none { it.startsWith("food_fts") })
    }

    @Test
    fun `restoring twice is not additive`() = runTest {
        seedSomething()
        val dump = DatabaseDump.write(db.openHelper.writableDatabase, schemaVersion = 1)

        DatabaseDump.read(db.openHelper.writableDatabase, dump)
        DatabaseDump.read(db.openHelper.writableDatabase, dump)

        assertEquals(2, db.foodDao().count())
    }
}
