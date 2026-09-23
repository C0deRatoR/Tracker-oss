package com.yash.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yash.tracker.data.local.seed.SeedProgress
import com.yash.tracker.data.prefs.ThemeMode
import com.yash.tracker.ui.RootViewModel
import com.yash.tracker.ui.StartDestination
import com.yash.tracker.ui.nav.AppNavHost
import com.yash.tracker.ui.onboarding.OnboardingScreen
import com.yash.tracker.ui.seed.SeedScreen
import com.yash.tracker.ui.seed.SeedViewModel
import com.yash.tracker.ui.components.ThinBar
import com.yash.tracker.ui.theme.TrackerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val rootViewModel: RootViewModel = hiltViewModel()
            val themeMode by rootViewModel.themeMode.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // The theme is chosen in Compose, so the system bars have to be told separately
            // or the status-bar icons stay light on a light background.
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = !darkTheme
            }

            TrackerTheme(darkTheme = darkTheme) {
                val seedViewModel: SeedViewModel = hiltViewModel()
                val seedState by seedViewModel.state.collectAsStateWithLifecycle()

                // The catalogue has to be in place before any screen can search it, so the
                // app holds here on first launch and goes straight through on every other.
                if (seedState !is SeedProgress.Finished) {
                    SeedScreen(state = seedState, onRetry = seedViewModel::start)
                    return@TrackerTheme
                }

                val start by rootViewModel.start.collectAsStateWithLifecycle()
                when (start) {
                    null -> Loading()
                    StartDestination.ONBOARDING ->
                        OnboardingScreen(onFinished = rootViewModel::onOnboardingFinished)
                    StartDestination.MAIN -> AppNavHost()
                }
            }
        }
    }
}

/**
 * The gap between "the database answered" and "we know which screen you want". Deliberately
 * not a spinner: it is usually one frame, and a spinner that flashes reads as a stutter.
 */
@Composable
private fun Loading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ThinBar(
            progress = 0.12f,
            modifier = Modifier.fillMaxWidth(0.4f),
            height = 4.dp,
        )
    }
}
