package com.yash.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.entity.FoodEntity
import com.yash.tracker.data.local.entity.ProductEntity
import com.yash.tracker.domain.diary.MealType
import com.yash.tracker.domain.nutrition.PortionChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * The rest of the panel, carried from the tables to the diary and back out as a day total.
 *
 * The case worth guarding is the absent one: a food nobody measured for sugar has to stay
 * absent all the way to the screen, because a zero there would be read as a measurement.
 */
@RunWith(RobolectricTestRunner::class)
class MicroLoggingTest {

    private lateinit var db: AppDatabase
    private lateinit var log: LogRepository
    private lateinit var products: ProductRepository
    private lateinit var meals: MealRepository

    private val date = LocalDate.of(2026, 9, 21)
    private val grams = PortionChoice("g", "G", 1.0)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        log = LogRepository(db.logDao(), Dispatchers.Unconfined)
        products = ProductRepository(db.productDao(), db.logDao(), Dispatchers.Unconfined)
        meals = MealRepository(db.mealDao(), db.logDao(), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = db.close()

    private fun food(
        name: String,
        fibre: Double?,
        sugar: Double?,
        sodium: Double?,
    ) = FoodEntity(
        name = name,
        altNames = null,
        category = "COMPOSITE",
        source = "INDB",
        sourceRef = "test",
        kcal100g = 200.0,
        protein100g = 8.0,
        carbs100g = 30.0,
        fat100g = 5.0,
        fibre100g = fibre,
        sugar100g = sugar,
        sodiumMg100g = sodium,
        defaultPortionG = 100.0,
        portionLabel = null,
        createdAt = 0L,
    )

    private suspend fun insert(food: FoodEntity): FoodEntity =
        food.copy(id = db.foodDao().insert(food))

    @Test
    fun `logging a catalogue food carries its sugar onto the entry`() = runTest {
        val barfi = insert(food("Barfi", fibre = 0.4, sugar = 37.0, sodium = 71.0))

        log.logFood(date, MealType.SNACK, barfi, quantity = 50.0, choice = grams)

        val entry = log.observeEntries(date).first().single()
        assertEquals(18.5, entry.items.single().sugarG!!, 0.01)
        assertEquals(18.5, entry.entry.sugarG!!, 0.01)
        assertEquals(0.2, entry.entry.fibreG!!, 0.01)
        assertEquals(35.5, entry.entry.sodiumMg!!, 0.01)
    }

    @Test
    fun `a food nobody measured leaves the entry with no figure rather than a zero`() = runTest {
        val guess = insert(food("Something", fibre = null, sugar = null, sodium = null))

        log.logFood(date, MealType.LUNCH, guess, quantity = 100.0, choice = grams)

        val entry = log.observeEntries(date).first().single()
        assertNull(entry.entry.sugarG)
        assertEquals("nothing on the plate was measured", 0.0, entry.entry.microKcal, 0.001)
    }

    @Test
    fun `a day mixing measured and unmeasured food says how much it covers`() = runTest {
        val measured = insert(food("Dal", fibre = 2.0, sugar = 1.0, sodium = 300.0))
        val unmeasured = insert(food("Mystery", fibre = null, sugar = null, sodium = null))

        // 100 g of each, 200 kcal apiece.
        log.logFood(date, MealType.LUNCH, measured, quantity = 100.0, choice = grams)
        log.logFood(date, MealType.DINNER, unmeasured, quantity = 100.0, choice = grams)

        val totals = log.observeTotals(date).first()
        assertEquals(400.0, totals.kcal, 0.01)
        assertEquals("only the measured half contributes", 1.0, totals.sugarG!!, 0.01)
        assertEquals(0.5, totals.microCoverage, 0.01)
    }

    @Test
    fun `a product logs the figures printed on its own label`() = runTest {
        val id = products.save(
            ProductEntity(
                brand = "Pintola",
                name = "Peanut spread",
                kcal100g = 587.0,
                protein100g = 24.0,
                carbs100g = 27.0,
                fat100g = 43.3,
                fibre100g = 6.0,
                sugar100g = 9.0,
                sodiumMg100g = 220.0,
                servingG = 32.0,
                servingLabel = "serving",
                ingredients = null,
                labelPhotoUri = null,
                barcode = null,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
        val product = products.getById(id)!!

        products.log(date, MealType.BREAKFAST, product, quantity = 100.0, choice = grams)

        val entry = log.observeEntries(date).first().single()
        assertEquals(9.0, entry.entry.sugarG!!, 0.01)
        assertEquals(entry.entry.kcal, entry.entry.microKcal, 0.01)
    }

    @Test
    fun `saving a meal and logging it again keeps the figures`() = runTest {
        val barfi = insert(food("Barfi", fibre = 0.4, sugar = 37.0, sodium = 71.0))
        val entryId = log.logFood(date, MealType.SNACK, barfi, quantity = 100.0, choice = grams)

        val mealId = meals.saveEntryAsMeal(entryId, "Sweet")!!
        meals.logMeal(mealId, date.plusDays(1), MealType.SNACK)

        val logged = log.observeEntries(date.plusDays(1)).first().single()
        assertEquals(37.0, logged.entry.sugarG!!, 0.01)
        assertEquals(logged.entry.kcal, logged.entry.microKcal, 0.01)
    }
}
