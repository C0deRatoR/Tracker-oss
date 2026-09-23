package com.yash.tracker.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MedicalInformation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.tracker.domain.model.ActivityLevel
import com.yash.tracker.domain.model.EatingStyle
import com.yash.tracker.domain.model.Goal
import com.yash.tracker.domain.model.HeightUnit
import com.yash.tracker.domain.model.Pace
import com.yash.tracker.domain.model.Sex
import com.yash.tracker.domain.model.WeightUnit
import com.yash.tracker.ui.components.ActionTone
import com.yash.tracker.ui.components.ChoiceCard
import com.yash.tracker.ui.components.Eyebrow
import com.yash.tracker.ui.components.IconPlate
import com.yash.tracker.ui.components.LuxTextField
import com.yash.tracker.ui.components.Pill
import com.yash.tracker.ui.components.PillButton
import com.yash.tracker.ui.components.PillTone
import com.yash.tracker.ui.components.SegmentedToggle
import com.yash.tracker.ui.components.SelectableChip
import com.yash.tracker.ui.components.SoftCard
import com.yash.tracker.ui.components.StaggerIn
import com.yash.tracker.ui.components.TextAction
import com.yash.tracker.ui.components.ThinBar
import com.yash.tracker.ui.theme.Decelerate
import kotlinx.coroutines.delay

