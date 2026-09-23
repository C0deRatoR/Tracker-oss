package com.yash.tracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yash.tracker.ui.theme.Motion
import com.yash.tracker.ui.theme.MetricSmallStyle
import com.yash.tracker.ui.theme.springClick

/**
 * The controls. All of them share one idea: the selected thing is a solid shape that *moves*
 * between positions, rather than a colour that blinks on somewhere else.
 */

/** cm / ft-in, kg / lbs, Grams / Bowls, 7 Days / 30 Days / 90 Days / All Time. */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
    thumbColor: Color = MaterialTheme.colorScheme.inverseSurface,
    thumbContentColor: Color = MaterialTheme.colorScheme.inverseOnSurface,
) {
    if (options.isEmpty()) return
    val padding = 4.dp

    BoxWithConstraints(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(padding),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(
            targetValue = segment * selectedIndex.coerceIn(0, options.lastIndex),
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
            label = "segmentThumb",
        )

        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(thumbColor),
        )

        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val colour by animateColorAsState(
                    targetValue = if (selected) thumbContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "segmentLabel",
                )
                Box(
                    modifier = Modifier
                        .width(segment)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .springClick(pressedScale = 0.96f, role = Role.Tab) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = colour,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun SelectableChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.inverseSurface
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        label = "chipBackground",
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.inverseOnSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "chipForeground",
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .springClick(pressedScale = 0.94f, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = foreground, maxLines = 1)
    }
}

/**
 * The radio-card from the onboarding objectives list: the whole card is the target, and the
 * dot is only there to say what kind of choice this is.
 */
@Composable
fun ChoiceCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
) {
    val background by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        label = "choiceBackground",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(background)
            .springClick(pressedScale = 0.985f, role = Role.RadioButton, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioDot(selected)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean, size: Dp = 22.dp) {
    val ring by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "radioRing",
    )
    val inner by animateDpAsState(
        targetValue = if (selected) size * 0.36f else 0.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "radioDot",
    )

    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerLowest else ring)
            .then(if (selected) Modifier.border(2.dp, ring, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(inner)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface),
        )
    }
}

/** − value + . The arithmetic stays with the caller; this only reports taps. */
@Composable
fun Stepper(
    value: String,
    unit: String?,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = Icons.Default.Remove,
            contentDescription = "Decrease${label?.let { " $it" }.orEmpty()}",
            onClick = onDecrement,
            size = 40.dp,
            background = MaterialTheme.colorScheme.surfaceContainerLowest,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(value, style = MetricSmallStyle)
            if (unit != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        CircleIconButton(
            icon = Icons.Default.Add,
            contentDescription = "Increase${label?.let { " $it" }.orEmpty()}",
            onClick = onIncrement,
            size = 40.dp,
            background = MaterialTheme.colorScheme.surfaceContainerLowest,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * A filled, indicator-less text field. Material's own is kept underneath so the IME, selection
 * handles and accessibility semantics all stay stock — only the chrome is replaced.
 */
@Composable
fun LuxTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    suffix: String? = null,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = label?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        suffix = suffix?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        supportingText = supporting?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = trailing,
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        shape = shape,
        textStyle = MaterialTheme.typography.bodyLarge,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            errorContainerColor = MaterialTheme.colorScheme.errorContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/** The pill-shaped search box at the top of the catalogue screens. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    LuxTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = Icons.Outlined.Search,
        trailing = trailing,
        shape = CircleShape,
    )
}

/** An expandable block header — the "Algorithmic Transparency" disclosure. */
@Composable
fun DisclosureRow(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = Motion.settle,
        label = "disclosure",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .springClick(pressedScale = 0.99f, onClick = onToggle)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Icon(
            Icons.Outlined.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .rotate(rotation),
        )
    }
}
