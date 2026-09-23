package com.yash.tracker.ui.progress

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.tracker.ui.components.ActionTone
import com.yash.tracker.ui.components.EmptyNote
import com.yash.tracker.ui.components.Eyebrow
import com.yash.tracker.ui.components.GroupHeader
import com.yash.tracker.ui.components.Hairline
import com.yash.tracker.ui.components.LineChart
import com.yash.tracker.ui.components.LuxCard
import com.yash.tracker.ui.components.LuxTextField
import com.yash.tracker.ui.components.LuxTopBar
import com.yash.tracker.ui.components.Metric
import com.yash.tracker.ui.components.Pill
import com.yash.tracker.ui.components.PillButton
import com.yash.tracker.ui.components.PillTone
import com.yash.tracker.ui.components.SegmentedToggle
import com.yash.tracker.ui.components.SmallAction
import com.yash.tracker.ui.components.SoftCard
import com.yash.tracker.ui.components.StaggerIn
import com.yash.tracker.ui.components.TextAction
import com.yash.tracker.ui.components.ThinBar
import com.yash.tracker.ui.theme.EyebrowStyle
import com.yash.tracker.ui.theme.Motion
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val WEIGH_IN_DATE = DateTimeFormatter.ofPattern("EEE d MMM")
private val AXIS_DATE = DateTimeFormatter.ofPattern("d MMM")
private val LOGGED_AT = DateTimeFormatter.ofPattern("d MMM 'at' HH:mm")
private val DAY_LETTER = DateTimeFormatter.ofPattern("EEEEE")

