package com.yash.tracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.entity.FoodEntity
import com.yash.tracker.data.local.entity.LogEntryEntity
import com.yash.tracker.data.local.entity.LogItemEntity
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

@RunWith(RobolectricTestRunner::class)
class AppDatabaseTest {

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

    private fun food(name: String, altNames: String? = null) = FoodEntity(
        name = name,
        altNames = altNames,
        category = "GRAIN",
        source = "INDB",
        sourceRef = "test",
        isComposite = true,
        kcal100g = 297.0,
        protein100g = 9.7,
        carbs100g = 57.4,
        fat100g = 3.5,
        fibre100g = 9.4,
        sugar100g = null,
        sodiumMg100g = null,
        defaultPortionG = 40.0,
        portionLabel = "1 roti",
        createdAt = 0L,
    )

    private fun entry(date: String, kcal: Double) = LogEntryEntity(
        date = date,
        loggedAt = 0L,
        mealType = "LUNCH",
        source = "MANUAL",
        photoUri = null,
        userHint = null,
        note = null,
        groundingSource = null,
        kcal = kcal,
        proteinG = 10.0,
        carbsG = 20.0,
        fatG = 5.0,
    )

    private fun item(name: String) = LogItemEntity(
        entryId = 0,
        foodId = null,
        productId = null,
        name = name,
        quantity = 1.0,
        unit = "ROTI",
        grams = 40.0,
        kcal = 119.0,
        proteinG = 3.9,
        carbsG = 23.0,
        fatG = 1.4,
        aiConfidence = null,
    )

    @Test
    fun `daily totals sum the entries for that date only`() = runTest {
        val dao = db.logDao()
        dao.insertEntryWithItems(entry("2026-09-13", 500.0), listOf(item("Roti")))
        dao.insertEntryWithItems(entry("2026-09-13", 320.0), listOf(item("Dal")))
        dao.insertEntryWithItems(entry("2026-09-12", 900.0), listOf(item("Poha")))

        val totals = dao.observeTotalsForDate("2026-09-13").first()
        assertEquals(820.0, totals.kcal, 0.001)
        assertEquals(20.0, totals.proteinG, 0.001)
    }

    @Test
    fun `a date with nothing logged reports zero rather than null`() = runTest {
        val totals = db.logDao().observeTotalsForDate("2026-01-01").first()
        assertEquals(0.0, totals.kcal, 0.001)
    }

    @Test
    fun `deleting an entry cascades to its items`() = runTest {
        val dao = db.logDao()
        val id = dao.insertEntryWithItems(
            entry("2026-09-13", 500.0),
            listOf(item("Roti"), item("Paneer sabji")),
        )
        assertEquals(2, dao.itemCountFor(id))

        dao.deleteEntry(id)

        assertEquals(0, dao.itemCountFor(id))
        assertNull(dao.getEntryWithItems(id))
    }

    @Test
    fun `entries come back with their items attached`() = runTest {
        val dao = db.logDao()
        val id = dao.insertEntryWithItems(entry("2026-09-13", 500.0), listOf(item("Roti")))

        val loaded = dao.observeEntriesForDate("2026-09-13").first()
        assertEquals(1, loaded.size)
        assertEquals(id, loaded.first().entry.id)
        assertEquals("Roti", loaded.first().items.single().name)
    }

    @Test
    fun `full-text search matches alternate names`() = runTest {
        val dao = db.foodDao()
        dao.insertAll(
            listOf(
                food("Roti (whole wheat)", altNames = "chapati phulka"),
                food("Paneer sabji"),
            ),
        )

        assertEquals("Roti (whole wheat)", dao.search("chapati").single().name)
        assertEquals("Paneer sabji", dao.search("paneer").single().name)
    }

    @Test
    fun `search ranks the foods logged most often first`() = runTest {
        val dao = db.foodDao()
        val ids = dao.insertAll(listOf(food("Dal tadka"), food("Dal fry")))
        dao.incrementTimesLogged(ids[1])

        val results = dao.search("dal")
        assertTrue(results.size == 2)
        assertEquals("Dal fry", results.first().name)
    }

    @Test
    fun `water accumulates per day and the last glass can be removed`() = runTest {
        val dao = db.logDao()
        repeat(3) {
            dao.insertWater(
                com.yash.tracker.data.local.entity.WaterLogEntity(
                    date = "2026-09-13",
                    loggedAt = it.toLong(),
                    ml = 250,
                ),
            )
        }
        assertEquals(750, dao.observeWaterForDate("2026-09-13").first())

        dao.removeLastWater("2026-09-13")
        assertEquals(500, dao.observeWaterForDate("2026-09-13").first())
    }
}
