package com.yash.tracker.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Caps how far the system font scale can push text inside a fixed-size object.
 *
 * Most of this app should scale all the way — a diary row can get taller, a card can grow. Two
 * things cannot: a gauge drawn at a fixed diameter, and a tab bar with five slots across a
 * phone. At 1.5× the ring's readout spilled past the arc and the tab labels ran into each
 * other, which is worse for the person who turned the setting up than not scaling at all.
 *
 * The cap is a ceiling, not a replacement: below it the text still grows normally.
 */
@Composable
fun CappedFontScale(max: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    if (density.fontScale <= max) {
        content()
        return
    }

    CompositionLocalProvider(
        LocalDensity provides Density(density.density, max),
        content = content,
    )
}