@Composable
fun ProgressScreen(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()

    if (state.entryOpen) WeightEntrySheet(state, viewModel)

    Column(Modifier.fillMaxSize()) {
        LuxTopBar(eyebrow = "Telemetry", title = "Progress")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                StaggerIn(0) {
                    SegmentedToggle(
                        options = ProgressTab.entries.map { it.label },
                        selectedIndex = ProgressTab.entries.indexOf(tab),
                        onSelect = { viewModel.setTab(ProgressTab.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                        height = 44.dp,
                    )
                }
            }
            // Training reads its own fixed windows — twelve weeks, six months — so the range
            // only means something on the other two.
            if (tab != ProgressTab.TRAINING) {
                item {
                    SegmentedToggle(
                        options = ProgressRange.entries.map { it.label },
                        selectedIndex = ProgressRange.entries.indexOf(state.range),
                        onSelect = { viewModel.setRange(ProgressRange.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                        height = 36.dp,
                    )
                }
            }

            when (tab) {
                ProgressTab.BODY -> {
                    item { StaggerIn(1) { WeightCard(state, viewModel) } }
                    state.body?.let { body -> item { StaggerIn(2) { BodyInsightsCard(body) } } }

                    item {
                        Spacer(Modifier.height(4.dp))
                        GroupHeader("Weigh-ins", count = "${state.weighIns.size}")
                    }
                    if (state.weighIns.isEmpty()) {
                        item { EmptyNote("Nothing logged yet.") }
                    } else {
                        items(state.weighIns, key = { it.id }) { weighIn ->
                            SoftCard(Modifier.fillMaxWidth().animateItem()) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        weighIn.date.format(WEIGH_IN_DATE),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        "%.1f kg".format(weighIn.weightKg),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    TextAction("Delete", onClick = { viewModel.deleteWeighIn(weighIn.id) })
                                }
                            }
                        }
                    }
                }

                ProgressTab.FOOD -> {
                    item { StaggerIn(1) { AdherenceCard(state) } }
                    item { StaggerIn(2) { MacrosCard(state) } }
                    state.nutrition?.let { nutrition -> item { StaggerIn(3) { FoodInsights(nutrition) } } }
                }

                ProgressTab.TRAINING -> {
                    item { StaggerIn(1) { VolumeCard(state) } }
                    state.strength?.let { strength -> item { StaggerIn(2) { TrainingInsights(strength) } } }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Everything here is computed on this phone from what you logged. " +
                        "Nothing is uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** The food insights are several cards; stacked here so the list sees one item. */
@Composable
private fun FoodInsights(report: com.yash.tracker.domain.progress.NutritionReport) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { FoodInsightsCard(report) }
}

@Composable
private fun TrainingInsights(report: com.yash.tracker.domain.progress.StrengthReport) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { StrengthInsights(report) }
}

// --- weight ---------------------------------------------------------------------------------

@Composable
private fun WeightCard(state: ProgressUiState, viewModel: ProgressViewModel) {
    LuxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Current baseline")
                    Spacer(Modifier.height(6.dp))
                    if (state.latestWeightKg != null) {
                        Metric("%.1f".format(state.latestWeightKg), unit = "kg")
                    } else {
                        Text("No weigh-ins yet", style = MaterialTheme.typography.headlineSmall)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    state.changeKg?.let { change ->
                        Pill(
                            text = "%s%.1f kg".format(if (change < 0) "−" else "+", abs(change)),
                            tone = PillTone.Quiet,
                            icon = if (change < 0) {
                                Icons.AutoMirrored.Filled.TrendingDown
                            } else {
                                Icons.AutoMirrored.Filled.TrendingUp
                            },
                        )
                    }
                    state.goalWeightKg?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Target %.1f kg".format(it),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // One point is not a trend, and drawing it leaves a tall empty box with a dot in
            // the corner. The chart starts once there is something to join up.
            if (state.points.size >= 2) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Legend("Trend", MaterialTheme.colorScheme.primary, line = true)
                    Legend("Weigh-in", MaterialTheme.colorScheme.onSurfaceVariant, line = false)
                    if (state.goalWeightKg != null) {
                        Legend("Target", MaterialTheme.colorScheme.outline, line = true, dashed = true)
                    }
                }

                Spacer(Modifier.height(12.dp))
                LineChart(
                    points = state.points,
                    trend = state.trend,
                    reference = state.goalWeightKg,
                    lineColour = MaterialTheme.colorScheme.primary,
                    pointColour = MaterialTheme.colorScheme.onSurface,
                    referenceColour = MaterialTheme.colorScheme.outline,
                    fillColour = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        state.points.first().date.format(AXIS_DATE),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "%s (%.1f kg)".format(
                            state.points.last().date.format(AXIS_DATE),
                            state.points.last().value,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (state.points.size == 1) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "The chart starts at your second weigh-in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.lastWeighInAt
                        ?.let {
                            "Last entry " + Instant.ofEpochMilli(it)
                                .atZone(ZoneId.systemDefault())
                                .format(LOGGED_AT)
                        }
                        ?: "No weight recorded yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                SmallAction(
                    text = "Log weight",
                    icon = Icons.Default.Add,
                    onClick = viewModel::openEntry,
                    tone = ActionTone.Soft,
                )
            }
        }
    }
}

@Composable
private fun Legend(label: String, colour: Color, line: Boolean, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = if (line) 14.dp else 7.dp, height = if (line) 2.dp else 7.dp)
                .clip(CircleShape)
                .background(if (dashed) colour.copy(alpha = 0.6f) else colour),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- adherence ------------------------------------------------------------------------------

@Composable
private fun AdherenceCard(state: ProgressUiState) {
    LuxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Calorie adherence")
                    Spacer(Modifier.height(6.dp))
                    Metric("%,d".format(state.weeklyAverageKcal), unit = "kcal a day")
                    if (state.targetKcal > 0) {
                        Text(
                            "against a %,d target".format(state.targetKcal),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${state.adherencePercent}%",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                    )
                    Eyebrow("Adherence")
                }
            }

            val recent = state.history.takeLast(7)
            val onTarget = recent.count { state.onTarget(it.value) }

            Spacer(Modifier.height(16.dp))
            SoftCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Eyebrow("Recent days", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Eyebrow(
                            if (recent.isEmpty()) {
                                "Nothing logged"
                            } else {
                                "$onTarget of ${recent.size} on track"
                            },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    DayDots(state, recent)
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "On target counts days within 10% of the calorie target.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${state.streakDays}", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "day streak",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
        }
    }
}

/** The last seven logged days as filled dots — on target, off target, or nothing logged. */
@Composable
private fun DayDots(state: ProgressUiState, days: List<com.yash.tracker.domain.progress.DayPoint>) {
    if (days.isEmpty()) {
        EmptyNote("Nothing logged in this window.")
        return
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { day ->
            val onTarget = state.onTarget(day.value)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    day.date.format(DAY_LETTER).uppercase(),
                    style = EyebrowStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(if (onTarget) 11.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (onTarget) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            ),
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    "%.1fk".format(day.value / 1000.0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// --- macros ---------------------------------------------------------------------------------

@Composable
private fun MacrosCard(state: ProgressUiState) {
    var showThirty by remember { mutableStateOf(false) }
    val average = if (showThirty) state.last30 else state.last7

    LuxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Macronutrient intake")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Daily averages",
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                SegmentedToggle(
                    options = listOf("7D", "30D"),
                    selectedIndex = if (showThirty) 1 else 0,
                    onSelect = { showThirty = it == 1 },
                    modifier = Modifier.width(108.dp),
                    height = 36.dp,
                )
            }

            Spacer(Modifier.height(18.dp))
            if (average == null) {
                EmptyNote("Nothing logged in this window yet.")
            } else {
                MacroAverageRow("Protein", average.proteinG)
                Spacer(Modifier.height(14.dp))
                MacroAverageRow("Carbohydrates", average.carbsG)
                Spacer(Modifier.height(14.dp))
                MacroAverageRow("Dietary fat", average.fatG)

                Spacer(Modifier.height(16.dp))
                Hairline()
                Spacer(Modifier.height(12.dp))
                Text(
                    "Averaged across ${average.days} logged " +
                        "${if (average.days == 1) "day" else "days"} · " +
                        "%,d kcal a day".format(average.kcal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MacroAverageRow(label: String, grams: Int) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text("$grams", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(3.dp))
            Text(
                "g/d",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        // Relative to 250 g, which is past any plausible daily average for one macro — so the
        // three bars can be compared with each other rather than each filling its own scale.
        ThinBar(grams / 250f, height = 5.dp)
    }
}

// --- workout volume -------------------------------------------------------------------------

@Composable
private fun VolumeCard(state: ProgressUiState) {
    val weeks = state.weekVolumes
    val peak = weeks.maxOfOrNull { it.kg }?.takeIf { it > 0 } ?: 1.0
    val total = weeks.lastOrNull()?.kg ?: 0.0
    val sessions = weeks.lastOrNull()?.sessions ?: 0

    LuxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Workout volume")
                    Spacer(Modifier.height(6.dp))
                    Metric("%,d".format(total.roundToInt()), unit = "kg this week")
                }
                Spacer(Modifier.width(12.dp))
                state.volumeChangePercent?.let { change ->
                    Pill(
                        text = "%s%d%%".format(if (change < 0) "−" else "+", abs(change)),
                        tone = PillTone.Quiet,
                        icon = if (change < 0) {
                            Icons.AutoMirrored.Filled.TrendingDown
                        } else {
                            Icons.AutoMirrored.Filled.TrendingUp
                        },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            if (weeks.all { it.kg <= 0.0 }) {
                EmptyNote("No finished sessions in the last four weeks.")
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    weeks.forEachIndexed { index, week ->
                        VolumeBar(
                            week = week,
                            fraction = (week.kg / peak).toFloat(),
                            isCurrent = index == weeks.lastIndex,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "$sessions ${if (sessions == 1) "session" else "sessions"} this week" +
                        if (sessions > 0) {
                            " · avg %,d kg a session".format((total / sessions).roundToInt())
                        } else {
                            ""
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun VolumeBar(
    week: WeekVolume,
    fraction: Float,
    isCurrent: Boolean,
    modifier: Modifier,
) {
    val grown by animateFloatAsState(
        targetValue = fraction.coerceIn(0.06f, 1f),
        animationSpec = Motion.settle,
        label = "volumeBar",
    )

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (week.kg > 0) "%.1fk".format(week.kg / 1000.0) else "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height((26 + grown * 62).dp)
                .clip(MaterialTheme.shapes.medium)
                .background(
                    if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            week.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- weight entry ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeightEntrySheet(state: ProgressUiState, viewModel: ProgressViewModel) {
    val focus = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = viewModel::closeEntry,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Eyebrow("Baseline")
            Spacer(Modifier.height(4.dp))
            Text("Today's weight", style = MaterialTheme.typography.headlineSmall)

            Spacer(Modifier.height(16.dp))
            LuxTextField(
                value = state.entryText,
                onValueChange = viewModel::setEntryText,
                suffix = "kg",
                placeholder = "0.0",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                keyboardActions = KeyboardActions(onDone = { viewModel.saveWeight() }),
                modifier = Modifier.focusRequester(focus),
            )

            // One field, one purpose: the keyboard should already be up.
            LaunchedEffect(Unit) { focus.requestFocus() }

            Spacer(Modifier.height(18.dp))
            PillButton(
                text = "Save weigh-in",
                onClick = viewModel::saveWeight,
                enabled = state.entryText.toDoubleOrNull() != null,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextAction("Cancel", onClick = viewModel::closeEntry)
            }
        }
    }
}
