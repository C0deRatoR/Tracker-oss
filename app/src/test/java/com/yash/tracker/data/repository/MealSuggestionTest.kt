package com.yash.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.entity.TargetEntity
import com.yash.tracker.data.local.seed.SeedImporter
import com.yash.tracker.domain.diary.MealType
import com.yash.tracker.domain.nutrition.MacroSuggestion
import com.yash.tracker.domain.nutrition.Macros
import com.yash.tracker.domain.nutrition.PortionResolver
import com.yash.tracker.domain.nutrition.ServingSource
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

/** The suggestion path end to end: pool, gap, plate, and the diary row it writes. */
@RunWith(RobolectricTestRunner::class)
class MealSuggestionTest {

    private lateinit var db: AppDatabase
    private lateinit var foods: FoodRepository
    private lateinit var log: LogRepository
    private lateinit var meals: MealRepository
    private lateinit var suggestions: SuggestionRepository

    private val today = LocalDate.of(2026, 9, 15)

    private val target = TargetEntity(
        computedAt = 0,
        basisWeightKg = 80.0,
        bmr = 1780,
        tdee = 2759,
        activityFactor = 1.55,
        kcal = 2260,
        proteinG = 176.0,
        carbsG = 226.0,
        fatG = 62.0,
        waterMl = 3000,
        weeksToGoal = null,
        rateLbPerWeek = null,
    )

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        SeedImporter(context, db, Dispatchers.Unconfined).import {}

        foods = FoodRepository(db.foodDao(), Dispatchers.Unconfined)
        log = LogRepository(db.logDao(), Dispatchers.Unconfined)
        meals = MealRepository(db.mealDao(), db.logDao(), Dispatchers.Unconfined)
        suggestions = SuggestionRepository(
            foodDao = db.foodDao(),
            productDao = db.productDao(),
            mealDao = db.mealDao(),
            logDao = db.logDao(),
            meals = meals,
            io = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() = db.close()

    /** Logs a food once so it enters the pool the way real use would. */
    private suspend fun eatOnce(term: String, quantity: Double = 1.0): Long {
        val food = foods.search(term).first()
        val choice = PortionResolver.choicesFor(food, foods.portionsFor(food.id)).first()
        foods.markLogged(food.id)
        return log.logFood(today, MealType.LUNCH, food, quantity, choice)
    }

    private suspend fun suggest(
        eaten: Macros = Macros.ZERO,
        hour: Int = 19,
        logged: Set<MealType> = emptySet(),
    ) = suggestions.suggest(target, eaten, hour, logged)

    @Test
    fun `an empty diary has nothing to suggest from`() = runTest {
        assertEquals(MacroSuggestion.NoHistoryYet, suggest())
    }

    @Test
    fun `suggestions only ever come from food already logged`() = runTest {
        eatOnce("paneer")
        eatOnce("roti")

        val result = suggest() as MacroSuggestion.Plates
        val pooled = db.foodDao().mostLogged().map { it.id }.toSet()

        val used = result.plates
            .flatMap { it.items }
            .mapNotNull { (it.source as? ServingSource.Food)?.foodId }

        assertTrue(used.isNotEmpty())
        assertTrue("the catalogue at large must stay out of it", pooled.containsAll(used))
    }

    @Test
    fun `a met target stops suggesting`() = runTest {
        eatOnce("paneer")
        val full = Macros(kcal = 2250.0, proteinG = 176.0, carbsG = 226.0, fatG = 62.0)

        assertEquals(MacroSuggestion.DayDone, suggest(eaten = full))
    }

    @Test
    fun `dinner is suggested in the evening and gets the whole remainder`() = runTest {
        eatOnce("paneer")
        val eaten = Macros(kcal = 1400.0, proteinG = 90.0, carbsG = 150.0, fatG = 40.0)

        val result = suggest(eaten = eaten, hour = 19) as MacroSuggestion.Plates

        assertEquals(MealType.DINNER, result.meal)
        assertEquals(860.0, result.gap.kcal, 1.0)
    }

    @Test
    fun `a suggested plate never spends more than the meal's share`() = runTest {
        eatOnce("paneer")
        eatOnce("roti")
        eatOnce("rice")

        val result = suggest(hour = 12) as MacroSuggestion.Plates

        assertTrue(result.plates.isNotEmpty())
        assertTrue(result.plates.all { it.total.kcal <= result.gap.kcal * 1.05 })
    }

    @Test
    fun `logging a plate writes one entry the day's totals pick up`() = runTest {
        eatOnce("paneer")
        eatOnce("roti")

        val before = log.observeTotals(today).first()
        val result = suggest() as MacroSuggestion.Plates
        val plate = result.plates.first()

        val entryId = suggestions.logPlate(today, MealType.DINNER, plate)
        assertTrue(entryId != null && entryId > 0)

        val after = log.observeTotals(today).first()
        assertEquals(before.kcal + plate.total.kcal, after.kcal, 0.01)
        assertEquals(before.proteinG + plate.total.proteinG, after.proteinG, 0.01)
    }

    @Test
    fun `a logged plate keeps one row per item so it can be corrected`() = runTest {
        eatOnce("paneer")
        eatOnce("roti")

        val result = suggest() as MacroSuggestion.Plates
        val plate = result.plates.first { it.items.size > 1 }
        val entryId = suggestions.logPlate(today, MealType.DINNER, plate)!!

        val entry = db.logDao().getEntryWithItems(entryId)!!
        assertEquals(plate.items.size, entry.items.size)
        assertEquals(MealType.DINNER.name, entry.entry.mealType)
    }

    @Test
    fun `logging a plate makes its foods more familiar next time`() = runTest {
        eatOnce("paneer")
        eatOnce("roti")

        val result = suggest() as MacroSuggestion.Plates
        val plate = result.plates.first()
        val foodIds = plate.items.mapNotNull { (it.source as? ServingSource.Food)?.foodId }
        val before = db.foodDao().mostLogged().filter { it.id in foodIds }.associate { it.id to it.timesLogged }

        suggestions.logPlate(today, MealType.DINNER, plate)

        val after = db.foodDao().mostLogged().filter { it.id in foodIds }.associate { it.id to it.timesLogged }
        foodIds.forEach { assertEquals(before.getValue(it) + 1, after.getValue(it)) }
    }

    @Test
    fun `a saved meal is suggested whole and logs through its template`() = runTest {
        val entryId = eatOnce("paneer")
        meals.saveEntryAsMeal(entryId, "Usual paneer plate")

        val result = suggest() as MacroSuggestion.Plates
        val savedPlate = result.plates
            .first { it.items.first().source is ServingSource.SavedMeal }

        assertEquals("a saved meal is a whole meal", 1, savedPlate.items.size)

        val logged = suggestions.logPlate(today, MealType.DINNER, savedPlate)!!
        assertEquals("MEAL_TEMPLATE", db.logDao().getEntryWithItems(logged)!!.entry.source)
    }
}
