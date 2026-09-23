package com.yash.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yash.tracker.data.local.entity.AiCacheEntity

@Dao
interface AiCacheDao {

    @Query(
        """
        SELECT * FROM ai_cache
        WHERE model = :model AND prompt_hash = :hash AND (pinned = 1 OR created_at > :notBefore)
        LIMIT 1
        """,
    )
    suspend fun find(model: String, hash: String, notBefore: Long): AiCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: AiCacheEntity)

    /** Called when the reading this entry produced was saved to the diary. */
    @Query("UPDATE ai_cache SET pinned = 1 WHERE model = :model AND prompt_hash = :hash")
    suspend fun pin(model: String, hash: String)

    @Query("DELETE FROM ai_cache WHERE created_at <= :notBefore AND pinned = 0")
    suspend fun evictOlderThan(notBefore: Long)

    @Query("SELECT COUNT(*) FROM ai_cache")
    suspend fun count(): Int
}