private const val AUTO_ADVANCE_MS = 130L

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.step == OnboardingStep.RESULTS) {
        PlanResultsScreen(
            plan = state.plan,
            answers = state.answers,
            onStart = onFinished,
        )
        return
    }

    // A choice question advances on its own so the flow keeps moving without a Next tap.
    LaunchedEffect(state.step, state.answers) {
        if (state.step.isChoice && Onboarding.validate(state.step, state.answers) == null) {
            delay(AUTO_ADVANCE_MS)
            viewModel.next()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
    ) {
        if (state.step != OnboardingStep.WELCOME) {
            val (index, total) = Onboarding.progress(state.step, state.answers)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextAction("Back", onClick = viewModel::back, enabled = state.step > OnboardingStep.NAME)
                Spacer(Modifier.weight(1f))
                Eyebrow("Step $index of $total")
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }
            Spacer(Modifier.height(12.dp))
            ThinBar(
                progress = index.toFloat() / total,
                height = 4.dp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Each question slides in from the side it came from, so the flow has a direction.
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val forward = targetState.ordinal >= initialState.ordinal
                val width = if (forward) 1 else -1
                (
                    slideInHorizontally(tween(360, easing = Decelerate)) { it / 6 * width } +
                        fadeIn(tween(240))
                    ) togetherWith (
                    slideOutHorizontally(tween(220)) { -it / 8 * width } + fadeOut(tween(160))
                    )
            },
            modifier = Modifier.weight(1f),
            label = "onboardingStep",
        ) { step ->
            // Top-aligned rather than centred: centring makes a two-line question sit low and a
            // five-option one sit high, so the heading jumps around as you move through the flow.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 36.dp, bottom = 24.dp),
            ) {
                when (step) {
                    OnboardingStep.WELCOME -> Welcome()
                    OnboardingStep.NAME -> NameQuestion(state, viewModel)
                    OnboardingStep.AGE -> AgeQuestion(state, viewModel)
                    OnboardingStep.SEX -> SexQuestion(state, viewModel)
                    OnboardingStep.HEIGHT -> HeightQuestion(state, viewModel)
                    OnboardingStep.WEIGHT -> WeightQuestion(state, viewModel)
                    OnboardingStep.GOAL -> GoalQuestion(state, viewModel)
                    OnboardingStep.GOAL_WEIGHT -> GoalWeightQuestion(state, viewModel)
                    OnboardingStep.ACTIVITY -> ActivityQuestion(state, viewModel)
                    OnboardingStep.PACE -> PaceQuestion(state, viewModel)
                    OnboardingStep.EATING_STYLE -> EatingStyleQuestion(state, viewModel)
                    OnboardingStep.NOTES -> NotesQuestion(state, viewModel)
                    OnboardingStep.RESULTS -> Unit
                }

                state.error?.let {
                    Spacer(Modifier.height(12.dp))
                    Pill(it, tone = PillTone.Danger)
                }
            }
        }

        // Choice steps advance themselves, so showing Next there would be a dead control.
        if (!state.step.isChoice) {
            PillButton(
                text = if (state.step == OnboardingStep.WELCOME) "Begin setup" else "Next",
                onClick = viewModel::next,
                trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward,
                tone = if (state.step == OnboardingStep.WELCOME) ActionTone.Accent else ActionTone.Ink,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

// --- welcome --------------------------------------------------------------------------------

@Composable
private fun Welcome() {
    StaggerIn(0) {
        Column {
            Eyebrow("Personal protocol")
            Spacer(Modifier.height(6.dp))
            Text("Let's get your plan dialled in.", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(10.dp))
            Text(
                "A few quick questions, then a calorie and macro target calculated from your " +
                    "own numbers rather than a lookup table.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(22.dp))
    StaggerIn(1) {
        SoftCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconPlate(Icons.Outlined.MedicalInformation, size = 40.dp, tone = PillTone.Solid)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Not medical advice",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Eyebrow("Read this first")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "I'm an AI assistant, not a medical professional. Check with your primary " +
                        "care physician before starting any new diet, or making changes for a " +
                        "medical or dietary condition.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Spacer(Modifier.height(24.dp))
    StaggerIn(2) {
        Column {
            Eyebrow("What this does")
            Spacer(Modifier.height(10.dp))
            Capability(
                Icons.Outlined.CameraAlt,
                "Photograph a plate",
                "A photo or a sentence becomes a draft you check before anything is saved.",
            )
            Spacer(Modifier.height(8.dp))
            Capability(
                Icons.Outlined.FitnessCenter,
                "Log sets and tonnage",
                "Rows arrive prefilled from last time, with the rest timer starting itself.",
            )
            Spacer(Modifier.height(8.dp))
            Capability(
                Icons.Outlined.Lock,
                "Stays on this phone",
                "There is no account and no cloud copy. Backups are encrypted with six words.",
            )
        }
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun Capability(icon: ImageVector, title: String, description: String) {
    SoftCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconPlate(icon, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// --- question chrome ------------------------------------------------------------------------

@Composable
private fun QuestionTitle(text: String, helper: String? = null, eyebrow: String? = null) {
    if (eyebrow != null) {
        Eyebrow(eyebrow)
        Spacer(Modifier.height(6.dp))
    }
    Text(text, style = MaterialTheme.typography.headlineLarge)
    if (helper != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            helper,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun <T> ChoiceList(
    options: List<Triple<T, String, String?>>,
    selected: T?,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        options.forEach { (value, label, description) ->
            ChoiceCard(
                title = label,
                description = description,
                selected = value == selected,
                onClick = { onSelect(value) },
            )
        }
    }
}

// --- questions ------------------------------------------------------------------------------

@Composable
private fun NameQuestion(state: OnboardingUiState, vm: OnboardingViewModel) {
    QuestionTitle("First, what's your name?", eyebrow = "Identity")
    LuxTextField(
        value = state.answers.name,
        onValueChange = { value -> vm.update { it.copy(name = value) } },
        label = "First name",
        isError = state.error != null,
    )
}

@Composable
private fun AgeQuestion(state: OnboardingUiState, vm: OnboardingViewModel) {
    QuestionTitle("How old are you?", eyebrow = "Biometrics")
    LuxTextField(
        value = state.answers.age,
        onValueChange = { value -> vm.update { it.copy(age = value.filter(Char::isDigit)) } },
        label = "Age",
        suffix = "years",
        isError = state.error != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

@Composable
private fun SexQuestion(state: OnboardingUiState, vm: OnboardingViewModel) {
    QuestionTitle(
        "What's your biological sex?",
        "Needed for the metabolic maths, not identity.",
        eyebrow = "Biometrics",
    )
    ChoiceList(
        options = listOf(
            Triple(Sex.MALE, "Male", null),
            Triple(Sex.FEMALE, "Female", null),
        ),
        selected = state.answers.sex,
        onSelect = { value -> vm.update { it.copy(sex = value) } },
    )
}

@Composable
private fun HeightQuestion(state: OnboardingUiState, vm: OnboardingViewModel) {
    val answers = state.answers
    QuestionTitle("How tall are you?", eyebrow = "Biometrics")

    SegmentedToggle(
        options = listOf("cm", "ft / in"),
        selectedIndex = if (answers.heightUnit == HeightUnit.CM) 0 else 1,
        onSelect = { index ->
            vm.update {
                it.copy(heightUnit = if (index == 0) HeightUnit.CM else HeightUnit.FT_IN)
            }
        },
        modifier = Modifier.width(180.dp),
    )
    Spacer(Modifier.height(16.dp))

    if (answers.heightUnit == HeightUnit.CM) {
        LuxTextField(
            value = answers.heightCmText,
            onValueChange = { v ->
                vm.update { it.copy(heightCmText = v.filter { c -> c.isDigit() || c == '.' }) }
            },
            label = "Height",
            suffix = "cm",
            isError = state.error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LuxTextField(
                value = answers.heightFeetText,
                onValueChange = { v -> vm.update { it.copy(heightFeetText = v.filter(Char::isDigit)) } },
                label = "Feet",
                isError = state.error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            LuxTextField(
                value = answers.heightInchesText,
                onValueChange = { v -> vm.update { it.copy(heightInchesText = v.filter(Char::isDigit)) } },
                label = "Inches",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeightField(
    label: String,
    value: String,
    unit: WeightUnit,
    isError: Boolean,
    onValueChange: (String) -> Unit,
) {
    LuxTextField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c.isDigit() || c == '.' }) },
        label = label,
        suffix = if (unit == WeightUnit.KG) "kg" else "lb",
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun WeightQuestion(state: OnboardingUiState, vm: OnboardingViewModel) {
    val answers = state.answers
    QuestionTitle("What do you weigh right now?", eyebrow = "Biometrics")

    SegmentedToggle(
        options = listOf("kg", "lb"),
        selectedIndex = if (answers.weightUnit == WeightUnit.KG) 0 else 1,
        onSelect = { index ->
            vm.update { it.copy(weightUnit = if (index == 0) WeightUnit.KG else WeightUnit.LB) }
        },
        modifier = Modifier.width(150.dp),
    )
    Spacer(Modifier.height(16.dp))

    WeightField(
        label = "Current weight",
        value = answers.weightText,
        unit = answers.weightUnit,
        isError = state.error != null,
    ) { value -> vm.update { it.copy(weightText = value) } }
}

@Composable
private fun GoalQuestion(state: OnboardingUiState, vm: OnboardingViewModel) {
    QuestionTitle("What are you aiming for?", eyebrow = "Primary objective")
    ChoiceList(
        options = listOf(
            Triple(Goal.LOSE, "Lose weight", "A steady deficit, capped so it stays survivable"),
            Triple(Goal.MAINTAIN, "Maintain my weight", "Hold the line at maintenance"),
            Triple(Goal.GAIN, "Gain weight", "A surplus sized for mass, not for fat"),
        ),
        selected = state.answers.goal,
        onSelect = { value -> vm.update { it.copy(goal = value) } },
    )
}

@Composable
private fun GoalWeightQuestion(state: OnboardingUiState, vm: OnboardingViewModel) {
    QuestionTitle("What weight are you heading for?", eyebrow = "Target")
    WeightField(
        label = "Goal weight",
        value = state.answers.goalWeightText,
        unit = state.answers.weightUnit,
        isError = state.error != null,
    ) { value -> vm.update { it.copy(goalWeightText = value) } }
}

@Composable
private fun ActivityQuestion(state: OnboardingUiState, vm: OnboardingViewModel) {
    QuestionTitle("How active are you day to day?", eyebrow = "Training frequency")
    ChoiceList(
        options = listOf(
            Triple(ActivityLevel.SEDENTARY, "Sedentary", "Desk job, little or no exercise"),
            Triple(ActivityLevel.LIGHTLY, "Lightly active", "Light exercise 1–3 days a week"),
            Triple(ActivityLevel.MODERATELY, "Moderately active", "Moderate exercise 3–5 days a week"),
            Triple(ActivityLevel.VERY, "Very active", "Hard exercise 6–7 days a week"),
            Triple(ActivityLevel.ATHLETE, "Athlete", "Hard daily training, or a physical job"),
        ),
        selected = state.answers.activity,
        onSelect = { value -> vm.update { it.copy(activity = value) } },
    )
}

@Composable
private fun PaceQuestion(state: OnboardingUiState, vm: OnboardingViewModel) {
    QuestionTitle(
        "How fast do you want to get there?",
        "A faster pace means a bigger daily gap, which is harder to hold to.",
        eyebrow = "Pace",
    )
    ChoiceList(
        options = listOf(
            Triple(Pace.GENTLE, "Gentle", "Slower, and the easiest to stick with"),
            Triple(Pace.STEADY, "Steady", "A balanced middle ground"),
            Triple(Pace.AGGRESSIVE, "Aggressive", "Fastest, but hardest to sustain"),
        ),
        selected = state.answers.pace,
        onSelect = { value -> vm.update { it.copy(pace = value) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EatingStyleQuestion(state: OnboardingUiState, vm: OnboardingViewModel) {
    QuestionTitle("How do you like to eat?", eyebrow = "Diet shape")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf(
            EatingStyle.NONE to "No preference",
            EatingStyle.HIGH_PROTEIN to "High-protein",
            EatingStyle.LOW_CARB to "Low-carb",
            EatingStyle.KETO to "Keto",
            EatingStyle.VEGETARIAN to "Vegetarian",
            EatingStyle.VEGAN to "Vegan",
        ).forEach { (value, label) ->
            SelectableChip(
                label = label,
                selected = state.answers.eatingStyle == value,
                onClick = { vm.update { it.copy(eatingStyle = value) } },
            )
        }
    }
}

@Composable
private fun NotesQuestion(state: OnboardingUiState, vm: OnboardingViewModel) {
    QuestionTitle(
        "Anything I should know?",
        "Allergies, foods you avoid, health notes. Optional — skip it if nothing comes to mind.",
        eyebrow = "Notes",
    )
    LuxTextField(
        value = state.answers.notes,
        onValueChange = { value -> vm.update { it.copy(notes = value) } },
        placeholder = "Notes",
        singleLine = false,
        minLines = 4,
        shape = MaterialTheme.shapes.large,
    )
}
