package com.yash.tracker.domain.nutrition

import com.yash.tracker.domain.diary.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun per100g(
    kcal: Double,
    p: Double,
    c: Double,
    f: Double,
    fibre: Double? = null,
    sugar: Double? = null,
    sodium: Double? = null,
) = object : NutritionPer100g {
    override val kcal100g = kcal
    override val protein100g = p
    override val carbs100g = c
    override val fat100g = f
    override val fibre100g = fibre
    override val sugar100g = sugar
    override val sodiumMg100g = sodium
}

/** Per 100 g, rounded off real catalogue rows so the numbers stay recognisable. */
private object Per100g {
    val paneer = per100g(kcal = 296.0, p = 18.3, c = 1.2, f = 25.0)
    val chickenBreast = per100g(kcal = 165.0, p = 31.0, c = 0.0, f = 3.6)
    val roti = per100g(kcal = 264.0, p = 8.0, c = 50.0, f = 4.0)
    val rice = per100g(kcal = 130.0, p = 2.7, c = 28.0, f = 0.3)
    val oil = per100g(kcal = 884.0, p = 0.0, c = 0.0, f = 100.0)
}

class MealSuggesterTest {

    private var nextId = 0L

    private fun servings(
        name: String,
        per100g: NutritionPer100g,
        portionG: Double,
        label: String? = null,
        familiarity: Int = 0,
    ): List<Serving> = Servings.ofFood(
        source = ServingSource.Food(++nextId),
        name = name,
        per100g = per100g,
        defaultPortionG = portionG,
        portionLabel = label,
        familiarity = familiarity,
    )

    private fun gap(kcal: Double, p: Double, c: Double, f: Double) = MacroGap(kcal, p, c, f)

    @Test
    fun `a protein-shaped gap is answered with protein, not with rice`() {
        val pool = servings("Chicken breast", Per100g.chickenBreast, portionG = 100.0) +
            servings("Rice", Per100g.rice, portionG = 150.0)

        val plate = MealSuggester.suggest(gap(kcal = 400.0, p = 60.0, c = 10.0, f = 10.0), pool).first()

        assertEquals("Chicken breast", plate.items.first().name)
    }

    @Test
    fun `a carb-shaped gap is answered with carbs`() {
        val pool = servings("Chicken breast", Per100g.chickenBreast, portionG = 100.0) +
            servings("Rice", Per100g.rice, portionG = 150.0)

        val plate = MealSuggester.suggest(gap(kcal = 350.0, p = 5.0, c = 70.0, f = 2.0), pool).first()

        assertEquals("Rice", plate.items.first().name)
    }

    @Test
    fun `overshooting a macro costs more than falling the same distance short`() {
        // Two oils: one lands the fat gap exactly, one doubles it. Same calorie distance from
        // a plate that is half the gap, so only the direction of the miss separates them.
        val pool = servings("Oil", Per100g.oil, portionG = 20.0)
        val plates = MealSuggester.suggest(gap(kcal = 180.0, p = 0.0, c = 0.0, f = 20.0), pool)

        val chosen = plates.first().items.first()
        assertEquals("the exact portion should win", 20.0, chosen.grams!!, 0.01)
    }

    @Test
    fun `two foods are combined when neither closes the gap alone`() {
        val pool = servings("Paneer", Per100g.paneer, portionG = 100.0) +
            servings("Roti", Per100g.roti, portionG = 40.0, label = "1 roti")

        val plate = MealSuggester.suggest(gap(kcal = 600.0, p = 35.0, c = 55.0, f = 28.0), pool).first()

        assertEquals(2, plate.items.size)
        assertEquals(setOf("Paneer", "Roti"), plate.items.map { it.name }.toSet())
    }

    @Test
    fun `a single food that already fits is not padded out`() {
        val pool = servings("Chicken breast", Per100g.chickenBreast, portionG = 100.0) +
            servings("Oil", Per100g.oil, portionG = 20.0)

        // 100 g of chicken is this gap almost exactly; adding oil can only overshoot.
        val plate = MealSuggester.suggest(gap(kcal = 165.0, p = 31.0, c = 0.0, f = 3.6), pool).first()

        assertEquals(1, plate.items.size)
    }

    @Test
    fun `nothing is suggested when the allowance is spent`() {
        val pool = servings("Rice", Per100g.rice, portionG = 150.0)
        assertTrue(MealSuggester.suggest(gap(60.0, 5.0, 5.0, 1.0), pool).isEmpty())
    }

    @Test
    fun `a serving that cannot fit the remaining calories is never offered`() {
        val pool = servings("Rice", Per100g.rice, portionG = 150.0)
        val plates = MealSuggester.suggest(gap(kcal = 120.0, p = 3.0, c = 25.0, f = 1.0), pool)

        assertTrue(plates.isNotEmpty())
        assertTrue(plates.all { it.total.kcal <= 120.0 * 1.05 })
    }

