package com.yash.tracker.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The one thing these numbers must never do is turn "nobody measured it" into "there is none". */
class MicrosTest {

    private fun per100g(
        fibre: Double? = null,
        sugar: Double? = null,
        sodium: Double? = null,
    ) = object : NutritionPer100g {
        override val kcal100g = 100.0
        override val protein100g = 5.0
        override val carbs100g = 20.0
        override val fat100g = 1.0
        override val fibre100g = fibre
        override val sugar100g = sugar
        override val sodiumMg100g = sodium
    }

    @Test
    fun `an unstated figure scales to nothing, not to zero`() {
        val micros = PortionResolver.microsFor(per100g(sugar = 10.0), grams = 200.0)

        assertEquals(20.0, micros.sugarG!!, 0.001)
        assertNull("fibre was never stated", micros.fibreG)
        assertNull(micros.sodiumMg)
    }

    @Test
    fun `adding a known figure to an unknown one keeps the known one`() {
        val known = Micros(sugarG = 12.0)
        val unknown = Micros.UNKNOWN

        assertEquals(12.0, (known + unknown).sugarG!!, 0.001)
        assertEquals(12.0, (unknown + known).sugarG!!, 0.001)
        assertNull((known + unknown).fibreG)
    }

    @Test
    fun `two unknowns stay unknown rather than summing to zero`() {
        val total = Micros.UNKNOWN + Micros.UNKNOWN

        assertNull(total.sugarG)
        assertFalse(total.isKnown)
    }

    @Test
    fun `a row that states anything at all counts as known`() {
        assertTrue(Micros(sodiumMg = 400.0).isKnown)
        assertFalse(Micros.UNKNOWN.isKnown)
    }

    @Test
    fun `rescaling moves every stated figure and invents none`() {
        val scaled = Micros(fibreG = 3.0, sugarG = 8.0) * 0.5

        assertEquals(1.5, scaled.fibreG!!, 0.001)
        assertEquals(4.0, scaled.sugarG!!, 0.001)
        assertNull(scaled.sodiumMg)
    }
}
