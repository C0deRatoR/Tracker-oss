package com.yash.tracker.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.tracker.data.local.dao.DayMacros
import com.yash.tracker.data.local.entity.ProfileEntity
import com.yash.tracker.data.local.entity.TargetEntity
import com.yash.tracker.data.local.entity.WeightLogEntity
import com.yash.tracker.data.repository.LogRepository
import com.yash.tracker.data.repository.ProfileRepository
import com.yash.tracker.data.repository.WorkoutRepository
import com.yash.tracker.domain.diary.DiaryDate
import com.yash.tracker.domain.progress.Adherence
import com.yash.tracker.domain.progress.DayPoint
import com.yash.tracker.domain.progress.MovingAverage
import com.yash.tracker.domain.progress.Streak
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt

/** Averages over a period, with the day count they were taken from. */
data class MacroAverage(
    val days: Int,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)

data class WeighIn(val id: Long, val date: LocalDate, val weightKg: Double)

/** One bar of the workout-volume chart. */
data class WeekVolume(val label: String, val kg: Double, val sessions: Int)

/** The window the whole screen is read through. */
enum class ProgressRange(val label: String, val days: Long?) {
    WEEK("7 days", 7),
    MONTH("30 days", 30),
    QUARTER("90 days", 90),
    ALL("All time", null),
}

data class ProgressUiState(
    val range: ProgressRange = ProgressRange.WEEK,
    val weighIns: List<WeighIn> = emptyList(),
    val points: List<DayPoint> = emptyList(),
    val trend: List<DayPoint> = emptyList(),
    val goalWeightKg: Double? = null,
    val changeKg: Double? = null,
    val latestWeightKg: Double? = null,
    val lastWeighInAt: Long? = null,
    val targetKcal: Int = 0,
    val history: List<DayPoint> = emptyList(),
    val adherencePercent: Int = 0,
    val weeklyAverageKcal: Int = 0,
    val last7: MacroAverage? = null,
    val last30: MacroAverage? = null,
    val streakDays: Int = 0,
    val weekVolumes: List<WeekVolume> = emptyList(),
    val volumeChangePercent: Int? = null,
    val entryOpen: Boolean = false,
    val entryText: String = "",
) {
    /** Within 10% of target, which is the definition the adherence figure uses too. */
    fun onTarget(kcal: Double): Boolean =
        targetKcal > 0 && abs(kcal - targetKcal) <= targetKcal * 0.1
}

