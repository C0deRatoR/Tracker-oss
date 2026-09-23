package com.yash.tracker.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.tracker.data.local.dao.EntryWithItems
import com.yash.tracker.data.local.dao.MealWithItems
import com.yash.tracker.data.local.entity.WorkoutSessionEntity
import com.yash.tracker.domain.diary.DiaryDate
import com.yash.tracker.domain.nutrition.Macros
import com.yash.tracker.domain.nutrition.MealReviewer
import com.yash.tracker.domain.nutrition.Micros
import com.yash.tracker.ui.backup.BackupReminderCard
import com.yash.tracker.ui.backup.BackupReminderViewModel
import com.yash.tracker.ui.components.ActionTone
import com.yash.tracker.ui.components.AnimatedCount
import com.yash.tracker.ui.components.CircleIconButton
import com.yash.tracker.ui.components.EmptyNote
import com.yash.tracker.ui.components.EnergyRing
import com.yash.tracker.ui.components.Eyebrow
import com.yash.tracker.ui.components.Hairline
import com.yash.tracker.ui.components.GroupHeader
import com.yash.tracker.ui.components.IconPlate
import com.yash.tracker.ui.components.LuxCard
import com.yash.tracker.ui.components.LuxTextField
import com.yash.tracker.ui.components.LuxTopBar
import com.yash.tracker.ui.components.MacroTile
import com.yash.tracker.ui.components.MealReviewCard
import com.yash.tracker.ui.components.PhotoThumb
import com.yash.tracker.ui.components.Pill
import com.yash.tracker.ui.components.PillButton
import com.yash.tracker.ui.components.PillTone
import com.yash.tracker.ui.components.SmallAction
import com.yash.tracker.ui.components.SoftCard
import com.yash.tracker.ui.components.StaggerIn
import com.yash.tracker.ui.components.TextAction
import com.yash.tracker.ui.components.ThinBar
import com.yash.tracker.ui.components.shareText
import com.yash.tracker.domain.share.ShareText
import com.yash.tracker.ui.theme.EyebrowStyle
import com.yash.tracker.ui.theme.LocalAccents
import com.yash.tracker.ui.theme.MetricSmallStyle
import com.yash.tracker.ui.theme.MetricStyle
import com.yash.tracker.ui.theme.springClick
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