    @Test
    fun `suggestions differ in their main item rather than repeating one food at five sizes`() {
        val pool = servings("Paneer", Per100g.paneer, portionG = 100.0) +
            servings("Chicken breast", Per100g.chickenBreast, portionG = 100.0) +
            servings("Rice", Per100g.rice, portionG = 150.0)

        val plates = MealSuggester.suggest(gap(kcal = 500.0, p = 40.0, c = 30.0, f = 15.0), pool)

        val leads = plates.map { it.items.first().source }
        assertEquals(leads.size, leads.distinct().size)
    }

    @Test
    fun `familiarity separates a tie without overturning a better fit`() {
        val familiar = servings("Rice", Per100g.rice, portionG = 150.0, familiarity = 40)
        val strange = servings("Chicken breast", Per100g.chickenBreast, portionG = 100.0)

        // A protein gap: familiarity with rice must not beat chicken being the right answer.
        val protein = MealSuggester.suggest(gap(400.0, 60.0, 10.0, 10.0), familiar + strange)
        assertEquals("Chicken breast", protein.first().items.first().name)
    }

    @Test
    fun `portions round to servings a person would actually eat`() {
        val roti = servings("Roti", Per100g.roti, portionG = 40.0, label = "1 roti")

        assertTrue("1 roti" in roti.map { it.portionLabel })
        assertTrue("2 roti" in roti.map { it.portionLabel })
        // Half a roti is a weight, not a count.
        assertTrue("20 g" in roti.map { it.portionLabel })
        assertTrue(roti.none { it.portionLabel.startsWith("0.") })
    }

    @Test
    fun `a saved meal is offered whole and never gets a side`() {
        val savedMeal = Serving(
            source = ServingSource.SavedMeal(1),
            name = "Post-gym plate",
            portionLabel = "1 serving",
            quantity = 1.0,
            unit = "SERVING",
            grams = null,
            macros = Macros(kcal = 380.0, proteinG = 30.0, carbsG = 40.0, fatG = 8.0),
            combinable = false,
        )
        val pool = listOf(savedMeal) + servings("Rice", Per100g.rice, portionG = 150.0)

        val plate = MealSuggester
            .suggest(gap(kcal = 700.0, p = 45.0, c = 80.0, f = 18.0), pool)
            .first { it.items.first().source is ServingSource.SavedMeal }

        assertEquals(1, plate.items.size)
    }

    @Test
    fun `a saved meal is never added as a side to something else`() {
        val savedMeal = Serving(
            source = ServingSource.SavedMeal(1),
            name = "Post-gym plate",
            portionLabel = "1 serving",
            quantity = 1.0,
            unit = "SERVING",
            grams = null,
            macros = Macros(kcal = 200.0, proteinG = 25.0, carbsG = 12.0, fatG = 4.0),
            combinable = false,
        )
        val pool = listOf(savedMeal) + servings("Rice", Per100g.rice, portionG = 150.0)

        val plates = MealSuggester.suggest(gap(kcal = 600.0, p = 40.0, c = 60.0, f = 12.0), pool)

        assertTrue(
            plates.none { plate ->
                plate.items.drop(1).any { it.source is ServingSource.SavedMeal }
            },
        )
    }

    @Test
    fun `the search finds a pairing a greedy first pick would miss`() {
        // The best single item is a big serving of chicken; stopping there leaves all the carbs
        // unpaid. Chicken and rice together is the plate, and it has to be found.
        val pool = servings("Chicken breast", Per100g.chickenBreast, portionG = 100.0) +
            servings("Rice", Per100g.rice, portionG = 150.0) +
            servings("Oil", Per100g.oil, portionG = 10.0)

        val plate = MealSuggester.suggest(gap(kcal = 560.0, p = 50.0, c = 60.0, f = 8.0), pool).first()

        assertEquals(setOf("Chicken breast", "Rice"), plate.items.map { it.name }.toSet())
    }

    @Test
    fun `at most four plates, all different`() {
        val pool = servings("Paneer", Per100g.paneer, portionG = 100.0) +
            servings("Chicken breast", Per100g.chickenBreast, portionG = 100.0) +
            servings("Rice", Per100g.rice, portionG = 150.0) +
            servings("Roti", Per100g.roti, portionG = 40.0, label = "1 roti") +
            servings("Oil", Per100g.oil, portionG = 10.0)

        val plates = MealSuggester.suggest(gap(kcal = 700.0, p = 45.0, c = 70.0, f = 22.0), pool)

        assertEquals(4, plates.size)
        val sets = plates.map { plate -> plate.items.map { it.source }.toSet() }
        assertEquals(sets.size, sets.distinct().size)
    }

