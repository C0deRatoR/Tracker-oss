package com.yash.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.yash.tracker.data.local.entity.WeightLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightDao {

    /** One weigh-in per date, so re-entering today's weight replaces it. */
    @Upsert
    suspend fun upsert(entry: WeightLogEntity)

    @Query("SELECT * FROM weight_log ORDER BY date ASC")
    fun observeAll(): Flow<List<WeightLogEntity>>

    @Query("SELECT * FROM weight_log ORDER BY date DESC LIMIT 1")
    suspend fun latest(): WeightLogEntity?

    @Query("SELECT * FROM weight_log WHERE date = :date")
    suspend fun forDate(date: String): WeightLogEntity?

    @Query("DELETE FROM weight_log WHERE id = :id")
    suspend fun delete(id: Long)
}
