package com.yash.tracker.data.repository

import com.yash.tracker.data.local.entity.FoodEntity
import com.yash.tracker.data.local.entity.ProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Renaming a row has to move its macros with it.
 *
 * The case this comes from: a photo of oats, milk and a banana came back as one row called
 * "muesli with milk". Renaming that row to "oats" while it kept muesli's numbers is not a
 * correction — it is the same wrong figure under a more convincing label.
 *
 * [swappedFor] already does the arithmetic. What is different here is the name: the user has
 * typed what they want the row called, so the match supplies numbers only.
 */
class RenameRematchTest {

    private val oats = FoodEntity(
        id = 11,
        name = "Oats, rolled",
        altNames = null,
        category = "Cereals",
        source = "USDA",
        sourceRef = null,
        kcal100g = 389.0,
        protein100g = 16.9,
        carbs100g = 66.3,
        fat100g = 6.9,
        fibre100g = 10.6,
        sugar100g = null,
        sodiumMg100g = null,
        defaultPortionG = 40.0,
        portionLabel = null,
        createdAt = 1,
    )

    private val amul = ProductEntity(
        id = 3,
        brand = "Amul",
        name = "Taaza Toned Milk",
        kcal100g = 58.0,
        protein100g = 3.1,
        carbs100g = 4.7,
        fat100g = 3.0,
        fibre100g = null,
        sugar100g = null,
        sodiumMg100g = null,
        servingG = 200.0,
        servingLabel = "1 glass",
        ingredients = null,
        labelPhotoUri = null,
        barcode = null,
        createdAt = 1,
        updatedAt = 1,
    )

    /** The bad row: one line for what was really three foods. */
    private fun muesli(grams: Double = 60.0) = DraftItem(
        id = 0,
        name = "Muesli with milk",
        originalName = "Muesli with milk",
        originalGrams = grams,
        quantity = 1.0,
        unit = "cup",
        grams = grams,
        kcal = 220.0,
        proteinG = 6.0,
        carbsG = 34.0,
        fatG = 6.0,
        confidence = 0.7,
        basis = "estimate",
        sourceNote = "estimated from a photo",
    )

    @Test
    fun `renaming a row applies the catalogue's numbers at the weight already on it`() {
        val renamed = muesli(grams = 60.0).copy(name = "oats").rematchedTo(oats)

        assertEquals("the weight on the plate did not change", 60.0, renamed.grams, 0.01)
        assertEquals(233.4, renamed.kcal, 0.01)
        assertEquals(10.14, renamed.proteinG, 0.01)
        assertEquals(39.78, renamed.carbsG, 0.01)
        assertEquals(4.14, renamed.fatG, 0.01)
    }

    @Test
    fun `the name the user typed survives the match`() {
        val renamed = muesli().copy(name = "oats").rematchedTo(oats)

        assertEquals("oats", renamed.name)
        assertEquals(
            "and the row says where the numbers came from",
            "from the food catalogue — Oats, rolled",
            renamed.sourceNote,
        )
    }

    @Test
    fun `a rename onto the user's own product is exact and keeps their wording`() {
        val renamed = muesli(grams = 200.0).copy(name = "milk").rematchedTo(amul)

        assertEquals("milk", renamed.name)
        assertTrue("a product's numbers came off a packet", renamed.isExact)
        assertEquals("your product — Amul Taaza Toned Milk", renamed.sourceNote)
        assertEquals(116.0, renamed.kcal, 0.01)
    }

    @Test
    fun `a rematched row stops claiming the old estimate's confidence`() {
        val renamed = muesli().copy(name = "oats").rematchedTo(oats)

        assertNull(renamed.confidence)
        assertNull(renamed.basis)
        assertEquals("and points at the food it matched", 11L, renamed.foodId)
    }
}
