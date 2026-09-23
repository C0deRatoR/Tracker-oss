package com.yash.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yash.tracker.data.local.entity.FoodEntity
import com.yash.tracker.data.local.entity.PortionMeasureEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(foods: List<FoodEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(food: FoodEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPortions(portions: List<PortionMeasureEntity>)

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun getById(id: Long): FoodEntity?

    /**
     * The one row that is unambiguously this name: exact on the name, or exact on one of its
     * transliterations in [FoodEntity.altNames].
     *
     * Separate from [searchByName] because the two answer different questions. A search ranks
     * candidates for a person to choose between, and a near-miss there costs nothing. This is
     * used to decide whether a typed food can be logged without asking the model at all, where
     * a near-miss would silently log the wrong food.
     */
    @Query(
        """
        SELECT * FROM food
        WHERE name = :name COLLATE NOCASE
           OR ' ' || alt_names || ' ' LIKE '% ' || :name || ' %'
        ORDER BY times_logged DESC, LENGTH(name) ASC
        LIMIT 1
        """,
    )
    suspend fun findByExactName(name: String): FoodEntity?

    /**
     * Adds to the words a food answers to.
     *
     * [FoodEntity.altNames] is one space-joined run of names, not a JSON array — that is the
     * shape the FTS index wants, and it is why a match here has to be anchored on spaces
     * rather than on quotes.
     */
    @Query("UPDATE food SET alt_names = :value WHERE id = :id")
    suspend fun setAltNames(id: Long, value: String?)

    /**
     * Fills in the panel figures a remembered food never had, and only those.
     *
     * COALESCE per column, so a row that already knows its fibre keeps it. Restricted to one
     * [source] by the caller — a bundled reference row whose source never measured sugar must
     * not quietly acquire a model's guess while still claiming to be USDA.
     */
    @Query(
        """
        UPDATE food SET
            fibre_100g     = COALESCE(fibre_100g, :fibre100g),
            sugar_100g     = COALESCE(sugar_100g, :sugar100g),
            sodium_mg_100g = COALESCE(sodium_mg_100g, :sodiumMg100g)
        WHERE id = :id AND source = :source
        """,
    )
    suspend fun fillMissingMicros(
        id: Long,
        fibre100g: Double?,
        sugar100g: Double?,
        sodiumMg100g: Double?,
        source: String,
    )

    @Query("SELECT COUNT(*) FROM food")
    suspend fun count(): Int

    /**
     * FTS match over name and alternate names, most-logged first so the foods this user
     * actually eats surface above the rest of the catalogue.
     */
    @Query(
        """
        SELECT food.* FROM food
        JOIN food_fts ON food.rowid = food_fts.rowid
        WHERE food_fts MATCH :query
        ORDER BY food.times_logged DESC, food.name ASC
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int = 50): List<FoodEntity>

    @Query("SELECT * FROM food WHERE name LIKE '%' || :term || '%' ORDER BY times_logged DESC LIMIT :limit")
    suspend fun searchByName(term: String, limit: Int = 50): List<FoodEntity>

    /**
     * The foods this user actually eats, for suggestions.
     *
     * Deliberately not the whole catalogue: an algorithm free to pick any of ten thousand rows
     * closes a macro gap perfectly with dried egg white, and nobody eats that. Suggesting only
     * from what has been logged before keeps the answer to food that is in the house.
     */
    @Query("SELECT * FROM food WHERE times_logged > 0 ORDER BY times_logged DESC LIMIT :limit")
    suspend fun mostLogged(limit: Int = 60): List<FoodEntity>

    @Query("UPDATE food SET times_logged = times_logged + 1 WHERE id = :id")
    suspend fun incrementTimesLogged(id: Long)

    @Query("SELECT * FROM portion_measure WHERE food_id = :foodId OR food_id IS NULL")
    suspend fun portionsFor(foodId: Long): List<PortionMeasureEntity>

    /** Measures that apply to any food — what a recognised row can be counted in. */
    @Query("SELECT * FROM portion_measure WHERE food_id IS NULL ORDER BY label ASC")
    suspend fun genericPortions(): List<PortionMeasureEntity>

    @Query("SELECT * FROM food ORDER BY name ASC LIMIT :limit")
    fun observeAll(limit: Int = 100): Flow<List<FoodEntity>>
}
