package com.yash.tracker.data.repository

import com.yash.tracker.data.local.entity.ProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PRD §7.6: a product added from a label is selectable on the recognition screen and its macros
 * replace the estimate exactly. "Exactly" is the word being tested here.
 */
class ProductSwapTest {

    /** Real numbers off a real jar. */
    private val pintola = ProductEntity(
        id = 7,
        brand = "Pintola",
        name = "Peanut Based Spread with Dark Chocolate",
        kcal100g = 587.0,
        protein100g = 24.0,
        carbs100g = 27.0,
        fat100g = 43.3,
        fibre100g = 7.9,
        sugar100g = null,
        sodiumMg100g = null,
        servingG = 32.0,
        servingLabel = "2 Tbsp.",
        ingredients = null,
        labelPhotoUri = null,
        barcode = null,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun estimate(grams: Double = 30.0) = DraftItem(
        id = 0,
        name = "Peanut butter",
        originalName = "Peanut butter",
        originalGrams = grams,
        quantity = 1.0,
        unit = "tbsp",
        grams = grams,
        kcal = 180.0,
        proteinG = 7.0,
        carbsG = 6.0,
        fatG = 15.0,
        confidence = 0.4,
        basis = "estimate",
        sourceNote = "estimated from a photo",
        foodId = 42,
    )

    @Test
    fun `the product's numbers replace the estimate at the weight already on the row`() {
        val swapped = estimate(grams = 30.0).swappedFor(pintola)

        assertEquals("the plate didn't change, only whose numbers describe it", 30.0, swapped.grams, 0.01)
        assertEquals(176.1, swapped.kcal, 0.01)
        assertEquals(7.2, swapped.proteinG, 0.01)
        assertEquals(8.1, swapped.carbsG, 0.01)
        assertEquals(12.99, swapped.fatG, 0.01)
    }

    @Test
    fun `a swapped row is exact, not an estimate`() {
        val swapped = estimate().swappedFor(pintola)

        assertTrue(swapped.isExact)
        assertNull("an exact row carries no confidence", swapped.confidence)
        assertNull(swapped.basis)
        assertFalse("and is never flagged as needing attention", swapped.needsAttention)
    }

    @Test
    fun `the row stops pointing at the catalogue`() {
        val swapped = estimate().swappedFor(pintola)

        assertEquals(7L, swapped.productId)
        assertNull("the catalogue match no longer applies", swapped.foodId)
    }

    @Test
    fun `the name becomes the product's, brand included`() {
        val swapped = estimate().swappedFor(pintola)

        assertEquals("Pintola Peanut Based Spread with Dark Chocolate", swapped.name)
    }

    @Test
    fun `a row with no weight falls back to the pack's serving`() {
        val swapped = estimate(grams = 0.0).swappedFor(pintola)

        assertEquals(32.0, swapped.grams, 0.01)
        assertEquals(187.84, swapped.kcal, 0.01)
    }

    @Test
    fun `a row with no weight and a pack with no serving falls back to 100 g`() {
        val swapped = estimate(grams = 0.0).swappedFor(pintola.copy(servingG = null))

        assertEquals(100.0, swapped.grams, 0.01)
        assertEquals("100 g of a 587 kcal per 100 g product", 587.0, swapped.kcal, 0.01)
    }

    @Test
    fun `swapping twice lands on the second product, not a compound of both`() {
        val other = pintola.copy(id = 9, brand = "Alpino", kcal100g = 600.0)
        val swapped = estimate(grams = 50.0).swappedFor(pintola).swappedFor(other)

        assertEquals(9L, swapped.productId)
        assertEquals(50.0, swapped.grams, 0.01)
        assertEquals(300.0, swapped.kcal, 0.01)
    }

    @Test
    fun `what the user originally typed survives, so the rename is still learnable`() {
        val swapped = estimate().swappedFor(pintola)

        assertEquals("Peanut butter", swapped.originalName)
    }
}
