package com.yash.tracker.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.data.repository.WorkoutRepository
import com.yash.tracker.domain.workout.Equipment
import com.yash.tracker.domain.workout.ExerciseFilter
import com.yash.tracker.domain.workout.ExerciseSearch
import com.yash.tracker.domain.workout.MuscleGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** A muscle-group heading and the exercises under it. */
data class ExerciseSection(val label: String, val exercises: List<ExerciseEntity>)

data class ExerciseLibraryUiState(
    val filter: ExerciseFilter = ExerciseFilter(),
    val sections: List<ExerciseSection> = emptyList(),
    val matches: Int = 0,
    val total: Int = 0,
    val loading: Boolean = true,
) {
    /** Searching wants one ranked list; browsing wants the catalogue in sections. */
    val isSearching: Boolean get() = filter.query.isNotBlank()
}

@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    private val workouts: WorkoutRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(ExerciseFilter())

    val state: StateFlow<ExerciseLibraryUiState> = combine(
        workouts.observeExercises(),
        filter,
    ) { all, current ->
        val matches = ExerciseSearch.apply(all, current)

        ExerciseLibraryUiState(
            filter = current,
            // A ranked list must not be re-sorted into sections, or the ranking is thrown away.
            sections = if (current.query.isNotBlank()) {
                listOf(ExerciseSection("", matches))
            } else {
                ExerciseSearch.groupByMuscle(matches).map { (label, rows) ->
                    ExerciseSection(label, rows)
                }
            },
            matches = matches.size,
            total = all.size,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseLibraryUiState())

    fun setQuery(query: String) {
        filter.value = filter.value.copy(query = query)
    }

    fun toggleMuscle(group: MuscleGroup) {
        filter.value = filter.value.copy(muscles = filter.value.muscles.toggle(group))
    }

    fun toggleEquipment(equipment: Equipment) {
        filter.value = filter.value.copy(equipment = filter.value.equipment.toggle(equipment))
    }

    fun clearFilters() {
        filter.value = ExerciseFilter(query = filter.value.query)
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> =
    if (value in this) this - value else this + value
