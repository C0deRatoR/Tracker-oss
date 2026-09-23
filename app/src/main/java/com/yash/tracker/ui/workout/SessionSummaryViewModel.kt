package com.yash.tracker.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.tracker.data.repository.WorkoutRepository
import com.yash.tracker.data.repository.label
import com.yash.tracker.data.local.entity.isLogged
import com.yash.tracker.domain.share.ShareText
import com.yash.tracker.domain.workout.RecordType
import com.yash.tracker.domain.workout.SessionAnalysis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class SessionSummaryUiState(
    val name: String = "",
    val durationSec: Int = 0,
    val volumeKg: Double = 0.0,
    val setsCompleted: Int = 0,
    val kcalBurned: Int = 0,
    val exercises: List<Pair<String, String>> = emptyList(),
    val records: List<String> = emptyList(),
    /** Where the work went and whether each lift moved. Null until the session has loaded. */
    val analysis: SessionAnalysis? = null,
    /** Built when the session loads, so sharing is a tap rather than another query. */
    val shareText: String = "",
)

@HiltViewModel
class SessionSummaryViewModel @Inject constructor(
    private val workouts: WorkoutRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionSummaryUiState())
    val state = _state.asStateFlow()

    fun load(sessionId: Long) {
        viewModelScope.launch {
            val session = workouts.session(sessionId) ?: return@launch
            val names = session.sets
                .map { it.exerciseId }
                .distinct()
                .associateWith { workouts.exercise(it)?.name }

            val duration = session.session.endedAt
                ?.let { ((it - session.session.startedAt) / 1000).toInt() }
                ?: 0

            val records = workouts.brokenRecordsForSession(sessionId).mapNotNull { record ->
                val name = names[record.exerciseId] ?: return@mapNotNull null
                val type = runCatching { RecordType.valueOf(record.type) }.getOrNull()
                    ?: return@mapNotNull null
                "$name — ${type.label()}, ${record.value.roundToInt()}"
            }

            _state.value = SessionSummaryUiState(
                name = session.session.name,
                durationSec = duration,
                volumeKg = session.session.totalVolumeKg,
                setsCompleted = session.sets.count { it.isLogged },
                kcalBurned = session.session.kcalBurned,
                exercises = session.bestSetLines { names[it] },
                analysis = workouts.analyse(sessionId),
                records = records,
                shareText = ShareText.forSession(
                    session = session.session,
                    sets = session.sets,
                    nameOf = { names[it] },
                ),
            )
        }
    }
}
