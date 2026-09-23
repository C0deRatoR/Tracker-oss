package com.yash.tracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yash.tracker.ui.theme.springClick

/**
 * The frame: the bar at the top of every tab, the bar at the top of every pushed screen, and
 * the tab bar itself. Kept here so no screen has to decide what a header looks like.
 */

/** Eyebrow over title, with controls on the right. The header of a top-level tab. */
@Composable
fun LuxTopBar(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    onProfile: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (eyebrow != null) {
                    Eyebrow(eyebrow)
                    Spacer(Modifier.height(1.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            actions?.invoke(this)
            if (onProfile != null) {
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .springClick(pressedScale = 0.9f, role = Role.Button, onClick = onProfile),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Person,
                        contentDescription = "Profile",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Hairline()
    }
}

/** Back arrow, centred title. The header of anything that was pushed onto the stack. */
@Composable
fun DetailTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    divider: Boolean = true,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (actions != null) {
                actions()
            } else {
                Spacer(Modifier.width(40.dp))
            }
        }
        if (divider) Hairline()
    }
}

data class NavTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

/**
 * The tab bar. The selection is a single pill that travels between slots — one moving object
 * rather than five that light up and go dark.
 */
@Composable
fun LuxNavBar(
    tabs: List<NavTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Hairline()
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .height(52.dp),
        ) {
            val slot = maxWidth / tabs.size
            val indicatorOffset by animateDpAsState(
                targetValue = slot * selectedIndex.coerceIn(0, tabs.lastIndex),
                animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
                label = "tabIndicator",
            )

            Box(
                Modifier
                    .offset(x = indicatorOffset + 4.dp)
                    .width(slot - 8.dp)
                    .height(52.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.inverseSurface),
            )

            // Five slots across a phone cannot absorb an unbounded font scale, and a label
            // that overruns its slot collides with its neighbours.
            CappedFontScale(max = 1.2f) {
                Row(Modifier.fillMaxWidth()) {
                    tabs.forEachIndexed { index, tab ->
                        NavSlot(
                            tab = tab,
                            selected = index == selectedIndex,
                            modifier = Modifier.width(slot),
                            onClick = { onSelect(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavSlot(
    tab: NavTab,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colour by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.inverseOnSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "tabColour",
    )
    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "tabLift",
    )

    Column(
        modifier = modifier
            .height(52.dp)
            .clip(MaterialTheme.shapes.medium)
            .springClick(pressedScale = 0.92f, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (selected) tab.selectedIcon else tab.icon,
            contentDescription = tab.label,
            tint = colour,
            modifier = Modifier
                .size(21.dp)
                .graphicsLayer {
                    val scale = 1f + lift * 0.08f
                    scaleX = scale
                    scaleY = scale
                    translationY = -lift * 1.dp.toPx()
                },
        )
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = colour,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
        )
    }
}
