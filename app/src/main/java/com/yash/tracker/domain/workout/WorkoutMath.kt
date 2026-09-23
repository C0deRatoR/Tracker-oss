package com.yash.tracker.domain.workout

import kotlin.math.roundToInt

/** A completed set, reduced to what the maths needs. */
data class ScoredSet(
    val exerciseId: Long,
    val reps: Int?,
    val weightKg: Double?,
    val durationSec: Int?,
    val isWarmup: Boolean,
    val isCompleted: Boolean,
)

object OneRepMax {
    /** Epley: w × (1 + reps / 30). A single rep is already the max. */
    fun epley(weightKg: Double, reps: Int): Double =
        if (reps <= 1) weightKg else weightKg * (1 + reps / 30.0)
}

object VolumeCalculator {
    /** Warmups are excluded, so volume tracks working effort (TRD §5.6). */
    fun totalKg(sets: List<ScoredSet>): Double = sets
        .filter { it.isCompleted && !it.isWarmup }
        .sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
}

object MetCalories {
    /** kcal = MET × bodyweight(kg) × hours. */
    fun burned(met: Double, bodyweightKg: Double, seconds: Int): Int =
        (met * bodyweightKg * (seconds / 3600.0)).roundToInt()
}

enum class RecordType { MAX_WEIGHT, EST_1RM, MAX_VOLUME, MAX_REPS }

data class Record(val exerciseId: Long, val type: RecordType, val value: Double)

object PersonalRecords {

    /**
     * The bests a session reached per exercise. Whether each is actually a record is decided
     * against stored history by the caller; this only reduces the session.
     */
    fun bestsIn(sets: List<ScoredSet>): List<Record> = sets
        .filter { it.isCompleted && !it.isWarmup }
        .groupBy { it.exerciseId }
        .flatMap { (exerciseId, exerciseSets) ->
            val weighted = exerciseSets.filter { (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 }
            buildList {
                weighted.mapNotNull { it.weightKg }.maxOrNull()?.let {
                    add(Record(exerciseId, RecordType.MAX_WEIGHT, it))
                }
                weighted.maxOfOrNull { OneRepMax.epley(it.weightKg!!, it.reps!!) }?.let {
                    add(Record(exerciseId, RecordType.EST_1RM, it))
                }
                weighted.maxOfOrNull { it.weightKg!! * it.reps!! }?.let {
                    add(Record(exerciseId, RecordType.MAX_VOLUME, it))
                }
                exerciseSets.mapNotNull { it.reps }.filter { it > 0 }.maxOrNull()?.let {
                    add(Record(exerciseId, RecordType.MAX_REPS, it.toDouble()))
                }
            }
        }

    /** The best single set of an exercise, as History shows it. */
    fun bestSet(sets: List<ScoredSet>): ScoredSet? = sets
        .filter { it.isCompleted && !it.isWarmup }
        .maxByOrNull { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
}
