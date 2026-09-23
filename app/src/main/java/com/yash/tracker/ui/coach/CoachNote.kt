package com.yash.tracker.ui.coach

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yash.tracker.data.remote.CoachNotes
import com.yash.tracker.data.remote.CoachOutcome
import com.yash.tracker.ui.components.ActionTone
import com.yash.tracker.ui.components.SmallAction
import kotlinx.coroutines.flow.MutableStateFlow

/** Where an on-demand explanation is. Idle until the user asks: nothing here spends quota by itself. */
sealed interface CoachNoteState {
    data object Idle : CoachNoteState
    data object Loading : CoachNoteState
    data class Ready(val note: String) : CoachNoteState
    data class Failed(val message: String) : CoachNoteState
}

/** Asks for a note once at a time; a second tap while one is in flight is ignored. */
suspend fun MutableStateFlow<CoachNoteState>.ask(coach: CoachNotes, findingsJson: String) {
    if (value == CoachNoteState.Loading) return
    value = CoachNoteState.Loading
    value = when (val outcome = coach.coachNote(findingsJson)) {
        is CoachOutcome.Success -> CoachNoteState.Ready(outcome.note)
        is CoachOutcome.Failure -> CoachNoteState.Failed(outcome.message)
    }
}

/**
 * The note, or the button that asks for one.
 *
 * Labelled as Gemini's so it is never mistaken for the app's own arithmetic, which is the part
 * above it that can be checked.
 */
@Composable
fun CoachNoteBlock(state: CoachNoteState, onAsk: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        when (state) {
            CoachNoteState.Idle -> SmallAction(
                text = "Explain",
                onClick = onAsk,
                tone = ActionTone.Soft,
                icon = Icons.Outlined.AutoAwesome,
            )

            CoachNoteState.Loading -> SmallAction(
                text = "Thinking…",
                onClick = {},
                tone = ActionTone.Soft,
                enabled = false,
                icon = Icons.Outlined.AutoAwesome,
            )

            is CoachNoteState.Ready -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gemini's read", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
                }
                Spacer(Modifier.height(4.dp))
                Text(state.note, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
            }

            is CoachNoteState.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.error,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                SmallAction(text = "Retry", onClick = onAsk, tone = ActionTone.Soft)
            }
        }
    }
}
