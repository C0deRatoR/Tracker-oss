package com.yash.tracker.ui.exercises

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.domain.workout.Equipment
import com.yash.tracker.domain.workout.ExerciseSearch
import com.yash.tracker.domain.workout.MuscleGroup
import com.yash.tracker.ui.components.DetailTopBar
import com.yash.tracker.ui.components.EmptyNote
import com.yash.tracker.ui.components.ExerciseArt
import com.yash.tracker.ui.components.Eyebrow
import com.yash.tracker.ui.components.LuxCard
import com.yash.tracker.ui.components.Pill
import com.yash.tracker.ui.components.PillTone
import com.yash.tracker.ui.components.SearchField
import com.yash.tracker.ui.components.SelectableChip
import com.yash.tracker.ui.components.TextAction

/**
 * The catalogue: eight hundred-odd movements, which is far too many to scroll.
 *
 * Two filters do the work, and they are the two you actually know when you are standing in the
 * gym: what you want to train, and what is free. Search ranks name matches on top of both. The
 * filters stay visible rather than hiding behind a sheet, because reopening a sheet between
 * sets is exactly the kind of friction this screen exists to remove.
 */
@Composable
fun ExerciseLibraryScreen(
    onBack: () -> Unit,
    onOpenExercise: (Long) -> Unit,
    title: String = "Exercises",
    onPick: ((ExerciseEntity) -> Unit)? = null,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var filtersOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        DetailTopBar(
            title = title,
            onBack = onBack,
            subtitle = if (state.loading) null else "%,d movements".format(state.total),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SearchField(
                    value = state.filter.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = if (state.loading) {
                        "Search exercises"
                    } else {
                        "Search %,d exercises".format(state.total)
                    },
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SelectableChip(
                        label = if (state.filter.activeCount == 0) {
                            "Filters"
                        } else {
                            "Filters · ${state.filter.activeCount}"
                        },
                        selected = filtersOpen || state.filter.activeCount > 0,
                        onClick = { filtersOpen = !filtersOpen },
                    )
                    Spacer(Modifier.width(10.dp))
                    Eyebrow(
                        if (state.loading) "" else "%,d shown".format(state.matches),
                        Modifier.weight(1f),
                    )
                    if (state.filter.activeCount > 0) {
                        TextAction("Clear", onClick = viewModel::clearFilters)
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = filtersOpen || state.filter.activeCount > 0,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    FilterPanel(state, viewModel)
                }
            }

            if (state.loading) {
                item { EmptyNote("Loading the catalogue…") }
            } else if (state.matches == 0) {
                item {
                    EmptyNote(
                        if (state.filter.query.isBlank()) {
                            "Nothing matches those filters."
                        } else {
                            "Nothing matches \"${state.filter.query}\"."
                        },
                    )
                }
            }

            state.sections.forEach { section ->
                if (section.label.isNotBlank()) {
                    item(key = "head-${section.label}") {
                        Spacer(Modifier.height(4.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Eyebrow(section.label, Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Eyebrow("${section.exercises.size}")
                        }
                    }
                }
                items(section.exercises, key = { "ex-${it.id}" }) { exercise ->
                    ExerciseRow(
                        exercise = exercise,
                        matchedAlias = ExerciseSearch.matchedAlias(exercise, state.filter.query),
                        onClick = {
                            if (onPick != null) onPick(exercise) else onOpenExercise(exercise.id)
                        },
                        showChevron = onPick == null,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterPanel(state: ExerciseLibraryUiState, viewModel: ExerciseLibraryViewModel) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Eyebrow("Muscle group")
        Spacer(Modifier.height(8.dp))
        // A single scrolling row rather than a wrap: the order stays stable as you filter, so
        // the chip you want does not move while you reach for it.
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MuscleGroup.entries.forEach { group ->
                SelectableChip(
                    label = group.label,
                    selected = group in state.filter.muscles,
                    onClick = { viewModel.toggleMuscle(group) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Eyebrow("Equipment")
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Equipment.entries.forEach { equipment ->
                SelectableChip(
                    label = equipment.label,
                    selected = equipment in state.filter.equipment,
                    onClick = { viewModel.toggleEquipment(equipment) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ExerciseRow(
    exercise: ExerciseEntity,
    matchedAlias: String?,
    onClick: () -> Unit,
    showChevron: Boolean,
) {
    LuxCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExerciseArt(exercise.art, exercise.equipment, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    listOf(
                        MuscleGroup.label(exercise.muscleGroup),
                        Equipment.label(exercise.equipment),
                    ).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (matchedAlias != null) {
                    Spacer(Modifier.height(5.dp))
                    Pill(
                        "Also called ${matchedAlias.replaceFirstChar(Char::uppercase)}",
                        tone = PillTone.Accent,
                    )
                }
            }
            if (exercise.type == "CARDIO") {
                Spacer(Modifier.width(8.dp))
                Pill("Cardio", tone = PillTone.Quiet)
            }
            if (showChevron) {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(20.dp),
                )
            }
        }
    }
}