    @Test
    fun `what was already eaten today gives way to an equal alternative`() {
        val chicken = servings("Chicken breast", Per100g.chickenBreast, portionG = 100.0)
        // Same numbers, different food: only the diary can separate them.
        val turkey = servings("Turkey breast", Per100g.chickenBreast, portionG = 100.0)
        val context = PlateContext(eatenToday = setOf(chicken.first().source))

        val plate = MealSuggester.suggest(gap(165.0, 31.0, 0.0, 3.6), chicken + turkey, context).first()

        assertEquals("Turkey breast", plate.items.first().name)
        assertTrue(PlateTag.HAD_TODAY !in plate.tags)
    }

    @Test
    fun `a food eaten at this meal beats a twin that never is`() {
        val poha = servings("Poha", Per100g.rice, portionG = 150.0)
        val rice = servings("Rice", Per100g.rice, portionG = 150.0)
        val context = PlateContext(
            affinity = mapOf(
                poha.first().source to SlotCount(atThisMeal = 12, total = 12),
                rice.first().source to SlotCount(atThisMeal = 0, total = 20),
            ),
        )

        val plate = MealSuggester.suggest(gap(200.0, 4.0, 42.0, 0.5), rice + poha, context).first()

        assertEquals("Poha", plate.items.first().name)
        assertTrue(PlateTag.USUAL_FOR_MEAL in plate.tags)
    }

    @Test
    fun `fibre separates two plates that fit alike`() {
        val white = servings("White rice", per100g(130.0, 2.7, 28.0, 0.3, fibre = 0.4), portionG = 150.0)
        val brown = servings("Brown rice", per100g(130.0, 2.7, 28.0, 0.3, fibre = 3.5), portionG = 150.0)

        val plate = MealSuggester.suggest(
            gap(200.0, 4.0, 42.0, 0.5),
            white + brown,
            PlateContext(fibreNeedG = 8.0),
        ).first()

        assertEquals("Brown rice", plate.items.first().name)
    }

    @Test
    fun `good micros never rescue a plate that misses the macros`() {
        // A protein gap. Lentil-like fibre bomb with little protein against plain chicken.
        val fibrous = servings("Bran", per100g(200.0, 5.0, 40.0, 2.0, fibre = 30.0, sugar = 1.0, sodium = 5.0), portionG = 100.0)
        val chicken = servings("Chicken breast", per100g(165.0, 31.0, 0.0, 3.6, fibre = 0.0, sugar = 0.0, sodium = 900.0), portionG = 100.0)

        val plate = MealSuggester.suggest(
            gap(330.0, 60.0, 5.0, 7.0),
            fibrous + chicken,
            PlateContext(fibreNeedG = 10.0, sodiumRoomMg = 300.0),
        ).first()

        assertEquals("Chicken breast", plate.items.first().name)
    }

    @Test
    fun `a stranger from the catalogue loses a tie to food the user eats`() {
        val known = servings("Chicken breast", Per100g.chickenBreast, portionG = 100.0)
        val stranger = Servings.ofFood(
            source = ServingSource.Food(++nextId),
            name = "Chicken tikka",
            per100g = Per100g.chickenBreast,
            defaultPortionG = 100.0,
            portionLabel = null,
            isNew = true,
        )

        val plates = MealSuggester.suggest(gap(165.0, 31.0, 0.0, 3.6), stranger + known)

        assertEquals("Chicken breast", plates.first().items.first().name)
        assertTrue(plates.first { it.items.first().name == "Chicken tikka" }.tags.contains(PlateTag.NEW_FOOD))
    }

    @Test
    fun `a counted food can be offered three or four at a time`() {
        val roti = servings("Roti", Per100g.roti, portionG = 40.0, label = "1 roti")
        assertTrue("4 roti" in roti.map { it.portionLabel })
    }

    @Test
    fun `a food with no calories per 100 g offers nothing`() {
        val water = servings("Water", per100g(0.0, 0.0, 0.0, 0.0), portionG = 250.0)
        assertTrue(water.isEmpty())
    }
}

class MacroGapTest {

    private val target = Macros(kcal = 2000.0, proteinG = 150.0, carbsG = 200.0, fatG = 60.0)

    @Test
    fun `an overshot macro is finished rather than owed backwards`() {
        val eaten = Macros(kcal = 900.0, proteinG = 40.0, carbsG = 230.0, fatG = 20.0)
        val gap = MacroGap.between(target, eaten)

        assertEquals(1100.0, gap.kcal, 0.01)
        assertEquals(110.0, gap.proteinG, 0.01)
        assertEquals("carbs are spent, not negative", 0.0, gap.carbsG, 0.01)
    }

