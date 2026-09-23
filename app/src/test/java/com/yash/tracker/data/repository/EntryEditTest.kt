package com.yash.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.dao.LogDao
import com.yash.tracker.data.local.entity.LogEntryEntity
import com.yash.tracker.data.local.entity.LogItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Correcting a meal you already logged.
 *
 * Until now the only fix for a wrong entry was to delete it and log it again, which moved it to
 * the bottom of the day and lost its photo. An edit rewrites what you ate; it must not touch the
 * record of *when* you ate it.
 */
@RunWith(RobolectricTestRunner::class)
class EntryEditTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: LogDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.logDao()
    }

    @After
    fun tearDown() = db.close()

    /** The bad reading: one row for what was really oats, milk and a banana. */
    private suspend fun logMuesli(): Long = dao.insertEntryWithItems(
        entry = LogEntryEntity(
            date = "2026-09-14",
            loggedAt = 1_757_800_000_000,
            mealType = "BREAKFAST",
            source = "PHOTO",
            photoUri = "/data/photos/7.jpg",
            userHint = null,
            note = null,
            groundingSource = null,
            kcal = 220.0,
            proteinG = 6.0,
            carbsG = 34.0,
            fatG = 6.0,
        ),
        items = listOf(
            LogItemEntity(
                entryId = 0,
                foodId = null,
                productId = null,
                name = "Muesli with milk",
                quantity = 1.0,
                unit = "CUP",
                grams = 60.0,
                kcal = 220.0,
                proteinG = 6.0,
                carbsG = 34.0,
                fatG = 6.0,
                aiConfidence = 0.7,
            ),
        ),
    )

    private fun row(name: String, grams: Double, kcal: Double, protein: Double) = LogItemEntity(
        entryId = 0,
        foodId = null,
        productId = null,
        name = name,
        quantity = 1.0,
        unit = "G",
        grams = grams,
        kcal = kcal,
        proteinG = protein,
        carbsG = 0.0,
        fatG = 0.0,
        aiConfidence = null,
        wasEdited = true,
    )

    @Test
    fun `splitting one row into three replaces the items and the totals together`() = runTest {
        val id = logMuesli()

        val split = listOf(
            row("Oats", grams = 60.0, kcal = 233.0, protein = 10.1),
            row("Milk", grams = 200.0, kcal = 116.0, protein = 6.2),
            row("Banana", grams = 118.0, kcal = 105.0, protein = 1.3),
        )
        val before = dao.getEntryWithItems(id)!!.entry
        dao.replaceEntryItems(
            entry = before.copy(
                kcal = split.sumOf { it.kcal },
                proteinG = split.sumOf { it.proteinG },
                carbsG = 0.0,
                fatG = 0.0,
            ),
            items = split,
        )

        val after = dao.getEntryWithItems(id)!!
        assertEquals("the merged row is gone, not sitting alongside the new ones", 3, after.items.size)
        assertEquals(listOf("Oats", "Milk", "Banana"), after.items.map { it.name })
        assertEquals(454.0, after.entry.kcal, 0.01)
        assertEquals(
            "the day's total is read off the entry, so it has to agree with the rows",
            after.items.sumOf { it.kcal },
            after.entry.kcal,
            0.01,
        )
    }

    @Test
    fun `an edit keeps the entry's place in the diary`() = runTest {
        val id = logMuesli()
        val before = dao.getEntryWithItems(id)!!.entry

        dao.replaceEntryItems(
            entry = before.copy(kcal = 454.0, mealType = "SNACK"),
            items = listOf(row("Oats", 60.0, 454.0, 10.1)),
        )

        val after = dao.getEntryWithItems(id)!!.entry
        assertEquals("same row, not a new one", id, after.id)
        assertEquals("2026-09-14", after.date)
        assertEquals("editing what you ate does not change when you ate it", 1_757_800_000_000, after.loggedAt)
        assertEquals("and the photo of the plate survives", "/data/photos/7.jpg", after.photoUri)
        assertEquals("but the meal slot is the user's to change", "SNACK", after.mealType)
    }

    @Test
    fun `the day's totals follow an edit`() = runTest {
        val id = logMuesli()
        assertEquals(220.0, dao.observeTotalsForDate("2026-09-14").first().kcal, 0.01)

        val before = dao.getEntryWithItems(id)!!.entry
        dao.replaceEntryItems(
            entry = before.copy(kcal = 454.0),
            items = listOf(row("Oats", 60.0, 454.0, 10.1)),
        )

        assertEquals(454.0, dao.observeTotalsForDate("2026-09-14").first().kcal, 0.01)
        assertNotNull(dao.getEntryWithItems(id))
    }
}
