package com.yash.tracker.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yash.tracker.domain.diary.MealType
import com.yash.tracker.domain.progress.BodyReport
import com.yash.tracker.domain.progress.Confidence
import com.yash.tracker.domain.progress.Correlation
import com.yash.tracker.domain.progress.NutritionReport
import com.yash.tracker.domain.progress.PaceVerdict
import com.yash.tracker.domain.progress.SplitAverages
import com.yash.tracker.domain.progress.StrengthReport
import com.yash.tracker.ui.components.Eyebrow
import com.yash.tracker.ui.components.Hairline
import com.yash.tracker.ui.components.LineChart
import com.yash.tracker.ui.components.LuxCard
import com.yash.tracker.ui.components.Pill
import com.yash.tracker.ui.components.PillTone
import com.yash.tracker.ui.components.ThinBar
import com.yash.tracker.ui.components.ValueRow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM")
private val LONG_DATE = DateTimeFormatter.ofPattern("d MMM yyyy")

// --- body -----------------------------------------------------------------------------------

/**
 * What the scale says once the noise is taken out: how fast, against what plan, arriving
 * when, and what the body is actually burning.
 */
@Composable
fun BodyInsightsCard(report: BodyReport) {
    InsightCard("Rate and energy") {
        val rate = report.ratePerWeekKg
        if (rate == null) {
            Note("Weigh in a few times across a week or more and the rate appears here.")
        } else {
            ValueRow("Trend weight", report.currentTrendKg?.let { "%.1f kg".format(it) } ?: "—")
            ValueRow("Rate", "%s kg / week".format(signed(rate)))
            report.targetRatePerWeekKg?.let { ValueRow("Plan", "%s kg / week".format(signed(it))) }
            report.pace?.let { pace ->
                Spacer(Modifier.height(6.dp))
                Pill(paceLabel(pace), tone = if (pace == PaceVerdict.ON_PACE) PillTone.Accent else PillTone.Quiet)
            }
            if (report.goalWeightKg != null) {
                Spacer(Modifier.height(6.dp))
                ValueRow(
                    "Goal %.1f kg".format(report.goalWeightKg),
                    report.projectedGoalDate?.let { "about ${it.format(LONG_DATE)}" }
                        ?: "no arrival date at this rate",
                )
            }
        }

        Rule()
        val tdee = report.tdee
        if (tdee == null) {
            Note(
                "Your real daily burn needs about two weeks of full days logged and a few weigh-ins " +
                    "across them. Then it is worked out from what you ate and what the scale did.",
            )
        } else {
            ValueRow("Measured burn", "%,d kcal / day".format(tdee.kcal))
            tdee.planKcal?.let {
                val gap = tdee.kcal - it
                ValueRow("Plan's estimate", "%,d kcal (%s%,d)".format(it, if (gap >= 0) "+" else "−", abs(gap)))
            }
            Note(
                "From ${tdee.loggedDays} full days over ${tdee.spanDays} days · " +
                    "${confidenceLabel(tdee.confidence)} confidence. Calories eaten, less the weight " +
                    "change at 7,700 kcal a kilo.",
            )
        }

        report.intakeVsWeight?.let {
            Rule()
            CorrelationLine("Heavier-eating weeks and weight gain", it, unit = "weeks")
        }
    }
}

// --- food -----------------------------------------------------------------------------------

