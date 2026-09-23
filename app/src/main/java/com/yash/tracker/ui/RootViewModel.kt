package com.yash.tracker.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.tracker.data.prefs.SettingsRepository
import com.yash.tracker.data.prefs.ThemeMode
import com.yash.tracker.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StartDestination { ONBOARDING, MAIN }

/**
 * Decides where the app opens, once. It deliberately does not observe the profile: onboarding
 * saves the profile the moment the last question is answered, and a live flow would swap the
 * results screen away before it could be read.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val repository: ProfileRepository,
    settings: SettingsRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    private val _start = MutableStateFlow<StartDestination?>(null)
    val start: StateFlow<StartDestination?> = _start.asStateFlow()

    init {
        viewModelScope.launch {
            _start.value = if (repository.hasProfile()) {
                StartDestination.MAIN
            } else {
                StartDestination.ONBOARDING
            }
        }
    }

    fun onOnboardingFinished() {
        _start.value = StartDestination.MAIN
    }
}
