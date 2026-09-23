package com.yash.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.seed.SeedImporter
import com.yash.tracker.domain.diary.MealType
import com.yash.tracker.domain.nutrition.PortionResolver
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

/** The offline path end to end: search the bundled catalogue, pick a portion, log it. */
@RunWith(RobolectricTestRunner::class)
class ManualLoggingTest {

    private lateinit var db: AppDatabase
    private lateinit var foods: FoodRepository
    private lateinit var log: LogRepository

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        SeedImporter(context, db, Dispatchers.Unconfined).import {}

        foods = FoodRepository(db.foodDao(), Dispatchers.Unconfined)
        log = LogRepository(db.logDao(), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `searching finds Indian dishes by name`() = runTest {
        assertTrue(foods.search("paneer").isNotEmpty())
        assertTrue(foods.search("idli").isNotEmpty())
        assertTrue(foods.search("dal").isNotEmpty())
    }

    @Test
    fun `a partial word still finds the food`() = runTest {
        assertTrue("prefix search should match", foods.search("pane").isNotEmpty())
    }

    @Test
    fun `punctuation in the query does not blow up FTS`() = runTest {
        // Raw text reaches MATCH, whose syntax would otherwise throw on these.
        for (query in listOf("\"", "paneer\"", "a*b", "dal AND", "(", "-", "'")) {
            foods.search(query) // must not throw
        }
    }

    @Test
    fun `an empty query returns nothing rather than everything`() = runTest {
        assertTrue(foods.search("   ").isEmpty())
    }

    @Test
    fun `logging a food writes an entry the diary can total`() = runTest {
        val roti = foods.search("chapati").first { it.name.contains("Chapati", true) }
        val choice = PortionResolver.choicesFor(roti, foods.portionsFor(roti.id)).first()
        val date = LocalDate.of(2026, 9, 13)

        log.logFood(date, MealType.LUNCH, roti, quantity = 2.0, choice = choice)

        val totals = log.observeTotals(date).first()
        val expected = PortionResolver.macrosFor(roti, choice.gramsPerUnit * 2)
        assertEquals(expected.kcal, totals.kcal, 0.01)

        val entries = log.observeEntries(date).first()
        assertEquals(1, entries.size)
        assertEquals("LUNCH", entries.first().entry.mealType)
        assertEquals(2.0, entries.first().items.single().quantity, 0.001)
    }

    @Test
    fun `macros are frozen on the item, not recomputed from the catalogue`() = runTest {
        val food = foods.search("idli").first()
        val choice = PortionResolver.choicesFor(food, foods.portionsFor(food.id)).first()
        val date = LocalDate.of(2026, 9, 13)

        log.logFood(date, MealType.BREAKFAST, food, 3.0, choice)
        val loggedKcal = log.observeEntries(date).first().single().items.single().kcal

        // Change what the catalogue says; history must not move.
        db.foodDao().insertAll(emptyList())
        db.openHelper.writableDatabase.execSQL("UPDATE food SET kcal_100g = 9999 WHERE id = ${food.id}")

        assertEquals(loggedKcal, log.observeEntries(date).first().single().items.single().kcal, 0.001)
    }

    @Test
    fun `entries land on the day they were logged for`() = runTest {
        val food = foods.search("poha").first()
        val choice = PortionResolver.choicesFor(food, foods.portionsFor(food.id)).first()

        log.logFood(LocalDate.of(2026, 9, 12), MealType.DINNER, food, 1.0, choice)

        assertTrue(log.observeEntries(LocalDate.of(2026, 9, 13)).first().isEmpty())
        assertEquals(1, log.observeEntries(LocalDate.of(2026, 9, 12)).first().size)
    }
}
