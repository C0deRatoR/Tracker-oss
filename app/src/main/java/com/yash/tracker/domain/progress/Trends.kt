package com.yash.tracker.domain.progress

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/** One dated number: a weigh-in, or a day's calories. */
data class DayPoint(val date: LocalDate, val value: Double)

/**
 * Smooths a series over a window of days rather than a window of points.
 *
 * Weigh-ins arrive whenever they arrive. Averaging "the last 7 entries" would quietly stretch
 * across a month of silence and draw a trend that never happened, so the window is measured in
 * days and simply contains fewer points when there were fewer weigh-ins.
 */
object MovingAverage {

    fun overDays(points: List<DayPoint>, days: Int): List<DayPoint> {
        require(days >= 1) { "window must be at least one day" }
        val sorted = points.sortedBy { it.date }

        return sorted.map { point ->
            val from = point.date.minusDays(days - 1L)
            val window = sorted.filter { it.date >= from && it.date <= point.date }
            DayPoint(point.date, window.sumOf { it.value } / window.size)
        }
    }
}

/**
 * How often intake landed near the target.
 *
 * "Near" is a band either side, because hitting a calorie number exactly is not a realistic
 * bar — and counting only days under target would score a day of barely eating as perfect.
 */
object Adherence {

    fun percent(days: List<DayPoint>, targetKcal: Int, bandPercent: Int = 10): Int {
        if (days.isEmpty() || targetKcal <= 0) return 0
        val band = targetKcal * bandPercent / 100.0
        val within = days.count { abs(it.value - targetKcal) <= band }
        return (within * 100.0 / days.size).roundToInt()
    }
}

/**
 * Consecutive days with something logged, counting back from today.
 *
 * Today being empty does not break the streak — the day is not over. PRD §5.7 wants this shown
 * quietly, so it is a count and nothing more.
 */
object Streak {

    fun current(loggedDates: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in loggedDates) today else today.minusDays(1)
        var count = 0
        while (day in loggedDates) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}
