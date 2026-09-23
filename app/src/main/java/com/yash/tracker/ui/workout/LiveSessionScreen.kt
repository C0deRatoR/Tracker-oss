package com.yash.tracker.ui.workout

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.tracker.data.local.dao.PreviousSet
import com.yash.tracker.data.local.entity.WorkoutSetEntity
import com.yash.tracker.data.repository.REST_RANGE
import com.yash.tracker.ui.components.ActionTone
import com.yash.tracker.ui.components.DetailTopBar
import com.yash.tracker.ui.components.Eyebrow
import com.yash.tracker.ui.components.IconPlate
import com.yash.tracker.ui.components.LuxCard
import com.yash.tracker.ui.components.Pill
import com.yash.tracker.ui.components.PillButton
import com.yash.tracker.ui.components.PillTone
import com.yash.tracker.ui.components.SelectableChip
import com.yash.tracker.ui.components.SmallAction
import com.yash.tracker.ui.components.SoftCard
import com.yash.tracker.ui.components.Stepper
import com.yash.tracker.ui.components.TextAction
import com.yash.tracker.ui.components.ThinBar
import com.yash.tracker.ui.exercises.ExerciseLibraryScreen
import com.yash.tracker.ui.theme.EyebrowStyle
import com.yash.tracker.ui.theme.MetricStyle
import com.yash.tracker.ui.theme.springClick
import kotlin.math.roundToInt

/**
 * The gym-context screen. Rows arrive prefilled from last time, so a repeat set is one tap on
 * the tick; completing a set starts the rest timer without being asked. Targets are 56dp for
 * the controls that get used between sets with half your attention on the bar.
 */
