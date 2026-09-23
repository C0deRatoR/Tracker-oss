package com.yash.tracker.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.yash.tracker.ui.components.LuxNavBar
import com.yash.tracker.ui.components.NavTab
import com.yash.tracker.ui.dashboard.DashboardScreen
import com.yash.tracker.ui.exercises.ExerciseDetailScreen
import com.yash.tracker.ui.exercises.ExerciseLibraryScreen
import com.yash.tracker.ui.foods.FoodSearchScreen
import com.yash.tracker.ui.products.ProductsScreen
import com.yash.tracker.ui.progress.ProgressScreen
import com.yash.tracker.ui.recognition.RecognitionScreen
import com.yash.tracker.ui.settings.SettingsScreen
import com.yash.tracker.ui.theme.Decelerate
import com.yash.tracker.ui.workout.LiveSessionScreen
import com.yash.tracker.ui.workout.SessionSummaryScreen
import com.yash.tracker.ui.workout.TrainingReportScreen
import com.yash.tracker.ui.workout.WorkoutScreen

/** Switching tabs is a dissolve, not a journey: nothing slides sideways between peers. */
private val tabEnter: EnterTransition =
    fadeIn(tween(260, easing = Decelerate)) + scaleIn(tween(320, easing = Decelerate), initialScale = 0.98f)

private val tabExit: ExitTransition =
    fadeOut(tween(150)) + scaleOut(tween(220), targetScale = 1.01f)

/** A pushed screen rises over the tab it came from, and sinks back into it. */
private val pushEnter: EnterTransition =
    slideInVertically(tween(380, easing = Decelerate)) { it / 5 } + fadeIn(tween(240))

private val pushExit: ExitTransition =
    slideOutVertically(tween(260)) { it / 6 } + fadeOut(tween(180))

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Products is pushed rather than a tab, but it belongs to Foods — the bar stays up and keeps
    // Foods lit, which is what the design shows.
    val onProducts = currentDestination?.hierarchy?.any { it.hasRoute(Products::class) } == true
    val selectedTab = if (onProducts) {
        TopLevelTab.FOODS.ordinal
    } else {
        TopLevelTab.entries.indexOfFirst { tab ->
            currentDestination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
        }
    }
    val onTopLevel = selectedTab >= 0

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Every screen draws its own header under the status bar, and the tab bar insets itself.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = onTopLevel,
                enter = slideInVertically(tween(340, easing = Decelerate)) { it },
                exit = slideOutVertically(tween(240)) { it },
            ) {
                LuxNavBar(
                    tabs = TopLevelTab.entries.map { NavTab(it.label, it.icon, it.selectedIcon) },
                    selectedIndex = selectedTab.coerceAtLeast(0),
                    onSelect = { index -> navController.switchTab(TopLevelTab.entries[index].route) },
                )
            }
        },
    ) { innerPadding ->
        // The screens inside apply imePadding() themselves. Without consuming the bar's height
        // here, that padding is measured from the bottom of the window and added on top of it —
        // so an open keyboard leaves a dead strip exactly one tab bar tall, and the list above
        // stops short of it.
        val barHeight = innerPadding.calculateBottomPadding()

        NavHost(
            navController = navController,
            startDestination = Today,
            modifier = Modifier
                .padding(bottom = barHeight)
                .consumeWindowInsets(PaddingValues(bottom = barHeight)),
            enterTransition = { tabEnter },
            exitTransition = { tabExit },
            popEnterTransition = { tabEnter },
            popExitTransition = { tabExit },
        ) {
            composable<Today> {
                DashboardScreen(
                    onTypeMeal = { date -> navController.navigate(Recognize(date = date)) },
                    onEditEntry = { navController.navigate(Recognize(entryId = it)) },
                    onSearchFoods = { navController.switchTab(Foods) },
                    onOpenSettings = { navController.switchTab(Settings) },
                    onOpenWorkout = { navController.switchTab(Workout) },
                )
            }
            composable<Foods> {
                FoodSearchScreen(onOpenProducts = { navController.navigate(Products) })
            }
            composable<Products>(
                enterTransition = { pushEnter },
                popExitTransition = { pushExit },
            ) {
                ProductsScreen(onBack = { navController.popBackStack() })
            }
            composable<Recognize>(
                enterTransition = { pushEnter },
                popExitTransition = { pushExit },
            ) { entry ->
                val route = entry.toRoute<Recognize>()
                RecognitionScreen(
                    entryId = route.entryId,
                    date = route.date,
                    onDone = { navController.popBackStack() },
                )
            }
            composable<Workout> {
                WorkoutScreen(
                    onStartRoutine = { navController.navigate(LiveSession(it)) },
                    onBrowseExercises = { navController.navigate(ExerciseLibrary) },
                    onOpenSession = { navController.navigate(SessionSummary(it)) },
                    onOpenReport = { navController.navigate(TrainingAnalysis) },
                )
            }
            composable<TrainingAnalysis>(
                enterTransition = { pushEnter },
                popExitTransition = { pushExit },
            ) {
                TrainingReportScreen(onBack = { navController.popBackStack() })
            }
            composable<ExerciseLibrary>(
                enterTransition = { pushEnter },
                popExitTransition = { pushExit },
            ) {
                ExerciseLibraryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenExercise = { navController.navigate(ExerciseDetail(it)) },
                )
            }
            composable<ExerciseDetail>(
                enterTransition = { pushEnter },
                popExitTransition = { pushExit },
            ) { entry ->
                ExerciseDetailScreen(
                    exerciseId = entry.toRoute<ExerciseDetail>().exerciseId,
                    onBack = { navController.popBackStack() },
                    onOpenSession = { navController.navigate(SessionSummary(it)) },
                )
            }
            composable<LiveSession>(
                enterTransition = { pushEnter },
                popExitTransition = { pushExit },
            ) { entry ->
                val route = entry.toRoute<LiveSession>()
                LiveSessionScreen(
                    routineId = route.routineId,
                    onFinished = { sessionId ->
                        navController.navigate(SessionSummary(sessionId)) {
                            popUpTo(Workout)
                        }
                    },
                    onExit = { navController.popBackStack() },
                )
            }
            composable<SessionSummary>(
                enterTransition = { pushEnter },
                popExitTransition = { pushExit },
            ) { entry ->
                SessionSummaryScreen(
                    sessionId = entry.toRoute<SessionSummary>().sessionId,
                    onDone = { navController.popBackStack() },
                )
            }
            composable<Progress> { ProgressScreen() }
            composable<Settings> { SettingsScreen() }
        }
    }
}

private fun androidx.navigation.NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
