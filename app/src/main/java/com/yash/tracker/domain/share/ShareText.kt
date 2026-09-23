package com.yash.tracker.domain.share

import com.yash.tracker.data.local.dao.DayTotals
import com.yash.tracker.data.local.entity.TargetEntity
import com.yash.tracker.data.local.entity.WorkoutSessionEntity
import com.yash.tracker.data.local.entity.WorkoutSetEntity
import com.yash.tracker.data.local.entity.isLogged
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * What gets shared, as plain text.
 *
 * Plain text rather than a rendered card: it pastes into WhatsApp, a note, or a message to a
 * coach without an image pipeline, and it stays readable when the receiving app strips
 * formatting. Built here, away from Compose, so the wording is testable.
 *
 * Nothing here includes anything the user did not ask to send — no name, no weight, no
 * targets beyond the day being shared.
 */
object ShareText {

    private val DAY = DateTimeFormatter.ofPattern("EEE d MMM")

    /** A day's intake against its target. */
    fun forDay(
        date: LocalDate,
        totals: DayTotals,
        target: TargetEntity?,
        waterMl: Int,
    ): String = buildString {
        appendLine("${date.format(DAY)} — food")
        appendLine()

        val kcal = totals.kcal.roundToInt()
        if (target != null && target.kcal > 0) {
            val remaining = target.kcal - kcal
            appendLine("$kcal / ${target.kcal} kcal")
            appendLine(
                if (remaining >= 0) "$remaining left" else "${-remaining} over",
            )
        } else {
            appendLine("$kcal kcal")
        }

        appendLine()
        appendLine(macroLine("Protein", totals.proteinG, target?.proteinG))
        appendLine(macroLine("Carbs", totals.carbsG, target?.carbsG))
        appendLine(macroLine("Fat", totals.fatG, target?.fatG))

        if (waterMl > 0) {
            appendLine()
            append("Water %.1f L".format(waterMl / 1000.0))
        }
    }.trimEnd()

    private fun macroLine(label: String, grams: Double, target: Double?): String {
        val value = grams.roundToInt()
        return if (target != null && target > 0) {
            "$label $value / ${target.roundToInt()} g"
        } else {
            "$label $value g"
        }
    }

    /**
     * A finished session, exercise by exercise.
     *
     * Only sets that were actually filled in: a routine prefills its rows, so sharing every
     * row would list work that was not done.
     */
    fun forSession(
        session: WorkoutSessionEntity,
        sets: List<WorkoutSetEntity>,
        nameOf: (Long) -> String?,
    ): String = buildString {
        val date = java.time.Instant.ofEpochMilli(session.startedAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()

        appendLine("${session.name.ifBlank { "Workout" }} — ${date.format(DAY)}")
        appendLine()

        val durationSec = session.endedAt?.let { ((it - session.startedAt) / 1000).toInt() } ?: 0
        val logged = sets.filter { it.isLogged }
        appendLine(
            listOf(
                clock(durationSec),
                "%,d kg".format(session.totalVolumeKg.roundToInt()),
                "${logged.count { !it.isWarmup }} sets",
            ).joinToString("  ·  "),
        )

        logged
            .groupBy { it.exerciseId }
            .forEach { (exerciseId, exerciseSets) ->
                appendLine()
                appendLine(nameOf(exerciseId) ?: "Exercise")
                exerciseSets.sortedBy { it.setIndex }.forEach { set ->
                    appendLine("  ${describe(set)}")
                }
            }
    }.trimEnd()

    private fun describe(set: WorkoutSetEntity): String {
        val prefix = if (set.isWarmup) "warm-up  " else ""
        return prefix + when {
            set.weightKg != null && set.reps != null ->
                "${trim(set.weightKg)} kg × ${set.reps}"
            set.reps != null -> "${set.reps} reps"
            set.distanceM != null && set.durationSec != null ->
                "${trim(set.distanceM / 1000)} km in ${clock(set.durationSec)}"
            set.distanceM != null -> "${trim(set.distanceM / 1000)} km"
            set.durationSec != null -> clock(set.durationSec)
            else -> "—"
        }
    }

    private fun clock(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val minutes = safe / 60
        return if (minutes >= 60) {
            "${minutes / 60}h ${minutes % 60}m"
        } else {
            "%d:%02d".format(minutes, safe % 60)
        }
    }

    private fun trim(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
}
