package com.yash.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.yash.tracker.data.local.entity.ProfileEntity
import com.yash.tracker.data.local.entity.TargetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Upsert
    suspend fun upsertProfile(profile: ProfileEntity)

    @Query("SELECT * FROM profile WHERE id = :id")
    fun observeProfile(id: Int = ProfileEntity.SINGLETON_ID): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = :id")
    suspend fun getProfile(id: Int = ProfileEntity.SINGLETON_ID): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTarget(target: TargetEntity): Long

    /** The newest revision is the active plan. */
    @Query("SELECT * FROM target ORDER BY computed_at DESC, id DESC LIMIT 1")
    fun observeActiveTarget(): Flow<TargetEntity?>

    @Query("SELECT * FROM target ORDER BY computed_at DESC, id DESC LIMIT 1")
    suspend fun getActiveTarget(): TargetEntity?
}
