package com.yash.tracker.ui.theme

import androidx.compose.ui.graphics.Color

// "Precision Soft Organic": a warm off-white paper ground, pure-white cards, hairline outlines
// and one saturated blue held back for the things that are actually actionable. Taken verbatim
// from the design pack's token table so a re-skin stays a one-file change.

// --- light ------------------------------------------------------------------------------

val Primary = Color(0xFF2B5BF5)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFE9EFFF)
val OnPrimaryContainer = Color(0xFF112F9E)

val Secondary = Color(0xFF555F6D)
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFD9E3F4)
val OnSecondaryContainer = Color(0xFF3E4855)

val Tertiary = Color(0xFFD97706)
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFFFDCC3)
val OnTertiaryContainer = Color(0xFF6E3900)

/** Warm paper, not white: the cards are the white, and they only read as white against this. */
val Background = Color(0xFFFBF9F4)
val OnBackground = Color(0xFF1B1C19)
val Surface = Color(0xFFFBFBFA)
val OnSurface = Color(0xFF1C1D1A)
val SurfaceVariant = Color(0xFFE4E2DD)
val OnSurfaceVariant = Color(0xFF5F615A)

val SurfaceDim = Color(0xFFDBDAD5)
val SurfaceBright = Color(0xFFFBF9F4)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val SurfaceContainerLow = Color(0xFFF6F6F4)
val SurfaceContainerC = Color(0xFFEFEFE9)
val SurfaceContainerHigh = Color(0xFFE7E8E1)
val SurfaceContainerHighest = Color(0xFFDEDFD7)

/** A hairline, not a border. Anything heavier fights the shadows. */
val Outline = Color(0xFFE2E3DC)
val OutlineVariant = Color(0xFFEBECE6)

val InverseSurface = Color(0xFF30312D)
val InverseOnSurface = Color(0xFFF2F1EB)
val InversePrimary = Color(0xFFB8C4FF)

val ErrorLight = Color(0xFFDC2626)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF93000A)

// --- dark -------------------------------------------------------------------------------

val PrimaryDark = Color(0xFFAEC4FF)
val OnPrimaryDark = Color(0xFF04277A)
val PrimaryContainerDark = Color(0xFF1B44C4)
val OnPrimaryContainerDark = Color(0xFFDCE4FF)

val SecondaryDark = Color(0xFFBEC7D6)
val OnSecondaryDark = Color(0xFF283140)
val SecondaryContainerDark = Color(0xFF3E4856)
val OnSecondaryContainerDark = Color(0xFFD9E3F4)

val TertiaryDark = Color(0xFFF5B368)
val OnTertiaryDark = Color(0xFF472A00)
val TertiaryContainerDark = Color(0xFF6E3900)
val OnTertiaryContainerDark = Color(0xFFFFDCC3)

val BackgroundDark = Color(0xFF0F100D)
val OnBackgroundDark = Color(0xFFECEDE6)
val SurfaceDark = Color(0xFF0F100D)
val OnSurfaceDark = Color(0xFFECEDE6)
val SurfaceVariantDark = Color(0xFF45473F)
val OnSurfaceVariantDark = Color(0xFFAFB1A7)

/**
 * The container ladder is deliberately inverted from Material's dark defaults. In this design
 * a card *floats*: in light that means whiter than the page, so in dark it has to mean lighter
 * than the page. Reusing Material's ordering would sink every card into the background.
 */
val SurfaceDimDark = Color(0xFF0A0B08)
val SurfaceBrightDark = Color(0xFF383931)
val SurfaceContainerLowestDark = Color(0xFF1A1B16)
val SurfaceContainerLowDark = Color(0xFF20211B)
val SurfaceContainerDark = Color(0xFF272821)
val SurfaceContainerHighDark = Color(0xFF31322A)
val SurfaceContainerHighestDark = Color(0xFF3C3D34)

val OutlineDark = Color(0xFF44453C)
val OutlineVariantDark = Color(0xFF2E2F27)

val InverseSurfaceDark = Color(0xFFECEDE6)
val InverseOnSurfaceDark = Color(0xFF1C1D1A)
val InversePrimaryDark = Color(0xFF2B5BF5)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

/**
 * Material 3 has no "warning" slot, but the dashboard needs one: PRD §5.3 turns the calorie
 * ring amber past 100% of target and red only past 110%.
 */
val Warning = Color(0xFFB26A00)
val WarningDark = Color(0xFFF5B368)
