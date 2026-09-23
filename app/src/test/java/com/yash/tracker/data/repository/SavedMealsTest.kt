package com.yash.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.entity.LogEntryEntity
import com.yash.tracker.data.local.entity.LogItemEntity
import com.yash.tracker.domain.diary.DiaryDate
import com.yash.tracker.domain.diary.MealType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class SavedMealsTest {

    private lateinit var db: AppDatabase
    private lateinit var meals: MealRepository
    private lateinit var log: LogRepository

    private val monday = LocalDate.of(2026, 9, 14)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        meals = MealRepository(db.mealDao(), db.logDao(), Dispatchers.Unconfined)
        log = LogRepository(db.logDao(), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun logThali(date: LocalDate = monday): Long =
        db.logDao().insertEntryWithItems(
            entry = LogEntryEntity(
                date = DiaryDate.format(date),
                loggedAt = 0L,
                mealType = "DINNER",
                source = "TEXT",
                photoUri = null,
                userHint = null,
                note = null,
                groundingSource = null,
                kcal = 480.0,
                proteinG = 19.0,
                carbsG = 69.0,
                fatG = 14.0,
            ),
            items = listOf("Roti", "Dal tadka", "Curd").map { name ->
                LogItemEntity(
                    entryId = 0,
                    foodId = null,
                    productId = null,
                    name = name,
                    quantity = 1.0,
                    unit = "KATORI",
                    grams = 150.0,
                    kcal = 160.0,
                    proteinG = 6.3,
                    carbsG = 23.0,
                    fatG = 4.7,
                    aiConfidence = null,
                )
            },
        )

    @Test
    fun `an entry becomes a saved meal carrying all its items`() = runTest {
        meals.saveEntryAsMeal(logThali(), "Weeknight thali")

        val saved = meals.observeAll().first().single()
        assertEquals("Weeknight thali", saved.template.name)
        assertEquals(480.0, saved.template.kcal, 0.01)
        assertEquals(3, saved.items.size)
        assertEquals("DINNER", saved.template.defaultMealType)
    }

    @Test
    fun `logging a saved meal writes a full entry and counts the use`() = runTest {
        meals.saveEntryAsMeal(logThali(), "Weeknight thali")
        val mealId = meals.observeAll().first().single().template.id

        val tuesday = monday.plusDays(1)
        assertNotNull(meals.logMeal(mealId, tuesday))

        val entry = log.observeEntries(tuesday).first().single()
        assertEquals(480.0, entry.entry.kcal, 0.01)
        assertEquals(3, entry.items.size)
        assertEquals("MEAL_TEMPLATE", entry.entry.source)
        assertEquals("DINNER", entry.entry.mealType)
        assertEquals(1, meals.observeAll().first().single().template.timesLogged)
    }

    @Test
    fun `the meal type can be overridden at log time`() = runTest {
        meals.saveEntryAsMeal(logThali(), "Thali")
        val mealId = meals.observeAll().first().single().template.id

        meals.logMeal(mealId, monday.plusDays(1), MealType.BREAKFAST)

        assertEquals("BREAKFAST", log.observeEntries(monday.plusDays(1)).first().single().entry.mealType)
    }

    @Test
    fun `most-logged meals sort to the top`() = runTest {
        meals.saveEntryAsMeal(logThali(), "Rare")
        meals.saveEntryAsMeal(logThali(), "Usual")
        val usual = meals.observeAll().first().first { it.template.name == "Usual" }.template.id

        repeat(3) { meals.logMeal(usual, monday.plusDays(1)) }

        assertEquals("Usual", meals.observeAll().first().first().template.name)
    }

    @Test
    fun `editing a saved meal later does not rewrite meals already eaten`() = runTest {
        meals.saveEntryAsMeal(logThali(), "Thali")
        val mealId = meals.observeAll().first().single().template.id
        meals.logMeal(mealId, monday.plusDays(1))

        meals.rename(mealId, "Renamed")

        // The diary keeps the item names it was written with.
        val entry = log.observeEntries(monday.plusDays(1)).first().single()
        assertTrue(entry.items.map { it.name }.containsAll(listOf("Roti", "Dal tadka", "Curd")))
    }

    @Test
    fun `deleting a saved meal leaves the meals already logged from it alone`() = runTest {
        meals.saveEntryAsMeal(logThali(), "Thali")
        val mealId = meals.observeAll().first().single().template.id
        meals.logMeal(mealId, monday.plusDays(1))

        meals.delete(mealId)

        assertTrue(meals.observeAll().first().isEmpty())
        assertEquals(1, log.observeEntries(monday.plusDays(1)).first().size)
    }

    @Test
    fun `deleting a saved meal takes its items with it`() = runTest {
        meals.saveEntryAsMeal(logThali(), "Thali")
        val mealId = meals.observeAll().first().single().template.id

        meals.delete(mealId)

        assertEquals(0, db.mealDao().count())
    }
}
