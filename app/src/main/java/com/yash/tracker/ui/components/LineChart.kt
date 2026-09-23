package com.yash.tracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yash.tracker.domain.progress.ChartScale
import com.yash.tracker.domain.progress.DayPoint
import com.yash.tracker.ui.theme.Decelerate

/**
 * A plain line chart drawn on a Canvas.
 *
 * No chart library: the only things needed here are a line, a shaded area under it, a set of
 * dots and a reference rule. The scaling lives in [ChartScale] so its edge cases are testable
 * without a screen.
 *
 * The line draws itself in from the left the first time it appears, which is the difference
 * between a chart that was rendered and a chart that was plotted.
 */
@Composable
fun LineChart(
    points: List<DayPoint>,
    trend: List<DayPoint>,
    reference: Double?,
    lineColour: Color,
    pointColour: Color,
    referenceColour: Color,
    modifier: Modifier = Modifier,
    fillColour: Color? = null,
    height: Dp = 150.dp,
) {
    val scale = ChartScale.of(points, trend.map { it.value } + listOfNotNull(reference)) ?: return

    var drawn by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(points.size, trend.size) { drawn = 1f }
    val reveal by animateFloatAsState(
        targetValue = drawn,
        animationSpec = tween(780, easing = Decelerate),
        label = "chartReveal",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        fun at(point: DayPoint) = Offset(
            x = scale.x(point) * size.width,
            y = size.height - scale.y(point.value) * size.height,
        )

        reference?.let {
            val y = size.height - scale.y(it) * size.height
            drawLine(
                color = referenceColour,
                start = Offset(0f, y),
                end = Offset(size.width * reveal, y),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
            )
        }

        val line = trend.takeIf { it.size >= 2 } ?: points.takeIf { it.size >= 2 }
        if (line != null) {
            val offsets = line.map(::at)

            if (fillColour != null) {
                val area = Path().apply {
                    moveTo(offsets.first().x, size.height)
                    offsets.forEach { lineTo(it.x, it.y) }
                    lineTo(offsets.last().x, size.height)
                    close()
                }
                clipRect(right = (size.width * reveal).coerceAtLeast(0.01f)) {
                    drawPath(
                        path = area,
                        brush = Brush.verticalGradient(
                            colors = listOf(fillColour, fillColour.copy(alpha = 0f)),
                            startY = 0f,
                            endY = size.height,
                        ),
                    )
                }
            }

            val path = Path().apply {
                moveTo(offsets.first().x, offsets.first().y)
                offsets.drop(1).forEach { lineTo(it.x, it.y) }
            }
            clipRect(right = (size.width * reveal).coerceAtLeast(0.01f)) {
                drawPath(
                    path = path,
                    color = lineColour,
                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }

        points.forEach { point ->
            val centre = at(point)
            if (centre.x <= size.width * reveal) {
                drawCircle(pointColour, radius = 5f, center = centre)
            }
        }

        // The newest reading gets a halo, because on a weight chart it is the only value the
        // reader is actually looking for.
        points.lastOrNull()?.let { latest ->
            if (reveal > 0.98f) {
                val centre = at(latest)
                drawCircle(lineColour.copy(alpha = 0.22f), radius = 13f, center = centre)
                drawCircle(lineColour, radius = 6f, center = centre)
            }
        }
    }
}
