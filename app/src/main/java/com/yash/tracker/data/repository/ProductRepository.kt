package com.yash.tracker.data.repository

import com.yash.tracker.data.local.dao.LogDao
import com.yash.tracker.data.local.dao.ProductDao
import com.yash.tracker.data.local.entity.LogEntryEntity
import com.yash.tracker.data.local.entity.LogItemEntity
import com.yash.tracker.data.local.entity.ProductEntity
import com.yash.tracker.domain.diary.DiaryDate
import com.yash.tracker.domain.diary.MealType
import com.yash.tracker.domain.nutrition.PortionChoice
import com.yash.tracker.domain.nutrition.PortionResolver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProductRepository @Inject constructor(
    private val dao: ProductDao,
    private val logs: LogDao,
    private val io: CoroutineDispatcher,
) {
    fun observeAll(): Flow<List<ProductEntity>> = dao.observeAll()

    suspend fun search(term: String): List<ProductEntity> = withContext(io) {
        term.trim().takeIf { it.isNotBlank() }?.let { dao.search(it) }.orEmpty()
    }

    suspend fun getById(id: Long): ProductEntity? = withContext(io) { dao.getById(id) }

    /** Every product, for a picker small enough to browse rather than search. */
    suspend fun all(): List<ProductEntity> = withContext(io) { dao.observeAll().first() }

    suspend fun save(product: ProductEntity): Long = withContext(io) {
        val now = System.currentTimeMillis()
        dao.upsert(
            product.copy(
                createdAt = product.createdAt.takeIf { it > 0 } ?: now,
                updatedAt = now,
            ),
        )
    }

    /**
     * PRD §7 is explicit: deleting a product leaves the diary alone. Entries keep the macros
     * frozen onto them at save time, so history does not change because a packet ran out.
     */
    suspend fun delete(id: Long) = withContext(io) { dao.delete(id) }

    /**
     * The portions worth offering for a packaged food: the serving printed on the pack, and
     * grams. No katoris — a scoop of protein powder is not measured in bowls.
     */
    fun choicesFor(product: ProductEntity): List<PortionChoice> = buildList {
        val serving = product.servingG
        if (serving != null && serving > 0) {
            val label = product.servingLabel?.trim()?.ifBlank { null } ?: "serving"
            add(PortionChoice(label, label.uppercase(), serving))
        }
        add(PortionChoice("g", "G", 1.0))
    }

    suspend fun log(
        date: LocalDate,
        mealType: MealType,
        product: ProductEntity,
        quantity: Double,
        choice: PortionChoice,
    ): Long = withContext(io) {
        val grams = PortionResolver.grams(quantity, choice)
        val macros = PortionResolver.macrosFor(product, grams)
        val micros = PortionResolver.microsFor(product, grams)
        val now = System.currentTimeMillis()

        logs.insertEntryWithItems(
            entry = LogEntryEntity(
                date = DiaryDate.format(date),
                loggedAt = now,
                mealType = mealType.name,
                // A label is measured, not guessed, so the entry says so and the confirm
                // screen never offers to re-estimate it.
                source = "PRODUCT",
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
                    foodId = null,
                    productId = product.id,
                    name = product.displayName(),
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
}

fun ProductEntity.displayName(): String =
    listOfNotNull(brand?.takeIf { it.isNotBlank() }, name).joinToString(" ")
