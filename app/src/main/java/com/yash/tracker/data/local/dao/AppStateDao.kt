package com.yash.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.yash.tracker.data.local.entity.AppStateEntity
import com.yash.tracker.data.local.entity.ExerciseEntity

@Dao
interface AppStateDao {

    @Upsert
    suspend fun put(state: AppStateEntity)

    @Query("SELECT value FROM app_state WHERE key = :key")
    suspend fun get(key: String): String?
}

@Dao
interface ExerciseDao {

    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun count(): Int

    @Query("SELECT * FROM exercise ORDER BY name ASC")
    suspend fun getAll(): List<ExerciseEntity>

    @Query("SELECT name FROM exercise")
    suspend fun names(): List<String>

    @androidx.room.Update
    suspend fun update(exercise: ExerciseEntity)

    @Query("DELETE FROM exercise WHERE id = :id")
    suspend fun deleteById(id: Long)
}