private val MEAL_ORDER = listOf("BREAKFAST", "LUNCH", "DINNER", "SNACK")
private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMM")
private val WEEKDAY_LETTER = DateTimeFormatter.ofPattern("EEEEE")
private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun DashboardScreen(
    onTypeMeal: (date: String?) -> Unit = {},
    onEditEntry: (Long) -> Unit = {},
    onSearchFoods: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenWorkout: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val savedMeals by viewModel.savedMeals.collectAsStateWithLifecycle()
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    // Hoisted so the list can leave the banner's slot out entirely rather than laying out an
    // empty item and the gaps either side of it.
    val reminder: BackupReminderViewModel = hiltViewModel()
    val backupOverdue by reminder.overdue.collectAsStateWithLifecycle()
    var sheetOpen by remember { mutableStateOf(false) }
    var calendarOpen by remember { mutableStateOf(false) }
    var entryActions by remember { mutableStateOf<EntryWithItems?>(null) }
    val context = LocalContext.current

    if (sheetOpen) {
        LogRouteSheet(
            savedMeals = savedMeals,
            onDismiss = { sheetOpen = false },
            // Today's dashboard passes no date, same as before; a past day picked from the
            // calendar or the strip is what makes this a missed-day log rather than today's.
            onTypeMeal = {
                sheetOpen = false
                onTypeMeal(if (state.isToday) null else DiaryDate.format(state.date))
            },
            onSearchFoods = { sheetOpen = false; onSearchFoods() },
            onLogSavedMeal = { sheetOpen = false; viewModel.logSavedMeal(it) },
            onDeleteSavedMeal = viewModel::deleteSavedMeal,
        )
    }

    if (calendarOpen) {
        DayPicker(
            selected = state.date,
            onDismiss = { calendarOpen = false },
            onPick = {
                viewModel.selectDate(it)
                calendarOpen = false
            },
        )
    }

    entryActions?.let { entry ->
        EntrySheet(
            entry = entry,
            onDismiss = { entryActions = null },
            onEdit = {
                entryActions = null
                onEditEntry(entry.entry.id)
            },
            onSaveAsMeal = { name ->
                viewModel.saveEntryAsMeal(entry.entry.id, name)
                entryActions = null
            },
            onDelete = {
                viewModel.deleteEntry(entry.entry.id)
                entryActions = null
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        LuxTopBar(
            eyebrow = "Journal",
            title = if (state.isToday) "Today" else state.date.format(DAY_FORMAT),
            onProfile = onOpenSettings,
            actions = {
                CircleIconButton(
                    icon = Icons.Outlined.Share,
                    contentDescription = "Share this day",
                    onClick = {
                        context.shareText(
                            text = ShareText.forDay(
                                date = state.date,
                                totals = state.totals,
                                target = state.target,
                                waterMl = state.waterMl,
                            ),
                            subject = "Food on ${state.date.format(DAY_FORMAT)}",
                        )
                    },
                )
                CircleIconButton(
                    icon = Icons.Outlined.CalendarToday,
                    contentDescription = "Pick a day",
                    onClick = { calendarOpen = true },
                )
            },
        )

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { StaggerIn(0) { LedgerStatus(state) } }
                item { StaggerIn(1) { Timeline(state, onCalendar = { calendarOpen = true }) } }
                item { StaggerIn(2) { DayStrip(state, viewModel::selectDate) } }
                if (backupOverdue) {
                    item { BackupReminderCard(onOpenSettings, reminder::dismiss) }
                }
                item { StaggerIn(3) { EnergyCard(state) } }
                item { StaggerIn(4) { MacroRow(state) } }
                if (state.totals.microKcal > 0) {
                    item { StaggerIn(4) { BreakdownCard(state) } }
                }
                item { StaggerIn(5) { HydrationCard(state, viewModel) } }

                suggestion?.let { next ->
                    item { StaggerIn(6) { SuggestionCard(next, viewModel::logSuggestion) } }
                }

                state.workout?.let { session ->
                    item { StaggerIn(7) { WorkoutCard(session, onOpenWorkout) } }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    GroupHeader(
                        title = "Meal ledger",
                        count = "${state.entries.size} logged",
                    )
                }

                if (state.entries.isEmpty()) {
                    item {
                        EmptyNote(
                            if (state.isToday) {
                                "Nothing logged yet. The button below takes a photo, reads a " +
                                    "description, or searches the catalogue."
                            } else {
                                "Nothing was logged on this day."
                            },
                        )
                    }
                } else {
                    val ordered = state.entries.sortedWith(
                        compareBy(
                            { MEAL_ORDER.indexOf(it.entry.mealType).takeIf { i -> i >= 0 } ?: 99 },
                            { it.entry.loggedAt },
                        ),
                    )
                    // Switching day replaces the whole ledger at once, which an item animation
                    // handles badly — it leaves a gap where the previous day's rows were.
                    items(ordered, key = { it.entry.id }) { entry ->
                        LedgerRow(entry) { entryActions = entry }
                    }
                }
            }

            PillButton(
                text = "Log meal",
                onClick = { sheetOpen = true },
                leadingIcon = Icons.Default.Add,
                height = 52.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp),
            )
        }
    }
}

// --- header blocks --------------------------------------------------------------------------

@Composable
private fun LedgerStatus(state: DashboardUiState) {
    val week = state.date.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear())

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // The labels are weighted so the pill is measured at its natural size first — at a
        // large font scale an unweighted label squeezed it down to a sliver.
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Eyebrow("Precision log", Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant),
            )
            Spacer(Modifier.width(8.dp))
            Eyebrow("Week $week", Modifier.weight(1f, fill = false))
        }
        Spacer(Modifier.width(8.dp))
        Pill(
            text = if (state.isToday) "Live" else "Past day",
            tone = PillTone.Quiet,
            leadingDot = true,
        )
    }
}

