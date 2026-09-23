package com.yash.tracker.domain.workout

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * What one exercise's best set was worth, measured in whatever that exercise is measured in.
 *
 * Three kinds rather than one number, because a pull-up and a treadmill do not have a common
 * unit and pretending otherwise is how a bodyweight movement ends up reading "no change" for
 * ever: weight × reps is zero on both sides, so every session ties.
 *
 * Two efforts only compare when they are the same kind. Anything else is not a comparison.
 */
sealed interface Effort {
    /** A loaded set, as its estimated one-rep max. */
    data class Load(val oneRepMaxKg: Double) : Effort

    /** An unloaded set, as reps. */
    data class Reps(val count: Int) : Effort

    /** A held or travelled set, as seconds. */
    data class Time(val seconds: Int) : Effort

    val magnitude: Double
        get() = when (this) {
            is Load -> oneRepMaxKg
            is Reps -> count.toDouble()
            is Time -> seconds.toDouble()
        }

    fun comparableWith(other: Effort): Boolean = this::class == other::class

    companion object {
        /**
         * What a single set was worth.
         *
         * Load beats reps beats time, so a weighted set is never scored as a bodyweight one
         * just because a weight of zero happened to be typed into it.
         */
        fun of(reps: Int?, weightKg: Double?, durationSec: Int?): Effort? {
            val weight = weightKg ?: 0.0
            val count = reps ?: 0

            return when {
                weight > 0 && count > 0 -> Load(OneRepMax.epley(weight, count))
                count > 0 -> Reps(count)
                (durationSec ?: 0) > 0 -> Time(durationSec!!)
                else -> null
            }
        }
    }
}

enum class Trend { UP, DOWN, LEVEL, FIRST }

/** One exercise as this session did it, against the last session that did it. */
data class ExerciseTrend(
    val exerciseId: Long,
    val name: String,
    val best: Effort,
    val previous: Effort?,
    val trend: Trend,
    /** Signed, as a share of the previous effort. Null when there is nothing to compare. */
    val changeFraction: Double?,
)

/** How much of the session one muscle group took. */
data class MuscleShare(
    val group: String,
    val workingSets: Int,
    val volumeKg: Double,
    /** Of the session's working sets, not its volume — sets are what a bodyweight lift moves. */
    val share: Double,
)

/** A session against the last one like it. */
data class SessionChange(
    val volumeKg: Double,
    val previousVolumeKg: Double,
    val workingSets: Int,
    val previousWorkingSets: Int,
) {
    val volumeFraction: Double?
        get() = if (previousVolumeKg > 0) (volumeKg - previousVolumeKg) / previousVolumeKg else null
}

data class SessionAnalysis(
    val muscleSplit: List<MuscleShare>,
    val trends: List<ExerciseTrend>,
    val change: SessionChange?,
    /** Plain observations, at most a few, worst or most surprising first. */
    val notes: List<String>,
    /** This session alone, muscle by muscle: the fine split, rep ranges, effort, push and pull. */
    val breakdown: TrainingReport? = null,
    /** The seven days ending with this session, for what the week still needs. */
    val week: TrainingReport? = null,
) {
    val isEmpty: Boolean get() = muscleSplit.isEmpty() && trends.isEmpty()
}

/** One set as the analysis needs it, with the exercise it belongs to already named. */
data class AnalysedSet(
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroup: String,
    val reps: Int?,
    val weightKg: Double?,
    val durationSec: Int?,
    val isWarmup: Boolean,
    val isCompleted: Boolean,
)

/**
 * Reads a finished session back: where the work went, and whether each lift moved.
 *
 * Everything here is comparison against this user's own history, never against a standard.
 * There is no table of what a bench press ought to be, and the useful question after a session
 * is not "is this good" but "is this more than last time".
 */
object SessionAnalyst {

    fun analyse(
        sets: List<AnalysedSet>,
        /** Each exercise's best effort in the last session that trained it before this one. */
        previousBests: Map<Long, Effort>,
        previousSession: SessionChange? = null,
    ): SessionAnalysis {
        val working = sets.filter { it.isCompleted && !it.isWarmup && it.isLogged }
        if (working.isEmpty()) {
            return SessionAnalysis(emptyList(), emptyList(), previousSession, emptyList())
        }

        val split = muscleSplit(working)
        val trends = trends(working, previousBests)

        return SessionAnalysis(
            muscleSplit = split,
            trends = trends,
            change = previousSession,
            notes = notes(split, trends, previousSession),
        )
    }

