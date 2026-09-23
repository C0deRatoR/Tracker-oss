package com.yash.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.entity.ProductEntity
import com.yash.tracker.domain.diary.MealType
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

@RunWith(RobolectricTestRunner::class)
class ProductLoggingTest {

    private lateinit var db: AppDatabase
    private lateinit var products: ProductRepository
    private lateinit var logs: LogRepository

    private val today = LocalDate.of(2026, 9, 13)

    /** A real one off a shelf: 30 g scoop, 120 kcal a scoop. */
    private val whey = ProductEntity(
        brand = "Avvatar",
        name = "Whey Protein",
        kcal100g = 400.0,
        protein100g = 80.0,
        carbs100g = 6.7,
        fat100g = 6.7,
        fibre100g = null,
        sugar100g = 1.0,
        sodiumMg100g = 200.0,
        servingG = 30.0,
        servingLabel = "1 scoop",
        ingredients = "Whey protein concentrate",
        labelPhotoUri = null,
        barcode = null,
        createdAt = 0,
        updatedAt = 0,
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        products = ProductRepository(db.productDao(), db.logDao(), Dispatchers.Unconfined)
        logs = LogRepository(db.logDao(), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a scoop logs the serving from the label, not a guess`() = runTest {
        val id = products.save(whey)
        val saved = products.getById(id)!!
        val scoop = products.choicesFor(saved).first()

        products.log(today, MealType.SNACK, saved, quantity = 1.0, choice = scoop)

        val totals = logs.observeTotals(today).first()
        assertEquals("30 g of a 400 kcal/100 g powder", 120.0, totals.kcal, 0.01)
        assertEquals(24.0, totals.proteinG, 0.01)
    }

    @Test
    fun `grams are always offered, even when the pack states no serving`() {
        val choices = products.choicesFor(whey.copy(servingG = null, servingLabel = null))

        assertEquals(1, choices.size)
        assertEquals("g", choices.single().label)
    }

    @Test
    fun `deleting a product leaves what was already eaten alone`() = runTest {
        val id = products.save(whey)
        val saved = products.getById(id)!!
        products.log(today, MealType.SNACK, saved, 2.0, products.choicesFor(saved).first())

        products.delete(id)

        assertNull("the product is gone", products.getById(id))
        val totals = logs.observeTotals(today).first()
        assertEquals("but the diary still says what it said", 240.0, totals.kcal, 0.01)
    }

    @Test
    fun `a product is found by brand as well as by name`() = runTest {
        products.save(whey)

        assertTrue(products.search("avvatar").isNotEmpty())
        assertTrue(products.search("whey").isNotEmpty())
        assertTrue("and the middle of a word too", products.search("rotein").isNotEmpty())
    }

    @Test
    fun `saving twice updates rather than duplicating`() = runTest {
        val id = products.save(whey)
        products.save(products.getById(id)!!.copy(kcal100g = 402.0))

        assertEquals(1, db.productDao().count())
        assertEquals(402.0, products.getById(id)!!.kcal100g, 0.01)
    }
}
