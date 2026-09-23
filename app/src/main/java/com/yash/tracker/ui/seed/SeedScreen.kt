package com.yash.tracker.ui.seed

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yash.tracker.data.local.seed.SeedProgress
import com.yash.tracker.ui.components.Eyebrow
import com.yash.tracker.ui.components.IconPlate
import com.yash.tracker.ui.components.PillButton
import com.yash.tracker.ui.components.PillTone
import com.yash.tracker.ui.components.ThinBar

/** The first thing anyone sees. It has one job: not to look like a stalled app. */
@Composable
fun SeedScreen(state: SeedProgress, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            is SeedProgress.Failed -> {
                IconPlate(Icons.Outlined.Restaurant, size = 64.dp, tone = PillTone.Solid)
                Spacer(Modifier.height(22.dp))
                Text(
                    "Couldn't load the food catalogue",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(26.dp))
                PillButton(
                    text = "Try again",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> {
                val working = state as? SeedProgress.Working
                val pulse = rememberInfiniteTransition(label = "seeding")
                val glow by pulse.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 0.55f,
                    animationSpec = infiniteRepeatable(
                        tween(1200, easing = FastOutSlowInEasing),
                        RepeatMode.Reverse,
                    ),
                    label = "seedGlow",
                )

                Box(contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(112.dp)
                            .graphicsLayer { alpha = glow }
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    IconPlate(Icons.Outlined.Restaurant, size = 68.dp, tone = PillTone.Solid)
                }

                Spacer(Modifier.height(26.dp))
                Eyebrow("Setting up")
                Spacer(Modifier.height(6.dp))
                Text(
                    working?.step ?: "Getting ready",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(26.dp))
                if (working != null && working.total > 0) {
                    ThinBar(
                        progress = working.done.toFloat() / working.total,
                        height = 6.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "%,d of %,d foods".format(working.done, working.total),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ThinBar(progress = 0.08f, height = 6.dp, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
