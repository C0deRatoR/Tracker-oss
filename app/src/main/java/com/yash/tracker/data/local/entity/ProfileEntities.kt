package com.yash.tracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Single-row table; [id] is always [SINGLETON_ID]. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val name: String,
    val age: Int,
    val sex: String,
    @ColumnInfo(name = "height_cm") val heightCm: Double,
    val goal: String,
    @ColumnInfo(name = "goal_weight_kg") val goalWeightKg: Double?,
    val activity: String,
    val pace: String,
    @ColumnInfo(name = "eating_style") val eatingStyle: String,
    val notes: String?,
    @ColumnInfo(name = "day_start_hour") val dayStartHour: Int = 4,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

/** History of plan revisions; the row with the newest [computedAt] is the active plan. */
@Entity(tableName = "target")
data class TargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "computed_at") val computedAt: Long,
    @ColumnInfo(name = "basis_weight_kg") val basisWeightKg: Double,
    val bmr: Int,
    val tdee: Int,
    @ColumnInfo(name = "activity_factor") val activityFactor: Double,
    val kcal: Int,
    @ColumnInfo(name = "protein_g") val proteinG: Double,
    @ColumnInfo(name = "carbs_g") val carbsG: Double,
    @ColumnInfo(name = "fat_g") val fatG: Double,
    @ColumnInfo(name = "water_ml") val waterMl: Int,
    @ColumnInfo(name = "weeks_to_goal") val weeksToGoal: Int?,
    @ColumnInfo(name = "rate_lb_per_week") val rateLbPerWeek: Double?,
    @ColumnInfo(name = "deficit_capped") val deficitCapped: Boolean = false,
    @ColumnInfo(name = "floor_applied") val floorApplied: Boolean = false,
    @ColumnInfo(name = "is_manual_override") val isManualOverride: Boolean = false,
)
