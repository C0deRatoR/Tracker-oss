package com.yash.tracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.yash.tracker.data.local.dao.AiCacheDao
import com.yash.tracker.data.local.dao.AppStateDao
import com.yash.tracker.data.local.dao.CorrectionDao
import com.yash.tracker.data.local.dao.ExerciseDao
import com.yash.tracker.data.local.dao.FoodDao
import com.yash.tracker.data.local.dao.LogDao
import com.yash.tracker.data.local.dao.MealDao
import com.yash.tracker.data.local.dao.ProfileDao
import com.yash.tracker.data.local.dao.ProductDao
import com.yash.tracker.data.local.dao.WeightDao
import com.yash.tracker.data.local.dao.WorkoutDao
import com.yash.tracker.data.local.entity.AiCacheEntity
import com.yash.tracker.data.local.entity.AppStateEntity
import com.yash.tracker.data.local.entity.CorrectionEntity
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.data.local.entity.FoodEntity
import com.yash.tracker.data.local.entity.FoodFts
import com.yash.tracker.data.local.entity.LogEntryEntity
import com.yash.tracker.data.local.entity.LogItemEntity
import com.yash.tracker.data.local.entity.MealTemplateEntity
import com.yash.tracker.data.local.entity.MealTemplateItemEntity
import com.yash.tracker.data.local.entity.PersonalRecordEntity
import com.yash.tracker.data.local.entity.PortionMeasureEntity
import com.yash.tracker.data.local.entity.ProductEntity
import com.yash.tracker.data.local.entity.ProfileEntity
import com.yash.tracker.data.local.entity.RoutineEntity
import com.yash.tracker.data.local.entity.RoutineExerciseEntity
import com.yash.tracker.data.local.entity.TargetEntity
import com.yash.tracker.data.local.entity.WaterLogEntity
import com.yash.tracker.data.local.entity.WeightLogEntity
import com.yash.tracker.data.local.entity.WorkoutSessionEntity
import com.yash.tracker.data.local.entity.WorkoutSetEntity

/**
 * Schema v1 covers every table the app will need, including ones no feature reads yet, so
 * that shipping later milestones never requires a migration on a database holding real data.
 * DAOs, by contrast, grow as features land.
 */
@Database(
    entities = [
        ProfileEntity::class,
        TargetEntity::class,
        FoodEntity::class,
        FoodFts::class,
        PortionMeasureEntity::class,
        ProductEntity::class,
        LogEntryEntity::class,
        LogItemEntity::class,
        WaterLogEntity::class,
        WeightLogEntity::class,
        MealTemplateEntity::class,
        MealTemplateItemEntity::class,
        ExerciseEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        WorkoutSessionEntity::class,
        WorkoutSetEntity::class,
        PersonalRecordEntity::class,
        CorrectionEntity::class,
        AiCacheEntity::class,
        AppStateEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun foodDao(): FoodDao
    abstract fun logDao(): LogDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun appStateDao(): AppStateDao
    abstract fun weightDao(): WeightDao
    abstract fun aiCacheDao(): AiCacheDao
    abstract fun mealDao(): MealDao
    abstract fun correctionDao(): CorrectionDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun productDao(): ProductDao

    companion object {
        const val NAME = "tracker.db"
    }
}
