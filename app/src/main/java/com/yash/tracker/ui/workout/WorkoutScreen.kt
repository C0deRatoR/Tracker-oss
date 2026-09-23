package com.yash.tracker.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.tracker.data.local.dao.SessionWithSets
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.data.local.entity.isLogged
import com.yash.tracker.domain.workout.NextWorkout
import com.yash.tracker.domain.workout.readable
import com.yash.tracker.ui.components.ActionTone
import com.yash.tracker.ui.components.CircleIconButton
import com.yash.tracker.ui.components.DetailTopBar
import com.yash.tracker.ui.components.EmptyNote
import com.yash.tracker.ui.components.Eyebrow
import com.yash.tracker.ui.components.GroupHeader
import com.yash.tracker.ui.components.Hairline
import com.yash.tracker.ui.components.IconPlate
import com.yash.tracker.ui.components.LuxCard
import com.yash.tracker.ui.components.LuxTextField
import com.yash.tracker.ui.components.LuxTopBar
import com.yash.tracker.ui.components.Pill
import com.yash.tracker.ui.components.PillButton
import com.yash.tracker.ui.components.PillTone
import com.yash.tracker.ui.components.ReorderableColumn
import com.yash.tracker.ui.components.SearchField
import com.yash.tracker.ui.components.SelectableChip
import com.yash.tracker.ui.components.SoftCard
import com.yash.tracker.ui.components.StaggerIn
import com.yash.tracker.ui.components.TextAction
import com.yash.tracker.ui.components.shareText
import com.yash.tracker.ui.theme.EyebrowStyle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private val SESSION_DATE = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")

