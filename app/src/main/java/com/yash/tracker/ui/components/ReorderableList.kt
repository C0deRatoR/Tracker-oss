package com.yash.tracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.yash.tracker.ui.theme.Motion
import kotlin.math.roundToInt

/**
 * A short list whose order can be dragged.
 *
 * Deliberately a plain Column rather than a LazyColumn with a reorder library: the lists this
 * serves are a handful of rows, every one is composed anyway, and a fixed row height makes the
 * target index a division rather than a hit test against moving bounds.
 *
 * Dragging is on a handle, not the row, so the row stays free for its own taps.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 64.dp,
    spacing: Dp = 8.dp,
    content: @Composable (index: Int, item: T) -> Unit,
) {
    val stepPx = with(LocalDensity.current) { (rowHeight + spacing).toPx() }

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing)) {
        items.forEachIndexed { index, item ->
            val isDragging = index == draggingIndex
            val lift by animateFloatAsState(
                targetValue = if (isDragging) 1f else 0f,
                animationSpec = Motion.press,
                label = "reorderLift",
            )

            Row(
                modifier = Modifier
                    .height(rowHeight)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffset else 0f
                        // The dragged row rides above its neighbours, or it slides under them.
                        shadowElevation = lift * 12f
                        scaleX = 1f + lift * 0.02f
                        scaleY = 1f + lift * 0.02f
                    }
                    .zIndexIf(isDragging),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { content(index, item) }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .semantics { contentDescription = "Reorder, position ${index + 1}" }
                        .pointerInput(key(item), items.size) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingIndex = index
                                    dragOffset = 0f
                                },
                                onDragEnd = {
                                    draggingIndex = -1
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = -1
                                    dragOffset = 0f
                                },
                            ) { change, drag ->
                                change.consume()
                                dragOffset += drag.y

                                // Swap as soon as the row has travelled past its neighbour's
                                // midpoint, and carry the remainder so the finger stays put.
                                val steps = (dragOffset / stepPx).roundToInt()
                                if (steps != 0) {
                                    val from = draggingIndex
                                    val to = (from + steps).coerceIn(items.indices)
                                    if (to != from) {
                                        onMove(from, to)
                                        draggingIndex = to
                                        dragOffset -= (to - from) * stepPx
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.DragHandle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(2.dp),
                    )
                }
            }
        }
    }
}

private fun Modifier.zIndexIf(raised: Boolean): Modifier =
    if (raised) zIndex(1f) else this
