package com.yash.tracker.data.repository

import com.yash.tracker.domain.nutrition.PortionChoice
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Editing how much of something is on the row.
 *
 * The confirm screen used to offer grams and nothing else, which is not how anyone thinks about
 * a portion — "three rotis" is the thought, and 120 g is the translation. Both views edit one
 * pair of numbers, so the rule under all of this is that grams, count and macros can never
 * disagree about the same row.
 */
class PortionEditTest {

    /** What the model returns for two rotis: a count, a unit, and its own weight for them. */
    private val roti = DraftItem(
        id = 0,
        name = "Roti",
        originalName = "Roti",
        originalGrams = 80.0,
        quantity = 2.0,
        unit = "roti",
        grams = 80.0,
        kcal = 200.0,
        proteinG = 6.0,
        carbsG = 40.0,
        fatG = 2.0,
        confidence = 0.8,
        basis = "reference",
        sourceNote = null,
    )

    @Test
    fun `one unit's weight is read off the row the model returned`() {
        assertEquals(40.0, roti.gramsPerUnit, 0.01)
    }

    @Test
    fun `rescaling moves every macro by the same ratio`() {
        val bigger = roti.rescaledTo(120.0)

        assertEquals(120.0, bigger.grams, 0.01)
        assertEquals(300.0, bigger.kcal, 0.01)
        assertEquals(9.0, bigger.proteinG, 0.01)
        assertEquals(60.0, bigger.carbsG, 0.01)
        assertEquals(3.0, bigger.fatG, 0.01)
    }

    @Test
    fun `a row with no weight cannot be rescaled into one`() {
        val weightless = roti.copy(grams = 0.0, kcal = 0.0)

        assertEquals(0.0, weightless.gramsPerUnit, 0.01)
        assertEquals("and rescaling it stays at zero rather than dividing by it", 0.0, weightless.rescaledTo(50.0).kcal, 0.01)
    }

    @Test
    fun `switching the measure restates the portion without changing it`() {
        val katori = PortionChoice(label = "katori", unit = "KATORI", gramsPerUnit = 150.0)
        val asKatori = roti.copy(
            unit = katori.label,
            quantity = roti.grams / katori.gramsPerUnit,
        )

        assertEquals("the food on the plate did not change", 80.0, asKatori.grams, 0.01)
        assertEquals("nor did what it is worth", 200.0, asKatori.kcal, 0.01)
        assertEquals("only how it is counted", 0.533, asKatori.quantity, 0.001)
    }

    @Test
    fun `a plain gram row counts one per gram, so switching to it is lossless`() {
        val grams = PortionChoice(label = "g", unit = "G", gramsPerUnit = 1.0)
        val asGrams = roti.copy(unit = grams.label, quantity = roti.grams / grams.gramsPerUnit)

        assertEquals(80.0, asGrams.quantity, 0.01)
        assertEquals(80.0, asGrams.grams, 0.01)
    }
}
