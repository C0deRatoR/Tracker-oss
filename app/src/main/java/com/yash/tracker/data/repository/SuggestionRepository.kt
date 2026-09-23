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
import com.yash.tracker.domain.nutrition.DayMicroStatus
import com.yash.tracker.domain.nutrition.MacroGap
import com.yash.tracker.domain.nutrition.MacroSuggestion
import com.yash.tracker.domain.nutrition.Macros
import com.yash.tracker.domain.nutrition.Micros
import com.yash.tracker.domain.nutrition.MealBudget
import com.yash.tracker.domain.nutrition.MealSuggester
import com.yash.tracker.domain.nutrition.Plate
import com.yash.tracker.domain.nutrition.PlateContext
import com.yash.tracker.domain.nutrition.Serving
import com.yash.tracker.domain.nutrition.ServingSource
import com.yash.tracker.domain.nutrition.Servings
import com.yash.tracker.domain.nutrition.SlotCount
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
        date: LocalDate,
        eatenMicros: Micros = Micros.UNKNOWN,
    ): MacroSuggestion = withContext(io) {
        val gap = MacroGap.between(target.asMacros(), eaten)
        if (gap.isSpent) return@withContext MacroSuggestion.DayDone

        val share = MealBudget.shareFor(gap, hour, alreadyLogged, learnedShares(date))
        val pool = pool()
        if (pool.isEmpty()) return@withContext MacroSuggestion.NoHistoryYet

        val micros = DayMicroStatus.of(target.kcal.toDouble(), eatenMicros)
        val (fibreNeed, sodiumRoom) = micros.forMeal(share.fraction)
        val context = PlateContext(
            fibreNeedG = fibreNeed,
            sodiumRoomMg = sodiumRoom,
            affinity = affinity(date, share.meal),
            eatenToday = sourcesOn(date),
            eatenYesterday = sourcesOn(date.minusDays(1)),
        )

        var plates = MealSuggester.suggest(share.gap, pool, context)
        if (isPoorFit(plates)) {
            plates = MealSuggester.suggest(share.gap, pool + fallback(share.gap, pool, context), context)
        }

        if (plates.isEmpty()) {
            MacroSuggestion.NothingFits(share.meal, share.gap)
        } else {
            MacroSuggestion.Plates(share.meal, share.gap, plates, micros)
        }
    }

    /**
     * Whether the user's own food has failed this gap badly enough to look further.
     *
     * Either the best plate misses by roughly a third of the gap, or it leaves a protein debt
     * that the rest of the day will struggle to pay. Anything better than that is answered
     * from what they eat, however imperfectly, because a familiar near-miss beats a stranger.
     */
    private fun isPoorFit(plates: List<Plate>): Boolean {
        val best = plates.firstOrNull() ?: return true
        return best.fit > POOR_FIT || best.leftover.proteinG > PROTEIN_LEFT_G
    }

    /** The catalogue dishes that best answer this gap, as servings marked new. */
    private suspend fun fallback(gap: MacroGap, pool: List<Serving>, context: PlateContext): List<Serving> {
        val owned = pool.mapNotNull { (it.source as? ServingSource.Food)?.foodId }.toSet()
        val servings = foodDao.fallbackDishes()
            .filter { it.id !in owned }
            .flatMap { food ->
                Servings.ofFood(
                    source = ServingSource.Food(food.id),
                    name = food.name,
                    per100g = food,
                    defaultPortionG = food.defaultPortionG,
                    portionLabel = food.portionLabel,
                    isNew = true,
                )
            }
        val keep = MealSuggester.bestSources(gap, servings, context, FALLBACK_FOODS).toSet()
        return servings.filter { it.source in keep }
    }

    /** The last fortnight's split of each day across meals, today excluded as unfinished. */
    private suspend fun learnedShares(date: LocalDate): Map<MealType, Double> {
        val rows = logDao.mealKcalByDay(
            from = DiaryDate.format(date.minusDays(SHARE_HISTORY_DAYS)),
            to = DiaryDate.format(date.minusDays(1)),
        )
        val days = rows.groupBy { it.date }.values.map { day ->
            day.mapNotNull { row ->
                runCatching { MealType.valueOf(row.mealType) }.getOrNull()?.let { it to row.kcal }
            }.toMap()
        }
        return MealBudget.learnShares(days)
    }

    private suspend fun affinity(date: LocalDate, meal: MealType): Map<ServingSource, SlotCount> =
        logDao.mealAffinity(DiaryDate.format(date.minusDays(AFFINITY_HISTORY_DAYS)))
            .groupBy { it.foodId?.let(ServingSource::Food) ?: ServingSource.Product(it.productId!!) }
            .mapValues { (_, rows) ->
                SlotCount(
                    atThisMeal = rows.filter { it.mealType == meal.name }.sumOf { it.times },
                    total = rows.sumOf { it.times },
                )
            }

    private suspend fun sourcesOn(date: LocalDate): Set<ServingSource> =
        logDao.sourcesOn(DiaryDate.format(date)).mapTo(mutableSetOf()) {
            it.foodId?.let(ServingSource::Food) ?: ServingSource.Product(it.productId!!)
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

        /** Roughly a third of the gap left unfilled, on [Plate.fit]'s scale. */
        const val POOR_FIT = 0.1
        const val PROTEIN_LEFT_G = 20.0

        /** Catalogue dishes carried into the search when the user's own food falls short. */
        const val FALLBACK_FOODS = 40

        const val SHARE_HISTORY_DAYS = 14L
        const val AFFINITY_HISTORY_DAYS = 60L
    }
}

fun TargetEntity.asMacros(): Macros = Macros(
    kcal = kcal.toDouble(),
    proteinG = proteinG,
    carbsG = carbsG,
    fatG = fatG,
)