@Composable
private fun Timeline(state: DashboardUiState, onCalendar: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Eyebrow("Timeline")
            Spacer(Modifier.height(3.dp))
            // One Text, not a Row of two: at a large font scale a Row gave the "Today" suffix
            // a column one letter wide and stacked it down the screen.
            Text(
                buildAnnotatedString {
                    append(state.date.format(DAY_FORMAT))
                    if (state.isToday) {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append("  Today")
                        }
                    }
                },
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SmallAction(
            text = "Calendar",
            icon = Icons.Outlined.CalendarToday,
            onClick = onCalendar,
            tone = ActionTone.Soft,
        )
    }
}

/** Sunday through Saturday of the current week, fixed regardless of the day being viewed. The
 *  selected day is the only solid shape, when it falls inside this week at all. */
@Composable
private fun DayStrip(state: DashboardUiState, onSelect: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        state.history.forEach { bar ->
            DayCell(
                date = bar.date,
                selected = bar.date == state.date,
                logged = bar.kcal > 0.0,
                modifier = Modifier.weight(1f),
                onClick = { onSelect(bar.date) },
            )
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    selected: Boolean,
    logged: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (selected) scheme.inverseSurface else scheme.surfaceContainerLow,
        label = "dayBackground",
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) scheme.inverseOnSurface else scheme.onSurfaceVariant,
        label = "dayForeground",
    )
    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "dayLift",
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                val scale = 1f + lift * 0.04f
                scaleX = scale
                scaleY = scale
            }
            .clip(MaterialTheme.shapes.medium)
            .background(background)
            .springClick(pressedScale = 0.94f, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            date.format(WEEKDAY_LETTER).uppercase(),
            style = EyebrowStyle,
            color = foreground.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = foreground,
        )
        Spacer(Modifier.height(4.dp))
        // A day with nothing in it should look empty at a glance, not merely unselected.
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (logged) foreground.copy(alpha = 0.6f) else Color.Transparent),
        )
    }
}

// --- the ring -------------------------------------------------------------------------------

@Composable
private fun progressColour(fraction: Float): Color {
    val warning = LocalAccents.current.warning
    return when {
        fraction > 1.10f -> MaterialTheme.colorScheme.error
        fraction > 1.0f -> warning
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
private fun EnergyCard(state: DashboardUiState) {
    val target = state.target?.kcal
    val consumed = state.totals.kcal.roundToInt()
    val fraction = if (target != null && target > 0) consumed.toFloat() / target else 0f
    val remaining = state.remainingKcal

    LuxCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The pill is measured first and the label yields: at a large font scale the
            // number is what has to survive, not the word next to it.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Energy balance", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Pill(
                    text = target?.let { "Target %,d kcal".format(it) } ?: "No target set",
                    tone = PillTone.Solid,
                )
            }

            Spacer(Modifier.height(18.dp))
            EnergyRing(
                progress = fraction,
                diameter = 176.dp,
                stroke = 12.dp,
                color = progressColour(fraction),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Eyebrow(if (remaining != null && remaining < 0) "Over by" else "Remaining")
                    Spacer(Modifier.height(2.dp))
                    AnimatedCount(
                        value = remaining?.let { if (it < 0) -it else it } ?: consumed,
                        style = MetricStyle,
                        grouped = true,
                    )
                    Text(
                        target?.let { "kcal of %,d".format(it) } ?: "kcal logged",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Pill(
                text = "%,d kcal consumed".format(consumed),
                tone = PillTone.Quiet,
                icon = Icons.Default.Check,
            )
        }
    }
}

@Composable
private fun MacroRow(state: DashboardUiState) {
    val target = state.target
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MacroTile("Protein", state.totals.proteinG, target?.proteinG, Modifier.weight(1f))
        MacroTile("Carbs", state.totals.carbsG, target?.carbsG, Modifier.weight(1f))
        MacroTile("Fat", state.totals.fatG, target?.fatG, Modifier.weight(1f))
    }
}

/**
 * Sugar, fibre and salt for the day, with how much of the day they actually cover.
 *
 * No targets and no verdict: the plan is written in the four macros, and these three are
 * context for it. The coverage line is the part that matters — a sugar figure drawn from half
 * the day's calories is not the day's sugar, and printing it bare would read as if it were.
 * The card stays hidden until something on the plate has a panel to read.
 */
