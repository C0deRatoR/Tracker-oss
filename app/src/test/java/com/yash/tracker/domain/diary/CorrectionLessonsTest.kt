package com.yash.tracker.domain.diary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CorrectionLessonsTest {

    private fun lesson(
        aiName: String = "Roti",
        finalName: String = "Roti",
        aiGrams: Double = 80.0,
        finalGrams: Double = 80.0,
    ) = CorrectionLessons.from(aiName, finalName, aiGrams, finalGrams)

    @Test
    fun `an untouched row teaches nothing`() {
        assertNull(lesson())
    }

    @Test
    fun `a rename is learned`() {
        val learned = lesson(aiName = "Scrambled eggs", finalName = "Paneer bhurji")!!

        assertEquals("Scrambled eggs", learned.aiName)
        assertEquals("Paneer bhurji", learned.correctedName)
        assertNull("the portion was left alone, so there is nothing to say about it", learned.grams)
    }

    @Test
    fun `a changed portion is learned even when the name was right`() {
        val learned = lesson(finalGrams = 45.0)!!

        assertEquals("Roti", learned.aiName)
        assertEquals("Roti", learned.correctedName)
        assertEquals(45.0, learned.grams!!, 0.01)
    }

    @Test
    fun `the model's own estimate is never stored as the user's portion`() {
        // The whole point: if the user did not touch the weight, 80 g is the model's guess and
        // feeding it back would be the app teaching itself.
        val renamed = lesson(aiName = "Flatbread", finalName = "Roti")!!

        assertNull(renamed.grams)
    }

    @Test
    fun `a rename and a reweigh together carry both`() {
        val learned = lesson(aiName = "Flatbread", finalName = "Roti", finalGrams = 45.0)!!

        assertEquals("Roti", learned.correctedName)
        assertEquals(45.0, learned.grams!!, 0.01)
    }

    @Test
    fun `a rounding-sized difference is not a correction`() {
        assertNull("half a gram is the same number", lesson(finalGrams = 80.4))
        assertNotNull(lesson(finalGrams = 82.0))
    }

    @Test
    fun `case and spacing alone are not a rename`() {
        assertNull(lesson(aiName = "Roti", finalName = "  roti "))
    }

    @Test
    fun `a row the user added themselves teaches nothing`() {
        assertNull(lesson(aiName = "", finalName = "Bhindi sabji", finalGrams = 150.0))
    }

    @Test
    fun `an emptied name teaches nothing`() {
        assertNull(lesson(aiName = "Roti", finalName = "   "))
    }

    @Test
    fun `a portion cleared to zero is not a lesson`() {
        assertNull(lesson(finalGrams = 0.0))
    }
}
