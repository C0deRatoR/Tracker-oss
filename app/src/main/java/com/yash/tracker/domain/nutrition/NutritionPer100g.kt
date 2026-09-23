package com.yash.tracker.domain.nutrition

/**
 * Anything whose macros are stated per 100 g.
 *
 * A catalogue food and a product the user added from a label carry the same four numbers and
 * scale the same way, so the portion maths does not need to know which it is holding.
 */
interface NutritionPer100g {
    val kcal100g: Double
    val protein100g: Double
    val carbs100g: Double
    val fat100g: Double

    /**
     * The rest of the panel, which not every source states.
     *
     * Nullable, and defaulted here rather than required: the four above are what makes a row
     * loggable, these three are what makes it reviewable, and a source that has never heard of
     * sugar should say so rather than claim zero. The seeded tables fill them for 77–100% of
     * rows depending on the source; a hand-entered food fills none.
     */
    val fibre100g: Double? get() = null
    val sugar100g: Double? get() = null
    val sodiumMg100g: Double? get() = null
}
