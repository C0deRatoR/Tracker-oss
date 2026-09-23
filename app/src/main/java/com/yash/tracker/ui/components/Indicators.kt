package com.yash.tracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yash.tracker.ui.theme.Decelerate
import com.yash.tracker.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The measuring instruments: a ring, a bar, and the numbers that sit inside them. Every one of
 * these animates from where it was to where it is, and never from nothing on a recomposition.
 */

/**
 * The dominant readout on the dashboard. The arc sweeps in on arrival and then only ever moves
 * by the difference, so logging a meal visibly *takes* a slice off the ring.
 */
@Composable
fun EnergyRing(
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 196.dp,
    stroke: Dp = 13.dp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    track: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    val swept = animatedFraction(progress)

    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            val inset = width / 2f
            val arcSize = androidx.compose.ui.geometry.Size(
                size.width - width,
                size.height - width,
            )
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)

            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = width, cap = StrokeCap.Round),
            )
            if (swept > 0.001f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * swept.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = width, cap = StrokeCap.Round),
                )
            }
        }
        // The arc is a fixed diameter, so its readout has to be too, or the numbers spill
        // past the stroke at a large system font scale.
        CappedFontScale(max = 1.15f) { content() }
    }
}

/** The 1–2dp rule under every macro and every hydration total. */
@Composable
fun ThinBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    track: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val fraction = animatedFraction(progress)

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(CircleShape)
                .background(color),
        )
    }
}

/**
 * A segmented rule: several proportions in one line, as on the plate-confirm screen where
 * protein, carbs and fat share a single bar.
 */
@Composable
fun SplitBar(
    parts: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    track: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val total = parts.sumOf { it.first.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f

    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        parts.forEach { (value, colour) ->
            val fraction = animatedFraction(value / total)
            if (fraction > 0.004f) {
                Box(
                    Modifier
                        .weight(fraction.coerceIn(0.004f, 1f))
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(colour),
                )
            }
        }
    }
}

/** One of the three macro tiles under the ring. */
@Composable
fun MacroTile(
    label: String,
    consumed: Double,
    target: Double?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val fraction = if (target != null && target > 0) (consumed / target).toFloat() else 0f

    LuxCard(modifier, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (target != null && target > 0) "${(fraction * 100).roundToInt()}%" else "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedCount(
                    consumed.roundToInt(),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "/${target?.roundToInt() ?: 0}g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            ThinBar(fraction, height = 4.dp, color = color)
        }
    }
}

/**
 * A figure that counts to its new value rather than cutting to it. Only worth it for the
 * numbers a user changes and then looks at — totals, remaining, volume.
 */
@Composable
fun AnimatedCount(
    value: Int,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    grouped: Boolean = false,
) {
    val animated by androidx.compose.animation.core.animateIntAsState(
        targetValue = value,
        animationSpec = tween(520, easing = Decelerate),
        label = "count",
    )
    Text(
        if (grouped) "%,d".format(animated) else animated.toString(),
        style = style,
        color = color,
        modifier = modifier,
    )
}

/**
 * Fade-and-rise, offset by position. Used on the blocks at the top of a screen, where the list
 * is not recycled and the entrance is only ever seen once.
 */
@Composable
fun StaggerIn(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(Motion.STAGGER_MAX) * Motion.STAGGER_MS.toLong())
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(440, easing = Decelerate),
        label = "stagger",
    )

    Box(
        modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 20.dp.toPx()
        },
    ) { content() }
}

/**
 * Animates to [target], starting from zero the first time it is drawn so bars and rings sweep
 * in rather than appearing already full.
 */
@Composable
private fun animatedFraction(target: Float): Float {
    var settled by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(target) { settled = target }
    val value by animateFloatAsState(
        targetValue = settled,
        animationSpec = Motion.settle,
        label = "fraction",
    )
    return value
}
