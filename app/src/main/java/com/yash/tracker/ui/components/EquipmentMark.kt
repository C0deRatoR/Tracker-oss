package com.yash.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yash.tracker.R
import com.yash.tracker.domain.workout.Equipment

/**
 * The equipment mark that stands in for exercise artwork.
 *
 * Six shapes drawn for this project, one per equipment value. Now the fallback rather than the
 * rule: [ExerciseArt] shows ExerciseDB's picture where a row has one and it loads, and this
 * for the rest — offline, the rows with no match, and any the user adds themselves.
 */
@Composable
fun EquipmentMark(
    equipment: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    background: Color = MaterialTheme.colorScheme.surfaceContainer,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val resolved = Equipment.of(equipment) ?: Equipment.OTHER

    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(resolved.drawable()),
            contentDescription = resolved.label,
            tint = tint,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

fun Equipment.drawable(): Int = when (this) {
    Equipment.BARBELL -> R.drawable.ic_equipment_barbell
    Equipment.DUMBBELL -> R.drawable.ic_equipment_dumbbell
    Equipment.CABLE -> R.drawable.ic_equipment_cable
    Equipment.MACHINE -> R.drawable.ic_equipment_machine
    Equipment.BODYWEIGHT -> R.drawable.ic_equipment_bodyweight
    Equipment.OTHER -> R.drawable.ic_equipment_other
}
