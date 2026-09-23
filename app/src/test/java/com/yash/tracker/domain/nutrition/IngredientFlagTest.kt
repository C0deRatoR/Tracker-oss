package com.yash.tracker.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a label reading says about the ingredients, on its way to and from the product row.
 *
 * Both ends of this are untrusted. The model writes the tone, and a column can have been
 * written by an older build or edited by hand — neither is allowed to take a product down with
 * it, because losing the macros over a malformed flag would be a very poor trade.
 */
class IngredientFlagTest {

    private val flags = listOf(
        IngredientFlag("Added sugar", "2nd ingredient", FlagTone.CONCERN),
        IngredientFlag("Palm oil", tone = FlagTone.CONCERN),
        IngredientFlag("No artificial colours", tone = FlagTone.FINE),
    )

    @Test
    fun `flags survive the round trip through the column`() {
        val restored = IngredientFlags.decode(IngredientFlags.encode(flags))

        assertEquals(flags, restored)
    }

    @Test
    fun `no flags is a null column rather than an empty list written out`() {
        assertNull(IngredientFlags.encode(emptyList()))
        assertTrue(IngredientFlags.decode(null).isEmpty())
        assertTrue(IngredientFlags.decode("").isEmpty())
    }

    @Test
    fun `a malformed column reads as no flags, not as a crash`() {
        assertTrue(IngredientFlags.decode("not json at all").isEmpty())
        assertTrue(IngredientFlags.decode("""{"label":"an object, not a list"}""").isEmpty())
        assertTrue(IngredientFlags.decode("""[{"tone":"CONCERN"}]""").isEmpty())
    }

    @Test
    fun `a tone the model invented reads as the neutral one`() {
        assertEquals(FlagTone.CONCERN, FlagTone.parse("CONCERN"))
        assertEquals("case is not the model's to get right", FlagTone.FINE, FlagTone.parse("fine"))
        assertEquals(FlagTone.NOTE, FlagTone.parse("  note  "))
        assertEquals(FlagTone.NOTE, FlagTone.parse("DANGER"))
        assertEquals(FlagTone.NOTE, FlagTone.parse(null))
        assertEquals(FlagTone.NOTE, FlagTone.parse(""))
    }
}
