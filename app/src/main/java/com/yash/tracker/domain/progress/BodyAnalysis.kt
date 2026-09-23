package com.yash.tracker.domain.progress

import com.yash.tracker.domain.model.Goal
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

enum class PaceVerdict { ON_PACE, AHEAD, BEHIND, WRONG_WAY }

enum class Confidence { LOW, MEDIUM, HIGH }

/**
 * What the body actually spends in a day, worked out backwards from what was eaten and what the
 * scale did — rather than from a formula about people of your height and age.
 */
data class TdeeEstimate(
    val kcal: Int,
    /** The formula's figure from the active plan, for comparison. */
    val planKcal: Int?,
    val loggedDays: Int,
    val spanDays: Int,
    val confidence: Confidence,
)

data class BodyReport(
    /** Exponentially smoothed weight, one point per weigh-in. */
    val trend: List<DayPoint>,
    val currentTrendKg: Double?,
    /** Signed: negative is losing. From the last four weeks of weigh-ins. */
    val ratePerWeekKg: Double?,
    /** Signed, what the plan is aiming for. Zero for maintenance. */
    val targetRatePerWeekKg: Double?,
    val pace: PaceVerdict?,
    val goalWeightKg: Double?,
    val projectedGoalDate: LocalDate?,
    val tdee: TdeeEstimate?,
    /** Weekly intake against that week's trend change — do the heavier weeks show up? */
    val intakeVsWeight: Correlation?,
)

/**
 * Reads the scale properly.
 *
 * Day-to-day weight swings by a kilo on water and salt alone, so everything here works off a
 * smoothed trend, not the raw readings. The smoothing is the exponential moving average from
 * The Hacker's Diet (10% of each new reading), stretched across gaps so a week without
 * weighing counts for a week rather than a day.
 */
object BodyAnalyst {

    fun analyse(
        weighIns: List<DayPoint>,
        dailyKcal: List<DayPoint>,
        today: LocalDate,
        goal: Goal?,
        goalWeightKg: Double?,
        planRateLbPerWeek: Double?,
        planTdee: Int?,
    ): BodyReport {
        val trend = trend(weighIns)
        val current = trend.lastOrNull()?.value
        val rate = ratePerWeek(weighIns, today)
        val target = targetRate(goal, planRateLbPerWeek)

        return BodyReport(
            trend = trend,
            currentTrendKg = current,
            ratePerWeekKg = rate,
            targetRatePerWeekKg = target,
            pace = if (rate != null && target != null) pace(rate, target) else null,
            goalWeightKg = goalWeightKg,
            projectedGoalDate = projectedDate(current, goalWeightKg, rate, today),
            tdee = tdee(weighIns, dailyKcal, today, planTdee),
            intakeVsWeight = intakeVsWeight(trend, dailyKcal),
        )
    }

    fun trend(weighIns: List<DayPoint>): List<DayPoint> {
        val sorted = weighIns.sortedBy { it.date }
        var previous: DayPoint? = null
        return sorted.map { point ->
            val last = previous
            val value = if (last == null) {
                point.value
            } else {
                val gap = ChronoUnit.DAYS.between(last.date, point.date).coerceAtLeast(1)
                // Each day without a reading still lets the trend drift towards the next one.
                val alpha = 1 - (1 - SMOOTHING).pow(gap.toDouble())
                last.value + alpha * (point.value - last.value)
            }
            DayPoint(point.date, value).also { previous = it }
        }
    }

    /**
     * kg per week, from a straight line through the last four weeks of weigh-ins.
     *
     * The raw readings rather than the smoothed trend: the trend lags by design, so its slope
     * understates any real change, and a least-squares line already averages out the noise.
     */
    private fun ratePerWeek(weighIns: List<DayPoint>, today: LocalDate): Double? {
        val recent = weighIns.sortedBy { it.date }.filter { it.date >= today.minusDays(RATE_WINDOW_DAYS - 1L) }
        if (recent.size < MIN_RATE_POINTS) return null
        val span = ChronoUnit.DAYS.between(recent.first().date, recent.last().date)
        if (span < MIN_RATE_SPAN_DAYS) return null
        return Stats.slope(recent.map { it.date.toEpochDay().toDouble() to it.value })?.times(7)
    }

    private fun targetRate(goal: Goal?, rateLbPerWeek: Double?): Double? = when (goal) {
        Goal.MAINTAIN -> 0.0
        Goal.LOSE -> rateLbPerWeek?.let { -it * KG_PER_LB }
        Goal.GAIN -> rateLbPerWeek?.let { it * KG_PER_LB }
        null -> null
    }

