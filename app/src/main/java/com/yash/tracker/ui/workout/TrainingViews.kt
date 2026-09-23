package com.yash.tracker.ui.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.domain.workout.NextWorkout
import com.yash.tracker.domain.workout.readable
import com.yash.tracker.domain.workout.MuscleVolume
import com.yash.tracker.domain.workout.TrainingAnalyst
import com.yash.tracker.domain.workout.TrainingReport
import com.yash.tracker.domain.workout.TrainingSuggestion
import com.yash.tracker.domain.workout.VolumeGrade
import com.yash.tracker.ui.components.Eyebrow
import com.yash.tracker.ui.components.Hairline
import com.yash.tracker.ui.components.IconPlate
import com.yash.tracker.ui.components.LuxCard
import com.yash.tracker.ui.components.Pill
import com.yash.tracker.ui.components.PillTone
import com.yash.tracker.ui.components.ThinBar
import kotlin.math.roundToInt

/**
 * What to train next and what the week is missing, in one card.
 *
 * This used to be two stacked cards, a due group with its movements and a balance card with
 * bars. Together they took most of a screen above the start button. Here each finding is one
 * line, and the bars live on the full analysis the card opens.
 */
@Composable
fun ThisWeekCard(
    due: NextWorkout.Train?,
    movements: List<ExerciseEntity>,
    report: TrainingReport?,
    onOpenReport: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    LuxCard(Modifier.fillMaxWidth(), onClick = onOpenReport) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconPlate(Icons.Outlined.Insights, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Eyebrow(if (due != null) "Next up" else "Last 7 days")
                    Spacer(Modifier.height(2.dp))
                    Text(
                        due?.group?.readable() ?: "Training balance",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                val pill = when {
                    due != null -> due.daysSince?.let { "${it}d rested" } ?: "Untrained"
                    report != null -> "${report.workingSets} sets"
                    else -> null
                }
                pill?.let { Pill(it, tone = PillTone.Quiet) }
            }

            if (due != null) {
                Spacer(Modifier.height(8.dp))
                Text(due.reason, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                if (movements.isNotEmpty()) {
                    Text(
                        "Try: " + movements.joinToString(" · ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            val findings = report?.suggestions.orEmpty().take(CARD_SUGGESTIONS)
            if (findings.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Hairline()
                findings.forEach { suggestion ->
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            suggestion.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        suggestion.readyInDays?.let {
                            Spacer(Modifier.width(8.dp))
                            Pill("in ${it}d", tone = PillTone.Quiet)
                        }
                    }
                }
            }

            if (report != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    buildString {
                        append("Full analysis")
                        val more = report.suggestions.size - findings.size
                        if (more > 0) append(" · $more more")
                        append(" →")
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                )
            }
        }
    }
}

/** Each muscle against the ten-to-twenty band, full bar at the top of it. */
@Composable
fun MuscleBars(muscles: List<MuscleVolume>) {
    muscles.forEachIndexed { index, volume ->
        if (index > 0) Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                volume.muscle.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(110.dp),
            )
            ThinBar(
                progress = (volume.sets / TrainingAnalyst.HIGH_SETS).toFloat().coerceIn(0f, 1f),
                modifier = Modifier.weight(1f),
                color = gradeColor(volume.grade),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "${volume.sets.short()} / ${TrainingAnalyst.PRODUCTIVE_SETS}+",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.width(64.dp),
            )
        }
    }
}

@Composable
fun SuggestionLine(suggestion: TrainingSuggestion) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                suggestion.title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            suggestion.readyInDays?.let {
                Spacer(Modifier.width(8.dp))
                Pill("in ${it}d", tone = PillTone.Quiet)
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(suggestion.detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        if (suggestion.exercises.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(
                "Try: " + suggestion.exercises.joinToString(" · ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.primary,
            )
        }
    }
}

@Composable
private fun gradeColor(grade: VolumeGrade): Color {
    val scheme = MaterialTheme.colorScheme
    return when (grade) {
        VolumeGrade.UNDER -> scheme.error
        VolumeGrade.LOW -> scheme.outline
        VolumeGrade.PRODUCTIVE -> scheme.primary
        VolumeGrade.HIGH -> scheme.tertiary
    }
}

fun Double.short(): String = if (this == roundToInt().toDouble()) roundToInt().toString() else "%.1f".format(this)

private const val CARD_SUGGESTIONS = 2
