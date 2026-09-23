package com.yash.tracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yash.tracker.ui.theme.EyebrowStyle
import com.yash.tracker.ui.theme.LocalAccents
import com.yash.tracker.ui.theme.MetricSmallStyle
import com.yash.tracker.ui.theme.MetricStyle
import com.yash.tracker.ui.theme.springClick

/**
 * The surfaces, labels and chips the whole app is assembled from. Every screen is meant to be
 * these pieces in a different order — if a screen reaches for a raw Card or Button, the design
 * has drifted.
 */

/**
 * A shadow the colour of the ink rather than of black, and only in light mode: on the dark
 * palette a shadow is invisible, so the card is separated with a hairline instead.
 */
@Composable
fun Modifier.softShadow(shape: Shape, elevation: Dp = 3.dp): Modifier {
    val accents = LocalAccents.current
    return if (accents.isDark) {
        border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    } else {
        shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = accents.shadow,
            spotColor = accents.shadow,
        )
    }
}

/** The white card the design rests almost everything on. */
@Composable
fun LuxCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    elevation: Dp = 3.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .softShadow(shape, elevation)
        .clip(shape)
        .background(color)

    Column(
        modifier = if (onClick != null) base.springClick(onClick = onClick) else base,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

/**
 * A card that is part of the page rather than floating on it — used for the quieter blocks
 * (notices, grouped rows) where a white card would over-claim.
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier.clip(shape).background(color)
    Column(modifier = if (onClick != null) base.springClick(onClick = onClick) else base, content = content)
}

/** ENERGY BALANCE, TIMELINE, STEP 2 OF 4 — the tracked micro-label above a block. */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text.uppercase(),
        style = EyebrowStyle,
        color = color,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Eyebrow over a title, with an optional control on the right of the title line. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) {
                Eyebrow(eyebrow)
                Spacer(Modifier.height(4.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** A smaller heading for the groups inside a screen: "Meal Ledger", "Routines", "History". */
@Composable
fun GroupHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Pill(count, tone = PillTone.Quiet)
        }
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action.uppercase(),
                style = EyebrowStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .springClick(pressedScale = 0.94f, onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

enum class PillTone { Quiet, Solid, Accent, Ink, Warning, Danger }

/** The rounded-full chip that carries every secondary fact in the design. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    tone: PillTone = PillTone.Solid,
    icon: ImageVector? = null,
    leadingDot: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val accents = LocalAccents.current
    val background = when (tone) {
        PillTone.Quiet -> scheme.surfaceContainerLow
        PillTone.Solid -> scheme.surfaceContainer
        PillTone.Accent -> scheme.primaryContainer
        PillTone.Ink -> scheme.inverseSurface
        PillTone.Warning -> accents.warning.copy(alpha = 0.14f)
        PillTone.Danger -> scheme.errorContainer
    }
    val foreground = when (tone) {
        PillTone.Quiet, PillTone.Solid -> scheme.onSurface
        PillTone.Accent -> scheme.onPrimaryContainer
        PillTone.Ink -> scheme.inverseOnSurface
        PillTone.Warning -> accents.warning
        PillTone.Danger -> scheme.onErrorContainer
    }

    val base = modifier.clip(CircleShape).background(background)
    Row(
        modifier = (if (onClick != null) base.springClick(pressedScale = 0.94f, onClick = onClick) else base)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingDot) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(foreground),
            )
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(14.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = foreground, maxLines = 1)
    }
}

/**
 * A number and its unit, set the way the design sets every readout: the figure carries the
 * weight, the unit trails it small and quiet, and the two share a baseline.
 */
@Composable
fun Metric(
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    large: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(value, style = if (large) MetricStyle else MetricSmallStyle, color = color)
        if (unit != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = if (large) 4.dp else 2.dp),
            )
        }
    }
}

/** A label on the left, a value on the right, with nothing between them but space. */
@Composable
fun ValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            color = valueColor,
            textAlign = TextAlign.End,
        )
    }
}

/** The rounded square that holds a glyph beside a title — a plate, not a circle. */
@Composable
fun IconPlate(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tone: PillTone = PillTone.Quiet,
    contentDescription: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val background = when (tone) {
        PillTone.Accent -> scheme.primaryContainer
        PillTone.Ink -> scheme.inverseSurface
        PillTone.Solid -> scheme.surfaceContainer
        else -> scheme.surfaceContainerLow
    }
    val foreground = when (tone) {
        PillTone.Accent -> scheme.primary
        PillTone.Ink -> scheme.inverseOnSurface
        else -> scheme.onSurface
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = foreground,
            modifier = Modifier.size(size * 0.46f),
        )
    }
}

/** A hairline. Deliberately thinner and lighter than Material's divider. */
@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

/** The empty state used everywhere: quiet, one line, never a full-page illustration. */
@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(vertical = 16.dp),
    )
}
