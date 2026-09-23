package com.yash.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.yash.tracker.data.local.entity.MealTemplateEntity
import com.yash.tracker.data.local.entity.MealTemplateItemEntity
import com.yash.tracker.data.local.entity.withMicrosFrom
import kotlinx.coroutines.flow.Flow

data class MealWithItems(
    @Embedded val template: MealTemplateEntity,
    @Relation(parentColumn = "id", entityColumn = "template_id")
    val items: List<MealTemplateItemEntity>,
)

@Dao
interface MealDao {

    @Insert
    suspend fun insertTemplate(template: MealTemplateEntity): Long

    @Insert
    suspend fun insertItems(items: List<MealTemplateItemEntity>)

    @Transaction
    suspend fun insertMeal(
        template: MealTemplateEntity,
        items: List<MealTemplateItemEntity>,
    ): Long {
        val id = insertTemplate(template.withMicrosFrom(items))
        insertItems(items.map { it.copy(templateId = id) })
        return id
    }

    /** Most-logged first, so the meals actually eaten sit at the top (PRD §5.4c). */
    @Transaction
    @Query("SELECT * FROM meal_template ORDER BY times_logged DESC, last_logged_at DESC, name ASC")
    fun observeAll(): Flow<List<MealWithItems>>

    @Transaction
    @Query("SELECT * FROM meal_template ORDER BY times_logged DESC, last_logged_at DESC, name ASC")
    suspend fun all(): List<MealWithItems>

    @Transaction
    @Query("SELECT * FROM meal_template WHERE id = :id")
    suspend fun getWithItems(id: Long): MealWithItems?

    @Query("UPDATE meal_template SET times_logged = times_logged + 1, last_logged_at = :at WHERE id = :id")
    suspend fun markLogged(id: Long, at: Long)

    @Query("UPDATE meal_template SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM meal_template WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM meal_template")
    suspend fun count(): Int
}
