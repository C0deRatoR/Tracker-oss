package com.yash.tracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yash.tracker.ui.theme.EyebrowStyle
import com.yash.tracker.ui.theme.springClick

/**
 * Four button weights and nothing else. The design never shows an outlined button and never
 * shows two solid buttons side by side — the heavier one is always the only one.
 */
enum class ActionTone {
    /** The single most important thing on the screen. Ink-coloured, full width. */
    Ink,

    /** The accented alternative, for actions that are additive rather than committal. */
    Accent,

    /** A control that belongs to the surface it sits on. */
    Soft,

    /** A way out. No fill at all. */
    Ghost,
}

@Composable
private fun toneColors(tone: ActionTone, enabled: Boolean): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        ActionTone.Ink -> scheme.inverseSurface to scheme.inverseOnSurface
        ActionTone.Accent -> scheme.primary to scheme.onPrimary
        ActionTone.Soft -> scheme.surfaceContainer to scheme.onSurface
        ActionTone.Ghost -> Color.Transparent to scheme.onSurfaceVariant
    }
    // A disabled solid button must not read as a grey blob: it drops to the surface tone and
    // keeps a legible label, so it still looks like a control that is currently unavailable.
    return when {
        enabled -> bg to fg
        tone == ActionTone.Ghost -> Color.Transparent to fg.copy(alpha = 0.38f)
        else -> scheme.surfaceContainer to scheme.onSurfaceVariant.copy(alpha = 0.55f)
    }
}

/**
 * The pill button. `trailingIcon` in a circle is the design's "this leads somewhere" affordance
 * (Begin Setup, Accept Plan); a leading icon is for actions that happen here and now.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ActionTone = ActionTone.Ink,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    height: Dp = 54.dp,
) {
    val (background, foreground) = toneColors(tone, enabled)
    val animatedBackground by animateColorAsState(background, label = "pillBackground")

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = height)
            .clip(CircleShape)
            .background(animatedBackground)
            .then(
                if (tone == ActionTone.Ghost) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                } else {
                    Modifier
                }
            )
            .springClick(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (trailingIcon != null) 12.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = foreground, modifier = Modifier.size(18.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingIcon != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(foreground.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    trailingIcon,
                    contentDescription = null,
                    tint = foreground,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** The small chip-shaped control: "+ 250 ml", "Calendar", "Log weight", "Retake". */
@Composable
fun SmallAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ActionTone = ActionTone.Soft,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val (background, foreground) = toneColors(tone, enabled)

    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(CircleShape)
            .background(if (tone == ActionTone.Ghost) Color.Transparent else background)
            .springClick(enabled = enabled, pressedScale = 0.94f, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(16.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = foreground, maxLines = 1)
    }
}

/** A bare word that acts, for the lowest-stakes controls: Delete, Forget, Cancel. */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    caps: Boolean = false,
) {
    Text(
        text = if (caps) text.uppercase() else text,
        style = if (caps) EyebrowStyle else MaterialTheme.typography.labelLarge,
        color = if (enabled) color else color.copy(alpha = 0.4f),
        modifier = modifier
            .clip(CircleShape)
            .springClick(enabled = enabled, pressedScale = 0.94f, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        maxLines = 1,
    )
}

/** A round tap target holding one glyph — the top-bar controls and row affordances. */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    background: Color = Color.Transparent,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .springClick(enabled = enabled, pressedScale = 0.9f, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            modifier = Modifier.size(size * 0.5f),
        )
    }
}
