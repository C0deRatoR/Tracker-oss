package com.yash.tracker.data.repository

import com.yash.tracker.data.local.entity.FoodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * PRD §5.4.4: a misidentified row can be swapped for a matching item from the bundled
 * catalogue, not just one of the user's own products — and that swap must correct the
 * macros along with the name, not just the name.
 */
class FoodSwapTest {

    private val tofu = FoodEntity(
        id = 55,
        name = "Tofu, firm",
        altNames = null,
        category = "PROTEIN",
        source = "USDA",
        sourceRef = null,
        isComposite = false,
        kcal100g = 144.0,
        protein100g = 15.0,
        carbs100g = 3.0,
        fat100g = 9.0,
        fibre100g = 2.0,
        sugar100g = null,
        sodiumMg100g = null,
        defaultPortionG = 100.0,
        portionLabel = null,
        isVerified = true,
        timesLogged = 0,
        createdAt = 1,
    )

    private fun estimate(grams: Double = 200.0) = DraftItem(
        id = 0,
        name = "Paneer sabji",
        originalName = "Paneer sabji",
        originalGrams = grams,
        quantity = 1.0,
        unit = "g",
        grams = grams,
        kcal = 380.0,
        proteinG = 18.0,
        carbsG = 12.0,
        fatG = 28.0,
        confidence = 0.4,
        basis = "estimate",
        sourceNote = "estimated from a photo",
        foodId = 42,
    )

    @Test
    fun `the catalogue's numbers replace the estimate at the weight already on the row`() {
        val swapped = estimate(grams = 200.0).swappedFor(tofu)

        assertEquals("the plate didn't change, only whose numbers describe it", 200.0, swapped.grams, 0.01)
        assertEquals(288.0, swapped.kcal, 0.01)
        assertEquals(30.0, swapped.proteinG, 0.01)
        assertEquals(6.0, swapped.carbsG, 0.01)
        assertEquals(18.0, swapped.fatG, 0.01)
    }

    @Test
    fun `the name changes along with the macros`() {
        val swapped = estimate().swappedFor(tofu)

        assertEquals("Tofu, firm", swapped.name)
    }

    @Test
    fun `the row points at the catalogue, not a product`() {
        val swapped = estimate().swappedFor(tofu)

        assertEquals(55L, swapped.foodId)
        assertNull(swapped.productId)
    }

    @Test
    fun `a catalogue match is not flagged as needing attention`() {
        val swapped = estimate().swappedFor(tofu)

        assertNull(swapped.confidence)
        assertNull(swapped.basis)
        assertFalse(swapped.needsAttention)
    }

    @Test
    fun `a row with no weight falls back to the food's default portion`() {
        val swapped = estimate(grams = 0.0).swappedFor(tofu)

        assertEquals(100.0, swapped.grams, 0.01)
        assertEquals(144.0, swapped.kcal, 0.01)
    }
}
