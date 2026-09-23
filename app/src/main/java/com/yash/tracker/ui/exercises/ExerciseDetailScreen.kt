package com.yash.tracker.ui.exercises

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.data.local.entity.WorkoutSetEntity
import com.yash.tracker.domain.workout.Equipment
import com.yash.tracker.domain.workout.MuscleGroup
import com.yash.tracker.data.repository.label
import com.yash.tracker.domain.workout.RecordType
import com.yash.tracker.ui.components.DetailTopBar
import com.yash.tracker.ui.components.EmptyNote
import com.yash.tracker.ui.components.ExerciseAnimation
import com.yash.tracker.ui.components.ExerciseArt
import com.yash.tracker.ui.components.Eyebrow
import com.yash.tracker.ui.components.GroupHeader
import com.yash.tracker.ui.components.Hairline
import com.yash.tracker.ui.components.LuxCard
import com.yash.tracker.ui.components.Metric
import com.yash.tracker.ui.components.Pill
import com.yash.tracker.ui.components.PillTone
import com.yash.tracker.ui.components.SoftCard
import com.yash.tracker.ui.components.SmallAction
import com.yash.tracker.ui.components.StaggerIn
import com.yash.tracker.ui.components.rememberExerciseGifUrl
import com.yash.tracker.ui.theme.EyebrowStyle
import com.yash.tracker.ui.theme.MetricSmallStyle
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val OUTING_DATE = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

/**
 * One movement's own record: what you have lifted on it, and every time you have.
 *
 * The rule this screen shares with the session summary is that a first outing is a baseline,
 * not an achievement — so bests only appear once there is something to have beaten.
 */
@Composable
fun ExerciseDetailScreen(
    exerciseId: Long,
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit = {},
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(exerciseId) { viewModel.load(exerciseId) }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(
            title = state.exercise?.name ?: "Exercise",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.exercise?.let { exercise ->
                item { StaggerIn(0) { Identity(state) } }

                if (exercise.primaryMuscleList.isNotEmpty() ||
                    exercise.secondaryMuscleList.isNotEmpty()
                ) {
                    item { StaggerIn(1) { Targets(exercise) } }
                }

                // What you have actually lifted comes before how to lift it: once there is
                // history, the reference text is the thing you scroll past.
                if (state.history.isNotEmpty()) {
                    item { StaggerIn(2) { Summary(state) } }

                    if (state.hasRecords) {
                        item { StaggerIn(3) { Bests(state) } }
                    } else {
                        item {
                            Text(
                                "One session in. Bests appear from the second, because a " +
                                    "baseline is not a record.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    item {
                        Spacer(Modifier.height(2.dp))
                        GroupHeader("History", count = "${state.history.size}")
                    }
                    items(state.history, key = { it.sessionId }) { outing ->
                        OutingCard(
                            outing,
                            isCardio = exercise.type == "CARDIO",
                            onOpen = { onOpenSession(outing.sessionId) },
                        )
                    }
                }

                exercise.art?.let { art ->
                    item { Demonstration(art, exercise.name) }
                }

                if (exercise.instructionSteps.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(2.dp))
                        HowTo(exercise)
                    }
                }

                item { WatchForm(exercise) }

                if (state.history.isEmpty()) {
                    item {
                        SoftCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp)) {
                                Eyebrow("No history yet")
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "You haven't logged a set of this. Once you do, its bests " +
                                        "and every session you did it in show up here.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (state.exercise == null && !state.loading) {
                item { EmptyNote("That exercise is no longer in the catalogue.") }
            }
        }
    }
}

/** Which muscles do the work, and which help. The thing that tells two similar rows apart. */
@Composable
private fun Targets(exercise: ExerciseEntity) {
    LuxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Eyebrow("Targets")
            Spacer(Modifier.height(10.dp))

            if (exercise.primaryMuscleList.isNotEmpty()) {
                MuscleRow("Primary", exercise.primaryMuscleList, prominent = true)
            }
            if (exercise.secondaryMuscleList.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                MuscleRow("Also works", exercise.secondaryMuscleList, prominent = false)
            }

            val meta = listOfNotNull(
                exercise.mechanic?.takeIf { it.isNotBlank() },
                exercise.force?.takeIf { it.isNotBlank() },
                exercise.level?.takeIf { it.isNotBlank() },
            )
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Hairline()
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    meta.forEach { Pill(it.replaceFirstChar(Char::uppercase), tone = PillTone.Quiet) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MuscleRow(label: String, muscles: List<String>, prominent: Boolean) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            muscles.forEach { muscle ->
                Pill(
                    muscle.replaceFirstChar(Char::uppercase),
                    tone = if (prominent) PillTone.Accent else PillTone.Quiet,
                )
            }
        }
    }
}

/**
 * Sends you to YouTube for the movement.
 *
 * No exercise footage can be licensed for this app, and linking is not copying — so the video
 * everyone actually wants is one tap away without a frame of it being redistributed here. It
 * is a search rather than a fixed video id: a curated id rots when the upload is taken down,
 * and nobody here is qualified to pick the one correct demonstration of a squat.
 */