private data class Baseline(
    val weights: List<WeightLogEntity>,
    val target: TargetEntity?,
    val profile: ProfileEntity?,
    val entry: Pair<Boolean, String>,
    val range: ProgressRange,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val logs: LogRepository,
    workouts: WorkoutRepository,
) : ViewModel() {

    private val entry = MutableStateFlow(false to "")
    private val range = MutableStateFlow(ProgressRange.WEEK)

    val state = combine(
        profiles.observeWeightLog(),
        profiles.observeActiveTarget(),
        profiles.observeProfile(),
        entry,
        range,
    ) { weights, target, profile, entryState, selected ->
        Baseline(weights, target, profile, entryState, selected)
    }.flatMapLatest { base ->
        // Always pulls at least thirty days, whatever the window: the macro card has its own
        // 7/30 toggle and must not go blank because the page is showing a week.
        val today = LocalDate.now()
        val depth = maxOf(base.range.days ?: MAX_DAYS, 30L)
        val from = today.minusDays(depth - 1)

        combine(
            logs.observeDailyMacros(from, today),
            workouts.observeRecentSessions(),
        ) { daily, sessions ->
            val weighIns = base.weights
                .map { WeighIn(it.id, DiaryDate.parse(it.date), it.weightKg) }
                .sortedBy { it.date }
            val windowStart = base.range.days?.let { today.minusDays(it - 1) }
            val points = weighIns
                .filter { windowStart == null || it.date >= windowStart }
                .map { DayPoint(it.date, it.weightKg) }

            val history = daily
                .map { DayPoint(DiaryDate.parse(it.date), it.kcal) }
                .filter { windowStart == null || it.date >= windowStart }
            val targetKcal = base.target?.kcal ?: 0

            val finished = sessions.map { it.session }.filter { it.isFinished }
            val volumes = finished.byWeek(today)

            ProgressUiState(
                range = base.range,
                weighIns = weighIns.reversed(),
                points = points,
                trend = MovingAverage.overDays(points, days = TREND_DAYS),
                goalWeightKg = base.profile?.goalWeightKg,
                // Change is measured across the window on screen, not across all of history.
                changeKg = if (points.size >= 2) points.last().value - points.first().value else null,
                latestWeightKg = weighIns.lastOrNull()?.weightKg,
                lastWeighInAt = base.weights.maxByOrNull { it.loggedAt }?.loggedAt,
                targetKcal = targetKcal,
                history = history,
                adherencePercent = Adherence.percent(history, targetKcal),
                weeklyAverageKcal = history.inLast(7).averageOf { it.value },
                last7 = daily.averageOver(7),
                last30 = daily.averageOver(30),
                streakDays = Streak.current(history.map { it.date }.toSet(), today),
                weekVolumes = volumes,
                volumeChangePercent = volumes.changePercent(),
                entryOpen = base.entry.first,
                entryText = base.entry.second,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    fun setRange(value: ProgressRange) {
        range.value = value
    }

    fun openEntry() {
        entry.value = true to ""
    }

    fun closeEntry() {
        entry.value = false to ""
    }

    fun setEntryText(text: String) {
        entry.value = true to text.filter { it.isDigit() || it == '.' }.take(5)
    }

    fun saveWeight() {
        val kg = entry.value.second.toDoubleOrNull() ?: return
        if (kg !in PLAUSIBLE_KG) return

        viewModelScope.launch {
            profiles.logWeight(LocalDate.now(), kg)
            entry.value = false to ""
        }
    }

    fun deleteWeighIn(id: Long) {
        viewModelScope.launch { profiles.deleteWeight(id) }
    }

    private companion object {
        const val TREND_DAYS = 7

        /** "All time" still has to end somewhere; ten years is past any real diary. */
        const val MAX_DAYS = 3650L

        /** A fat-fingered 8 or 800 is a typo, not a weight. */
        val PLAUSIBLE_KG = 25.0..300.0
    }
}

private fun List<DayPoint>.inLast(days: Int): List<DayPoint> {
    val from = LocalDate.now().minusDays(days - 1L)
    return filter { it.date >= from }
}

private fun List<DayPoint>.averageOf(selector: (DayPoint) -> Double): Int =
    if (isEmpty()) 0 else (sumOf(selector) / size).toInt()

/**
 * Averaged over the days that were actually logged, not the calendar window. A week with two
 * days logged shows what those two days looked like rather than diluting them with zeroes.
 */
private fun List<DayMacros>.averageOver(days: Int): MacroAverage? {
    val from = DiaryDate.format(LocalDate.now().minusDays(days - 1L))
    val window = filter { it.date >= from }
    if (window.isEmpty()) return null

    return MacroAverage(
        days = window.size,
        kcal = (window.sumOf { it.kcal } / window.size).toInt(),
        proteinG = (window.sumOf { it.proteinG } / window.size).toInt(),
        carbsG = (window.sumOf { it.carbsG } / window.size).toInt(),
        fatG = (window.sumOf { it.fatG } / window.size).toInt(),
    )
}

/** The last four calendar weeks of tonnage, oldest first, including weeks with no sessions. */
private fun List<com.yash.tracker.data.local.entity.WorkoutSessionEntity>.byWeek(
    today: LocalDate,
): List<WeekVolume> {
    val field = WeekFields.of(Locale.getDefault())
    val zone = ZoneId.systemDefault()

    val startOfThisWeek = today.with(field.dayOfWeek(), 1)
    return (3 downTo 0).map { weeksBack ->
        val start = startOfThisWeek.minusWeeks(weeksBack.toLong())
        val end = start.plusDays(6)
        val inWeek = filter {
            val date = java.time.Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate()
            !date.isBefore(start) && !date.isAfter(end)
        }
        WeekVolume(
            label = if (weeksBack == 0) "This wk" else "W${start.get(field.weekOfWeekBasedYear())}",
            kg = inWeek.sumOf { it.totalVolumeKg },
            sessions = inWeek.size,
        )
    }
}

private fun List<WeekVolume>.changePercent(): Int? {
    if (size < 2) return null
    val previous = this[size - 2].kg
    if (previous <= 0.0) return null
    return (((last().kg - previous) / previous) * 100).roundToInt()
}
