package com.yash.tracker.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FitnessCenter
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
import com.yash.tracker.data.local.entity.isLogged
import com.yash.tracker.domain.workout.NextWorkout
import com.yash.tracker.domain.workout.TemplatePlan
import com.yash.tracker.ui.components.ActionTone
import com.yash.tracker.ui.components.CircleIconButton
import com.yash.tracker.ui.components.DetailTopBar
import com.yash.tracker.ui.components.EmptyNote
import com.yash.tracker.ui.components.Eyebrow
import com.yash.tracker.ui.components.GroupHeader
import com.yash.tracker.ui.components.IconPlate
import com.yash.tracker.ui.components.LuxCard
import com.yash.tracker.ui.components.LuxTextField
import com.yash.tracker.ui.components.LuxTopBar
import com.yash.tracker.ui.components.SmallAction
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
    onOpenReport: () -> Unit = {},
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val routines by viewModel.routines.collectAsStateWithLifecycle()
    val sessions by viewModel.recentSessions.collectAsStateWithLifecycle()
    val resumableId by viewModel.resumableId.collectAsStateWithLifecycle()
    val draft by viewModel.newRoutine.collectAsStateWithLifecycle()
    val nextWorkout by viewModel.nextWorkout.collectAsStateWithLifecycle()
    val trainingReport by viewModel.trainingReport.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val addedTemplates by viewModel.addedTemplates.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // A full screen, not a bottom sheet: the sheet's own drag-to-dismiss swallows a
    // drag-to-reorder before it starts, and the editor needs the height anyway.
    if (draft.open) {
        RoutineEditorScreen(viewModel)
        return
    }

    // Collapsed by default once there is something of your own above them: templates are
    // how a routine list starts, not something to scroll past every visit.
    var templatesOpen by rememberSaveable(routines.isEmpty()) { mutableStateOf(routines.isEmpty()) }
    var allHistory by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        LuxTopBar(eyebrow = "Training", title = "Workout")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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

            // The two things the tab is opened for, side by side rather than a button and a
            // whole card stacked on top of each other.
            item {
                StaggerIn(0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton(
                            text = "Start workout",
                            onClick = { onStartRoutine(null) },
                            leadingIcon = Icons.Default.Add,
                            modifier = Modifier.weight(1f),
                        )
                        PillButton(
                            text = "Library",
                            onClick = onBrowseExercises,
                            tone = ActionTone.Soft,
                        )
                    }
                }
            }

            val due = nextWorkout?.plan as? NextWorkout.Train
            val report = trainingReport?.takeIf { !it.isEmpty }
            if (due != null || report != null) {
                item {
                    StaggerIn(1) {
                        ThisWeekCard(
                            due = due,
                            movements = nextWorkout?.movements.orEmpty(),
                            report = report,
                            onOpenReport = onOpenReport,
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                GroupHeader(
                    title = "Routines",
                    count = "${routines.size}",
                    action = "New",
                    onAction = viewModel::openNewRoutine,
                )
            }

            if (routines.isEmpty()) {
                item { EmptyNote("No routines yet. Add a template below, or build your own.") }
            } else {
                // Routines and sessions share this list, and their ids are separate sequences that
                // both start at 1 — so the keys have to say which table they came from.
                items(routines, key = { "routine-${it.routine.routine.id}" }) { card ->
                    RoutineRow(
                        card = card,
                        modifier = Modifier.animateItem(),
                        onStart = { onStartRoutine(card.routine.routine.id) },
                        onEdit = { viewModel.editRoutine(card) },
                        onDuplicate = { viewModel.duplicateRoutine(card.routine.routine.id) },
                        onDelete = { viewModel.deleteRoutine(card.routine.routine.id) },
                    )
                }
            }

            if (templates.isNotEmpty()) {
                item {
                    CollapsibleHeader(
                        title = "Templates",
                        count = "${templates.size}",
                        open = templatesOpen,
                        onToggle = { templatesOpen = !templatesOpen },
                    )
                }
                if (templatesOpen) {
                    items(templates, key = { "template-${it.split.name}" }) { plan ->
                        TemplateRow(
                            plan = plan,
                            added = plan.split in addedTemplates,
                            onAdd = { viewModel.addTemplate(plan) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                GroupHeader(
                    title = "History",
                    count = "${sessions.size}",
                    action = if (sessions.size > RECENT_SESSIONS) {
                        if (allHistory) "Show less" else "Show all"
                    } else {
                        null
                    },
                    onAction = { allHistory = !allHistory },
                )
            }

            if (sessions.isEmpty()) {
                item { EmptyNote("Nothing logged yet.") }
            } else {
                val shown = if (allHistory) sessions else sessions.take(RECENT_SESSIONS)
                items(shown, key = { "session-${it.session.id}" }) { session ->
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

/** Past this, history is behind "Show all": the last few sessions are the ones you look up. */
private const val RECENT_SESSIONS = 3

/**
 * One routine on one card: name, size, when it was last done. Tapping it starts it.
 *
 * Edit, duplicate and delete used to sit as three icons on every card, which made each
 * routine twice as tall as the one thing it is for. They live in a menu now.
 */
@Composable
private fun RoutineRow(
    card: RoutineCard,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val name = card.routine.routine.name

    LuxCard(modifier.fillMaxWidth(), onClick = onStart) {
        Row(
            Modifier.padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        val count = card.exerciseNames.size
                        append(if (count == 1) "1 exercise" else "$count exercises")
                        append(" · ")
                        append(card.lastPerformedAt?.let { "last done ${ago(it)}" } ?: "never done")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                CircleIconButton(
                    icon = Icons.Outlined.MoreVert,
                    contentDescription = "More for $name",
                    onClick = { menuOpen = true },
                    size = 40.dp,
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = { menuOpen = false; onDuplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

/** A section heading that folds what is under it. The chevron points the way it will move. */
@Composable
private fun CollapsibleHeader(title: String, count: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = if (open) "Collapse $title" else "Expand $title", onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Icon(
            if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A standard split, fitted to the onboarding answers, one tap from being routines.
 *
 * Folded to a line by default — name, days, and whether it is the one for you. Tapping it
 * shows its days; the full exercise list was what made the section a wall.
 */
@Composable
private fun TemplateRow(plan: TemplatePlan, added: Boolean, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable(plan.split) { mutableStateOf(false) }

    SoftCard(modifier.fillMaxWidth(), onClick = { open = !open }) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(plan.split.label, style = MaterialTheme.typography.titleSmall)
                        if (plan.isRecommended) {
                            Spacer(Modifier.width(8.dp))
                            Pill("For you", tone = PillTone.Accent)
                        }
                    }
                    Text(
                        "${plan.split.daysPerWeek} · ${plan.routines.joinToString(" · ") { it.name }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                SmallAction(
                    text = if (added) "Added" else "Add",
                    onClick = onAdd,
                    enabled = !added,
                    tone = ActionTone.Soft,
                )
            }
            if (open) {
                plan.reason?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                plan.routines.forEach { routine ->
                    Spacer(Modifier.height(8.dp))
                    Text(routine.name, style = MaterialTheme.typography.labelLarge)
                    Text(
                        routine.exercises.joinToString(" · ") { "${it.name} ${it.sets}×${it.repsLow}–${it.repsHigh}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