@Composable
private fun WatchForm(exercise: ExerciseEntity) {
    val context = LocalContext.current

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        SmallAction(
            text = "Watch form on YouTube",
            icon = Icons.AutoMirrored.Outlined.OpenInNew,
            onClick = {
                val query = Uri.encode("${exercise.name} proper form")
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    "https://www.youtube.com/results?search_query=$query".toUri(),
                )
                // Not worth crashing over if the phone has no browser at all.
                runCatching { context.startActivity(intent) }
            },
        )
    }
}

/**
 * ExerciseDB's animation of the movement, and the credit its free tier asks for.
 *
 * Drawn at the gif's own scale rather than full width: the free tier serves 180 px, and
 * stretched across a phone it is mostly blur. Absent until the URL arrives, and for good when
 * it cannot — offline, say — since nothing of it is kept on the device.
 */
@Composable
private fun Demonstration(art: String, name: String) {
    val url = rememberExerciseGifUrl(art) ?: return

    LuxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ExerciseAnimation(
                art = art,
                url = url,
                contentDescription = "$name, demonstrated",
                modifier = Modifier.size(200.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Animation from ExerciseDB by AscendAPI",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * How the movement is performed, in the source's own words.
 *
 * Strong's wording, read off the phone alongside the rest of the catalogue, so it describes
 * the exact variant the row names rather than a movement of roughly the same shape. Rows it
 * had nothing for simply show no card — BACKLOG.md covers what that text is and is not.
 */
@Composable
private fun HowTo(exercise: ExerciseEntity) {
    LuxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Eyebrow("How to")
            Spacer(Modifier.height(12.dp))
            exercise.instructionSteps.forEachIndexed { index, step ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "${index + 1}",
                        style = EyebrowStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .width(24.dp)
                            .padding(top = 3.dp),
                    )
                    Text(
                        step,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun Identity(state: ExerciseDetailUiState) {
    val exercise = state.exercise ?: return

    LuxCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            ExerciseArt(exercise.art, exercise.equipment, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(MuscleGroup.label(exercise.muscleGroup), tone = PillTone.Quiet)
                    Pill(Equipment.label(exercise.equipment), tone = PillTone.Quiet)
                }
                if (exercise.aliasList.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Also called ${exercise.aliasList.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun Summary(state: ExerciseDetailUiState) {
    LuxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Eyebrow("Your record")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Sessions", "${state.timesPerformed}", Modifier.weight(1f))
                Stat("Sets", "${state.completedSets}", Modifier.weight(1f))
                Stat(
                    "Best volume",
                    state.bestEverVolume?.let { "%,d kg".format(it.roundToInt()) } ?: "—",
                    Modifier.weight(1.2f),
                )
            }
            state.lastPerformed?.let {
                Spacer(Modifier.height(14.dp))
                Hairline()
                Spacer(Modifier.height(10.dp))
                Text(
                    "Last done ${it.format(OUTING_DATE)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Eyebrow(label)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MetricSmallStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Bests(state: ExerciseDetailUiState) {
    LuxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Eyebrow("Personal bests")
            Spacer(Modifier.height(4.dp))
            state.bests.forEachIndexed { index, best ->
                if (index > 0) Hairline()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            best.type.label().replaceFirstChar(Char::uppercase),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        best.date?.let {
                            Text(
                                it.format(OUTING_DATE),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(best.format(), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun OutingCard(outing: ExerciseOuting, isCardio: Boolean, onOpen: () -> Unit) {
    // One outing shows only this exercise's sets. Tapping opens the whole session it was part
    // of, which is usually the next question: what else was done that day.
    SoftCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        outing.date.format(OUTING_DATE),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        outing.sessionName.ifBlank { "Workout" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!isCardio && outing.volumeKg > 0) {
                    Spacer(Modifier.width(12.dp))
                    Metric("%,d".format(outing.volumeKg.roundToInt()), unit = "kg", large = false)
                }
            }

            Spacer(Modifier.height(12.dp))
            outing.sets.forEachIndexed { index, set ->
                SetLine(set, index, isCardio)
            }

            if (!isCardio) {
                outing.estimatedOneRepMax?.let { oneRm ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Estimated 1RM %.1f kg".format(oneRm),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetLine(set: WorkoutSetEntity, index: Int, isCardio: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (set.isWarmup) "W" else "${index + 1}",
            style = EyebrowStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp),
        )
        Text(
            set.describe(isCardio),
            style = MaterialTheme.typography.bodyMedium,
            color = if (set.isCompleted) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        if (set.isWarmup) {
            Text(
                "warmup",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun WorkoutSetEntity.describe(isCardio: Boolean): String = when {
    isCardio -> {
        val km = distanceM?.let { "%.2f km".format(it / 1000.0) }
        val time = durationSec?.let { "%d:%02d".format(it / 60, it % 60) }
        listOfNotNull(km, time).joinToString(" · ").ifBlank { "—" }
    }
    weightKg != null && reps != null -> "%s kg × %d".format(weightKg.trimmed(), reps)
    reps != null -> "$reps reps"
    else -> "—"
}

private fun Best.format(): String = when (type) {
    RecordType.MAX_WEIGHT -> "%s kg".format(value.trimmed())
    RecordType.EST_1RM -> "%.1f kg".format(value)
    RecordType.MAX_VOLUME -> "%,d kg".format(value.roundToInt())
    RecordType.MAX_REPS -> "${value.roundToInt()} reps"
}

private fun Double.trimmed(): String =
    if (this % 1.0 == 0.0) toInt().toString() else "%.1f".format(this)
