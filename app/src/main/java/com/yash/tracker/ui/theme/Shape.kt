package com.yash.tracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * The design pack's `roundness: 24px` with its Tailwind ladder either side of it. Cards are
 * `large`, the tiles and icon plates inside them are `medium`, and anything smaller than that
 * is a control rather than a surface.
 */
val TrackerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
