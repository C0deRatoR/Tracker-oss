package com.yash.tracker.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import com.yash.tracker.domain.model.EatingStyle
import com.yash.tracker.domain.model.Goal
import com.yash.tracker.domain.model.Plan
import com.yash.tracker.ui.components.DisclosureRow
import com.yash.tracker.ui.components.Eyebrow
import com.yash.tracker.ui.components.Hairline
import com.yash.tracker.ui.components.IconPlate
import com.yash.tracker.ui.components.LuxCard
import com.yash.tracker.ui.components.Metric
import com.yash.tracker.ui.components.Pill
import com.yash.tracker.ui.components.PillButton
import com.yash.tracker.ui.components.PillTone
import com.yash.tracker.ui.components.SmallAction
import com.yash.tracker.ui.components.SoftCard
import com.yash.tracker.ui.components.SplitBar
import com.yash.tracker.ui.components.StaggerIn
import com.yash.tracker.ui.components.ValueRow
import com.yash.tracker.ui.theme.MetricSmallStyle
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun PlanResultsScreen(
    plan: Plan?,
    answers: OnboardingAnswers,
    onStart: () -> Unit,
) {
    if (plan == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }
        return
    }

    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var methodologyOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))
            StaggerIn(0) {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("Calibrated targets")
                        Spacer(Modifier.weight(1f))
                        Pill("Ready", tone = PillTone.Accent, icon = Icons.Outlined.Verified)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your daily calibration",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Calculated with the Mifflin-St Jeor equation at a ×${plan.activityFactor} " +
                            "activity multiplier, ${answers.name.trim().ifBlank { "and" }} — not a lookup table.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            StaggerIn(1) { TargetCard(plan) }

            Spacer(Modifier.height(18.dp))
            StaggerIn(2) {
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Eyebrow("Macro allocation")
                        Spacer(Modifier.weight(1f))
                        Eyebrow("per day")
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MacroCard(
                            "Protein",
                            plan.proteinG,
                            share(plan.proteinG * 4, plan.kcal),
                            "%.1f g/kg".format(plan.proteinG / (answers.weightKg ?: 1.0)),
                            MaterialTheme.colorScheme.primary,
                            Modifier.weight(1f),
                        )
                        MacroCard(
                            "Carbs",
                            plan.carbsG,
                            share(plan.carbsG * 4, plan.kcal),
                            "energy",
                            MaterialTheme.colorScheme.onSurface,
                            Modifier.weight(1f),
                        )
                        MacroCard(
                            "Fat",
                            plan.fatG,
                            share(plan.fatG * 9, plan.kcal),
                            "hormonal",
                            MaterialTheme.colorScheme.outline,
                            Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            StaggerIn(3) {
                LuxCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconPlate(Icons.Outlined.WaterDrop, size = 46.dp, tone = PillTone.Accent)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Metric(
                                "%.1f".format(plan.waterMl / 1000.0),
                                unit = "litres / day",
                                large = false,
                            )
                            Text(
                                "Roughly 500 ml on waking, the rest spread across the day.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (plan.deficitCapped || plan.floorApplied) {
                Spacer(Modifier.height(14.dp))
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Eyebrow("Adjusted")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            when {
                                plan.floorApplied ->
                                    "Your target was raised to the minimum safe intake, so the " +
                                        "pace you picked won't fully apply."
                                else ->
                                    "Your deficit was capped so you don't lose more than about " +
                                        "1% of bodyweight a week."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            eatingStyleNote(answers.eatingStyle)?.let {
                Spacer(Modifier.height(14.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(18.dp))
            DisclosureRow(
                title = "Algorithmic transparency",
                expanded = methodologyOpen,
                onToggle = { methodologyOpen = !methodologyOpen },
                icon = Icons.Outlined.Science,
            )
            AnimatedVisibility(
                visible = methodologyOpen,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(Modifier.padding(top = 12.dp)) {
                    ValueRow("Resting burn (BMR)", "%,d kcal".format(plan.bmr))
                    ValueRow("Maintenance (TDEE)", "%,d kcal".format(plan.tdee))
                    ValueRow("Activity multiplier", "×${plan.activityFactor}")
                    plan.weeksToGoal?.let { weeks ->
                        ValueRow("Expected pace", "%.1f lb a week".format(plan.rateLbPerWeek ?: 0.0))
                        ValueRow("Time to goal", "about $weeks weeks")
                    }
                    Spacer(Modifier.height(6.dp))
                    Hairline()
                    Spacer(Modifier.height(10.dp))
                    ValueRow("Age", "${answers.age} years")
                    ValueRow("Height", "${answers.heightCm?.roundToInt() ?: 0} cm")
                    ValueRow("Weight", "%.1f kg".format(answers.weightKg ?: 0.0))
                    ValueRow("Goal", goalLabel(answers.goal))
                    answers.goalWeightKg?.takeIf { answers.goal != Goal.MAINTAIN }?.let {
                        ValueRow("Goal weight", "%.1f kg".format(it))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "The timeline assumes about 3,500 kcal per pound of bodyweight. Protein " +
                            "and fat targets come from common sports-nutrition guidelines. These " +
                            "are population-average estimates, not medical advice.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SmallAction(
                    text = "Copy plan",
                    icon = Icons.Outlined.ContentCopy,
                    onClick = {
                        scope.launch {
                            val clip = android.content.ClipData.newPlainText(
                                "My plan",
                                planText(plan, answers),
                            )
                            clipboard.setClipEntry(ClipEntry(clip))
                        }
                    },
                )
            }

            Spacer(Modifier.height(20.dp))
        }

        PillButton(
            text = "Accept plan & start",
            onClick = onStart,
            trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Everything here can be recalculated later from Settings.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun TargetCard(plan: Plan) {
    val scheme = MaterialTheme.colorScheme
    val deficit = plan.kcal - plan.tdee

    LuxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Target intake")
                Spacer(Modifier.weight(1f))
                Pill(
                    text = when {
                        deficit < 0 -> "Deficit"
                        deficit > 0 -> "Surplus"
                        else -> "Maintenance"
                    },
                    tone = PillTone.Quiet,
                    leadingDot = true,
                )
            }

            Spacer(Modifier.height(10.dp))
            Metric("%,d".format(plan.kcal), unit = "kcal / day")

            Spacer(Modifier.height(16.dp))
            SplitBar(
                parts = listOf(
                    (plan.proteinG * 4).toFloat() to scheme.primary,
                    (plan.carbsG * 4).toFloat() to scheme.onSurface,
                    (plan.fatG * 9).toFloat() to scheme.outline,
                ),
                height = 7.dp,
            )

            Spacer(Modifier.height(18.dp))
            SoftCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Eyebrow("BMR")
                        Spacer(Modifier.height(2.dp))
                        Text("%,d".format(plan.bmr), style = MaterialTheme.typography.titleMedium)
                    }
                    Divider()
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Eyebrow("Maintenance")
                        Spacer(Modifier.height(2.dp))
                        Text("%,d".format(plan.tdee), style = MaterialTheme.typography.titleMedium)
                    }
                    Divider()
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Eyebrow(
                            when {
                                deficit < 0 -> "Deficit"
                                deficit > 0 -> "Surplus"
                                else -> "Gap"
                            },
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (deficit == 0) "—" else "%+,d".format(deficit),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .width(1.dp)
            .height(26.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun MacroCard(
    label: String,
    grams: Double,
    share: Int,
    note: String,
    dot: Color,
    modifier: Modifier,
) {
    LuxCard(modifier, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dot),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${grams.roundToInt()}", style = MetricSmallStyle)
                Text(
                    "g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp, start = 2.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            SoftCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Text("$share%", style = MaterialTheme.typography.labelMedium)
                    Text(
                        note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun share(macroKcal: Double, totalKcal: Int): Int =
    if (totalKcal <= 0) 0 else ((macroKcal / totalKcal) * 100).roundToInt()

private fun goalLabel(goal: Goal?) = when (goal) {
    Goal.LOSE -> "Lose weight"
    Goal.GAIN -> "Gain weight"
    Goal.MAINTAIN -> "Maintain"
    null -> "—"
}

private fun eatingStyleNote(style: EatingStyle?) = when (style) {
    EatingStyle.KETO -> "Keto holds carbs at 30 g and lets fat make up the rest."
    EatingStyle.LOW_CARB -> "Low-carb caps carbs at 100 g, moving the balance into fat."
    EatingStyle.HIGH_PROTEIN -> "High-protein raises your protein target to 1 g per pound."
    EatingStyle.VEGETARIAN -> "Hitting protein on a vegetarian diet takes some planning — dal, paneer and curd do most of the work."
    EatingStyle.VEGAN -> "Hitting protein on a vegan diet takes some planning — dal, soya and nuts do most of the work."
    EatingStyle.NONE, null -> null
}

private fun planText(plan: Plan, answers: OnboardingAnswers): String {
    val goalLine = when (answers.goal) {
        Goal.MAINTAIN -> "Maintain"
        else -> "${goalLabel(answers.goal)} (from ${"%.1f".format(answers.weightKg ?: 0.0)} kg " +
            "toward ${"%.1f".format(answers.goalWeightKg ?: 0.0)} kg)"
    }
    return buildString {
        appendLine("MY PLAN (calculated ${LocalDate.now()}):")
        appendLine("- Goal: $goalLine")
        appendLine("- Daily calories: ${plan.kcal}")
        append("- Protein: ${plan.proteinG.roundToInt()}g")
        append("  | Carbs: ${plan.carbsG.roundToInt()}g")
        append("  | Fat: ${plan.fatG.roundToInt()}g")
        append("  | Water: ${"%.1f".format(plan.waterMl / 1000.0)}L")
    }
}