@Composable
fun FoodInsightsCard(report: NutritionReport) {
    if (report.fullDays == 0) {
        InsightCard("Patterns") { Note("Log a few full days and the patterns start here.") }
        return
    }

    InsightCard("How often you hit it", count = "${report.fullDays} full days") {
        report.hitRates.forEachIndexed { index, rate ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            BarRow(rate.label, rate.percent / 100f, "${rate.percent}%", caption = rate.rule)
        }
        report.proteinPerKg?.let {
            Rule()
            ValueRow("Protein per kg bodyweight", "%.1f g / kg".format(it))
            Note("1.6 g/kg and up is the range most strength research lands on.")
        }
    }

    if (report.micros.isNotEmpty()) {
        InsightCard("Fibre, sugar and salt", count = "daily average") {
            report.micros.forEachIndexed { index, micro ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                val value = "%,d %s".format(micro.average.roundToInt(), micro.unit)
                val target = micro.target
                if (target != null) {
                    BarRow(
                        micro.label,
                        (micro.average / target).toFloat(),
                        value,
                        caption = if (micro.targetIsCap) {
                            "cap %,d %s".format(target.roundToInt(), micro.unit)
                        } else {
                            "aim %,d %s".format(target.roundToInt(), micro.unit)
                        },
                        over = micro.targetIsCap && micro.average > target,
                    )
                } else {
                    ValueRow(micro.label, value)
                }
            }
        }
    }

    InsightCard("When and what") {
        report.weekdayVsWeekend?.let { SplitRow("Weekdays vs weekend", it, "kcal") }
        report.trainingVsRestKcal?.let { SplitRow("Training vs rest days", it, "kcal") }
        report.trainingVsRestProtein?.let { SplitRow("Protein, training vs rest", it, "g") }

        if (report.mealShares.isNotEmpty()) {
            Rule()
            Eyebrow("Share of the day")
            Spacer(Modifier.height(8.dp))
            MealType.entries.forEach { meal ->
                val share = report.mealShares[meal] ?: 0.0
                BarRow(meal.name.lowercase().replaceFirstChar(Char::uppercase), share.toFloat(), "${(share * 100).roundToInt()}%")
                Spacer(Modifier.height(8.dp))
            }
        }

        if (report.topFoods.isNotEmpty()) {
            Rule()
            Eyebrow("Where the calories came from")
            Spacer(Modifier.height(8.dp))
            report.topFoods.forEach { food ->
                ValueRow(food.name, "${(food.share * 100).roundToInt()}% · ×${food.times}")
            }
        }
    }
}

// --- training -------------------------------------------------------------------------------

@Composable
fun StrengthInsights(report: StrengthReport) {
    if (report.isEmpty) {
        InsightCard("Strength") { Note("Finish a workout and your lifts start charting here.") }
        return
    }

    if (report.lifts.isNotEmpty()) {
        InsightCard("Estimated one-rep max", count = "last 6 months") {
            report.lifts.forEachIndexed { index, lift ->
                if (index > 0) Rule()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        lift.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    lift.changeFraction?.let {
                        Pill("%+d%%".format((it * 100).roundToInt()), tone = if (it >= 0) PillTone.Accent else PillTone.Quiet)
                    }
                }
                Note("Best %.0f kg on %s · %d sessions".format(lift.best, lift.bestOn.format(SHORT_DATE), lift.points.size))
                if (lift.points.size >= 2) {
                    Spacer(Modifier.height(6.dp))
                    LineChart(
                        points = lift.points,
                        trend = lift.points,
                        reference = null,
                        lineColour = MaterialTheme.colorScheme.primary,
                        pointColour = MaterialTheme.colorScheme.onSurface,
                        referenceColour = MaterialTheme.colorScheme.outline,
                        height = 72.dp,
                    )
                }
            }
        }
    }

    InsightCard("Weekly sets per muscle", count = "12 weeks") {
        val peak = report.muscleWeeks.maxOf { it.sets.maxOrNull() ?: 0.0 }.coerceAtLeast(1.0)
        report.muscleWeeks.forEachIndexed { index, row ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.muscle.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(84.dp),
                )
                MiniBars(row.sets, peak, Modifier.weight(1f))
                Text(
                    "%.0f".format(row.sets.last()),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(32.dp).padding(start = 8.dp),
                )
            }
        }
        Note("Each bar is a week, this week last. Aim for 10–20 hard sets per muscle.")
    }

    InsightCard("Consistency", count = "%.1f sessions / week".format(report.sessionsPerWeek)) {
        Heatmap(report.trainingDays, report.weeks.first())
        report.longestGapDays?.let {
            Spacer(Modifier.height(8.dp))
            Note("Longest break in the last 6 months: $it days.")
        }
    }

    if (report.personalBests.isNotEmpty()) {
        InsightCard("Recent personal bests") {
            report.personalBests.forEach { pb ->
                ValueRow(
                    "${pb.lift} · ${pb.date.format(SHORT_DATE)}",
                    "%.0f kg (+%.1f)".format(pb.oneRepMaxKg, pb.oneRepMaxKg - pb.previousKg),
                )
            }
        }
    }

    report.proteinVsStrength?.let {
        InsightCard("Protein and progress") {
            CorrelationLine("Higher-protein weeks and stronger lifts", it, unit = "weeks")
        }
    }
}

