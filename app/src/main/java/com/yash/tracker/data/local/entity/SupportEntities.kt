package com.yash.tracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * What the model said versus what the user corrected it to. Matching consults these first,
 * so recognition converges on how this user actually eats.
 */
@Entity(tableName = "correction", indices = [Index("ai_name")])
data class CorrectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "ai_name") val aiName: String,
    @ColumnInfo(name = "corrected_food_id") val correctedFoodId: Long?,
    @ColumnInfo(name = "corrected_product_id") val correctedProductId: Long?,
    @ColumnInfo(name = "corrected_name") val correctedName: String,
    @ColumnInfo(name = "typical_grams") val typicalGrams: Double?,
    @ColumnInfo(name = "hit_count") val hitCount: Int = 1,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/**
 * Caches Gemini responses by model and prompt hash so re-recognising the same photo or
 * repeating a text query costs nothing. TRD §5.5 relies on this table but never defines it.
 */
@Entity(tableName = "ai_cache", indices = [Index(value = ["model", "prompt_hash"], unique = true)])
data class AiCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val model: String,
    @ColumnInfo(name = "prompt_hash") val promptHash: String,
    @ColumnInfo(name = "response_json") val responseJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    /**
     * Kept past the thirty days, because the reading was saved to the diary.
     *
     * A phrase someone actually logged is a phrase they will log again, and letting that one
     * expire means paying for the same sentence twice. Readings that were discarded still age
     * out: they were wrong, or they were never wanted.
     */
    val pinned: Boolean = false,
)

/** Small key-value store for things with nowhere better to live: seed status, backup dates. */
@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val key: String,
    val value: String,
) {
    companion object {
        const val KEY_SEED_IMPORTED = "seed_imported"
        const val KEY_LAST_BACKUP_AT = "last_backup_at"
        const val KEY_EXERCISE_LIBRARY_VERSION = "exercise_library_version"
    }
}
