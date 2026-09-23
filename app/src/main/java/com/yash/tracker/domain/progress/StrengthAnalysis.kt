package com.yash.tracker.domain.progress

import com.yash.tracker.domain.workout.Effort
import com.yash.tracker.domain.workout.HistorySet
import com.yash.tracker.domain.workout.Muscle
import com.yash.tracker.domain.workout.TrainingAnalyst
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields

/** One lift's estimated one-rep max, session by session. */
data class LiftSeries(
    val exerciseId: Long,
    val name: String,
    val points: List<DayPoint>,
    val best: Double,
    val bestOn: LocalDate,
    /** First to last session in the window, as a share of the first. */
    val changeFraction: Double?,
)

data class PersonalBest(val date: LocalDate, val lift: String, val oneRepMaxKg: Double, val previousKg: Double)

/** Effective sets for one muscle, oldest week first. */
data class MuscleWeeks(val muscle: Muscle, val sets: List<Double>)

data class StrengthReport(
    val lifts: List<LiftSeries>,
    /** Week starts, oldest first, matching every [MuscleWeeks.sets]. */
    val weeks: List<LocalDate>,
    val muscleWeeks: List<MuscleWeeks>,
    val personalBests: List<PersonalBest>,
    /** Days with a finished session, for the heatmap. */
    val trainingDays: Set<LocalDate>,
    val sessionsPerWeek: Double,
    val longestGapDays: Int?,
    /** Weekly average protein against that week's strength change. */
    val proteinVsStrength: Correlation?,
) {
    val isEmpty: Boolean get() = trainingDays.isEmpty()
}

/**
 * The long view of training: how each lift has moved, where the weekly work went, and how
 * steadily it was done.
 *
 * Only loaded sets make a lift's line — an estimated one-rep max is what lets a set of five at
 * 80 kg and a set of ten at 65 kg be compared at all. Bodyweight and timed work still counts
 * towards volume and consistency.
 */
object StrengthAnalyst {

    fun analyse(
        sets: List<HistorySet>,
        today: LocalDate,
        proteinByDay: Map<LocalDate, Double>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): StrengthReport {
        val dated = sets.map { it to Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        val sessions = liftSessions(dated)
        val lifts = sessions.groupBy { it.exerciseId }
            .map { (_, rows) -> series(rows.sortedBy { it.date }) }
            .sortedByDescending { it.points.size }
            .take(MAX_LIFTS)

        val weeks = (WEEKS - 1 downTo 0).map { today.weekStart().minusWeeks(it.toLong()) }
        val trainingDays = dated.map { it.second }.toSet()

        return StrengthReport(
            lifts = lifts,
            weeks = weeks,
            muscleWeeks = muscleWeeks(dated, weeks),
            personalBests = personalBests(sessions),
            trainingDays = trainingDays,
            sessionsPerWeek = dated.filter { it.second >= weeks.first() }
                .map { it.first.sessionId }.distinct().size / WEEKS.toDouble(),
            longestGapDays = trainingDays.sorted().zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b).toInt() }.maxOrNull(),
            proteinVsStrength = proteinVsStrength(sessions, proteinByDay),
        )
    }

    private data class LiftSession(val exerciseId: Long, val name: String, val date: LocalDate, val oneRepMaxKg: Double)

    /** The best loaded set of each exercise in each session. */
    private fun liftSessions(dated: List<Pair<HistorySet, LocalDate>>): List<LiftSession> =
        dated.groupBy { it.first.sessionId to it.first.exerciseId }
            .mapNotNull { (_, rows) ->
                val best = rows.mapNotNull { (set, _) ->
                    Effort.of(set.reps, set.weightKg, set.durationSec) as? Effort.Load
                }.maxOfOrNull { it.oneRepMaxKg } ?: return@mapNotNull null
                val first = rows.first()
                LiftSession(first.first.exerciseId, first.first.exerciseName, first.second, best)
            }

    private fun series(rows: List<LiftSession>): LiftSeries {
        val points = rows.map { DayPoint(it.date, it.oneRepMaxKg) }
        val best = rows.maxBy { it.oneRepMaxKg }
        val first = points.first().value
        return LiftSeries(
            exerciseId = rows.first().exerciseId,
            name = rows.first().name,
            points = points,
            best = best.oneRepMaxKg,
            bestOn = best.date,
            changeFraction = if (points.size >= 2 && first > 0) (points.last().value - first) / first else null,
        )
    }

    /**
     * A personal best is a session that beat every earlier session of the same lift. The very
     * first session is a baseline, not a record, for the reason the session summary gives.
     */
    private fun personalBests(sessions: List<LiftSession>): List<PersonalBest> =
        sessions.groupBy { it.exerciseId }.flatMap { (_, rows) ->
            var best: Double? = null
            rows.sortedBy { it.date }.mapNotNull { row ->
                val previous = best
                if (previous == null || row.oneRepMaxKg > previous * (1 + PB_MARGIN)) {
                    best = maxOf(previous ?: 0.0, row.oneRepMaxKg)
                    previous?.let { PersonalBest(row.date, row.name, row.oneRepMaxKg, it) }
                } else {
                    null
                }
            }
        }.sortedByDescending { it.date }.take(MAX_PBS)

    private fun muscleWeeks(dated: List<Pair<HistorySet, LocalDate>>, weeks: List<LocalDate>): List<MuscleWeeks> {
        val byWeek = weeks.map { start ->
            TrainingAnalyst.effectiveSets(dated.filter { it.second >= start && it.second < start.plusWeeks(1) }.map { it.first })
        }
        return Muscle.entries.filter { it.isPriority }.map { muscle ->
            MuscleWeeks(muscle, byWeek.map { it[muscle] ?: 0.0 })
        }
    }

    /**
     * Whether the weeks with more protein were the weeks lifts moved more.
     *
     * Each week's strength change is the average, across the lifts trained that week, of how
     * far each one's best sat above or below that lift's previous session. Protein is the
     * week's average over the days that logged any. Correlation is not cause, and the screen
     * says so; this only asks whether the pattern is there at all.
     */
    private fun proteinVsStrength(sessions: List<LiftSession>, proteinByDay: Map<LocalDate, Double>): Correlation? {
        val changes = sessions.groupBy { it.exerciseId }.flatMap { (_, rows) ->
            rows.sortedBy { it.date }.zipWithNext { a, b ->
                b.date to (b.oneRepMaxKg - a.oneRepMaxKg) / a.oneRepMaxKg
            }
        }
        val strengthByWeek = changes.groupBy { it.first.weekKey() }.mapValues { (_, v) -> v.map { it.second }.average() }
        val proteinByWeek = proteinByDay.entries.groupBy { it.key.weekKey() }
            .filterValues { it.size >= MIN_PROTEIN_DAYS }
            .mapValues { (_, v) -> v.map { it.value }.average() }
        val pairs = strengthByWeek.mapNotNull { (week, change) -> proteinByWeek[week]?.let { it to change } }
        return Stats.pearson(pairs)
    }

    private fun LocalDate.weekStart(): LocalDate = minusDays(dayOfWeek.value - 1L)

    private fun LocalDate.weekKey(): Int = get(IsoFields.WEEK_BASED_YEAR) * 100 + get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    /** Twelve weeks: long enough to see a trend, short enough to read at a glance. */
    const val WEEKS = 12

    private const val MAX_LIFTS = 6
    private const val MAX_PBS = 8

    /** A best has to clear the last one by this much, or a rounding difference is a "record". */
    private const val PB_MARGIN = 0.005

    private const val MIN_PROTEIN_DAYS = 3
}