/** Twelve weeks as columns, Monday at the top, a filled square for a day trained. */
@Composable
private fun Heatmap(days: Set<LocalDate>, firstWeek: LocalDate) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(WEEKS) { week ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(7) { day ->
                    val date = firstWeek.plusWeeks(week.toLong()).plusDays(day.toLong())
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(
                                when {
                                    date in days -> scheme.primary
                                    date.isAfter(LocalDate.now()) -> scheme.surface
                                    else -> scheme.surfaceContainer
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniBars(values: List<Double>, peak: Double, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(modifier.height(22.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { value ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight((value / peak).toFloat().coerceIn(0.04f, 1f))
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(if (value >= PRODUCTIVE_SETS) scheme.primary else scheme.outline),
            )
        }
    }
}

// --- shared pieces --------------------------------------------------------------------------

@Composable
private fun InsightCard(title: String, count: String? = null, content: @Composable () -> Unit) {
    LuxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(title, Modifier.weight(1f))
                count?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun BarRow(label: String, fraction: Float, value: String, caption: String? = null, over: Boolean = false) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(4.dp))
        ThinBar(
            progress = fraction.coerceIn(0f, 1f),
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
        caption?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SplitRow(label: String, split: SplitAverages, unit: String) {
    val gap = split.first - split.second
    ValueRow(
        label,
        "%,d / %,d %s".format(split.first.roundToInt(), split.second.roundToInt(), unit),
    )
    Note(
        "%s%,d %s on the first · %d vs %d days".format(
            if (gap >= 0) "+" else "−",
            abs(gap).roundToInt(),
            unit,
            split.firstDays,
            split.secondDays,
        ),
    )
    Spacer(Modifier.height(6.dp))
}

/** A correlation in words, with its sample size and the standard caveat, never a bare r. */
@Composable
private fun CorrelationLine(question: String, correlation: Correlation, unit: String) {
    val direction = if (correlation.r >= 0) "go together" else "go opposite ways"
    val verdict = when (correlation.strength) {
        Correlation.Strength.NONE -> "No clear link yet"
        Correlation.Strength.WEAK -> "A weak tendency to $direction"
        Correlation.Strength.MODERATE -> "A moderate tendency to $direction"
        Correlation.Strength.STRONG -> "A strong tendency to $direction"
    }
    Text(question, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(2.dp))
    Text(verdict, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    Note("r = %.2f over %d %s. A pattern in your own data, not proof of cause.".format(correlation.r, correlation.n, unit))
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Rule() {
    Spacer(Modifier.height(12.dp))
    Hairline()
    Spacer(Modifier.height(12.dp))
}

private fun signed(kg: Double): String = "%s%.2f".format(if (kg < 0) "−" else "+", abs(kg))

private fun paceLabel(pace: PaceVerdict) = when (pace) {
    PaceVerdict.ON_PACE -> "On pace"
    PaceVerdict.AHEAD -> "Faster than plan"
    PaceVerdict.BEHIND -> "Slower than plan"
    PaceVerdict.WRONG_WAY -> "Moving away from plan"
}

private fun confidenceLabel(confidence: Confidence) = when (confidence) {
    Confidence.LOW -> "low"
    Confidence.MEDIUM -> "medium"
    Confidence.HIGH -> "high"
}

private const val WEEKS = com.yash.tracker.domain.progress.StrengthAnalyst.WEEKS
private const val PRODUCTIVE_SETS = com.yash.tracker.domain.workout.TrainingAnalyst.PRODUCTIVE_SETS
