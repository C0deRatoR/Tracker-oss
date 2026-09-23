package com.yash.tracker.data.repository

import com.yash.tracker.data.local.dao.DayKcal
import com.yash.tracker.data.local.dao.DayMacros
import com.yash.tracker.data.local.dao.DayNutritionRow
import com.yash.tracker.data.local.dao.FoodTotalRow
import com.yash.tracker.data.local.dao.MealKcal
import com.yash.tracker.data.local.dao.DayTotals
import com.yash.tracker.data.local.dao.EntryWithItems
import com.yash.tracker.data.local.dao.LogDao
import com.yash.tracker.data.local.entity.FoodEntity
import com.yash.tracker.data.local.entity.LogEntryEntity
import com.yash.tracker.data.local.entity.LogItemEntity
import com.yash.tracker.data.local.entity.WaterLogEntity
import com.yash.tracker.domain.diary.DiaryDate
import com.yash.tracker.domain.diary.MealType
import com.yash.tracker.domain.nutrition.PortionChoice
import com.yash.tracker.domain.nutrition.PortionResolver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor(
    private val dao: LogDao,
    private val io: CoroutineDispatcher,
) {
    fun observeEntries(date: LocalDate): Flow<List<EntryWithItems>> =
        dao.observeEntriesForDate(DiaryDate.format(date))

    fun observeTotals(date: LocalDate): Flow<DayTotals> =
        dao.observeTotalsForDate(DiaryDate.format(date))

    fun observeWater(date: LocalDate): Flow<Int> =
        dao.observeWaterForDate(DiaryDate.format(date))

    fun observeDailyMacros(from: LocalDate, to: LocalDate): Flow<List<DayMacros>> =
        dao.observeDailyMacros(DiaryDate.format(from), DiaryDate.format(to))

    fun observeDailyNutrition(from: LocalDate, to: LocalDate): Flow<List<DayNutritionRow>> =
        dao.observeDailyNutrition(DiaryDate.format(from), DiaryDate.format(to))

    fun observeMealKcal(from: LocalDate, to: LocalDate): Flow<List<MealKcal>> =
        dao.observeMealKcal(DiaryDate.format(from), DiaryDate.format(to))

    fun observeTopFoods(from: LocalDate, to: LocalDate): Flow<List<FoodTotalRow>> =
        dao.observeTopFoods(DiaryDate.format(from), DiaryDate.format(to))

    fun observeDailyKcal(from: LocalDate, to: LocalDate): Flow<List<DayKcal>> =
        dao.observeDailyKcal(DiaryDate.format(from), DiaryDate.format(to))

    suspend fun addWater(date: LocalDate, ml: Int) = withContext(io) {
        dao.insertWater(
            WaterLogEntity(
                date = DiaryDate.format(date),
                loggedAt = System.currentTimeMillis(),
                ml = ml,
            ),
        )
    }

    suspend fun removeLastWater(date: LocalDate) = withContext(io) {
        dao.removeLastWater(DiaryDate.format(date))
    }

    suspend fun deleteEntry(id: Long) = withContext(io) { dao.deleteEntry(id) }

    /**
     * Saves one hand-picked food. Macros are frozen onto the item at save time (TRD §3.4), so
     * editing the catalogue later never rewrites what the diary says you ate.
     */
    suspend fun logFood(
        date: LocalDate,
        mealType: MealType,
        food: FoodEntity,
        quantity: Double,
        choice: PortionChoice,
    ): Long = withContext(io) {
        val grams = PortionResolver.grams(quantity, choice)
        val macros = PortionResolver.macrosFor(food, grams)
        val micros = PortionResolver.microsFor(food, grams)
        val now = System.currentTimeMillis()

        dao.insertEntryWithItems(
            entry = LogEntryEntity(
                date = DiaryDate.format(date),
                loggedAt = now,
                mealType = mealType.name,
                source = "MANUAL",
                photoUri = null,
                userHint = null,
                note = null,
                groundingSource = null,
                kcal = macros.kcal,
                proteinG = macros.proteinG,
                carbsG = macros.carbsG,
                fatG = macros.fatG,
            ),
            items = listOf(
                LogItemEntity(
                    entryId = 0,
                    foodId = food.id,
                    productId = null,
                    name = food.name,
                    quantity = quantity,
                    unit = choice.unit,
                    grams = grams,
                    kcal = macros.kcal,
                    proteinG = macros.proteinG,
                    carbsG = macros.carbsG,
                    fatG = macros.fatG,
                    fibreG = micros.fibreG,
                    sugarG = micros.sugarG,
                    sodiumMg = micros.sodiumMg,
                    aiConfidence = null,
                ),
            ),
        )
    }

    /** Which diary day "now" belongs to, honouring the profile's day-start hour. */
    fun today(dayStartHour: Int, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        DiaryDate.resolve(Instant.now(), zone, dayStartHour)
}
