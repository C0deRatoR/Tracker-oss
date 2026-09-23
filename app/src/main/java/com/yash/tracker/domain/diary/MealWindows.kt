package com.yash.tracker.domain.diary

enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }

/**
 * Picks the meal a new entry defaults to from the clock. Windows are the defaults PRD §5.9
 * makes editable in Settings; anything outside them is a snack.
 */
object MealWindows {

    fun defaultFor(hour: Int): MealType = when (hour) {
        in 4..10 -> MealType.BREAKFAST
        in 11..15 -> MealType.LUNCH
        in 18..23 -> MealType.DINNER
        else -> MealType.SNACK
    }
}
