package com.yash.tracker.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.tracker.data.local.dao.RoutineWithExercises
import com.yash.tracker.data.local.dao.SessionWithSets
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.data.repository.NextWorkoutAdvice
import com.yash.tracker.data.repository.WorkoutRepository
import com.yash.tracker.domain.share.ShareText
import com.yash.tracker.domain.workout.PersonalRecords
import com.yash.tracker.data.repository.toScored
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RoutineCard(
    val routine: RoutineWithExercises,
    val exerciseNames: List<String>,
    val lastPerformedAt: Long?,
)

/**
 * The routine editor's draft. [editingId] is null for a new routine and set when an existing
 * one is being changed, which is the only difference between the two flows.
 */
data class NewRoutineState(
    val open: Boolean = false,
    val editingId: Long? = null,
    val name: String = "",
    val query: String = "",
    val results: List<ExerciseEntity> = emptyList(),
    val picked: List<ExerciseEntity> = emptyList(),
) {
    val isEditing: Boolean get() = editingId != null
}

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val workouts: WorkoutRepository,
) : ViewModel() {

    val recentSessions: StateFlow<List<SessionWithSets>> = workouts.observeRecentSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** What is due, for the card above the routines. */
    val nextWorkout: StateFlow<NextWorkoutAdvice?> = workouts.observeNextWorkout()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _routines = MutableStateFlow<List<RoutineCard>>(emptyList())
    val routines = _routines.asStateFlow()

    private val _newRoutine = MutableStateFlow(NewRoutineState())
    val newRoutine = _newRoutine.asStateFlow()

    private val _resumableId = MutableStateFlow<Long?>(null)
    val resumableId = _resumableId.asStateFlow()

    init {
        viewModelScope.launch {
            workouts.observeRoutines().collect { list ->
                _routines.value = list.map { routine ->
                    RoutineCard(
                        routine = routine,
                        exerciseNames = routine.exercises
                            .sortedBy { it.position }
                            .mapNotNull { workouts.exercise(it.exerciseId)?.name },
                        lastPerformedAt = workouts.routineLastPerformed(routine.routine.id),
                    )
                }
            }
        }
        refreshResumable()
    }

    fun refreshResumable() {
        viewModelScope.launch { _resumableId.value = workouts.activeSessionId() }
    }

    fun deleteRoutine(id: Long) {
        viewModelScope.launch { workouts.deleteRoutine(id) }
    }

    // --- building a routine -------------------------------------------------------------

    fun openNewRoutine() = _newRoutine.update { NewRoutineState(open = true) }

    fun closeNewRoutine() = _newRoutine.update { NewRoutineState() }

    fun setRoutineName(name: String) = _newRoutine.update { it.copy(name = name) }

    fun searchExercises(term: String) {
        _newRoutine.update { it.copy(query = term) }
        viewModelScope.launch {
            val results = if (term.isBlank()) emptyList() else workouts.searchExercises(term)
            _newRoutine.update { it.copy(results = results) }
        }
    }

    fun pick(exercise: ExerciseEntity) = _newRoutine.update {
        if (it.picked.any { picked -> picked.id == exercise.id }) {
            it
        } else {
            it.copy(picked = it.picked + exercise, query = "", results = emptyList())
        }
    }

    fun unpick(exercise: ExerciseEntity) = _newRoutine.update {
        it.copy(picked = it.picked.filterNot { picked -> picked.id == exercise.id })
    }

    /** Opens the editor on an existing routine, prefilled with its name and order. */
    fun editRoutine(card: RoutineCard) {
        viewModelScope.launch {
            val picked = card.routine.exercises
                .sortedBy { it.position }
                .mapNotNull { workouts.exercise(it.exerciseId) }

            _newRoutine.value = NewRoutineState(
                open = true,
                editingId = card.routine.routine.id,
                name = card.routine.routine.name,
                picked = picked,
            )
        }
    }

    /** Resolves exercise names before handing finished text back for the share sheet. */
    fun shareSession(session: SessionWithSets, onReady: (String, String) -> Unit) {
        viewModelScope.launch {
            val names = session.sets
                .map { it.exerciseId }
                .distinct()
                .associateWith { workouts.exercise(it)?.name }

            onReady(
                ShareText.forSession(session.session, session.sets) { names[it] },
                session.session.name.ifBlank { "Workout" },
            )
        }
    }

    fun duplicateRoutine(id: Long) {
        viewModelScope.launch { workouts.duplicateRoutine(id) }
    }

    /** Reordering is a list move; position is written from the list's order when it is saved. */
    fun moveExercise(from: Int, to: Int) {
        _newRoutine.update { draft ->
            val picked = draft.picked
            if (from !in picked.indices || to !in picked.indices || from == to) return@update draft
            draft.copy(picked = picked.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    fun saveRoutine() {
        val draft = _newRoutine.value
        if (draft.name.isBlank() || draft.picked.isEmpty()) return

        viewModelScope.launch {
            val exerciseIds = draft.picked.map { it.id }
            val editingId = draft.editingId
            if (editingId == null) {
                workouts.createRoutine(draft.name, exerciseIds)
            } else {
                workouts.updateRoutine(editingId, draft.name, exerciseIds)
            }
            _newRoutine.value = NewRoutineState()
        }
    }
}

/** History shows a best set per exercise, which reads better than every row. */
fun SessionWithSets.bestSetLines(nameOf: (Long) -> String?): List<Pair<String, String>> =
    sets.groupBy { it.exerciseId }
        .mapNotNull { (exerciseId, rows) ->
            val name = nameOf(exerciseId) ?: return@mapNotNull null
            val best = PersonalRecords.bestSet(rows.map { it.toScored() }) ?: return@mapNotNull null
            val done = rows.count { it.isCompleted }
            val summary = when {
                best.weightKg != null && best.reps != null ->
                    "${best.weightKg.let { if (it % 1.0 == 0.0) it.toInt() else it }} kg × ${best.reps}"
                best.durationSec != null -> "${best.durationSec / 60} min"
                best.reps != null -> "${best.reps} reps"
                else -> "—"
            }
            "$done × $name" to summary
        }
