package com.yash.tracker.domain.nutrition

import com.yash.tracker.data.local.entity.FoodEntity
import com.yash.tracker.data.local.entity.PortionMeasureEntity
import com.yash.tracker.domain.diary.MealType
import com.yash.tracker.domain.diary.MealWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortionResolverTest {

    private fun food(
        id: Long = 1,
        name: String = "Chapati/Roti",
        kcal: Double = 202.0,
        portionLabel: String? = "1 chapati",
        portionG: Double = 36.0,
    ) = FoodEntity(
        id = id,
        name = name,
        altNames = null,
        category = "COMPOSITE",
        source = "INDB",
        sourceRef = "ASC096",
        isComposite = true,
        kcal100g = kcal,
        protein100g = 5.9,
        carbs100g = 35.6,
        fat100g = 3.6,
        fibre100g = null,
        sugar100g = null,
        sodiumMg100g = null,
        defaultPortionG = portionG,
        portionLabel = portionLabel,
        createdAt = 0L,
    )

    private val generics = listOf(
        PortionMeasureEntity(id = 1, foodId = null, label = "katori", grams = 150.0),
        PortionMeasureEntity(id = 2, foodId = null, label = "cup", grams = 240.0),
        PortionMeasureEntity(id = 3, foodId = null, label = "tbsp", grams = 15.0),
    )

    @Test
    fun `a food's own serving is offered first`() {
        val choices = PortionResolver.choicesFor(food(), generics)

        assertEquals("chapati", choices.first().label)
        assertEquals(36.0, choices.first().gramsPerUnit, 0.01)
    }

    @Test
    fun `grams is always available`() {
        val choices = PortionResolver.choicesFor(food(portionLabel = null), generics)

        assertEquals("g", choices.first().label)
        assertEquals(1.0, choices.first().gramsPerUnit, 0.01)
    }

    @Test
    fun `generic household measures are offered too`() {
        val labels = PortionResolver.choicesFor(food(), generics).map { it.label }

        assertTrue("katori" in labels)
        assertTrue("cup" in labels)
    }

    @Test
    fun `a food-specific measure beats the generic of the same name`() {
        val measures = generics + PortionMeasureEntity(
            id = 9,
            foodId = 1,
            label = "katori",
            grams = 90.0,
        )
        val katori = PortionResolver.choicesFor(food(), measures).first { it.label == "katori" }

        assertEquals(90.0, katori.gramsPerUnit, 0.01)
    }

    @Test
    fun `two roti weigh twice one roti`() {
        val chapati = PortionResolver.choicesFor(food(), generics).first()

        assertEquals(72.0, PortionResolver.grams(2.0, chapati), 0.01)
    }

    @Test
    fun `macros scale with grams`() {
        val macros = PortionResolver.macrosFor(food(), grams = 72.0)

        // 202 kcal/100g over 72g
        assertEquals(145.4, macros.kcal, 0.1)
        assertEquals(4.2, macros.proteinG, 0.1)
    }

    @Test
    fun `a half portion halves the macros`() {
        val full = PortionResolver.macrosFor(food(), 100.0)
        val half = PortionResolver.macrosFor(food(), 50.0)

        assertEquals(full.kcal / 2, half.kcal, 0.001)
    }

    @Test
    fun `meal defaults follow the clock`() {
        assertEquals(MealType.BREAKFAST, MealWindows.defaultFor(8))
        assertEquals(MealType.LUNCH, MealWindows.defaultFor(13))
        assertEquals(MealType.DINNER, MealWindows.defaultFor(20))
        assertEquals(MealType.SNACK, MealWindows.defaultFor(17))
        assertEquals(MealType.SNACK, MealWindows.defaultFor(2))
    }
}
