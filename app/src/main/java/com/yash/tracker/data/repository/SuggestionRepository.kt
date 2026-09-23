package com.yash.tracker.data.repository

import com.yash.tracker.data.local.dao.FoodDao
import com.yash.tracker.data.local.dao.LogDao
import com.yash.tracker.data.local.dao.MealDao
import com.yash.tracker.data.local.dao.ProductDao
import com.yash.tracker.data.local.entity.LogEntryEntity
import com.yash.tracker.data.local.entity.LogItemEntity
import com.yash.tracker.data.local.entity.TargetEntity
import com.yash.tracker.domain.diary.DiaryDate
import com.yash.tracker.domain.diary.MealType
import com.yash.tracker.domain.nutrition.MacroGap
import com.yash.tracker.domain.nutrition.MacroSuggestion
import com.yash.tracker.domain.nutrition.Macros
import com.yash.tracker.domain.nutrition.Micros
import com.yash.tracker.domain.nutrition.MealBudget
import com.yash.tracker.domain.nutrition.MealSuggester
import com.yash.tracker.domain.nutrition.Plate
import com.yash.tracker.domain.nutrition.Serving
import com.yash.tracker.domain.nutrition.ServingSource
import com.yash.tracker.domain.nutrition.Servings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers "what should I eat now" from what is left of the day's targets.
 *
 * The arithmetic lives in [MealSuggester] and is not a model: closing a macro gap is
 * deterministic, and a number the user can check beats a sentence they have to trust. This
 * class only assembles the pool and writes the answer back to the diary.
 */
@Singleton
class SuggestionRepository @Inject constructor(
    private val foodDao: FoodDao,
    private val productDao: ProductDao,
    private val mealDao: MealDao,
    private val logDao: LogDao,
    private val meals: MealRepository,
    private val io: CoroutineDispatcher,
) {

    suspend fun suggest(
        target: TargetEntity,
        eaten: Macros,
        hour: Int,
        alreadyLogged: Set<MealType>,
    ): MacroSuggestion = withContext(io) {
        val gap = MacroGap.between(target.asMacros(), eaten)
        if (gap.isSpent) return@withContext MacroSuggestion.DayDone

        val share = MealBudget.shareFor(gap, hour, alreadyLogged)
        val pool = pool()
        if (pool.isEmpty()) return@withContext MacroSuggestion.NoHistoryYet

        val plates = MealSuggester.suggest(share.gap, pool)
        if (plates.isEmpty()) {
            MacroSuggestion.NothingFits(share.meal, share.gap)
        } else {
            MacroSuggestion.Plates(share.meal, share.gap, plates)
        }
    }

    /**
     * Everything the user could plausibly eat, as concrete portions.
     *
     * Three sources, all of them things they have already chosen once: catalogue rows they
     * have logged, products they own, and meals they saved. The bundled catalogue at large is
     * deliberately excluded — see [FoodDao.mostLogged].
     */
    private suspend fun pool(): List<Serving> = buildList {
        foodDao.mostLogged().forEach { food ->
            addAll(
                Servings.ofFood(
                    source = ServingSource.Food(food.id),
                    name = food.name,
                    per100g = food,
                    defaultPortionG = food.defaultPortionG,
                    portionLabel = food.portionLabel,
                    familiarity = food.timesLogged,
                ),
            )
        }

        productDao.all().forEach { product ->
            addAll(
                Servings.ofFood(
                    source = ServingSource.Product(product.id),
                    name = listOfNotNull(product.brand, product.name).joinToString(" "),
                    per100g = product,
                    defaultPortionG = product.servingG ?: DEFAULT_PRODUCT_SERVING_G,
                    portionLabel = product.servingLabel,
                    // A product in the cupboard was bought on purpose; treat it as familiar
                    // even before it has been logged, or it never surfaces on day one.
                    familiarity = PRODUCT_FAMILIARITY,
                ),
            )
        }

        mealDao.all().forEach { saved ->
            if (saved.template.kcal <= 0) return@forEach
            add(
                Serving(
                    source = ServingSource.SavedMeal(saved.template.id),
                    name = saved.template.name,
                    portionLabel = "saved meal",
                    quantity = 1.0,
                    unit = "SERVING",
                    grams = null,
                    macros = Macros(
                        kcal = saved.template.kcal,
                        proteinG = saved.template.proteinG,
                        carbsG = saved.template.carbsG,
                        fatG = saved.template.fatG,
                    ),
                    micros = Micros(
                        fibreG = saved.template.fibreG,
                        sugarG = saved.template.sugarG,
                        sodiumMg = saved.template.sodiumMg,
                    ),
                    familiarity = saved.template.timesLogged,
                    combinable = false,
                ),
            )
        }
    }

    /**
     * Writes a suggested plate into the diary.
     *
     * Macros are copied off the serving rather than recomputed, for the same reason the rest
     * of the diary freezes them (TRD §3.4): what was suggested and what gets logged have to be
     * the same numbers, whatever the catalogue says later.
     */
    suspend fun logPlate(date: LocalDate, mealType: MealType, plate: Plate): Long? =
        withContext(io) {
            val savedMeal = plate.items.singleOrNull()?.source as? ServingSource.SavedMeal
            if (savedMeal != null) {
                return@withContext meals.logMeal(savedMeal.mealId, date, mealType)
            }

            val now = System.currentTimeMillis()
            val entryId = logDao.insertEntryWithItems(
                entry = LogEntryEntity(
                    date = DiaryDate.format(date),
                    loggedAt = now,
                    mealType = mealType.name,
                    source = SOURCE,
                    photoUri = null,
                    userHint = null,
                    note = null,
                    groundingSource = null,
                    kcal = plate.total.kcal,
                    proteinG = plate.total.proteinG,
                    carbsG = plate.total.carbsG,
                    fatG = plate.total.fatG,
                ),
                items = plate.items.map { serving ->
                    LogItemEntity(
                        entryId = 0,
                        foodId = (serving.source as? ServingSource.Food)?.foodId,
                        productId = (serving.source as? ServingSource.Product)?.productId,
                        name = serving.name,
                        quantity = serving.quantity,
                        unit = serving.unit,
                        grams = serving.grams ?: 0.0,
                        kcal = serving.macros.kcal,
                        proteinG = serving.macros.proteinG,
                        carbsG = serving.macros.carbsG,
                        fatG = serving.macros.fatG,
                        fibreG = serving.micros.fibreG,
                        sugarG = serving.micros.sugarG,
                        sodiumMg = serving.micros.sodiumMg,
                        aiConfidence = null,
                    )
                },
            )

            plate.items
                .mapNotNull { (it.source as? ServingSource.Food)?.foodId }
                .distinct()
                .forEach { foodDao.incrementTimesLogged(it) }

            entryId
        }

    private companion object {
        /** A packet with no stated serving is scored in the size everything else is. */
        const val DEFAULT_PRODUCT_SERVING_G = 100.0

        /** Enough to rank with a food logged a handful of times, not enough to win outright. */
        const val PRODUCT_FAMILIARITY = 3

        const val SOURCE = "SUGGESTED"
    }
}

fun TargetEntity.asMacros(): Macros = Macros(
    kcal = kcal.toDouble(),
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
)
