package com.yash.tracker.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.tracker.data.local.dao.PreviousSet
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.data.local.entity.WorkoutSetEntity
import com.yash.tracker.data.repository.FinishedSession
import com.yash.tracker.data.repository.WorkoutRepository
import com.yash.tracker.data.rest.RestActionReceiver
import com.yash.tracker.data.rest.RestAlerts
import com.yash.tracker.data.rest.RestCommand
import com.yash.tracker.data.rest.RestCommands
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** One exercise's rows within the session, with what was done last time alongside. */
data class ExerciseBlock(
    val exercise: ExerciseEntity,
    val sets: List<WorkoutSetEntity>,
    val previous: List<PreviousSet>,
) {
    val isCardio: Boolean get() = exercise.type == "CARDIO"

    fun previousFor(setIndex: Int): PreviousSet? =
        previous.getOrNull(setIndex) ?: previous.lastOrNull()
}

data class RestTimer(val totalSec: Int, val remainingSec: Int) {
    val isRunning: Boolean get() = remainingSec > 0
}

data class LiveSessionUiState(
    val sessionId: Long? = null,
    val routineId: Long? = null,
    val name: String = "",
    val startedAt: Long = 0L,
    val elapsedSec: Int = 0,
    val blocks: List<ExerciseBlock> = emptyList(),
    val rest: RestTimer? = null,
    val finished: FinishedSession? = null,
    val exercisePicker: List<ExerciseEntity> = emptyList(),
    /** Seconds left before the session ends itself, or null when nothing is pending. */
    val autoFinishIn: Int? = null,
) {
    val completedSets: Int get() = blocks.sumOf { block -> block.sets.count { it.isCompleted } }

    val totalSets: Int get() = blocks.sumOf { it.sets.size }

    /**
     * A routine says up front what the session is meant to contain, so "everything is ticked"
     * is a meaningful end. An empty workout has no such plan — its sets are all complete the
     * moment you tick the first one — so it is never auto-finished.
     */
    val isRoutineComplete: Boolean
        get() = routineId != null && totalSets > 0 && completedSets == totalSets
}

