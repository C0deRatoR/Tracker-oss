package com.yash.tracker.di

import android.content.Context
import androidx.room.Room
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.MIGRATION_1_2
import com.yash.tracker.data.local.MIGRATION_2_3
import com.yash.tracker.data.local.MIGRATION_3_4
import com.yash.tracker.data.local.MIGRATION_4_5
import com.yash.tracker.data.local.MIGRATION_5_6
import com.yash.tracker.data.local.MIGRATION_6_7
import com.yash.tracker.data.local.MIGRATION_7_8
import com.yash.tracker.data.local.dao.AiCacheDao
import com.yash.tracker.data.local.dao.AppStateDao
import com.yash.tracker.data.local.dao.CorrectionDao
import com.yash.tracker.data.local.dao.ExerciseDao
import com.yash.tracker.data.local.dao.FoodDao
import com.yash.tracker.data.local.dao.LogDao
import com.yash.tracker.data.local.dao.MealDao
import com.yash.tracker.data.local.dao.ProfileDao
import com.yash.tracker.data.local.dao.WeightDao
import com.yash.tracker.data.local.dao.ProductDao
import com.yash.tracker.data.local.dao.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
            .build()

    @Provides
    fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideFoodDao(db: AppDatabase): FoodDao = db.foodDao()

    @Provides
    fun provideLogDao(db: AppDatabase): LogDao = db.logDao()

    @Provides
    fun provideExerciseDao(db: AppDatabase): ExerciseDao = db.exerciseDao()

    @Provides
    fun provideAppStateDao(db: AppDatabase): AppStateDao = db.appStateDao()

    @Provides
    fun provideWeightDao(db: AppDatabase): WeightDao = db.weightDao()

    @Provides
    fun provideAiCacheDao(db: AppDatabase): AiCacheDao = db.aiCacheDao()

    @Provides
    fun provideMealDao(db: AppDatabase): MealDao = db.mealDao()

    @Provides
    fun provideCorrectionDao(db: AppDatabase): CorrectionDao = db.correctionDao()

    @Provides
    fun provideWorkoutDao(db: AppDatabase): WorkoutDao = db.workoutDao()

    @Provides
    fun provideProductDao(db: AppDatabase): ProductDao = db.productDao()
}
