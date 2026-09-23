package com.yash.tracker.ui.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yash.tracker.data.remote.CoachNotes
import com.yash.tracker.data.remote.prompts.CoachNote
import com.yash.tracker.data.repository.WorkoutRepository
import com.yash.tracker.domain.workout.Effort
import com.yash.tracker.domain.workout.LiftProgress
import com.yash.tracker.domain.workout.LiftStatus
import com.yash.tracker.domain.workout.TrainingReport
import com.yash.tracker.ui.coach.CoachNoteBlock
import com.yash.tracker.ui.coach.CoachNoteState
import com.yash.tracker.ui.coach.ask
import com.yash.tracker.ui.components.DetailTopBar
import com.yash.tracker.ui.components.EmptyNote
import com.yash.tracker.ui.components.GroupHeader
import com.yash.tracker.ui.components.Hairline
import com.yash.tracker.ui.components.SoftCard
import com.yash.tracker.ui.components.ValueRow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class TrainingReportViewModel @Inject constructor(
    workouts: WorkoutRepository,
    private val coach: CoachNotes,
) : ViewModel() {

    private val _coachNote = MutableStateFlow<CoachNoteState>(CoachNoteState.Idle)
    val coachNote: StateFlow<CoachNoteState> = _coachNote

    val report: StateFlow<TrainingReport?> = workouts.observeTrainingReport()
        .onEach { _coachNote.value = CoachNoteState.Idle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun explain() {
        val current = report.value ?: return
        viewModelScope.launch { _coachNote.ask(coach, CoachNote.forTraining(current)) }
    }
}

/** Everything the rolling week says, section by section, with every suggestion in full. */
@Composable
fun TrainingReportScreen(onBack: () -> Unit, viewModel: TrainingReportViewModel = hiltViewModel()) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val coachNote by viewModel.coachNote.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(title = "Training analysis", subtitle = "Last 7 days", onBack = onBack)

        val current = report
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 32.dp),
        ) {
            if (current == null) return@Column
            if (current.isEmpty) {
                EmptyNote("Finish a workout and the analysis starts here.")
                return@Column
            }

            Section("What to do next", count = "${current.suggestions.size}") {
                if (current.suggestions.isEmpty()) {
                    EmptyNote("Nothing to fix — the week is balanced.")
                }
                current.suggestions.forEachIndexed { index, suggestion ->
                    if (index > 0) Rule()
                    SuggestionLine(suggestion)
                }
                Rule()
                CoachNoteBlock(coachNote, viewModel::explain)
            }

            Section("Sets per muscle", count = "${current.workingSets} sets") {
                MuscleBars(current.muscles.filter { it.muscle.isPriority })
                Rule()
                MuscleBars(current.muscles.filter { !it.muscle.isPriority })
            }

            Section("Movement patterns") {
                current.patterns.forEach { (pattern, sets) ->
                    ValueRow(pattern.label, if (sets == 0) "none" else "$sets sets")
                }
            }

            Section("Balance") {
                ValueRow("Upper-body push", "${current.pushSets} sets")
                ValueRow("Upper-body pull", "${current.pullSets} sets")
                current.compoundShare?.let { ValueRow("Compound lifts", "${(it * 100).roundToInt()}%") }
                ValueRow("Sessions", "${current.sessionsThisWeek} (usually ${"%.1f".format(current.sessionsPerWeekUsual)})")
            }

            Section("Intensity") {
                val bands = current.repBands
                ValueRow("1–5 reps (strength)", "${bands.strength} sets")
                ValueRow("6–12 reps (size)", "${bands.hypertrophy} sets")
                ValueRow("13+ reps (endurance)", "${bands.endurance} sets")
                ValueRow(
                    "Average RPE",
                    current.averageRpe?.let { "%.1f · %d%% rated".format(it, (current.rpeCoverage * 100).roundToInt()) }
                        ?: "not logged",
                )
            }

            if (current.lifts.isNotEmpty()) {
                Section("Lifts, last 6 weeks", count = "${current.lifts.size}") {
                    current.lifts.forEach { lift -> ValueRow(lift.name, liftLine(lift)) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, count: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        GroupHeader(title, count = count)
        Spacer(Modifier.height(10.dp))
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) { content() }
        }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun Rule() {
    Spacer(Modifier.height(12.dp))
    Hairline()
    Spacer(Modifier.height(12.dp))
}

private fun liftLine(lift: LiftProgress): String {
    val change = lift.changeFraction?.let { " %+d%%".format((it * 100).roundToInt()) }.orEmpty()
    return when (lift.status) {
        LiftStatus.PROGRESSING -> "Up$change"
        LiftStatus.STALLED -> "Stalled"
        LiftStatus.REGRESSING -> "Down$change"
        LiftStatus.NEW -> "First time · ${lift.latest.describe()}"
    }
}

private fun Effort.describe(): String = when (this) {
    is Effort.Load -> "e1RM ${oneRepMaxKg.roundToInt()} kg"
    is Effort.Reps -> "$count reps"
    is Effort.Time -> "${seconds}s"
}
