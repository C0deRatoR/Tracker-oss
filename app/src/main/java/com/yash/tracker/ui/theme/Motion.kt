package com.yash.tracker.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset

/**
 * One motion vocabulary for the whole app, so a card, a ring and a screen transition all decay
 * the same way. Everything here is critically-or-over damped: nothing in a measuring instrument
 * should wobble.
 */
object Motion {

    /** Tap feedback. Fast enough to feel like the surface, not an animation of the surface. */
    val press: AnimationSpec<Float> = spring(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessHigh,
    )

    /** A value settling into place: a ring filling, a bar growing, a thumb sliding. */
    val settle: AnimationSpec<Float> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Layout that has to get out of the way, or arrive, without drawing attention. */
    val enter: FiniteAnimationSpec<Float> = tween(360, easing = Decelerate)

    val exit: FiniteAnimationSpec<Float> = tween(220, easing = Accelerate)

    val offset: FiniteAnimationSpec<IntOffset> = tween(380, easing = Decelerate)

    /** Content that swaps in place — a step, a tab body, a state machine's next screen. */
    val content: FiniteAnimationSpec<Float> = tween(300, easing = Standard)

    /** Each item in a freshly drawn list waits this much longer than the one above it. */
    const val STAGGER_MS = 38

    /** Capped so a long list does not end with a row arriving a second and a half late. */
    const val STAGGER_MAX = 8
}

/** Expo-out: leaves fast, lands slowly. The house easing for anything arriving. */
val Decelerate = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Its mirror, for anything leaving. */
val Accelerate = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)

val Standard = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/**
 * A tap that presses the surface in rather than washing a ripple over it. The ripple is still
 * available for list rows, where the pressed thing is a region rather than an object.
 */
@Composable
fun Modifier.springClick(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    ripple: Boolean = false,
    role: Role? = null,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = Motion.press,
        label = "springClick",
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = if (ripple) LocalIndication.current else null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}
