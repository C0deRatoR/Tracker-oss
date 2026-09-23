package com.yash.tracker.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.yash.tracker.ui.components.CircleIconButton
import com.yash.tracker.ui.components.DetailTopBar
import com.yash.tracker.ui.components.Eyebrow
import com.yash.tracker.ui.components.GroupHeader
import com.yash.tracker.ui.components.Hairline
import com.yash.tracker.ui.components.IconPlate
import com.yash.tracker.ui.components.LuxCard
import com.yash.tracker.ui.components.PillButton
import com.yash.tracker.ui.components.PillTone
import com.yash.tracker.ui.components.SoftCard
import com.yash.tracker.ui.components.StaggerIn
import com.yash.tracker.ui.components.shareText
import com.yash.tracker.domain.workout.ExerciseTrend
import com.yash.tracker.domain.workout.MuscleShare
import com.yash.tracker.domain.workout.Trend
import com.yash.tracker.domain.workout.readable
import com.yash.tracker.ui.components.SplitBar
import com.yash.tracker.ui.theme.MetricSmallStyle
import kotlin.math.roundToInt

@Composable
private fun SummaryStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Eyebrow(label)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MetricSmallStyle, maxLines = 1)
    }
}

/**
 * Where the session's working sets went.
 *
 * Counted in sets rather than kilos: a set of pull-ups moves no measurable load and would
 * disappear from a volume split entirely, while the shoulders it worked are exactly what the
 * next session has to know about.
 */
@Composable
private fun WorkSplitCard(split: List<MuscleShare>) {
    val scheme = MaterialTheme.colorScheme
    // Four shades, cycled. Each group is named on its own row, so the bar only has to show
    // proportion — it is not carrying the identification.
    val shades = listOf(scheme.onSurface, scheme.onSurfaceVariant, scheme.outline, scheme.outlineVariant)

    Column {
        GroupHeader("Where the work went", count = "${split.sumOf { it.workingSets }} sets")
        Spacer(Modifier.height(10.dp))
        LuxCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                SplitBar(
                    parts = split.mapIndexed { index, share ->
                        share.workingSets.toFloat() to shades[index % shades.size]
                    },
                    height = 7.dp,
                )
                Spacer(Modifier.height(14.dp))
                split.forEachIndexed { index, share ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(shades[index % shades.size]),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            share.group.readable(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${share.workingSets} · ${(share.share * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Only the lifts that actually moved, plus the sentences worth reading.
 *
 * Everything that stayed level is left out on purpose: a list of eight exercises with six of
 * them saying "no change" is a list nobody reads twice, and the best-set table above already
 * says what every exercise did.
 */
@Composable
private fun AgainstLastTimeCard(movers: List<ExerciseTrend>, notes: List<String>) {
    val scheme = MaterialTheme.colorScheme

    Column {
        GroupHeader("Against last time")
        Spacer(Modifier.height(10.dp))
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                movers.forEachIndexed { index, mover ->
                    if (index > 0) Spacer(Modifier.height(10.dp))
                    val up = mover.trend == Trend.UP
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            mover.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            mover.changeFraction
                                ?.let { "%+d%%".format((it * 100).roundToInt()) }
                                .orEmpty(),
                            style = MaterialTheme.typography.titleSmall,
                            // Up is the app's one accent colour, down is its error colour.
                            // There is no third state to colour: level lifts are not listed.
                            color = if (up) scheme.primary else scheme.error,
                        )
                    }
                }

                if (movers.isNotEmpty() && notes.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Hairline()
                    Spacer(Modifier.height(14.dp))
                }

                notes.forEachIndexed { index, note ->
                    if (index > 0) Spacer(Modifier.height(6.dp))
                    Text(
                        note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * What the session achieved. PRs get a mention but not a celebration — PRD §4 is explicit
 * that this is a measuring instrument, so no confetti.
 */
@Composable
fun SessionSummaryScreen(
    sessionId: Long,
    onDone: () -> Unit,
    viewModel: SessionSummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(sessionId) { viewModel.load(sessionId) }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(
            title = "Session summary",
            onBack = onDone,
            actions = {
                CircleIconButton(
                    icon = Icons.Outlined.Share,
                    contentDescription = "Share this workout",
                    onClick = {
                        context.shareText(
                            text = state.shareText,
                            subject = state.name.ifBlank { "Workout" },
                        )
                    },
                    enabled = state.shareText.isNotBlank(),
                )
            },
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp),
        ) {
            StaggerIn(0) {
                Column {
                    Eyebrow("Finished")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        state.name.ifBlank { "Workout" },
                        style = MaterialTheme.typography.headlineLarge,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            StaggerIn(1) {
                LuxCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        // Two rows of two rather than four columns: at phone width four
                        // eyebrow labels run into each other and read as one string.
                        Row(Modifier.fillMaxWidth()) {
                            SummaryStat("Time", formatClock(state.durationSec), Modifier.weight(1f))
                            SummaryStat(
                                "Volume",
                                "%,d kg".format(state.volumeKg.roundToInt()),
                                Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Row(Modifier.fillMaxWidth()) {
                            SummaryStat("Sets", "${state.setsCompleted}", Modifier.weight(1f))
                            SummaryStat("Burned", "${state.kcalBurned} kcal", Modifier.weight(1f))
                        }
                    }
                }
            }

            if (state.exercises.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                StaggerIn(2) {
                    Column {
                        GroupHeader("Best set", count = "${state.exercises.size}")
                        Spacer(Modifier.height(10.dp))
                        SoftCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                state.exercises.forEachIndexed { index, (label, best) ->
                                    if (index > 0) Hairline()
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Text(best, style = MaterialTheme.typography.titleSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            state.analysis?.takeIf { !it.isEmpty }?.let { analysis ->
                Spacer(Modifier.height(24.dp))
                StaggerIn(3) { WorkSplitCard(analysis.muscleSplit) }

                val movers = analysis.trends.filter { it.trend == Trend.UP || it.trend == Trend.DOWN }
                if (movers.isNotEmpty() || analysis.notes.isNotEmpty()) {
                    Spacer(Modifier.height(24.dp))
                    StaggerIn(4) { AgainstLastTimeCard(movers, analysis.notes) }
                }
            }

            if (state.records.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                StaggerIn(5) {
                    LuxCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconPlate(
                                    Icons.Outlined.EmojiEvents,
                                    size = 40.dp,
                                    tone = PillTone.Accent,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Eyebrow("Personal records")
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        if (state.records.size == 1) {
                                            "1 record broken"
                                        } else {
                                            "${state.records.size} records broken"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            state.records.forEach { line ->
                                Text(
                                    line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Box(Modifier.fillMaxWidth()) {
                PillButton(
                    text = "Done",
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
