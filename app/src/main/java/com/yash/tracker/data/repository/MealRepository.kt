package com.yash.tracker.data.repository

import com.yash.tracker.data.local.dao.LogDao
import com.yash.tracker.data.local.dao.MealDao
import com.yash.tracker.data.local.dao.MealWithItems
import com.yash.tracker.data.local.entity.LogEntryEntity
import com.yash.tracker.data.local.entity.LogItemEntity
import com.yash.tracker.data.local.entity.MealTemplateEntity
import com.yash.tracker.data.local.entity.MealTemplateItemEntity
import com.yash.tracker.domain.diary.DiaryDate
import com.yash.tracker.domain.diary.MealType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MealRepository @Inject constructor(
    private val mealDao: MealDao,
    private val logDao: LogDao,
    private val io: CoroutineDispatcher,
) {
    fun observeAll(): Flow<List<MealWithItems>> = mealDao.observeAll()

    suspend fun rename(id: Long, name: String) = withContext(io) { mealDao.rename(id, name.trim()) }

    suspend fun delete(id: Long) = withContext(io) { mealDao.delete(id) }

    /**
     * Turns an existing diary entry into a reusable meal. Values are copied rather than
     * referenced, so editing the saved meal later never rewrites the diary it came from.
     */
    suspend fun saveEntryAsMeal(entryId: Long, name: String): Long? = withContext(io) {
        val entry = logDao.getEntryWithItems(entryId) ?: return@withContext null

        mealDao.insertMeal(
            template = MealTemplateEntity(
                name = name.trim().ifBlank { entry.items.firstOrNull()?.name ?: "Saved meal" },
                photoUri = entry.entry.photoUri,
                defaultMealType = entry.entry.mealType,
                kcal = entry.entry.kcal,
                proteinG = entry.entry.proteinG,
                carbsG = entry.entry.carbsG,
                fatG = entry.entry.fatG,
                lastLoggedAt = null,
                createdAt = System.currentTimeMillis(),
            ),
            items = entry.items.map { item ->
                MealTemplateItemEntity(
                    templateId = 0,
                    foodId = item.foodId,
                    productId = item.productId,
                    name = item.name,
                    quantity = item.quantity,
                    unit = item.unit,
                    grams = item.grams,
                    kcal = item.kcal,
                    proteinG = item.proteinG,
                    carbsG = item.carbsG,
                    fatG = item.fatG,
                    fibreG = item.fibreG,
                    sugarG = item.sugarG,
                    sodiumMg = item.sodiumMg,
                )
            },
        )
    }

    /**
     * The two-tap log from PRD §5.4c. Macros are copied onto the new diary items, so a saved
     * meal edited afterwards does not rewrite meals already eaten.
     */
    suspend fun logMeal(
        mealId: Long,
        date: LocalDate,
        mealType: MealType? = null,
    ): Long? = withContext(io) {
        val meal = mealDao.getWithItems(mealId) ?: return@withContext null
        val now = System.currentTimeMillis()

        val resolvedType = mealType
            ?: runCatching { MealType.valueOf(meal.template.defaultMealType.orEmpty()) }
                .getOrElse { MealType.SNACK }

        val entryId = logDao.insertEntryWithItems(
            entry = LogEntryEntity(
                date = DiaryDate.format(date),
                loggedAt = now,
                mealType = resolvedType.name,
                source = "MEAL_TEMPLATE",
                photoUri = meal.template.photoUri,
                userHint = null,
                note = null,
                groundingSource = null,
                kcal = meal.template.kcal,
                proteinG = meal.template.proteinG,
                carbsG = meal.template.carbsG,
                fatG = meal.template.fatG,
            ),
            items = meal.items.map { item ->
                LogItemEntity(
                    entryId = 0,
                    foodId = item.foodId,
                    productId = item.productId,
                    name = item.name,
                    quantity = item.quantity,
                    unit = item.unit,
                    grams = item.grams,
                    kcal = item.kcal,
                    proteinG = item.proteinG,
                    carbsG = item.carbsG,
                    fatG = item.fatG,
                    fibreG = item.fibreG,
                    sugarG = item.sugarG,
                    sodiumMg = item.sodiumMg,
                    aiConfidence = null,
                )
            },
        )

        mealDao.markLogged(mealId, now)
        entryId
    }
}
