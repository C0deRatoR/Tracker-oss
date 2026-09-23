package com.yash.tracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One movement.
 *
 * [primaryMuscles], [secondaryMuscles], [instructions] and [aliases] are newline-delimited
 * rather than a relation: they are read as a block whenever the exercise is, never queried
 * across, and four one-to-many tables to hold what amounts to a paragraph would buy nothing.
 */
@Entity(tableName = "exercise", indices = [Index("name")])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "muscle_group") val muscleGroup: String,
    val equipment: String,
    val type: String,
    @ColumnInfo(name = "met_value") val metValue: Double = 5.0,
    @ColumnInfo(name = "is_custom") val isCustom: Boolean = false,
    @ColumnInfo(name = "default_rest_sec") val defaultRestSec: Int = 90,
    val notes: String?,
    @ColumnInfo(name = "primary_muscles") val primaryMuscles: String? = null,
    @ColumnInfo(name = "secondary_muscles") val secondaryMuscles: String? = null,
    val instructions: String? = null,
    /** push, pull or static, as the source records it. */
    val force: String? = null,
    /** compound or isolation. */
    val mechanic: String? = null,
    /** beginner, intermediate or expert. */
    val level: String? = null,
    /** What this is called on a gym floor, so search finds "pec dec". */
    val aliases: String? = null,
    /**
     * ExerciseDB id of the movement's animation, or null for a row with none. An id, never a
     * URL: the free tier's media URLs rotate weekly and may not be stored.
     */
    val art: String? = null,
) {
    val primaryMuscleList: List<String> get() = primaryMuscles.toLines()
    val secondaryMuscleList: List<String> get() = secondaryMuscles.toLines()
    val instructionSteps: List<String> get() = instructions.toLines()
    val aliasList: List<String> get() = aliases.toLines()
}

private fun String?.toLines(): List<String> =
    this?.split('\n')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()

@Entity(tableName = "routine")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "routine_exercise",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("routine_id"), Index("exercise_id")],
)
data class RoutineExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "routine_id") val routineId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    val position: Int,
    @ColumnInfo(name = "target_sets") val targetSets: Int?,
    @ColumnInfo(name = "target_reps_low") val targetRepsLow: Int?,
    @ColumnInfo(name = "target_reps_high") val targetRepsHigh: Int?,
)

@Entity(tableName = "workout_session", indices = [Index("date")])
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "routine_id") val routineId: Long?,
    val name: String,
    val date: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    @ColumnInfo(name = "total_volume_kg") val totalVolumeKg: Double = 0.0,
    @ColumnInfo(name = "kcal_burned") val kcalBurned: Int = 0,
    val note: String?,
    @ColumnInfo(name = "is_finished") val isFinished: Boolean = false,
)

/**
 * Whether a set records anything.
 *
 * A routine prefills its rows, so a session can be finished with sets ticked but never filled
 * in — the tick says "done", the row says nothing. Those are not performances, and counting
 * them puts an exercise in your history that you did not actually do.
 */
val WorkoutSetEntity.isLogged: Boolean
    get() = isCompleted &&
        (reps != null || weightKg != null || distanceM != null || durationSec != null)

/** Warmup sets are flagged here and excluded from volume. Cardio uses distance and duration. */
@Entity(
    tableName = "workout_set",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("session_id"), Index("exercise_id")],
)
data class WorkoutSetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    val position: Int,
    @ColumnInfo(name = "set_index") val setIndex: Int,
    val reps: Int?,
    @ColumnInfo(name = "weight_kg") val weightKg: Double?,
    val rpe: Double?,
    @ColumnInfo(name = "distance_m") val distanceM: Double?,
    @ColumnInfo(name = "duration_sec") val durationSec: Int?,
    @ColumnInfo(name = "is_warmup") val isWarmup: Boolean = false,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false,
)

@Entity(tableName = "personal_record", indices = [Index("exercise_id")])
data class PersonalRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "exercise_id") val exerciseId: Long,
    val type: String,
    val value: Double,
    val date: String,
    @ColumnInfo(name = "session_id") val sessionId: Long?,
)