@Composable
fun WorkoutScreen(
    onStartRoutine: (Long?) -> Unit,
    onBrowseExercises: () -> Unit = {},
    onOpenSession: (Long) -> Unit = {},
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val sessions by viewModel.recentSessions.collectAsStateWithLifecycle()
    val resumableId by viewModel.resumableId.collectAsStateWithLifecycle()
    val draft by viewModel.newRoutine.collectAsStateWithLifecycle()
    val nextWorkout by viewModel.nextWorkout.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // A full screen, not a bottom sheet: the sheet's own drag-to-dismiss swallows a
    // drag-to-reorder before it starts, and the editor needs the height anyway.
    if (draft.open) {
        RoutineEditorScreen(viewModel)
        return
    }

    Column(Modifier.fillMaxSize()) {
        LuxTopBar(eyebrow = "Training", title = "Workout")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // An unfinished session outranks everything: it is almost certainly why the tab
            // was opened.
            if (resumableId != null) {
                item {
                    StaggerIn(0) {
                        LuxCard(Modifier.fillMaxWidth(), onClick = { onStartRoutine(null) }) {
                            Row(
                                Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconPlate(
                                    Icons.Default.PlayArrow,
                                    size = 46.dp,
                                    tone = PillTone.Accent,
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Eyebrow("In progress", color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        "Resume your workout",
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Above the buttons, because it is the thing that decides which of them to press.
            (nextWorkout?.plan as? NextWorkout.Train)?.let { due ->
                item { StaggerIn(1) { NextUpCard(due, nextWorkout?.movements.orEmpty()) } }
            }

            item {
                StaggerIn(1) {
                    PillButton(
                        text = "Start an empty workout",
                        onClick = { onStartRoutine(null) },
                        leadingIcon = Icons.Default.Add,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            item {
                StaggerIn(2) {
                    SoftCard(Modifier.fillMaxWidth(), onClick = onBrowseExercises) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconPlate(Icons.Outlined.FitnessCenter, size = 44.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Exercise library",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    // No count here: the library screen prints the real one, and
                                    // a literal drifts every time the catalogue is rebuilt.
                                    "Every movement, with your history on each",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                GroupHeader(
                    title = "Routines",
                    count = "${routines.size}",
                    action = "New",
                    onAction = viewModel::openNewRoutine,
                )
            }

            if (routines.isEmpty()) {
                item {
                    EmptyNote(
                        "No routines yet. Build one and it'll start prefilled with last time's " +
                            "numbers.",
                    )
                }
            } else {
                // Routines and sessions share this list, and their ids are separate sequences that
                // both start at 1 — so the keys have to say which table they came from.
                items(routines, key = { "routine-${it.routine.routine.id}" }) { card ->
                    LuxCard(
                        Modifier.fillMaxWidth().animateItem(),
                        onClick = { onStartRoutine(card.routine.routine.id) },
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    card.routine.routine.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                CircleIconButton(
                                    icon = Icons.Outlined.Edit,
                                    contentDescription = "Edit ${card.routine.routine.name}",
                                    onClick = { viewModel.editRoutine(card) },
                                    size = 36.dp,
                                )
                                CircleIconButton(
                                    icon = Icons.Outlined.ContentCopy,
                                    contentDescription = "Duplicate ${card.routine.routine.name}",
                                    onClick = { viewModel.duplicateRoutine(card.routine.routine.id) },
                                    size = 36.dp,
                                )
                                CircleIconButton(
                                    icon = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Delete ${card.routine.routine.name}",
                                    onClick = { viewModel.deleteRoutine(card.routine.routine.id) },
                                    size = 36.dp,
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                card.exerciseNames.joinToString(" · ").ifBlank { "No exercises" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(10.dp))
                            Pill(
                                card.lastPerformedAt?.let { "Last done ${ago(it)}" } ?: "Never done",
                                tone = PillTone.Quiet,
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                GroupHeader("History", count = "${sessions.size}")
            }

            if (sessions.isEmpty()) {
                item { EmptyNote("Nothing logged yet.") }
            } else {
                items(sessions, key = { "session-${it.session.id}" }) { session ->
                    SessionCard(
                        session = session,
                        modifier = Modifier.animateItem(),
                        onOpen = { onOpenSession(session.session.id) },
                        onShare = { viewModel.shareSession(session) { text, subject ->
                            context.shareText(text, subject)
                        } },
                    )
                }
            }
        }
    }
}

/**
 * The muscle group with the best claim on this session, and what to do for it.
 *
 * Shown only when something is actually due. A card that says "everything is still
 * recovering" every other day is a card that trains the user to scroll past this one, and the
 * rest of the screen already works without it.
 */
@Composable
private fun NextUpCard(due: NextWorkout.Train, movements: List<ExerciseEntity>) {
    LuxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconPlate(Icons.Outlined.Insights, size = 44.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Eyebrow("Next up")
                    Spacer(Modifier.height(3.dp))
                    Text(due.group.readable(), style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.width(8.dp))
                Pill(
                    text = due.daysSince?.let { "${it}d rested" } ?: "Untrained",
                    tone = PillTone.Quiet,
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                due.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (movements.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Hairline()
                Spacer(Modifier.height(12.dp))
                movements.forEachIndexed { index, movement ->
                    if (index > 0) Spacer(Modifier.height(6.dp))
                    Text(movement.name, style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (due.alsoDue.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "${due.alsoDue.joinToString(" and ") { it.readable() }} " +
                        "${if (due.alsoDue.size == 1) "is" else "are"} just as due.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: SessionWithSets,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    val duration = session.session.endedAt?.let { it - session.session.startedAt } ?: 0L

    // The card only ever showed totals. Tapping it opens the session itself — every exercise
    // and every set, which is the thing you actually want when looking a workout up.
    SoftCard(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconPlate(Icons.Outlined.FitnessCenter, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    session.session.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    Instant.ofEpochMilli(session.session.startedAt)
                        .atZone(ZoneId.systemDefault())
                        .format(SESSION_DATE),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(formatClock((duration / 1000).toInt()))
                    Pill("%,d kg".format(session.session.totalVolumeKg.roundToInt()))
                    Pill("${session.sets.count { it.isLogged }} sets")
                }
            }
            CircleIconButton(
                icon = Icons.Outlined.Share,
                contentDescription = "Share ${session.session.name}",
                onClick = onShare,
                size = 38.dp,
            )
        }
    }
}

/**
 * A sheet rather than a dialog: this form has a text field, a search box and a result list, and
 * an AlertDialog both cramps them and throws the whole draft away on a stray tap outside.
 */
@Composable
private fun RoutineEditorScreen(viewModel: WorkoutViewModel) {
    val draft by viewModel.newRoutine.collectAsStateWithLifecycle()
    val nextWorkout by viewModel.nextWorkout.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        DetailTopBar(
            title = if (draft.isEditing) "Edit routine" else "New routine",
            onBack = viewModel::closeNewRoutine,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 20.dp),
        ) {
            LuxTextField(
                value = draft.name,
                onValueChange = viewModel::setRoutineName,
                label = "Name",
            )

            if (draft.picked.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("In order", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Eyebrow("Drag to reorder")
                }
                Spacer(Modifier.height(8.dp))
                ReorderableColumn(
                    items = draft.picked,
                    key = { it.id },
                    onMove = viewModel::moveExercise,
                    rowHeight = 60.dp,
                ) { index, exercise ->
                    SoftCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${index + 1}",
                                style = EyebrowStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(20.dp),
                            )
                            Text(
                                exercise.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            CircleIconButton(
                                icon = Icons.Outlined.Close,
                                contentDescription = "Remove ${exercise.name}",
                                onClick = { viewModel.unpick(exercise) },
                                size = 34.dp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SearchField(
                value = draft.query,
                onValueChange = viewModel::searchExercises,
                placeholder = "Add an exercise",
            )

            // A plain Column, because this sits inside a scrolling parent now. The search
            // already caps its own results, so there is nothing here worth virtualising.
            Column(
                Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                draft.results.forEach { exercise ->
                    SoftCard(
                        Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        onClick = { viewModel.pick(exercise) },
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(exercise.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${exercise.muscleGroup.lowercase()} · " +
                                        exercise.equipment.lowercase(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            CircleIconButton(
                                icon = Icons.Default.Add,
                                contentDescription = "Add ${exercise.name}",
                                onClick = { viewModel.pick(exercise) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp),
        ) {
            PillButton(
                text = if (draft.isEditing) "Save changes" else "Save routine",
                onClick = viewModel::saveRoutine,
                enabled = draft.name.isNotBlank() && draft.picked.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextAction("Cancel", onClick = viewModel::closeNewRoutine)
            }
        }
    }
}

private fun ago(epochMillis: Long): String {
    val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - epochMillis)
    return when (days) {
        0L -> "today"
        1L -> "yesterday"
        in 2..30 -> "$days days ago"
        else -> Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
    }
}
