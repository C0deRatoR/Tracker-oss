package com.yash.tracker.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards against the failure seen on the phone: photographing something that is not a label —
 * a dark desk, in that case — and getting back a confident-looking set of zeroes plus the
 * literal string "null", which then overwrote a perfectly good product.
 */
class LabelParsingTest {

    private val real = ParsedLabelDto(
        brand = "Pintola",
        name = "Peanut Based Spread with Dark Chocolate",
        kcal100g = 587.0,
        protein100g = 24.0,
        carbs100g = 27.0,
        fat100g = 43.3,
        servingG = 32.0,
        servingLabel = "2 Tbsp.",
    )

    @Test
    fun `a real panel is a reading`() {
        assertFalse(real.hasNoReading())
    }

    @Test
    fun `no calories means nothing was read`() {
        assertTrue(ParsedLabelDto().hasNoReading())
        assertTrue("zero is not a nutrition panel", real.copy(kcal100g = 0.0).hasNoReading())
        assertTrue(real.copy(kcal100g = null).hasNoReading())
    }

    @Test
    fun `the literal word null never reaches a field`() {
        val junk = real.copy(brand = "null", name = "N/A", servingLabel = "unknown").cleaned()

        assertNull(junk.brand)
        assertNull(junk.name)
        assertNull(junk.servingLabel)
    }

    @Test
    fun `real text is left alone`() {
        val cleaned = real.cleaned()

        assertEquals("Pintola", cleaned.brand)
        assertEquals("2 Tbsp.", cleaned.servingLabel)
        assertEquals(587.0, cleaned.kcal100g!!, 0.01)
    }
}