    /**
     * Whether the trend is doing what the plan asked.
     *
     * Within a tenth of a kilo a week, or a quarter of the target, is on pace: tighter than
     * that and ordinary water noise flips the verdict every few days.
     */
    fun pace(rate: Double, target: Double): PaceVerdict {
        val tolerance = maxOf(PACE_TOLERANCE_KG, abs(target) * PACE_TOLERANCE_SHARE)
        if (abs(rate - target) <= tolerance) return PaceVerdict.ON_PACE
        // Maintenance has no "ahead": drifting either way is off plan.
        if (target == 0.0) return PaceVerdict.WRONG_WAY
        if (rate * target < 0 || rate == 0.0) return PaceVerdict.WRONG_WAY
        return if (abs(rate) > abs(target)) PaceVerdict.AHEAD else PaceVerdict.BEHIND
    }

    private fun projectedDate(current: Double?, goal: Double?, rate: Double?, today: LocalDate): LocalDate? {
        if (current == null || goal == null || rate == null) return null
        val remaining = goal - current
        if (abs(remaining) < GOAL_REACHED_KG) return today
        // Heading away from the goal, or not moving, has no arrival date.
        if (rate == 0.0 || remaining * rate < 0) return null
        val days = (remaining / rate * 7).roundToInt()
        return if (days > MAX_PROJECTION_DAYS) null else today.plusDays(days.toLong())
    }

    /**
     * Intake minus what went into or out of storage.
     *
     * Over the window, the average day's calories less the weight change converted at 7,700
     * kcal a kilo — the change read off a straight line through the weigh-ins, since the
     * smoothed trend lags and would understate it. Only full days count as intake — a day with just breakfast logged is a gap in
     * the diary, and averaging it in would make the body look like it runs on air. The answer
     * is only as good as the logging, so it carries a confidence the screen shows with it.
     */
    fun tdee(weighIns: List<DayPoint>, dailyKcal: List<DayPoint>, today: LocalDate, planTdee: Int?): TdeeEstimate? {
        val from = today.minusDays(TDEE_WINDOW_DAYS - 1L)
        val window = weighIns.sortedBy { it.date }.filter { it.date >= from }
        if (window.size < 2) return null
        val span = ChronoUnit.DAYS.between(window.first().date, window.last().date).toInt()
        if (span < MIN_TDEE_SPAN_DAYS) return null

        val intake = dailyKcal.filter {
            it.date >= window.first().date && it.date <= window.last().date && it.value >= FULL_DAY_KCAL
        }
        if (intake.size < MIN_TDEE_DAYS) return null

        val perDay = Stats.slope(window.map { it.date.toEpochDay().toDouble() to it.value }) ?: return null
        val stored = perDay * KCAL_PER_KG
        val estimate = intake.sumOf { it.value } / intake.size - stored

        val confidence = when {
            intake.size >= HIGH_DAYS && window.size >= HIGH_WEIGH_INS -> Confidence.HIGH
            intake.size >= MEDIUM_DAYS && window.size >= MEDIUM_WEIGH_INS -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        return TdeeEstimate(estimate.roundToInt(), planTdee, intake.size, span, confidence)
    }

    /** Per ISO week: average full-day intake against how much the trend moved that week. */
    private fun intakeVsWeight(trend: List<DayPoint>, dailyKcal: List<DayPoint>): Correlation? {
        val trendByWeek = trend.groupBy { it.date.weekKey() }
        val pairs = dailyKcal
            .filter { it.value >= FULL_DAY_KCAL }
            .groupBy { it.date.weekKey() }
            .filterValues { it.size >= MIN_DAYS_PER_WEEK }
            .mapNotNull { (week, days) ->
                val points = trendByWeek[week]?.sortedBy { it.date } ?: return@mapNotNull null
                if (points.size < 2) return@mapNotNull null
                days.sumOf { it.value } / days.size to points.last().value - points.first().value
            }
        return Stats.pearson(pairs)
    }

    private fun LocalDate.weekKey(): Int = get(IsoFields.WEEK_BASED_YEAR) * 100 + get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    /** The Hacker's Diet smoothing: each day moves the trend a tenth of the way to the scale. */
    private const val SMOOTHING = 0.1

    private const val RATE_WINDOW_DAYS = 28
    private const val MIN_RATE_POINTS = 3
    private const val MIN_RATE_SPAN_DAYS = 7L

    private const val KG_PER_LB = 0.4536
    private const val PACE_TOLERANCE_KG = 0.1
    private const val PACE_TOLERANCE_SHARE = 0.25

    private const val GOAL_REACHED_KG = 0.3
    private const val MAX_PROJECTION_DAYS = 3 * 365

    /** The usual energy figure for a kilo of body mass, fat and the water that comes with it. */
    private const val KCAL_PER_KG = 7700.0

    private const val TDEE_WINDOW_DAYS = 28
    private const val MIN_TDEE_SPAN_DAYS = 10
    private const val MIN_TDEE_DAYS = 10
    private const val HIGH_DAYS = 21
    private const val HIGH_WEIGH_INS = 8
    private const val MEDIUM_DAYS = 14
    private const val MEDIUM_WEIGH_INS = 4

    /** Fewer calories than this logged, and the day is missing meals rather than showing intake. */
    const val FULL_DAY_KCAL = 800.0

    private const val MIN_DAYS_PER_WEEK = 3
}
