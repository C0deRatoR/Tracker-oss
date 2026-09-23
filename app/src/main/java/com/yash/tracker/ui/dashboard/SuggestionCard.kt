package com.yash.tracker.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yash.tracker.domain.diary.MealType
import com.yash.tracker.domain.nutrition.DayMicroStatus
import com.yash.tracker.domain.nutrition.MacroGap
import com.yash.tracker.domain.nutrition.MacroSuggestion
import com.yash.tracker.domain.nutrition.Plate
import com.yash.tracker.domain.nutrition.PlateTag
import com.yash.tracker.ui.coach.CoachNoteBlock
import com.yash.tracker.ui.coach.CoachNoteState
import com.yash.tracker.ui.components.ActionTone
import com.yash.tracker.ui.components.Hairline
import com.yash.tracker.ui.components.IconPlate
import com.yash.tracker.ui.components.LuxCard
import com.yash.tracker.ui.components.Pill
import com.yash.tracker.ui.components.PillTone
import com.yash.tracker.ui.components.SmallAction
import kotlin.math.roundToInt

/**
 * What to eat next, worked out from what the day has left.
 *
 * Framed as an offer rather than an instruction, the way the workout card is: the numbers are
 * shown in full so the suggestion can be disagreed with, and every plate is one tap from the
 * diary because the point of the card is to save the search, not to start one.
 */
@Composable
fun SuggestionCard(
    suggestion: MacroSuggestion,
    onLog: (MealType, Plate) -> Unit,
    coachNote: CoachNoteState = CoachNoteState.Idle,
    onExplain: () -> Unit = {},
) {
    LuxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconPlate(Icons.Outlined.RestaurantMenu, size = 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(heading(suggestion), style = MaterialTheme.typography.titleMedium)
                    Text(
                        subheading(suggestion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                (suggestion as? MacroSuggestion.Plates)?.let {
                    Spacer(Modifier.width(8.dp))
                    Pill("${it.gap.kcal.roundToInt()} kcal", tone = PillTone.Solid)
                }
            }

            when (suggestion) {
                is MacroSuggestion.Plates -> {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        gapLine(suggestion.gap),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    suggestion.micros?.let { micros ->
                        Text(
                            microLine(micros),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    suggestion.plates.forEach { plate ->
                        Spacer(Modifier.height(10.dp))
                        Hairline()
                        Spacer(Modifier.height(10.dp))
                        PlateRow(plate, suggestion.meal) { onLog(suggestion.meal, plate) }
                    }
                    Spacer(Modifier.height(14.dp))
                    CoachNoteBlock(coachNote, onExplain)
                }

                // The three ways of having nothing to suggest all say so in the subheading;
                // adding an empty list underneath would only repeat it.
                MacroSuggestion.DayDone,
                MacroSuggestion.NoHistoryYet,
                is MacroSuggestion.NothingFits,
                -> Unit
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlateRow(plate: Plate, meal: MealType, onLog: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                plate.items.joinToString(" + ") { "${it.portionLabel} ${it.name}" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${plate.total.kcal.roundToInt()} kcal · " +
                    "P ${plate.total.proteinG.roundToInt()} · " +
                    "C ${plate.total.carbsG.roundToInt()} · " +
                    "F ${plate.total.fatG.roundToInt()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (plate.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PlateTag.entries.filter { it in plate.tags }.forEach { tag ->
                        Pill(
                            tag.label(meal),
                            tone = if (tag == PlateTag.NEW_FOOD) PillTone.Accent else PillTone.Quiet,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        SmallAction(text = "Log", onClick = onLog, tone = ActionTone.Soft)
    }
}

private fun heading(suggestion: MacroSuggestion): String = when (suggestion) {
    is MacroSuggestion.Plates -> "Next: ${suggestion.meal.readable()}"
    is MacroSuggestion.NothingFits -> "Next: ${suggestion.meal.readable()}"
    MacroSuggestion.DayDone -> "Day's target met"
    MacroSuggestion.NoHistoryYet -> "Nothing to suggest yet"
}

private fun subheading(suggestion: MacroSuggestion): String = when (suggestion) {
    is MacroSuggestion.Plates ->
        if (suggestion.plates.any { PlateTag.NEW_FOOD in it.tags }) {
            "From what you eat, plus a few new ideas"
        } else {
            "From what you actually eat"
        }
    is MacroSuggestion.NothingFits ->
        "Nothing you eat, or in the catalogue, fits in what's left"
    MacroSuggestion.DayDone -> "Nothing left worth planning a meal around"
    MacroSuggestion.NoHistoryYet -> "Log a few meals and suggestions start here"
}

/** The debt the plates below are trying to close, stated so they can be argued with. */
private fun gapLine(gap: MacroGap): String =
    "Room for P ${gap.proteinG.roundToInt()} g · " +
        "C ${gap.carbsG.roundToInt()} g · " +
        "F ${gap.fatG.roundToInt()} g"

/** Fibre against the day's line, and how much salt is left — the two micros a day runs out of. */
private fun microLine(micros: DayMicroStatus): String = buildString {
    append("Fibre ${(micros.fibreEatenG ?: 0.0).roundToInt()} of ${micros.fibreTargetG.roundToInt()} g")
    micros.sodiumLeftMg?.let { append(" · Salt ${it.roundToInt()} mg left") }
}

private fun PlateTag.label(meal: MealType): String = when (this) {
    PlateTag.CLOSES_PROTEIN -> "Closes protein"
    PlateTag.ADDS_FIBRE -> "Adds fibre"
    PlateTag.USUAL_FOR_MEAL -> "Your usual ${meal.readable().lowercase()}"
    PlateTag.NEW_FOOD -> "New to you"
    PlateTag.HAD_TODAY -> "Had today"
}

private fun MealType.readable(): String = name.lowercase().replaceFirstChar(Char::uppercase)
