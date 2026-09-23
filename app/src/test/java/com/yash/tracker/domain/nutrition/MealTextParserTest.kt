package com.yash.tracker.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The half of the offline path that decides whether a typed meal can be looked up at all. */
class MealTextParserTest {

    private val units = setOf("g", "ml", "katori", "roti", "cup", "bowl", "tbsp", "piece")

    private fun parse(text: String) = MealTextParser.parse(text, units)

    @Test
    fun `a counted food takes its name from its measure`() {
        val parts = parse("2 roti")

        assertEquals(1, parts.size)
        assertEquals(ParsedPortion(quantity = 2.0, unit = "roti", name = "roti"), parts.single())
    }

    @Test
    fun `a sentence splits into one part per food`() {
        val parts = parse("2 roti and a katori of dal")

        assertEquals(2, parts.size)
        assertEquals(ParsedPortion(2.0, "roti", "roti"), parts[0])
        assertEquals(ParsedPortion(1.0, "katori", "dal"), parts[1])
    }

    @Test
    fun `grams stuck to their number are still grams`() {
        assertEquals(ParsedPortion(100.0, "g", "paneer"), parse("100g paneer").single())
        assertEquals(ParsedPortion(100.0, "g", "paneer"), parse("100 grams of paneer").single())
    }

    @Test
    fun `the amount may come after the food`() {
        assertEquals(ParsedPortion(250.0, "ml", "milk"), parse("milk 250 ml").single())
    }

    @Test
    fun `a bare food names no measure at all`() {
        assertEquals(ParsedPortion(1.0, null, "dal"), parse("dal").single())
    }

    @Test
    fun `fractions and word numbers count`() {
        assertEquals(0.5, parse("1/2 cup rice").single().quantity, 0.001)
        assertEquals(2.0, parse("two roti").single().quantity, 0.001)
        assertEquals(1.0, parse("a bowl of poha").single().quantity, 0.001)
    }

    @Test
    fun `a measure the tables do not know is not a measure`() {
        // "handful" is nobody's portion row, so it stays part of the name and fails to match
        // later rather than being silently treated as a unit.
        val part = parse("a handful of peanuts").single()

        assertNull(part.unit)
        assertEquals("handful of peanuts", part.name)
    }

    @Test
    fun `text with nothing to name parses to nothing`() {
        assertTrue(parse("").isEmpty())
        assertTrue(parse("   ").isEmpty())
        assertTrue(parse("123").isEmpty())
    }

    @Test
    fun `a plural measure is the same measure`() {
        assertEquals(ParsedPortion(3.0, "roti", "roti"), parse("3 rotis").single())
    }
}