@Composable
private fun BreakdownCard(state: DashboardUiState) {
    val totals = state.totals
    val coverage = totals.microCoverage

    LuxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Breakdown", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Pill(
                    text = if (coverage >= FULL_COVERAGE) {
                        "Every meal"
                    } else {
                        "${(coverage * 100).roundToInt()}% of the day"
                    },
                    tone = PillTone.Quiet,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                BreakdownStat("Sugar", totals.sugarG, "g", Modifier.weight(1f))
                BreakdownStat("Fibre", totals.fibreG, "g", Modifier.weight(1f))
                BreakdownStat("Sodium", totals.sodiumMg, "mg", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BreakdownStat(label: String, value: Double?, unit: String, modifier: Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            // An em dash rather than a zero: nothing on the plate stated this one, which is
            // not the same as there being none of it.
            Text(
                value?.roundToInt()?.toString() ?: "—",
                style = MetricSmallStyle,
                maxLines = 1,
            )
            if (value != null) {
                Text(
                    unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Below this the card says which share of the day it is speaking for. */
private const val FULL_COVERAGE = 0.995

@Composable
private fun HydrationCard(state: DashboardUiState, viewModel: DashboardViewModel) {
    val targetMl = state.target?.waterMl ?: 0
    val fraction = if (targetMl > 0) state.waterMl.toFloat() / targetMl else 0f
    val percent = (fraction * 100).roundToInt()

    LuxCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconPlate(Icons.Outlined.WaterDrop, size = 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Hydration", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Water intake",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Pill(
                    text = "%.1f / %.1f L".format(state.waterMl / 1000.0, targetMl / 1000.0),
                    tone = PillTone.Solid,
                )
            }

            Spacer(Modifier.height(16.dp))
            ThinBar(fraction, height = 8.dp)

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (targetMl > 0) "$percent% of daily target" else "No water target set",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (state.waterMl > 0) {
                    TextAction("−$DEFAULT_GLASS_ML", onClick = viewModel::removeWater)
                    Spacer(Modifier.width(4.dp))
                }
                SmallAction(
                    text = "+ $DEFAULT_GLASS_ML ml",
                    onClick = { viewModel.addWater() },
                    tone = ActionTone.Soft,
                )
            }
        }
    }
}

@Composable
private fun WorkoutCard(session: WorkoutSessionEntity, onOpen: () -> Unit) {
    LuxCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconPlate(Icons.Outlined.FitnessCenter, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        session.name.ifBlank { "Workout" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Pill("Completed", tone = PillTone.Quiet)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "${session.kcalBurned} kcal burned · " +
                        "${session.totalVolumeKg.roundToInt()} kg moved",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- the ledger -----------------------------------------------------------------------------

@Composable
private fun LedgerRow(entry: EntryWithItems, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val meal = entry.entry.mealType.lowercase().replaceFirstChar(Char::uppercase)
    val time = Instant.ofEpochMilli(entry.entry.loggedAt)
        .atZone(ZoneId.systemDefault())
        .format(CLOCK)

    LuxCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhotoThumb(
                path = entry.entry.photoUri,
                fallbackIcon = if (entry.entry.source == "PHOTO") {
                    Icons.Outlined.CameraAlt
                } else {
                    Icons.Outlined.Restaurant
                },
                size = 56.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        meal,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${entry.entry.kcal.roundToInt()}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "KCAL",
                        style = EyebrowStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
                Text(
                    entry.items.joinToString(", ") { it.name }.ifBlank { "Entry" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        "P${entry.entry.proteinG.roundToInt()}g · " +
                            "C${entry.entry.carbsG.roundToInt()}g · " +
                            "F${entry.entry.fatG.roundToInt()}g",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// --- sheets ---------------------------------------------------------------------------------

/**
 * The routes from PRD §5.4, in the order they get used.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogRouteSheet(
    savedMeals: List<MealWithItems>,
    onDismiss: () -> Unit,
    onTypeMeal: () -> Unit,
    onSearchFoods: () -> Unit,
    onLogSavedMeal: (Long) -> Unit,
    onDeleteSavedMeal: (Long) -> Unit,
) {
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
                Eyebrow("Add to today")
                Spacer(Modifier.height(4.dp))
                Text("Log food", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(14.dp))
            }
            item {
                RouteRow(
                    icon = Icons.Outlined.CameraAlt,
                    title = "Photograph it",
                    subtitle = "Snap the plate and correct anything wrong",
                    onClick = onTypeMeal,
                )
            }
            item {
                RouteRow(
                    icon = Icons.Outlined.Restaurant,
                    title = "Type what I ate",
                    subtitle = "Describe it and I'll work out the macros",
                    onClick = onTypeMeal,
                )
            }
            item {
                RouteRow(
                    icon = Icons.Outlined.Search,
                    title = "Search foods",
                    subtitle = "Pick from the catalogue",
                    onClick = onSearchFoods,
                )
            }

            // Listed here rather than behind another screen: that is what makes a repeat meal
            // two taps from the dashboard (PRD §7.5).
            if (savedMeals.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    GroupHeader("Saved meals", count = "${savedMeals.size}")
                }
                items(savedMeals, key = { it.template.id }) { meal ->
                    SavedMealRow(
                        meal = meal,
                        onLog = { onLogSavedMeal(meal.template.id) },
                        onDelete = { onDeleteSavedMeal(meal.template.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    SoftCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconPlate(icon, size = 42.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SavedMealRow(meal: MealWithItems, onLog: () -> Unit, onDelete: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth(), onClick = onLog) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(meal.template.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${meal.template.kcal.roundToInt()} kcal · ${meal.items.size} items" +
                        if (meal.template.timesLogged > 0) " · logged ${meal.template.timesLogged}×" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CircleIconButton(
                icon = Icons.Outlined.Close,
                contentDescription = "Delete ${meal.template.name}",
                onClick = onDelete,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntrySheet(
    entry: EntryWithItems,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSaveAsMeal: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember {
        mutableStateOf(entry.items.joinToString(", ") { it.name }.take(40))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Eyebrow("Diary entry")
            Spacer(Modifier.height(4.dp))
            Text(
                "${entry.entry.kcal.roundToInt()} kcal",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                entry.items.joinToString(", ") { it.name }.ifBlank { "Entry" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val review = MealReviewer.review(
                macros = Macros(
                    kcal = entry.entry.kcal,
                    proteinG = entry.entry.proteinG,
                    carbsG = entry.entry.carbsG,
                    fatG = entry.entry.fatG,
                ),
                micros = Micros(
                    fibreG = entry.entry.fibreG,
                    sugarG = entry.entry.sugarG,
                    sodiumMg = entry.entry.sodiumMg,
                ),
                microKcal = entry.entry.microKcal,
            )
            if (review.isWorthShowing) {
                Spacer(Modifier.height(16.dp))
                MealReviewCard(review, Modifier.fillMaxWidth())
            }

            // Correcting the entry comes first: a wrong number is worth fixing sooner than a
            // right one is worth saving as a template.
            Spacer(Modifier.height(18.dp))
            PillButton(
                text = "Edit entry",
                onClick = onEdit,
                leadingIcon = Icons.Outlined.Edit,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            Hairline()
            Spacer(Modifier.height(20.dp))

            Eyebrow("Log it again later")
            Spacer(Modifier.height(6.dp))
            Text(
                "Save this as a meal you can log again in two taps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))
            LuxTextField(
                value = name,
                onValueChange = { name = it },
                label = "Meal name",
            )

            Spacer(Modifier.height(18.dp))
            PillButton(
                text = "Save as meal",
                onClick = { onSaveAsMeal(name) },
                enabled = name.isNotBlank(),
                tone = ActionTone.Soft,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PillButton(
                text = "Delete entry",
                onClick = onDelete,
                tone = ActionTone.Ghost,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPicker(
    selected: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = selected.atStartOfDay(zone).toInstant().toEpochMilli(),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        confirmButton = {
            TextAction(
                text = "Show day",
                color = MaterialTheme.colorScheme.primary,
                onClick = {
                    pickerState.selectedDateMillis?.let {
                        // The picker works in UTC midnights; reading it back as a UTC date is
                        // what keeps a day from sliding either side of the date line.
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate())
                    } ?: onDismiss()
                },
            )
        },
        dismissButton = { TextAction("Cancel", onClick = onDismiss) },
    ) {
        DatePicker(
            state = pickerState,
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
        )
    }
}