@Composable
fun LiveSessionScreen(
    routineId: Long?,
    onFinished: (Long) -> Unit,
    onExit: () -> Unit,
    viewModel: LiveSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    var addingExercise by remember { mutableStateOf(false) }
    var setActions by remember { mutableStateOf<WorkoutSetEntity?>(null) }
    var restFor by remember { mutableStateOf<ExerciseBlock?>(null) }

    LaunchedEffect(routineId) { viewModel.startOrResume(routineId) }

    // Asked here rather than at launch: this is the one screen where a notification is the
    // point, so the request arrives with a reason attached. Refusing it costs the countdown
    // in the shade and nothing else — the timer still runs and the phone still buzzes.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val context = LocalContext.current
        val askToNotify = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { }

        LaunchedEffect(Unit) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) askToNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(state.finished) {
        state.finished?.let { onFinished(it.sessionId) }
    }

    if (confirmDiscard) {
        DiscardSheet(
            onKeepGoing = { confirmDiscard = false },
            onDiscard = { viewModel.discard(onExit) },
        )
    }

    setActions?.let { set ->
        SetActionsSheet(
            set = set,
            onDismiss = { setActions = null },
            onToggleWarmup = {
                viewModel.toggleWarmup(set)
                setActions = null
            },
            onDelete = {
                viewModel.deleteSet(set)
                setActions = null
            },
        )
    }

    restFor?.let { block ->
        RestPickerSheet(
            block = block,
            onDismiss = { restFor = null },
            onSave = { seconds ->
                viewModel.setDefaultRest(block.exercise.id, seconds)
                restFor = null
            },
        )
    }

    if (addingExercise) {
        ExerciseLibraryScreen(
            onBack = { addingExercise = false },
            onOpenExercise = {},
            title = "Add an exercise",
            onPick = { exercise ->
                viewModel.addExercise(exercise.id)
                addingExercise = false
            },
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        DetailTopBar(
            title = "Active session",
            onBack = onExit,
            divider = false,
            actions = {
                SmallAction("Discard", onClick = { confirmDiscard = true }, tone = ActionTone.Ghost)
            },
        )

        // Pinned above the list, not inside it. The rest timer is read between sets, which is
        // exactly when you are scrolled down among the rows it started from — in the list it
        // was only visible if you happened to be at the top.
        AnimatedVisibility(
            visible = state.rest != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            state.rest?.let {
                RestCard(
                    rest = it,
                    onAdjust = viewModel::adjustRest,
                    onStop = viewModel::stopRest,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = state.autoFinishIn != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            state.autoFinishIn?.let { seconds ->
                AutoFinishCard(
                    seconds = seconds,
                    onKeepGoing = viewModel::cancelAutoFinish,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SessionCard(state, onFinish = viewModel::finish) }

            state.blocks.forEach { block ->
                item(key = "block-${block.exercise.id}") {
                    ExerciseCard(
                        block = block,
                        onOpenSetActions = { setActions = it },
                        onEditRest = { restFor = block },
                        viewModel = viewModel,
                    )
                }
            }

            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    SmallAction(
                        text = "Add an exercise",
                        icon = Icons.Outlined.Add,
                        onClick = { addingExercise = true },
                    )
                }
            }

            if (state.blocks.isEmpty()) {
                item {
                    SoftCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text(
                                "No exercises in this session yet.",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Start from a routine and the rows arrive prefilled with last " +
                                    "time's numbers.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- session header -------------------------------------------------------------------------

@Composable
private fun SessionCard(state: LiveSessionUiState, onFinish: () -> Unit) {
    val volume = state.blocks.sumOf { block ->
        block.sets.filter { it.isCompleted && !it.isWarmup }
            .sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }
    }

    SoftCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Spacer(Modifier.width(7.dp))
                        Eyebrow("Active session", color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.name.ifBlank { "Workout" },
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                SmallAction(
                    text = "Finish",
                    icon = Icons.Default.Check,
                    onClick = onFinish,
                    tone = ActionTone.Ink,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Elapsed", formatClock(state.elapsedSec), Modifier.weight(1f))
                StatTile("Volume", "%,d kg".format(volume.roundToInt()), Modifier.weight(1f))
                StatTile("Sets", "${state.completedSets}", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    LuxCard(modifier, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp)) {
            Eyebrow(label)
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Readable at arm's length with the phone on a bench. */
@Composable
private fun RestCard(
    rest: RestTimer,
    onAdjust: (Int) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fraction = if (rest.totalSec > 0) rest.remainingSec.toFloat() / rest.totalSec else 0f

    LuxCard(modifier.fillMaxWidth()) {
        Column {
            ThinBar(
                progress = fraction,
                height = 4.dp,
                color = MaterialTheme.colorScheme.primary,
                track = MaterialTheme.colorScheme.surfaceContainer,
            )
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Eyebrow("Rest period")
                    Spacer(Modifier.weight(1f))
                    Pill("Target ${formatClock(rest.totalSec)}", tone = PillTone.Quiet)
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatClock(rest.remainingSec), style = MetricStyle)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    Spacer(Modifier.weight(1f))
                }

                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallAction("−15s", onClick = { onAdjust(-15) })
                    SmallAction("+15s", onClick = { onAdjust(15) })
                    Spacer(Modifier.weight(1f))
                    SmallAction(
                        text = "Skip",
                        icon = Icons.Outlined.SkipNext,
                        onClick = onStop,
                        tone = ActionTone.Accent,
                    )
                }
            }
        }
    }
}

// --- exercise -------------------------------------------------------------------------------

@Composable
private fun ExerciseCard(
    block: ExerciseBlock,
    onOpenSetActions: (WorkoutSetEntity) -> Unit,
    onEditRest: () -> Unit,
    viewModel: LiveSessionViewModel,
) {
    LuxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconPlate(
                    icon = Icons.Outlined.FitnessCenter,
                    size = 44.dp,
                    tone = PillTone.Solid,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        block.exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(
                            block.exercise.equipment.lowercase().replaceFirstChar(Char::uppercase),
                            tone = PillTone.Quiet,
                        )
                        Spacer(Modifier.width(8.dp))
                        Pill(
                            text = "Rest ${formatClock(block.exercise.defaultRestSec)}",
                            tone = PillTone.Quiet,
                            icon = Icons.Outlined.Timer,
                            onClick = onEditRest,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SetHeaderRow(block.isCardio)
            Spacer(Modifier.height(6.dp))

            block.sets.forEach { set ->
                SetRow(
                    set = set,
                    previous = block.previousFor(set.setIndex),
                    isCardio = block.isCardio,
                    restSec = block.exercise.defaultRestSec,
                    onOpenActions = { onOpenSetActions(set) },
                    viewModel = viewModel,
                )
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SmallAction(
                    text = "Add set",
                    icon = Icons.Outlined.Add,
                    onClick = { viewModel.addSet(block.exercise.id) },
                )
            }
        }
    }
}

@Composable
private fun SetHeaderRow(isCardio: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        HeaderCell("Set", Modifier.width(34.dp))
        HeaderCell("Previous", Modifier.weight(1.3f))
        HeaderCell(if (isCardio) "km" else "kg", Modifier.weight(1f))
        HeaderCell(if (isCardio) "Time" else "Reps", Modifier.weight(1f))
        HeaderCell("Done", Modifier.width(52.dp))
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = EyebrowStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SetRow(
    set: WorkoutSetEntity,
    previous: PreviousSet?,
    isCardio: Boolean,
    restSec: Int,
    onOpenActions: () -> Unit,
    viewModel: LiveSessionViewModel,
) {
    val background by animateColorAsState(
        targetValue = if (set.isCompleted) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        label = "setBackground",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tapping the number opens its actions rather than silently toggling warm-up: the row
        // is too narrow for a delete control of its own, and a hidden toggle was undiscoverable.
        Box(
            Modifier
                .width(34.dp)
                .springClick(pressedScale = 0.9f) { onOpenActions() },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (set.isWarmup) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (set.isWarmup) "W" else "${set.setIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (set.isWarmup) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        Text(
            previous.describe(isCardio),
            modifier = Modifier.weight(1.3f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )

        if (isCardio) {
            NumberCell(
                value = set.distanceM?.let { it / 1000.0 }?.trimmed().orEmpty(),
                modifier = Modifier.weight(1f),
                onSurface = set.isCompleted,
            ) { viewModel.setDistance(set, it.toDoubleOrNull()?.times(1000)) }
            NumberCell(
                value = set.durationSec?.let { formatClock(it) }.orEmpty(),
                modifier = Modifier.weight(1f),
                numeric = false,
                onSurface = set.isCompleted,
            ) { viewModel.setDuration(set, parseClock(it)) }
        } else {
            NumberCell(
                value = set.weightKg?.trimmed().orEmpty(),
                modifier = Modifier.weight(1f),
                onSurface = set.isCompleted,
            ) { viewModel.setWeight(set, it.toDoubleOrNull()) }
            NumberCell(
                value = set.reps?.toString().orEmpty(),
                modifier = Modifier.weight(1f),
                onSurface = set.isCompleted,
            ) { viewModel.setReps(set, it.toIntOrNull()) }
        }

        // The one control that gets pressed mid-set: 52dp, per DESIGN-PRD §4.
        DoneTick(set.isCompleted) { viewModel.toggleCompleted(set, restSec) }
    }
}

@Composable
private fun DoneTick(completed: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        targetValue = if (completed) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        label = "tickBackground",
    )
    val pop by animateFloatAsState(
        targetValue = if (completed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "tickPop",
    )

    Box(
        Modifier
            .size(52.dp)
            .padding(4.dp)
            .clip(CircleShape)
            .background(background)
            .springClick(pressedScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = if (completed) "Set done" else "Mark set done",
            tint = if (completed) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer {
                    val scale = 1f + pop * 0.18f
                    scaleX = scale
                    scaleY = scale
                },
        )
    }
}

@Composable
private fun NumberCell(
    value: String,
    modifier: Modifier,
    numeric: Boolean = true,
    onSurface: Boolean = false,
    onChange: (String) -> Unit,
) {
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }

    // Only adopt the stored value while the field is not being typed into. Every keystroke
    // persists the set and reloads the session, so the formatted value comes straight back —
    // keyed on it, this cell rewrote the caller's own half-typed text under the cursor. That
    // is what made the Time cell unusable, and it loses a trailing "." in the weight cell too.
    LaunchedEffect(value, focused) {
        if (!focused) text = value
    }

    Box(modifier.padding(horizontal = 3.dp)) {
        BasicTextField(
            value = text,
            onValueChange = {
                text = if (numeric) it.filter { c -> c.isDigit() || c == '.' } else it
                onChange(text)
            },
            textStyle = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .background(
                    if (onSurface) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                )
                .onFocusChanged { focused = it.isFocused }
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun DiscardSheet(onKeepGoing: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepGoing,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("Discard this workout?", style = MaterialTheme.typography.headlineSmall) },
        text = {
            Text(
                "Every set you've logged in it will be thrown away.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            PillButton(
                text = "Discard",
                onClick = onDiscard,
                tone = ActionTone.Ink,
                height = 44.dp,
            )
        },
        dismissButton = {
            PillButton(
                text = "Keep going",
                onClick = onKeepGoing,
                tone = ActionTone.Ghost,
                height = 44.dp,
            )
        },
    )
}

private fun PreviousSet?.describe(isCardio: Boolean): String = when {
    this == null -> "—"
    isCardio -> {
        val km = distanceM?.let { "${(it / 1000.0).trimmed()} km" }
        listOfNotNull(km, durationSec?.let(::formatClock)).joinToString(", ").ifBlank { "—" }
    }
    weightKg != null && reps != null -> "${weightKg.trimmed()} × $reps"
    reps != null -> "$reps reps"
    else -> "—"
}

private fun Double.trimmed(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)

internal fun formatClock(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val remainder = safe % 60
    return if (minutes >= 60) {
        "${minutes / 60}h ${minutes % 60}m"
    } else {
        "%d:%02d".format(minutes, remainder)
    }
}

/**
 * What the Time column means by what you typed: "5" is five minutes, "5:30" is five and a half.
 *
 * A bare number is minutes rather than seconds because this cell only ever holds cardio time,
 * and nobody logs a nine second walk. Reading it as seconds turned a five minute treadmill walk
 * into a five second one, with no way to say otherwise — the field reformatted "5" to "0:05"
 * under the cursor before a colon could be typed.
 *
 * A half-typed "5:" counts as five minutes exactly, so the value does not blink out between the
 * colon and the seconds.
 */
internal fun parseClock(text: String): Int? {
    val parts = text.trim().split(":")
    return when (parts.size) {
        1 -> parts[0].toIntOrNull()?.times(60)
        2 -> parts[0].toIntOrNull()?.let { minutes ->
            parts[1].ifBlank { "0" }.toIntOrNull()?.let { seconds -> minutes * 60 + seconds }
        }
        else -> null
    }
}

// --- sheets and banners ---------------------------------------------------------------------

/**
 * Everything you can do to one set. The row itself has no space for a delete control, and a
 * long-press would be invisible — so the set number is the handle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetActionsSheet(
    set: WorkoutSetEntity,
    onDismiss: () -> Unit,
    onToggleWarmup: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Eyebrow(if (set.isWarmup) "Warm-up set" else "Set ${set.setIndex + 1}")
            Spacer(Modifier.height(4.dp))
            Text(
                set.summarise(),
                style = MaterialTheme.typography.headlineSmall,
            )

            Spacer(Modifier.height(20.dp))
            PillButton(
                text = if (set.isWarmup) "Make it a working set" else "Mark as warm-up",
                onClick = onToggleWarmup,
                tone = ActionTone.Soft,
                leadingIcon = Icons.Outlined.LocalFireDepartment,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PillButton(
                text = "Delete this set",
                onClick = onDelete,
                tone = ActionTone.Ghost,
                leadingIcon = Icons.Outlined.DeleteOutline,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextAction("Cancel", onClick = onDismiss)
            }
        }
    }
}

/** Warm-ups excluded from volume is a rule worth restating where a set is being reclassified. */
private fun WorkoutSetEntity.summarise(): String = when {
    weightKg != null && reps != null -> "${weightKg.trimmed()} kg × $reps"
    reps != null -> "$reps reps"
    distanceM != null -> "${(distanceM / 1000.0).trimmed()} km"
    durationSec != null -> formatClock(durationSec)
    else -> "Empty set"
}

/**
 * The rest this exercise defaults to, from now on. Presets cover the usual answers; the stepper
 * is there because "the usual answers" are not everyone's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestPickerSheet(
    block: ExerciseBlock,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var seconds by remember(block.exercise.id) { mutableStateOf(block.exercise.defaultRestSec) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Eyebrow("Rest between sets")
            Spacer(Modifier.height(4.dp))
            Text(
                block.exercise.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(18.dp))
            Stepper(
                value = formatClock(seconds),
                unit = null,
                label = "rest",
                onDecrement = { seconds = (seconds - 15).coerceIn(REST_RANGE) },
                onIncrement = { seconds = (seconds + 15).coerceIn(REST_RANGE) },
            )

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REST_PRESETS.forEach { preset ->
                    SelectableChip(
                        label = formatClock(preset),
                        selected = seconds == preset,
                        onClick = { seconds = preset },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            PillButton(
                text = "Save for this exercise",
                onClick = { onSave(seconds) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Applies to every future session too. A timer already running is left alone.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextAction("Cancel", onClick = onDismiss)
            }
        }
    }
}

/** Every set in the routine is ticked. Says so, and gets out of the way unless stopped. */
@Composable
private fun AutoFinishCard(seconds: Int, onKeepGoing: () -> Unit, modifier: Modifier = Modifier) {
    LuxCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconPlate(Icons.Default.Check, size = 44.dp, tone = PillTone.Accent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Routine complete",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Finishing in ${seconds}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SmallAction("Keep going", onClick = onKeepGoing)
        }
    }
}

private val REST_PRESETS = listOf(60, 90, 120, 180, 240)
