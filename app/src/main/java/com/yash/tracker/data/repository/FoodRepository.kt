package com.yash.tracker.data.repository

import com.yash.tracker.data.local.dao.FoodDao
import com.yash.tracker.data.local.entity.FoodEntity
import com.yash.tracker.data.local.entity.PortionMeasureEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodRepository @Inject constructor(
    private val dao: FoodDao,
    private val io: CoroutineDispatcher,
) {
    suspend fun search(query: String, limit: Int = 50): List<FoodEntity> = withContext(io) {
        val match = toFtsQuery(query)
        if (match.isEmpty()) return@withContext emptyList()

        // FTS handles whole words and prefixes; LIKE catches the middle of a word, which is
        // how you find "sabji" inside "Bhindi ki sabji".
        val hits = runCatching { dao.search(match, limit) }.getOrDefault(emptyList())
        hits.ifEmpty { dao.searchByName(query.trim(), limit) }
    }

    suspend fun getById(id: Long): FoodEntity? = withContext(io) { dao.getById(id) }

    /** How many rows the bundled catalogue holds, for the library header. */
    suspend fun count(): Int = withContext(io) { dao.count() }

    suspend fun portionsFor(foodId: Long): List<PortionMeasureEntity> =
        withContext(io) { dao.portionsFor(foodId) }

    suspend fun genericPortions(): List<PortionMeasureEntity> =
        withContext(io) { dao.genericPortions() }

    suspend fun markLogged(foodId: Long) = withContext(io) { dao.incrementTimesLogged(foodId) }

    /**
     * FTS4 MATCH has its own syntax, and raw user text can make it throw rather than return
     * nothing. Reducing the query to alphanumeric prefix terms keeps it total.
     */
    private fun toFtsQuery(raw: String): String =
        raw.split(Regex("\\s+"))
            .map { it.filter(Char::isLetterOrDigit) }
            .filter { it.isNotEmpty() }
            .joinToString(" ") { "$it*" }
}
