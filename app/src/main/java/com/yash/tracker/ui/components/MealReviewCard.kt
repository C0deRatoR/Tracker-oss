package com.yash.tracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yash.tracker.domain.nutrition.MealReview
import com.yash.tracker.domain.nutrition.ReviewNote
import com.yash.tracker.domain.nutrition.ReviewTone
import com.yash.tracker.domain.nutrition.Verdict
import com.yash.tracker.ui.theme.LocalAccents
import com.yash.tracker.ui.theme.MetricSmallStyle
import kotlin.math.roundToInt

/**
 * What a meal's composition says about it, shown the same way wherever it appears.
 *
 * One component for the confirm screen and the diary, because the judgement has to read
 * identically in both — a plate that scores 40 before saving and something else afterwards
 * makes the number worthless.
 *
 * No celebration and no scolding: PRD §4 has this app as a measuring instrument. The notes
 * carry the reasoning, worst first, so the line that explains a bad score is already in view.
 */
@Composable
fun MealReviewCard(review: MealReview, modifier: Modifier = Modifier) {
    if (!review.isWorthShowing) return

    when (review) {
        is MealReview.Rated -> RatedCard(review, modifier)
        is MealReview.Unrated -> UnratedCard(modifier)
    }
}

@Composable
private fun RatedCard(review: MealReview.Rated, modifier: Modifier) {
    LuxCard(modifier) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Meal review", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Pill(text = review.verdict.label(), tone = PillTone.Quiet, leadingDot = true)
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${review.score}", style = MetricSmallStyle)
                Text(
                    " / 100",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(12.dp))
            review.notes.forEachIndexed { index, note ->
                if (index > 0) Spacer(Modifier.height(9.dp))
                NoteRow(note)
            }

            if (review.isPartial) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Judged on the ${(review.coverage * 100).roundToInt()}% of this meal that " +
                        "carries a nutrition panel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * How much of it there was, and whether that is a good amount.
 *
 * The verdict carries the colour rather than a dot beside the name: it is the word being
 * judged, and colouring a bullet instead makes the reader hunt for what it refers to.
 */
@Composable
private fun NoteRow(note: ReviewNote) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            note.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(note.amount, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End)
        Spacer(Modifier.width(10.dp))
        // A fixed width, so four verdicts of different lengths still line up as a column.
        Text(
            note.verdict,
            style = MaterialTheme.typography.labelMedium,
            color = note.tone.verdictColour(),
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(44.dp),
        )
    }
}

@Composable
private fun UnratedCard(modifier: Modifier) {
    SoftCard(modifier) {
        Column(Modifier.padding(18.dp)) {
            Eyebrow("Meal review")
            Spacer(Modifier.height(6.dp))
            Text(
                "Not enough of this plate states its sugar, fibre or salt to judge it. " +
                    "Swapping a row for a catalogue food or one of your products fills that in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Verdict.label(): String = when (this) {
    Verdict.GOOD -> "Good"
    Verdict.FAIR -> "Fair"
    Verdict.POOR -> "Poor"
}

@Composable
private fun ReviewTone.verdictColour(): Color = when (this) {
    ReviewTone.CONCERN -> MaterialTheme.colorScheme.error
    ReviewTone.NOTE -> LocalAccents.current.warning
    ReviewTone.FINE -> MaterialTheme.colorScheme.onSurfaceVariant
}
