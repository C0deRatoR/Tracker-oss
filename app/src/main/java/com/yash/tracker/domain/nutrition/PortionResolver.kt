package com.yash.tracker.domain.nutrition

import com.yash.tracker.data.local.entity.FoodEntity
import com.yash.tracker.data.local.entity.PortionMeasureEntity

/** One way to measure a food, and what it weighs. */
data class PortionChoice(
    val label: String,
    val unit: String,
    val gramsPerUnit: Double,
)

data class Macros(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
) {
    /** Two helpings, or two foods on one plate — the same addition either way. */
    operator fun plus(other: Macros): Macros = Macros(
        kcal = kcal + other.kcal,
        proteinG = proteinG + other.proteinG,
        carbsG = carbsG + other.carbsG,
        fatG = fatG + other.fatG,
    )

    companion object {
        val ZERO = Macros(0.0, 0.0, 0.0, 0.0)
    }
}

/**
 * The part of a nutrition panel that is not always printed.
 *
 * Separate from [Macros] rather than three more fields on it, because the two answer different
 * questions and have different rules. Macros are what a day is planned against — always known,
 * always summable, targeted. These are what a meal is judged on afterwards, and any of them can
 * be missing.
 *
 * Null means nobody measured it, which is not the same as zero: reading an absent sugar figure
 * as "no sugar" would have the review praising a dessert.
 */
data class Micros(
    val fibreG: Double? = null,
    val sugarG: Double? = null,
    val sodiumMg: Double? = null,
) {
    /** True when this source stated at least one of the three. */
    val isKnown: Boolean get() = fibreG != null || sugarG != null || sodiumMg != null

    /** Adds what both sides know. An unknown side contributes nothing and hides nothing. */
    operator fun plus(other: Micros): Micros = Micros(
        fibreG = fibreG addKnown other.fibreG,
        sugarG = sugarG addKnown other.sugarG,
        sodiumMg = sodiumMg addKnown other.sodiumMg,
    )

    /**
     * This, with anything it does not state taken from [fallback].
     *
     * Per field, not all-or-nothing. A catalogue row that knows its fibre and not its sugar
     * should still win on fibre, and letting its blank suppress a figure from somewhere else
     * would freeze that blank for ever.
     */
    fun orElse(fallback: Micros): Micros = Micros(
        fibreG = fibreG ?: fallback.fibreG,
        sugarG = sugarG ?: fallback.sugarG,
        sodiumMg = sodiumMg ?: fallback.sodiumMg,
    )

    operator fun times(ratio: Double): Micros = Micros(
        fibreG = fibreG?.times(ratio),
        sugarG = sugarG?.times(ratio),
        sodiumMg = sodiumMg?.times(ratio),
    )

    companion object {
        val UNKNOWN = Micros()
    }
}

private infix fun Double?.addKnown(other: Double?): Double? = when {
    this == null -> other
    other == null -> this
    else -> this + other
}

/**
 * Turns "2 roti" into grams. Food-specific measures win over generic ones (TRD §6), and a
 * food's own serving weight comes first because it is the most accurate thing we know.
 */
object PortionResolver {

    private const val GRAMS = "G"

    /** Generic measures worth offering on any food; the rest would be noise in a picker. */
    private val GENERIC_LABELS = listOf(
        "katori", "bowl", "cup", "glass", "plate", "piece", "tbsp", "tsp",
    )

    fun choicesFor(food: FoodEntity, measures: List<PortionMeasureEntity>): List<PortionChoice> {
        val choices = mutableListOf<PortionChoice>()

        food.portionLabel?.takeIf { it.isNotBlank() }?.let { label ->
            val name = label.removePrefix("1").trim().ifBlank { "serving" }
            choices += PortionChoice(name, name.uppercase(), food.defaultPortionG)
        }

        choices += PortionChoice("g", GRAMS, 1.0)

        // Food-specific rows override generics of the same name.
        val specific = measures.filter { it.foodId != null }.associateBy { it.label.lowercase() }
        val generic = measures.filter { it.foodId == null }.associateBy { it.label.lowercase() }

        val alreadyOffered = choices.map { it.label.lowercase() }.toSet()
        for (label in GENERIC_LABELS) {
            if (label in alreadyOffered) continue
            val measure = specific[label] ?: generic[label] ?: continue
            choices += PortionChoice(label, label.uppercase(), measure.grams)
        }

        return choices
    }

    fun grams(quantity: Double, choice: PortionChoice): Double = quantity * choice.gramsPerUnit

    fun macrosFor(source: NutritionPer100g, grams: Double): Macros {
        val ratio = grams / 100.0
        return Macros(
            kcal = source.kcal100g * ratio,
            proteinG = source.protein100g * ratio,
            carbsG = source.carbs100g * ratio,
            fatG = source.fat100g * ratio,
        )
    }

    /** The same scaling for the rest of the panel, leaving anything unstated unstated. */
    fun microsFor(source: NutritionPer100g, grams: Double): Micros {
        val ratio = grams / 100.0
        return Micros(
            fibreG = source.fibre100g?.times(ratio),
            sugarG = source.sugar100g?.times(ratio),
            sodiumMg = source.sodiumMg100g?.times(ratio),
        )
    }
}
