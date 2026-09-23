package com.yash.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.yash.tracker.data.local.entity.LogEntryEntity
import com.yash.tracker.data.local.entity.LogItemEntity
import com.yash.tracker.data.local.entity.WaterLogEntity
import com.yash.tracker.data.local.entity.withMicrosFrom
import kotlinx.coroutines.flow.Flow

data class EntryWithItems(
    @Embedded val entry: LogEntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entry_id")
    val items: List<LogItemEntity>,
)

data class DayKcal(val date: String, val kcal: Double)

/** One meal's calories on one day, for learning how this user splits a day. */
data class MealKcal(val date: String, val mealType: String, val kcal: Double)

/** How many entries at one meal carried a given food or product. Exactly one id is set. */
data class SourceAtMeal(val foodId: Long?, val productId: Long?, val mealType: String, val times: Int)

/** A food or product that appears on a day. Exactly one id is set. */
data class SourceRef(val foodId: Long?, val productId: Long?)

/** A day's totals with the date attached, for trends over a range. */
data class DayMacros(
    val date: String,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
)

/** Denormalised daily totals, read straight off [LogEntryEntity] rather than summing items. */
data class DayTotals(
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fibreG: Double? = null,
    val sugarG: Double? = null,
    val sodiumMg: Double? = null,
    /** The calories behind those three, which is how much of the day they actually describe. */
    val microKcal: Double = 0.0,
) {
    /** What share of the day's food stated anything beyond the four macros. */
    val microCoverage: Double get() = if (kcal > 0) (microKcal / kcal).coerceIn(0.0, 1.0) else 0.0
}

@Dao
interface LogDao {

    @Insert
    suspend fun insertEntry(entry: LogEntryEntity): Long

    @Insert
    suspend fun insertItems(items: List<LogItemEntity>)

    /** An entry and its items are written together or not at all. */
    @Transaction
    suspend fun insertEntryWithItems(entry: LogEntryEntity, items: List<LogItemEntity>): Long {
        val entryId = insertEntry(entry.withMicrosFrom(items))
        insertItems(items.map { it.copy(entryId = entryId) })
        return entryId
    }

    @Update
    suspend fun updateEntry(entry: LogEntryEntity)

    @Query("DELETE FROM log_item WHERE entry_id = :entryId")
    suspend fun deleteItemsFor(entryId: Long)

    /**
     * Rewrites an edited entry: its denormalised totals and its rows are replaced together, so
     * the diary is never briefly showing a total that none of its items add up to.
     */
    @Transaction
    suspend fun replaceEntryItems(entry: LogEntryEntity, items: List<LogItemEntity>) {
        updateEntry(entry.withMicrosFrom(items))
        deleteItemsFor(entry.id)
        insertItems(items.map { it.copy(id = 0, entryId = entry.id) })
    }

    @Transaction
    @Query("SELECT * FROM log_entry WHERE date = :date ORDER BY logged_at ASC")
    fun observeEntriesForDate(date: String): Flow<List<EntryWithItems>>

    @Transaction
    @Query("SELECT * FROM log_entry WHERE id = :id")
    suspend fun getEntryWithItems(id: Long): EntryWithItems?

    @Query(
        """
        SELECT COALESCE(SUM(kcal), 0.0) AS kcal,
               COALESCE(SUM(protein_g), 0.0) AS proteinG,
               COALESCE(SUM(carbs_g), 0.0) AS carbsG,
               COALESCE(SUM(fat_g), 0.0) AS fatG,
               SUM(fibre_g) AS fibreG,
               SUM(sugar_g) AS sugarG,
               SUM(sodium_mg) AS sodiumMg,
               COALESCE(SUM(micro_kcal), 0.0) AS microKcal
        FROM log_entry WHERE date = :date
        """,
    )
    fun observeTotalsForDate(date: String): Flow<DayTotals>

    /** Pre-aggregated per day for the history strip, rather than reading every entry. */
    @Query(
        """
        SELECT date, COALESCE(SUM(kcal), 0.0) AS kcal
        FROM log_entry WHERE date BETWEEN :from AND :to
        GROUP BY date
        """,
    )
    fun observeDailyKcal(from: String, to: String): Flow<List<DayKcal>>

    /** The same aggregation with macros, for the Progress trends. */
    @Query(
        """
        SELECT date,
               COALESCE(SUM(kcal), 0.0) AS kcal,
               COALESCE(SUM(protein_g), 0.0) AS proteinG,
               COALESCE(SUM(carbs_g), 0.0) AS carbsG,
               COALESCE(SUM(fat_g), 0.0) AS fatG
        FROM log_entry WHERE date BETWEEN :from AND :to
        GROUP BY date
        ORDER BY date ASC
        """,
    )
    fun observeDailyMacros(from: String, to: String): Flow<List<DayMacros>>

    @Query("UPDATE log_entry SET photo_uri = :path WHERE id = :id")
    suspend fun attachPhoto(id: Long, path: String)

    @Query("DELETE FROM log_entry WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("SELECT COUNT(*) FROM log_item WHERE entry_id = :entryId")
    suspend fun itemCountFor(entryId: Long): Int

    @Insert
    suspend fun insertWater(water: WaterLogEntity)

    @Query("SELECT COALESCE(SUM(ml), 0) FROM water_log WHERE date = :date")
    fun observeWaterForDate(date: String): Flow<Int>

    @Query("DELETE FROM water_log WHERE id = (SELECT id FROM water_log WHERE date = :date ORDER BY logged_at DESC LIMIT 1)")
    suspend fun removeLastWater(date: String)

    @Query(
        """
        SELECT date, meal_type AS mealType, COALESCE(SUM(kcal), 0.0) AS kcal
        FROM log_entry WHERE date BETWEEN :from AND :to
        GROUP BY date, meal_type
        """,
    )
    suspend fun mealKcalByDay(from: String, to: String): List<MealKcal>

    /** Counted per entry, not per row: two roti on one plate is one lunch with roti in it. */
    @Query(
        """
        SELECT li.food_id AS foodId, li.product_id AS productId, le.meal_type AS mealType,
               COUNT(DISTINCT le.id) AS times
        FROM log_item li JOIN log_entry le ON le.id = li.entry_id
        WHERE le.date >= :from AND (li.food_id IS NOT NULL OR li.product_id IS NOT NULL)
        GROUP BY li.food_id, li.product_id, le.meal_type
        """,
    )
    suspend fun mealAffinity(from: String): List<SourceAtMeal>

    @Query(
        """
        SELECT DISTINCT li.food_id AS foodId, li.product_id AS productId
        FROM log_item li JOIN log_entry le ON le.id = li.entry_id
        WHERE le.date = :date AND (li.food_id IS NOT NULL OR li.product_id IS NOT NULL)
        """,
    )
    suspend fun sourcesOn(date: String): List<SourceRef>
}