@HiltViewModel
class LiveSessionViewModel @Inject constructor(
    private val workouts: WorkoutRepository,
    private val alerts: RestAlerts,
    restCommands: RestCommands,
) : ViewModel() {

    private val _state = MutableStateFlow(LiveSessionUiState())
    val state = _state.asStateFlow()

    init {
        // The notification's buttons arrive here: it has no handle on this view model, so it
        // posts and this listens for as long as the session is on screen.
        viewModelScope.launch {
            restCommands.commands.collect { command ->
                when (command) {
                    RestCommand.ADD_TIME -> adjustRest(RestActionReceiver.ADDED_SECONDS)
                    RestCommand.SKIP -> stopRest()
                }
            }
        }
    }

    private var tickJob: Job? = null
    private var restJob: Job? = null
    private var autoFinishJob: Job? = null
    private var autoFinishDeclined = false

    fun startOrResume(routineId: Long?) {
        viewModelScope.launch {
            val existing = workouts.activeSessionId()
            val sessionId = existing ?: workouts.startSession(routineId, LocalDate.now())
            load(sessionId)
            startTicking()
        }
    }

    private suspend fun load(sessionId: Long) {
        val session = workouts.session(sessionId) ?: return
        val blocks = session.sets
            .groupBy { it.exerciseId }
            .toList()
            .sortedBy { (_, sets) -> sets.minOf { it.position } }
            .mapNotNull { (exerciseId, sets) ->
                val exercise = workouts.exercise(exerciseId) ?: return@mapNotNull null
                ExerciseBlock(
                    exercise = exercise,
                    sets = sets.sortedBy { it.setIndex },
                    previous = workouts.previousSetsFor(exerciseId),
                )
            }

        _state.update {
            it.copy(
                sessionId = sessionId,
                routineId = session.session.routineId,
                name = session.session.name,
                startedAt = session.session.startedAt,
                blocks = blocks,
            )
        }
        considerAutoFinish()
    }

    // --- finishing itself ----------------------------------------------------------------

    /**
     * Ending a workout cannot be undone, so this counts down in the open with a way out rather
     * than firing the moment the last tick lands. Someone about to add a drop set gets to say
     * no; someone who is done does nothing and it finishes.
     */
    private fun considerAutoFinish() {
        val complete = _state.value.isRoutineComplete

        if (!complete) {
            // Un-ticking a set, or adding one, re-arms it — otherwise a single cancel would
            // switch the behaviour off for the rest of the session.
            autoFinishDeclined = false
            cancelAutoFinish()
            return
        }

        if (autoFinishDeclined || autoFinishJob?.isActive == true) return

        autoFinishJob = viewModelScope.launch {
            for (remaining in AUTO_FINISH_SECONDS downTo 1) {
                _state.update { it.copy(autoFinishIn = remaining) }
                delay(1000)
            }
            _state.update { it.copy(autoFinishIn = null) }
            finish()
        }
    }

    /** The user said no. Stays off until the session stops being complete. */
    fun cancelAutoFinish() {
        autoFinishJob?.cancel()
        autoFinishJob = null
        if (_state.value.autoFinishIn != null) autoFinishDeclined = true
        _state.update { it.copy(autoFinishIn = null) }
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (true) {
                val started = _state.value.startedAt
                if (started > 0) {
                    _state.update {
                        it.copy(elapsedSec = ((System.currentTimeMillis() - started) / 1000).toInt())
                    }
                }
                delay(1000)
            }
        }
    }

    // --- editing sets -------------------------------------------------------------------

    private fun persist(set: WorkoutSetEntity) {
        viewModelScope.launch {
            workouts.updateSet(set)
            _state.value.sessionId?.let { load(it) }
        }
    }

    /**
     * A weight typed on one set is the weight of the sets under it.
     *
     * Straight sets are the normal case, so typing the same number three times is work the app
     * should be doing. Only sets that are not yet ticked are filled: a set already logged is a
     * record of what was actually lifted, and a drop set typed afterwards would be overwritten
     * by a correction made further up.
     */
    fun setWeight(set: WorkoutSetEntity, weightKg: Double?) {
        persist(set.copy(weightKg = weightKg))
        if (weightKg == null) return

        val sets = _state.value.blocks
            .firstOrNull { block -> block.sets.any { it.id == set.id } }
            ?.sets
            .orEmpty()

        carriesTo(sets, set, weightKg).forEach { row -> persist(row.copy(weightKg = weightKg)) }
    }

    fun setReps(set: WorkoutSetEntity, reps: Int?) = persist(set.copy(reps = reps))

    fun setDistance(set: WorkoutSetEntity, metres: Double?) =
        persist(set.copy(distanceM = metres))

    fun setDuration(set: WorkoutSetEntity, seconds: Int?) =
        persist(set.copy(durationSec = seconds))

    fun toggleWarmup(set: WorkoutSetEntity) = persist(set.copy(isWarmup = !set.isWarmup))

    /**
     * Completing a set starts the rest timer, which is the whole point of the tick: the phone
     * is on a bench and the user should not have to start anything by hand.
     */
    fun toggleCompleted(set: WorkoutSetEntity, restSec: Int) {
        val nowCompleted = !set.isCompleted
        persist(set.copy(isCompleted = nowCompleted))
        if (nowCompleted) startRest(restSec) else stopRest()
    }

    fun addSet(exerciseId: Long) {
        viewModelScope.launch {
            _state.value.sessionId?.let {
                workouts.addSet(it, exerciseId)
                load(it)
            }
        }
    }

    fun deleteSet(set: WorkoutSetEntity) {
        viewModelScope.launch {
            workouts.deleteSet(set)
            _state.value.sessionId?.let { load(it) }
        }
    }

    /**
     * Persists this exercise's rest, so the change outlives the session rather than being one
     * more thing to redo next week. The running timer is left alone: you are resting now on the
     * old value, and restarting it under you would be worse than finishing it.
     */
    fun setDefaultRest(exerciseId: Long, seconds: Int) {
        viewModelScope.launch {
            workouts.setDefaultRest(exerciseId, seconds)
            _state.value.sessionId?.let { load(it) }
        }
    }

    fun addExercise(exerciseId: Long) {
        viewModelScope.launch {
            _state.value.sessionId?.let {
                workouts.addExerciseToSession(it, exerciseId)
                load(it)
            }
            _state.update { it.copy(exercisePicker = emptyList()) }
        }
    }

    fun searchExercises(term: String) {
        viewModelScope.launch {
            _state.update { it.copy(exercisePicker = workouts.searchExercises(term)) }
        }
    }

    fun dismissPicker() = _state.update { it.copy(exercisePicker = emptyList()) }

    // --- rest timer ---------------------------------------------------------------------

    fun startRest(seconds: Int) {
        restJob?.cancel()
        _state.update { it.copy(rest = RestTimer(seconds, seconds)) }
        alerts.counting(seconds, seconds)

        restJob = viewModelScope.launch {
            for (remaining in seconds - 1 downTo 0) {
                delay(1000)
                _state.update { it.copy(rest = it.rest?.copy(remainingSec = remaining)) }
                alerts.counting(remaining, seconds)
                // Three taps and then go, so the end is felt coming rather than just arriving.
                if (remaining in 1..3) alerts.countingDown()
            }
            // Reaching zero used to leave the banner sitting there at 0:00 until Skip was
            // pressed, which made the timer look broken and meant the one moment it exists
            // for passed unannounced.
            alerts.finished()
            _state.update { it.copy(rest = null) }
        }
    }

    fun stopRest() {
        restJob?.cancel()
        alerts.clear()
        _state.update { it.copy(rest = null) }
    }

    fun adjustRest(deltaSec: Int) {
        val current = _state.value.rest ?: return
        startRest((current.remainingSec + deltaSec).coerceAtLeast(0))
    }

    // --- leaving ------------------------------------------------------------------------

    fun finish() {
        viewModelScope.launch {
            val id = _state.value.sessionId ?: return@launch
            autoFinishJob?.cancel()
            tickJob?.cancel()
            stopRest()
            _state.update { it.copy(finished = workouts.finishSession(id)) }
        }
    }

    fun discard(onDone: () -> Unit) {
        viewModelScope.launch {
            _state.value.sessionId?.let { workouts.discardSession(it) }
            tickJob?.cancel()
            stopRest()
            _state.value = LiveSessionUiState()
            onDone()
        }
    }

    override fun onCleared() {
        tickJob?.cancel()
        restJob?.cancel()
        autoFinishJob?.cancel()
        alerts.clear()
    }

    private companion object {
        /** Long enough to notice and stop, short enough not to be a wait. */
        const val AUTO_FINISH_SECONDS = 5
    }
}

/**
 * The sets a weight typed on one row should be copied onto.
 *
 * Everything under it that has not been ticked yet. A ticked set is a record of what was
 * actually lifted, so it is never rewritten, and a row that already holds the number is left
 * alone rather than written again — every write persists and reloads the session.
 */
internal fun carriesTo(
    sets: List<WorkoutSetEntity>,
    from: WorkoutSetEntity,
    weightKg: Double,
): List<WorkoutSetEntity> = sets.filter {
    it.setIndex > from.setIndex && !it.isCompleted && it.weightKg != weightKg
}
