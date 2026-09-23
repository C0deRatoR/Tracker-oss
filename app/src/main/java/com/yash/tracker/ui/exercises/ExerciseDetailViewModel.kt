package com.yash.tracker.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.tracker.data.local.dao.SessionWithSets
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.data.local.entity.PersonalRecordEntity
import com.yash.tracker.data.local.entity.WorkoutSetEntity
import com.yash.tracker.data.local.entity.isLogged
import com.yash.tracker.data.repository.WorkoutRepository
import com.yash.tracker.domain.workout.OneRepMax
import com.yash.tracker.domain.workout.RecordType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** One past performance of this exercise: the sets from it, and what its best set was. */
data class ExerciseOuting(
    val sessionId: Long,
    val sessionName: String,
    val startedAt: Long,
    val date: LocalDate,
    val sets: List<WorkoutSetEntity>,
) {
    val workingSets: List<WorkoutSetEntity>
        get() = sets.filter { it.isLogged && !it.isWarmup }

    val volumeKg: Double
        get() = workingSets.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }

    val heaviest: WorkoutSetEntity?
        get() = workingSets.filter { (it.weightKg ?: 0.0) > 0 }.maxByOrNull { it.weightKg ?: 0.0 }

    val estimatedOneRepMax: Double?
        get() = workingSets
            .filter { (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 }
            .maxOfOrNull { OneRepMax.epley(it.weightKg!!, it.reps!!) }
}

/** A personal best, with the day it was set. */
data class Best(val type: RecordType, val value: Double, val date: LocalDate?)

data class ExerciseDetailUiState(
    val exercise: ExerciseEntity? = null,
    val bests: List<Best> = emptyList(),
    val history: List<ExerciseOuting> = emptyList(),
    val completedSets: Int = 0,
    val loading: Boolean = true,
) {
    val timesPerformed: Int get() = history.size

    val lastPerformed: LocalDate? get() = history.firstOrNull()?.date

    /**
     * A single outing is a baseline, not a record — the same rule the session summary uses, so
     * the two screens never disagree about what counts as an achievement.
     */
    val hasRecords: Boolean get() = history.size >= 2 && bests.isNotEmpty()

    val bestEverVolume: Double? get() = history.maxOfOrNull { it.volumeKg }?.takeIf { it > 0 }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    private val workouts: WorkoutRepository,
) : ViewModel() {

    private val exerciseId = MutableStateFlow<Long?>(null)

    val state: StateFlow<ExerciseDetailUiState> = exerciseId
        .flatMapLatest { id ->
            if (id == null) {
                return@flatMapLatest MutableStateFlow(ExerciseDetailUiState(loading = false))
            }

            combine(
                workouts.observeExercise(id),
                workouts.observeRecords(id),
                workouts.observeHistoryFor(id),
                workouts.observeCompletedSetCount(id),
            ) { exercise, records, sessions, setCount ->
                ExerciseDetailUiState(
                    exercise = exercise,
                    bests = records.bestPerType(),
                    history = sessions.toOutings(id),
                    completedSets = setCount,
                    loading = false,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseDetailUiState())

    fun load(id: Long) {
        exerciseId.value = id
    }
}

/**
 * The record table keeps every best ever set, so the same type appears once per session that
 * improved it. Only the highest of each is a current best.
 */
private fun List<PersonalRecordEntity>.bestPerType(): List<Best> = this
    .groupBy { it.type }
    .mapNotNull { (type, rows) ->
        val kind = runCatching { RecordType.valueOf(type) }.getOrNull() ?: return@mapNotNull null
        val top = rows.maxByOrNull { it.value } ?: return@mapNotNull null
        Best(kind, top.value, runCatching { LocalDate.parse(top.date) }.getOrNull())
    }
    .sortedBy { RecordType.entries.indexOf(it.type) }

private fun List<SessionWithSets>.toOutings(exerciseId: Long): List<ExerciseOuting> {
    val zone = ZoneId.systemDefault()

    return mapNotNull { session ->
        // The query returns whole sessions, so everything but this exercise is dropped here.
        // Only sets that were actually filled in: a routine prefills its rows, so a session
        // can carry three ticked-but-empty sets of an exercise that was skipped on the day.
        val sets = session.sets
            .filter { it.exerciseId == exerciseId && it.isLogged }
            .sortedBy { it.setIndex }
        if (sets.isEmpty()) return@mapNotNull null

        ExerciseOuting(
            sessionId = session.session.id,
            sessionName = session.session.name,
            startedAt = session.session.startedAt,
            date = Instant.ofEpochMilli(session.session.startedAt).atZone(zone).toLocalDate(),
            sets = sets,
        )
    }
}
