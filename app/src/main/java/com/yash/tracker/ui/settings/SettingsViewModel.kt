package com.yash.tracker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.tracker.data.local.entity.ProfileEntity
import com.yash.tracker.data.local.entity.TargetEntity
import com.yash.tracker.domain.model.Plan
import com.yash.tracker.data.prefs.SettingsRepository
import com.yash.tracker.data.prefs.ThemeMode
import com.yash.tracker.data.remote.ConnectionResult
import com.yash.tracker.data.remote.GeminiConnectionTester
import com.yash.tracker.data.local.entity.CorrectionEntity
import com.yash.tracker.data.repository.ProfileRepository
import com.yash.tracker.data.repository.RecognitionRepository
import com.yash.tracker.domain.model.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val profile: ProfileEntity? = null,
    val target: TargetEntity? = null,
    val hasApiKey: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val weightUnit: WeightUnit = WeightUnit.KG,
    /** Whether "look it up" may put a search behind a reading, which costs more per call. */
    val groundingEnabled: Boolean = true,
)

data class ConnectionUiState(
    val testing: Boolean = false,
    val message: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val profileRepository: ProfileRepository,
    private val tester: GeminiConnectionTester,
    private val recognition: RecognitionRepository,
) : ViewModel() {

    val state = combine(
        profileRepository.observeProfile(),
        profileRepository.observeActiveTarget(),
        settings.hasApiKey,
        combine(
            settings.themeMode,
            settings.weightUnit,
            settings.groundingEnabled,
        ) { theme, unit, grounding -> Triple(theme, unit, grounding) },
    ) { profile, target, hasKey, (theme, unit, grounding) ->
        SettingsUiState(profile, target, hasKey, theme, unit, grounding)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _connection = MutableStateFlow(ConnectionUiState())
    val connection = _connection.asStateFlow()

    private val _planMessage = MutableStateFlow<String?>(null)
    val planMessage = _planMessage.asStateFlow()

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            settings.setApiKey(key)
            _connection.value = ConnectionUiState(message = "Key saved", success = true)
        }
    }

    fun removeApiKey() {
        viewModelScope.launch {
            settings.setApiKey("")
            _connection.value = ConnectionUiState(message = "Key removed", success = true)
        }
    }

    fun setGroundingEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setGroundingEnabled(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch { settings.setWeightUnit(unit) }
    }

    fun testConnection() {
        viewModelScope.launch {
            _connection.value = ConnectionUiState(testing = true)
            _connection.value = when (val result = tester.test()) {
                is ConnectionResult.Ok -> ConnectionUiState(
                    message = if (result.modelAvailable) {
                        "Connected. ${result.modelCount} models available, including ${result.model}."
                    } else {
                        "Key works, but ${result.model} wasn't in the list of " +
                            "${result.modelCount} models this key can use."
                    },
                    success = result.modelAvailable,
                )

                is ConnectionResult.Failed -> ConnectionUiState(
                    message = result.message,
                    success = false,
                )
            }
        }
    }

    fun recalculatePlan() {
        viewModelScope.launch {
            val plan = profileRepository.recalculateFromProfile()
            _planMessage.value = plan
                ?.let { "Plan updated: ${it.kcal} kcal a day" }
                ?: "Couldn't recalculate — no profile saved."
        }
    }

    /** What the calculator says, held alongside the active plan so the two can be compared. */
    private val _suggested = MutableStateFlow<Plan?>(null)
    val suggested = _suggested.asStateFlow()

    fun loadSuggestedPlan() {
        viewModelScope.launch { _suggested.value = profileRepository.suggestedPlan() }
    }

    /** Sets the targets by hand. Everything else about the plan is left as the calculator had it. */
    fun saveTargets(kcal: Int, proteinG: Double, carbsG: Double, fatG: Double, waterMl: Int) {
        viewModelScope.launch {
            val saved = profileRepository.overrideTarget(kcal, proteinG, carbsG, fatG, waterMl)
            _planMessage.value = saved
                ?.let { "Targets set by hand: ${it.kcal} kcal a day" }
                ?: "Couldn't save — there is no plan to adjust yet."
        }
    }

    /** Drops a hand-set plan and goes back to what the numbers say. */
    fun useSuggestedPlan() {
        viewModelScope.launch {
            val plan = profileRepository.recalculateFromProfile()
            _planMessage.value = plan
                ?.let { "Back to the suggested plan: ${it.kcal} kcal a day" }
                ?: "Couldn't recalculate — no profile saved."
        }
    }

    val corrections: StateFlow<List<CorrectionEntity>> = recognition.observeCorrections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun forgetCorrection(id: Long) {
        viewModelScope.launch { recognition.forgetCorrection(id) }
    }

    fun clearMessages() {
        _connection.update { it.copy(message = null) }
        _planMessage.value = null
    }
}