    private fun muscleSplit(working: List<AnalysedSet>): List<MuscleShare> {
        val total = working.size.toDouble()

        return working
            .groupBy { it.muscleGroup }
            .map { (group, groupSets) ->
                MuscleShare(
                    group = group,
                    workingSets = groupSets.size,
                    volumeKg = groupSets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) },
                    share = groupSets.size / total,
                )
            }
            .sortedByDescending { it.workingSets }
    }

    private fun trends(
        working: List<AnalysedSet>,
        previousBests: Map<Long, Effort>,
    ): List<ExerciseTrend> = working
        .groupBy { it.exerciseId }
        .mapNotNull { (exerciseId, exerciseSets) ->
            val best = exerciseSets
                .mapNotNull { Effort.of(it.reps, it.weightKg, it.durationSec) }
                .maxByOrNull { it.magnitude }
                ?: return@mapNotNull null
            val previous = previousBests[exerciseId]?.takeIf { it.comparableWith(best) }

            val change = previous
                ?.takeIf { it.magnitude > 0 }
                ?.let { (best.magnitude - it.magnitude) / it.magnitude }

            ExerciseTrend(
                exerciseId = exerciseId,
                name = exerciseSets.first().exerciseName,
                best = best,
                previous = previous,
                trend = when {
                    previous == null -> Trend.FIRST
                    change == null || abs(change) < LEVEL_BAND -> Trend.LEVEL
                    change > 0 -> Trend.UP
                    else -> Trend.DOWN
                },
                changeFraction = change,
            )
        }
        .sortedByDescending { it.changeFraction ?: 0.0 }

    /**
     * The two or three things worth saying in a sentence.
     *
     * Deliberately few. A screen of observations is a screen nobody reads, and the numbers
     * above it already say most of what happened.
     */
    private fun notes(
        split: List<MuscleShare>,
        trends: List<ExerciseTrend>,
        change: SessionChange?,
    ): List<String> = buildList {
        val dominant = split.firstOrNull()
        if (dominant != null && split.size > 1 && dominant.share >= LOPSIDED) {
            add(
                "${dominant.group.readable()} took ${(dominant.share * 100).roundToInt()}% of " +
                    "the working sets.",
            )
        }

        val up = trends.count { it.trend == Trend.UP }
        val down = trends.count { it.trend == Trend.DOWN }
        if (up > 0 || down > 0) {
            add(
                buildString {
                    if (up > 0) append("$up ${"exercise".plural(up)} up")
                    if (up > 0 && down > 0) append(", ")
                    if (down > 0) append("$down down")
                    append(" on last time.")
                },
            )
        }

        val first = trends.count { it.trend == Trend.FIRST }
        if (first > 0 && first == trends.size) {
            add("Nothing here has a previous session to compare against yet.")
        }

        change?.volumeFraction?.let { fraction ->
            if (abs(fraction) >= NOTABLE_VOLUME_SHIFT) {
                val direction = if (fraction > 0) "up" else "down"
                add("Total volume $direction ${(abs(fraction) * 100).roundToInt()}% on the last one.")
            }
        }
    }

    /** Within this, a lift did the same thing twice and calling it progress would be noise. */
    private const val LEVEL_BAND = 0.02

    /** One group past this share is the session's subject, whatever it was called. */
    private const val LOPSIDED = 0.5

    private const val NOTABLE_VOLUME_SHIFT = 0.1
}

/** Ticked and filled in — the same rule the diary uses, on the analysis's own row shape. */
val AnalysedSet.isLogged: Boolean
    get() = reps != null || weightKg != null || durationSec != null

/** FULL_BODY reads as "Full body" on a card; the enum spelling belongs in the database. */
fun String.readable(): String = lowercase()
    .replace('_', ' ')
    .replaceFirstChar(Char::uppercase)

private fun String.plural(count: Int): String = if (count == 1) this else "${this}s"
