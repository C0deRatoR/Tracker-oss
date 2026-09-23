package com.yash.tracker.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.tracker.BuildConfig
import com.yash.tracker.data.local.entity.TargetEntity
import com.yash.tracker.data.prefs.ThemeMode
import com.yash.tracker.domain.model.WeightUnit
import com.yash.tracker.domain.model.Plan
import com.yash.tracker.ui.backup.BackupSection
import com.yash.tracker.ui.components.ActionTone
import com.yash.tracker.ui.components.EmptyNote
import com.yash.tracker.ui.components.Eyebrow
import com.yash.tracker.ui.components.Hairline
import com.yash.tracker.ui.components.LuxCard
import com.yash.tracker.ui.components.LuxTextField
import com.yash.tracker.ui.components.LuxTopBar
import com.yash.tracker.ui.components.Pill
import com.yash.tracker.ui.components.PillButton
import com.yash.tracker.ui.components.PillTone
import com.yash.tracker.ui.components.SegmentedToggle
import com.yash.tracker.ui.components.SmallAction
import com.yash.tracker.ui.components.SoftCard
import com.yash.tracker.ui.components.StaggerIn
import com.yash.tracker.ui.components.TextAction
import com.yash.tracker.ui.components.ValueRow
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val planMessage by viewModel.planMessage.collectAsStateWithLifecycle()
    val corrections by viewModel.corrections.collectAsStateWithLifecycle()
    val suggested by viewModel.suggested.collectAsStateWithLifecycle()


    var editingTargets by remember { mutableStateOf(false) }

    if (editingTargets) {
        state.target?.let { target ->
            TargetEditor(
                target = target,
                suggested = suggested,
                onDismiss = { editingTargets = false },
                onSave = { kcal, protein, carbs, fat, water ->
                    viewModel.saveTargets(kcal, protein, carbs, fat, water)
                    editingTargets = false
                },
                onUseSuggested = {
                    viewModel.useSuggestedPlan()
                    editingTargets = false
                },
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        LuxTopBar(eyebrow = "System", title = "Settings")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 40.dp),
        ) {
            // --- plan ---------------------------------------------------------------------
            StaggerIn(0) {
                SettingsCard("Your plan") {
                    state.target?.let { target ->
                        ValueRow("Daily calories", "%,d kcal".format(target.kcal))
                        ValueRow("Protein", "${target.proteinG.roundToInt()} g")
                        ValueRow("Carbs", "${target.carbsG.roundToInt()} g")
                        ValueRow("Fat", "${target.fatG.roundToInt()} g")
                        ValueRow("Water", "%.1f L".format(target.waterMl / 1000.0))
                    } ?: EmptyNote("No plan yet.")

                    state.profile?.let { profile ->
                        Spacer(Modifier.height(6.dp))
                        Hairline()
                        Spacer(Modifier.height(6.dp))
                        ValueRow("Name", profile.name)
                        ValueRow("Goal", profile.goal.lowercase().replaceFirstChar(Char::uppercase))
                        ValueRow(
                            "Activity",
                            profile.activity.lowercase().replaceFirstChar(Char::uppercase),
                        )
                    }

                    if (state.target?.isManualOverride == true) {
                        Spacer(Modifier.height(10.dp))
                        Pill("Set by you", tone = PillTone.Accent)
                    }

                    Spacer(Modifier.height(14.dp))
                    SmallAction(
                        text = "Edit my targets",
                        icon = Icons.Outlined.Tune,
                        onClick = {
                            viewModel.loadSuggestedPlan()
                            editingTargets = true
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    SmallAction(
                        text = "Recalculate from my latest weight",
                        icon = Icons.Outlined.Refresh,
                        onClick = viewModel::recalculatePlan,
                    )
                    planMessage?.let { Notice(it) }
                }
            }

            // --- gemini -------------------------------------------------------------------
            Spacer(Modifier.height(14.dp))
            StaggerIn(1) {
                SettingsCard(
                    title = "Gemini",
                    subtitle = "Photo logging runs on a key the app provides. Nothing to set up.",
                ) {
                    Spacer(Modifier.height(16.dp))
                    Text("Search grounding", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Lets \"look it up\" put a web search behind a reading. More accurate " +
                            "on branded food, and more expensive on every call that uses it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    SegmentedToggle(
                        options = listOf("Off", "On"),
                        selectedIndex = if (state.groundingEnabled) 1 else 0,
                        onSelect = { viewModel.setGroundingEnabled(it == 1) },
                        modifier = Modifier.fillMaxWidth(),
                        height = 44.dp,
                    )
                }
            }

            // --- appearance ---------------------------------------------------------------
            Spacer(Modifier.height(14.dp))
            StaggerIn(2) {
                SettingsCard("Appearance") {
                    Spacer(Modifier.height(12.dp))
                    SegmentedToggle(
                        options = ThemeMode.entries.map { it.label() },
                        selectedIndex = ThemeMode.entries.indexOf(state.themeMode),
                        onSelect = { viewModel.setThemeMode(ThemeMode.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                        height = 44.dp,
                    )

                    Spacer(Modifier.height(18.dp))
                    Eyebrow("Weight unit")
                    Spacer(Modifier.height(8.dp))
                    SegmentedToggle(
                        options = WeightUnit.entries.map { it.name.lowercase() },
                        selectedIndex = WeightUnit.entries.indexOf(state.weightUnit),
                        onSelect = { viewModel.setWeightUnit(WeightUnit.entries[it]) },
                        modifier = Modifier.width(160.dp),
                    )
                }
            }

            // --- backup -------------------------------------------------------------------
            Spacer(Modifier.height(14.dp))
            StaggerIn(3) {
                SettingsCard("Backup") {
                    Spacer(Modifier.height(10.dp))
                    BackupSection()
                }
            }

            // --- corrections --------------------------------------------------------------
            Spacer(Modifier.height(14.dp))
            StaggerIn(4) {
                SettingsCard(
                    title = "What I've learned",
                    badge = if (corrections.isEmpty()) null else "${corrections.size}",
                ) {
                    if (corrections.isEmpty()) {
                        EmptyNote(
                            "When you rename something on the confirm screen, I remember it and " +
                                "stop making that mistake. Nothing learned yet.",
                        )
                    } else {
                        Spacer(Modifier.height(6.dp))
                        corrections.forEachIndexed { index, correction ->
                            if (index > 0) Hairline()
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${correction.aiName} → ${correction.correctedName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    if (correction.hitCount > 1) {
                                        Text(
                                            "corrected ${correction.hitCount}×",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                TextAction(
                                    "Forget",
                                    onClick = { viewModel.forgetCorrection(correction.id) },
                                )
                            }
                        }
                    }
                }
            }

            // --- sources ------------------------------------------------------------------
            Spacer(Modifier.height(14.dp))
            StaggerIn(5) {
                SoftCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Eyebrow("Sources")
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Indian dishes: Indian Nutrient Databank (Vijayakumar, Dubasi, " +
                                "Awasthi & Jaacks, Curr Dev Nutr 2024), CC BY 4.0.\n\n" +
                                "Other foods: USDA FoodData Central, public domain.\n\n" +
                                "Exercises, including their instructions and preview images: " +
                                "read from Strong (strong.app) and used here only on this " +
                                "phone — that artwork and text is Strong's, not ours. " +
                                "MET values from the Compendium of Physical Activities.\n\n" +
                                "Typeface: Geist by Vercel in collaboration with " +
                                "basement.studio, SIL Open Font License 1.1. The full licence " +
                                "is bundled with the app at assets/licences/geist-OFL.txt.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // --- about ----------------------------------------------------------------------
            Spacer(Modifier.height(14.dp))
            StaggerIn(6) {
                SettingsCard("About") {
                    ValueRow("Version", BuildConfig.VERSION_NAME)
                    ValueRow("Build", BuildConfig.VERSION_CODE.toString())
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String? = null,
    badge: String? = null,
    content: @Composable () -> Unit,
) {
    LuxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (badge != null) Pill(badge, tone = PillTone.Quiet)
            }
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            content()
        }
    }
}

@Composable
private fun Notice(text: String, success: Boolean = true) {
    Spacer(Modifier.height(12.dp))
    SoftCard(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (success) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (success) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onErrorContainer
            },
            modifier = Modifier.padding(12.dp),
        )
    }
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
    ThemeMode.SYSTEM -> "System"
}

/**
 * Setting the daily targets by hand.
 *
 * The calculator's plan is a good default and a bad rule: someone training for something, or
 * told a number by a doctor, or simply not hungry on 2,100 kcal, knows things the formula does
 * not. What matters is that the suggestion stays visible and one tap away, so a hand-set target
 * is a choice rather than a door closing behind you.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetEditor(
    target: TargetEntity,
    suggested: Plan?,
    onDismiss: () -> Unit,
    onSave: (Int, Double, Double, Double, Int) -> Unit,
    onUseSuggested: () -> Unit,
) {
    var kcal by remember { mutableStateOf(target.kcal.toString()) }
    var protein by remember { mutableStateOf(target.proteinG.roundToInt().toString()) }
    var carbs by remember { mutableStateOf(target.carbsG.roundToInt().toString()) }
    var fat by remember { mutableStateOf(target.fatG.roundToInt().toString()) }
    var water by remember { mutableStateOf(target.waterMl.toString()) }

    val valid = kcal.toIntOrNull()?.let { it > 0 } == true &&
        water.toIntOrNull()?.let { it > 0 } == true &&
        listOf(protein, carbs, fat).all { it.toDoubleOrNull() != null }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Eyebrow("Daily targets")
                Spacer(Modifier.height(4.dp))
                Text("Your numbers", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "These drive the rings on Today and nothing else. Changing them does not " +
                        "change what you have already logged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
            }

            item { NumberField(kcal, "Daily calories (kcal)") { kcal = it } }
            item { NumberField(protein, "Protein (g)") { protein = it } }
            item { NumberField(carbs, "Carbs (g)") { carbs = it } }
            item { NumberField(fat, "Fat (g)") { fat = it } }
            item { NumberField(water, "Water (ml)") { water = it } }

            suggested?.let { plan ->
                item {
                    Spacer(Modifier.height(8.dp))
                    SoftCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Eyebrow("What the calculator suggests")
                            Spacer(Modifier.height(8.dp))
                            ValueRow("Daily calories", "%,d kcal".format(plan.kcal))
                            ValueRow("Protein", "${plan.proteinG.roundToInt()} g")
                            ValueRow("Carbs", "${plan.carbsG.roundToInt()} g")
                            ValueRow("Fat", "${plan.fatG.roundToInt()} g")
                            ValueRow("Water", "%.1f L".format(plan.waterMl / 1000.0))

                            Spacer(Modifier.height(10.dp))
                            SmallAction(
                                text = "Use these instead",
                                icon = Icons.Outlined.Refresh,
                                onClick = onUseSuggested,
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                PillButton(
                    text = "Save targets",
                    onClick = {
                        onSave(
                            kcal.toInt(),
                            protein.toDouble(),
                            carbs.toDouble(),
                            fat.toDouble(),
                            water.toInt(),
                        )
                    },
                    enabled = valid,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextAction("Cancel", onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun NumberField(value: String, label: String, onChange: (String) -> Unit) {
    LuxTextField(
        value = value,
        onValueChange = { raw -> onChange(raw.filter { it.isDigit() || it == '.' }) },
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}
