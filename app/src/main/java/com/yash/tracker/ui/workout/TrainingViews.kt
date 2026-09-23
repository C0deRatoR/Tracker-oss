package com.yash.tracker.ui.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import com.yash.tracker.ui.components.TextAction
import com.yash.tracker.ui.components.ThinBar
import kotlin.math.roundToInt

/**
 * The week in one card: the muscles furthest behind, and the few things most worth doing.
 *
 * Deliberately a teaser for the full analysis rather than all of it — the Workout tab is where
 * a session is started, and a screen of findings above the start button is in the way.
 */
@Composable
fun TrainingWeekCard(report: TrainingReport, onOpen: () -> Unit) {
    LuxCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconPlate(Icons.Outlined.Analytics, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Eyebrow("Last 7 days")
                    Spacer(Modifier.height(3.dp))
                    Text("Training balance", style = MaterialTheme.typography.titleMedium)
                }
                Pill("${report.workingSets} sets", tone = PillTone.Quiet)
            }

            val behind = report.muscles
                .filter { it.muscle.isPriority }
                .sortedBy { it.sets }
                .take(CARD_MUSCLES)
            Spacer(Modifier.height(14.dp))
            MuscleBars(behind)

            if (report.suggestions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Hairline()
                report.suggestions.take(CARD_SUGGESTIONS).forEach { suggestion ->
                    Spacer(Modifier.height(12.dp))
                    SuggestionLine(suggestion, compact = true)
                }
            }

            Spacer(Modifier.height(12.dp))
            TextAction("Full analysis", onClick = onOpen, color = MaterialTheme.colorScheme.primary)
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
fun SuggestionLine(suggestion: TrainingSuggestion, compact: Boolean = false) {
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
        if (!compact) {
            Spacer(Modifier.height(3.dp))
            Text(suggestion.detail, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
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

private const val CARD_MUSCLES = 4
private const val CARD_SUGGESTIONS = 3