    @Test
    fun `calories are allowed to go negative because the day really is over budget`() {
        val eaten = Macros(kcal = 2200.0, proteinG = 150.0, carbsG = 200.0, fatG = 60.0)
        assertTrue(MacroGap.between(target, eaten).kcal < 0)
    }

    @Test
    fun `mid-afternoon leaves a snack and dinner`() {
        assertEquals(
            listOf(MealType.SNACK, MealType.DINNER),
            MealBudget.slotsLeft(hour = 16),
        )
    }

    @Test
    fun `breakfast is gone by the afternoon`() {
        assertTrue(MealType.BREAKFAST !in MealBudget.slotsLeft(hour = 14))
    }

    @Test
    fun `the small hours belong to the tail of the night before`() {
        assertEquals(listOf(MealType.SNACK), MealBudget.slotsLeft(hour = 2))
    }

    @Test
    fun `a logged meal drops out of what is left`() {
        val left = MealBudget.slotsLeft(hour = 12, alreadyLogged = setOf(MealType.LUNCH))
        assertEquals(listOf(MealType.SNACK, MealType.DINNER), left)
    }

    @Test
    fun `dinner does not get the whole remainder when a snack is still to come`() {
        val gap = MacroGap(kcal = 900.0, proteinG = 60.0, carbsG = 90.0, fatG = 30.0)
        val share = MealBudget.shareFor(gap, hour = 16)

        assertEquals(MealType.SNACK, share.meal)
        // Snack is 0.10 of a day against dinner's 0.30, so a quarter of what is left.
        assertEquals(225.0, share.gap.kcal, 0.5)
    }

    @Test
    fun `the last meal of the day gets everything that is left`() {
        val gap = MacroGap(kcal = 700.0, proteinG = 50.0, carbsG = 70.0, fatG = 25.0)
        val share = MealBudget.shareFor(gap, hour = 19)

        assertEquals(MealType.DINNER, share.meal)
        assertEquals(700.0, share.gap.kcal, 0.5)
    }

    @Test
    fun `everything logged still leaves room for a snack`() {
        val all = MealType.entries.toSet()
        assertEquals(listOf(MealType.SNACK), MealBudget.slotsLeft(hour = 20, alreadyLogged = all))
    }
    @Test
    fun `with no history the textbook split stands`() {
        val shares = MealBudget.learnShares(emptyList())
        assertEquals(0.25, shares.getValue(MealType.BREAKFAST), 1e-9)
    }

    @Test
    fun `a fortnight of small breakfasts shrinks breakfast`() {
        val day = mapOf(
            MealType.BREAKFAST to 200.0,
            MealType.LUNCH to 800.0,
            MealType.SNACK to 200.0,
            MealType.DINNER to 800.0,
        )
        val shares = MealBudget.learnShares(List(14) { day })

        // Observed 0.10; fourteen days moves two thirds of the way from 0.25.
        assertEquals(0.15, shares.getValue(MealType.BREAKFAST), 0.01)
        assertEquals(1.0, shares.values.sum(), 1e-9)
    }

    @Test
    fun `a half-logged day does not redraw the budget`() {
        val lunchOnly = mapOf(MealType.LUNCH to 600.0)
        assertEquals(MealBudget.learnShares(emptyList()), MealBudget.learnShares(List(10) { lunchOnly }))
    }

    @Test
    fun `a meal the user never eats still keeps a small share`() {
        val noSnack = mapOf(MealType.BREAKFAST to 500.0, MealType.LUNCH to 800.0, MealType.DINNER to 700.0)
        val shares = MealBudget.learnShares(List(60) { noSnack })
        assertTrue(shares.getValue(MealType.SNACK) >= 0.04)
    }

    @Test
    fun `learned shares change how much of the rest the next meal gets`() {
        val gap = MacroGap(kcal = 1000.0, proteinG = 60.0, carbsG = 100.0, fatG = 30.0)
        val bigDinner = mapOf(
            MealType.BREAKFAST to 0.25,
            MealType.LUNCH to 0.35,
            MealType.SNACK to 0.05,
            MealType.DINNER to 0.35,
        )
        val share = MealBudget.shareFor(gap, hour = 16, shares = bigDinner)
        assertEquals(125.0, share.gap.kcal, 0.5)
        assertEquals(0.125, share.fraction, 1e-9)
    }

    @Test
    fun `a meal is asked for its share of the fibre still owed`() {
        val status = DayMicroStatus.of(targetKcal = 2000.0, eaten = Micros(fibreG = 8.0, sodiumMg = 1300.0))
        val (fibre, sodium) = status.forMeal(0.5)

        assertEquals(28.0, status.fibreTargetG, 1e-9)
        assertEquals(10.0, fibre, 1e-9)
        assertEquals(500.0, sodium, 1e-9)
    }
}
