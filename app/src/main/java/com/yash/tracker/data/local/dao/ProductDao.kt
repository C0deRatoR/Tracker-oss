package com.yash.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.yash.tracker.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Query("SELECT * FROM product ORDER BY name ASC")
    fun observeAll(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM product ORDER BY name ASC")
    suspend fun all(): List<ProductEntity>

    @Query("SELECT * FROM product WHERE id = :id")
    suspend fun getById(id: Long): ProductEntity?

    /**
     * Products are few — tens, not thousands — so a LIKE over name and brand is both enough
     * and better than FTS here: it matches the middle of a word, which is how "nut" finds
     * "Pintola peanut butter".
     */
    @Query(
        """
        SELECT * FROM product
        WHERE name LIKE '%' || :term || '%' OR brand LIKE '%' || :term || '%'
        ORDER BY name ASC LIMIT :limit
        """,
    )
    suspend fun search(term: String, limit: Int = 20): List<ProductEntity>

    @Upsert
    suspend fun upsert(product: ProductEntity): Long

    @Query("DELETE FROM product WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM product")
    suspend fun count(): Int
}
