package com.yash.tracker.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.entity.FoodEntity
import com.yash.tracker.data.local.entity.LogEntryEntity
import com.yash.tracker.data.local.entity.LogItemEntity
import com.yash.tracker.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The backfill that fills in what was logged before there were columns for it.
 *
 * [MIGRATION_6_7] changes no schema — it is only UPDATE statements — so it is exercised
 * against a database Room has already built, with rows written the way an older build left
 * them. That runs the real SQL rather than a re-implementation of it.
 */
@RunWith(RobolectricTestRunner::class)
class MicroBackfillTest {

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

    private fun backfill() {
        MIGRATION_6_7.migrate(db.openHelper.writableDatabase)
        MIGRATION_7_8.migrate(db.openHelper.writableDatabase)
    }

    private fun food(
        name: String,
        fibre: Double? = null,
        sugar: Double? = null,
        sodium: Double? = null,
        altNames: String? = null,
    ) = FoodEntity(
        name = name,
        altNames = altNames,
        category = null,
        source = "INDB",
        sourceRef = null,
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

    /** An entry as an older build wrote it: macros only, every new column still empty. */
    private suspend fun oldEntry(vararg items: LogItemEntity): Long {
        val entryId = db.logDao().insertEntry(
            LogEntryEntity(
                date = "2026-09-01",
                loggedAt = 0L,
                mealType = "LUNCH",
                source = "TEXT",
                photoUri = null,
                userHint = null,
                note = null,
                groundingSource = null,
                kcal = items.sumOf { it.kcal },
                proteinG = 0.0,
                carbsG = 0.0,
                fatG = 0.0,
            ),
        )
        db.logDao().insertItems(items.map { it.copy(entryId = entryId) })
        return entryId
    }

    private fun oldItem(
        name: String,
        foodId: Long? = null,
        productId: Long? = null,
        grams: Double = 200.0,
        kcal: Double = 400.0,
    ) = LogItemEntity(
        entryId = 0,
        foodId = foodId,
        productId = productId,
        name = name,
        quantity = grams,
        unit = "G",
        grams = grams,
        kcal = kcal,
        proteinG = 0.0,
        carbsG = 0.0,
        fatG = 0.0,
        aiConfidence = null,
    )

    @Test
    fun `an item pointing at a catalogue food gets the catalogue's figures`() = runTest {
        val id = db.foodDao().insert(food("Dal", fibre = 2.0, sugar = 1.0, sodium = 300.0))
        oldEntry(oldItem("Dal", foodId = id))

        backfill()

        val entry = db.logDao().observeEntriesForDate("2026-09-01").first().single()
        assertEquals("200 g is twice the panel", 4.0, entry.items.single().fibreG!!, 0.001)
        assertEquals(2.0, entry.entry.sugarG!!, 0.001)
        assertEquals(600.0, entry.entry.sodiumMg!!, 0.001)
        assertEquals("the whole meal is covered", 400.0, entry.entry.microKcal, 0.001)
    }

    @Test
    fun `an item pointing at a product gets the packet's figures`() = runTest {
        val id = db.productDao().upsert(
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
        oldEntry(oldItem("Pintola Peanut spread", productId = id, grams = 100.0))

        backfill()

        val entry = db.logDao().observeEntriesForDate("2026-09-01").first().single()
        assertEquals(9.0, entry.entry.sugarG!!, 0.001)
    }

    @Test
    fun `a food that states nothing leaves the row empty rather than zero`() = runTest {
        // What an AI_ESTIMATE row looks like: learned from a reading, no panel behind it.
        val id = db.foodDao().insert(food("Masala Bhaat"))
        oldEntry(oldItem("Masala Bhaat", foodId = id))

        backfill()

        val entry = db.logDao().observeEntriesForDate("2026-09-01").first().single()
        assertNull(entry.items.single().sugarG)
        assertNull(entry.entry.sugarG)
        assertEquals("nothing was recovered, so nothing is covered", 0.0, entry.entry.microKcal, 0.001)
    }

    @Test
    fun `a half-recovered meal says how much of itself it speaks for`() = runTest {
        val known = db.foodDao().insert(food("Dal", fibre = 2.0, sugar = 1.0, sodium = 300.0))
        val unknown = db.foodDao().insert(food("Masala Bhaat"))
        oldEntry(
            oldItem("Dal", foodId = known, grams = 100.0, kcal = 200.0),
            oldItem("Masala Bhaat", foodId = unknown, grams = 100.0, kcal = 200.0),
        )

        backfill()

        val entry = db.logDao().observeEntriesForDate("2026-09-01").first().single()
        assertEquals("only the measured half", 1.0, entry.entry.sugarG!!, 0.001)
        assertEquals(200.0, entry.entry.microKcal, 0.001)
    }

    @Test
    fun `a row naming no source is matched on its name when exactly one food answers`() = runTest {
        db.foodDao().insert(food("Idli", fibre = 1.0, sugar = 0.5, sodium = 200.0))
        oldEntry(oldItem("idli", grams = 100.0))

        backfill()

        val entry = db.logDao().observeEntriesForDate("2026-09-01").first().single()
        assertEquals("case does not decide whether history gets its figures", 0.5, entry.entry.sugarG!!, 0.001)
    }

    @Test
    fun `an ambiguous name is left alone rather than guessed at`() = runTest {
        db.foodDao().insert(food("Poha", sugar = 2.0))
        db.foodDao().insert(food("Poha", sugar = 9.0))
        oldEntry(oldItem("Poha", grams = 100.0))

        backfill()

        val entry = db.logDao().observeEntriesForDate("2026-09-01").first().single()
        assertNull("two foods answer to it, so neither wins", entry.entry.sugarG)
    }

    @Test
    fun `a weightless row is not credited with a confident zero`() = runTest {
        val id = db.foodDao().insert(food("Saved meal", fibre = 2.0, sugar = 1.0, sodium = 300.0))
        oldEntry(oldItem("Saved meal", foodId = id, grams = 0.0, kcal = 400.0))

        backfill()

        val entry = db.logDao().observeEntriesForDate("2026-09-01").first().single()
        assertNull(entry.items.single().sugarG)
    }

    @Test
    fun `running it twice changes nothing`() = runTest {
        val id = db.foodDao().insert(food("Dal", fibre = 2.0, sugar = 1.0, sodium = 300.0))
        oldEntry(oldItem("Dal", foodId = id, grams = 100.0, kcal = 200.0))

        backfill()
        val once = db.logDao().observeEntriesForDate("2026-09-01").first().single().entry
        backfill()
        val twice = db.logDao().observeEntriesForDate("2026-09-01").first().single().entry

        assertEquals(once, twice)
    }
}
