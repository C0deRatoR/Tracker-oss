package com.yash.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.yash.tracker.data.local.entity.CorrectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CorrectionDao {

    @Query("SELECT * FROM correction WHERE ai_name = :aiName COLLATE NOCASE LIMIT 1")
    suspend fun find(aiName: String): CorrectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(correction: CorrectionEntity): Long

    @Query("UPDATE correction SET hit_count = hit_count + 1, corrected_name = :correctedName, typical_grams = :grams, updated_at = :at WHERE id = :id")
    suspend fun reinforce(id: Long, correctedName: String, grams: Double?, at: Long)

    /**
     * Records the correction, or strengthens it if this mistake has been corrected before, so
     * repeated fixes carry more weight than one-offs.
     */
    @Transaction
    suspend fun record(aiName: String, correctedName: String, grams: Double?, at: Long) {
        val existing = find(aiName)
        if (existing == null) {
            insert(
                CorrectionEntity(
                    aiName = aiName,
                    correctedFoodId = null,
                    correctedProductId = null,
                    correctedName = correctedName,
                    typicalGrams = grams,
                    updatedAt = at,
                ),
            )
        } else {
            reinforce(existing.id, correctedName, grams, at)
        }
    }

    /** Most-corrected first: the prompt only has room for the ones that keep recurring. */
    @Query("SELECT * FROM correction ORDER BY hit_count DESC, updated_at DESC LIMIT :limit")
    suspend fun top(limit: Int): List<CorrectionEntity>

    @Query("SELECT * FROM correction ORDER BY hit_count DESC, updated_at DESC")
    fun observeAll(): Flow<List<CorrectionEntity>>

    @Query("DELETE FROM correction WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM correction")
    suspend fun count(): Int
}
