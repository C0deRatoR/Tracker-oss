package com.yash.tracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.yash.tracker.R

/**
 * Geist, bundled rather than downloaded: the type scale is half of what makes this design look
 * like an instrument instead of a form, and a font that arrives one frame late is worse than
 * no font at all. SIL OFL 1.1.
 */
val Geist = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
)

/**
 * Every figure in this app sits in a column that changes while you watch it — a ring counting
 * down, a rest timer, a set's weight. Proportional digits make those columns twitch.
 */
private const val TABULAR = "tnum"

private fun geist(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = Geist,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em,
    fontFeatureSettings = TABULAR,
)

val TrackerTypography = Typography(
    displayLarge = geist(48, 56, FontWeight.SemiBold, -0.03f),
    displayMedium = geist(40, 48, FontWeight.SemiBold, -0.03f),
    displaySmall = geist(32, 40, FontWeight.SemiBold, -0.02f),

    headlineLarge = geist(32, 40, FontWeight.SemiBold, -0.02f),
    headlineMedium = geist(24, 32, FontWeight.Medium, -0.01f),
    headlineSmall = geist(20, 28, FontWeight.Medium),

    titleLarge = geist(20, 28, FontWeight.SemiBold, -0.01f),
    titleMedium = geist(16, 24, FontWeight.SemiBold),
    titleSmall = geist(14, 20, FontWeight.SemiBold),

    bodyLarge = geist(16, 24),
    bodyMedium = geist(14, 20),
    bodySmall = geist(12, 16, tracking = 0.01f),

    labelLarge = geist(14, 20, FontWeight.Medium, 0.01f),
    labelMedium = geist(13, 16, FontWeight.Medium, 0.01f),
    labelSmall = geist(11, 14, FontWeight.Medium, 0.02f),
)

/**
 * The all-caps, widely tracked micro-label that sits above nearly every block in the design
 * ("ENERGY BALANCE", "TIMELINE", "STEP 2 OF 4"). Not a Material slot, so it lives here.
 */
val EyebrowStyle = geist(11, 14, FontWeight.Medium, 0.12f)

/** The oversized readout at the centre of a card: 1,420 kcal, 74.2 kg, 01:42. */
val MetricStyle = geist(32, 38, FontWeight.SemiBold, -0.02f)

/** Same shape, one step down, for the metrics that share a row with two others. */
val MetricSmallStyle = geist(22, 28, FontWeight.SemiBold, -0.02f)
