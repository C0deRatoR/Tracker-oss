package com.yash.tracker.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
data object Today

@Serializable
data object Foods

@Serializable
data object Products

@Serializable
data object Workout

@Serializable
data object Progress

@Serializable
data object Settings

/**
 * Each tab carries both weights of its glyph: the bar fills the icon of the tab it is sitting
 * on, so the selected state survives being read at a glance in the dark.
 */
enum class TopLevelTab(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    TODAY(Today, "Today", Icons.Outlined.CalendarToday, Icons.Filled.CalendarToday),
    FOODS(Foods, "Foods", Icons.Outlined.Restaurant, Icons.Filled.Restaurant),
    WORKOUT(Workout, "Workout", Icons.Outlined.FitnessCenter, Icons.Filled.FitnessCenter),
    PROGRESS(Progress, "Progress", Icons.Outlined.Insights, Icons.Filled.Insights),
    SETTINGS(Settings, "Settings", Icons.Outlined.Tune, Icons.Filled.Tune),
}

/**
 * Not a tab: reached from the log sheet and popped when done.
 *
 * [entryId] is null for a new meal and set when correcting one already in the diary — the same
 * editor either way, because the thing being edited is the same thing.
 *
 * [date] carries the diary day (ISO, [com.yash.tracker.domain.diary.DiaryDate]) the dashboard was
 * showing when this was opened, so a plate photographed for a missed day lands on that day
 * rather than on today. Null means today, and is what a new meal opened from today's dashboard
 * passes.
 */
@Serializable
data class Recognize(val entryId: Long? = null, val date: String? = null)

/** Routine id is null for an empty workout. */
@Serializable
data class LiveSession(val routineId: Long? = null)

@Serializable
data class SessionSummary(val sessionId: Long)

/** The exercise catalogue, browsable rather than only searchable. */
@Serializable
data object ExerciseLibrary

@Serializable
data class ExerciseDetail(val exerciseId: Long)
