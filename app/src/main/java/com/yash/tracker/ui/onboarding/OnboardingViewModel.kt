package com.yash.tracker.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.tracker.data.repository.ProfileRepository
import com.yash.tracker.domain.model.Plan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val answers: OnboardingAnswers = OnboardingAnswers(),
    val error: String? = null,
    val plan: Plan? = null,
    val saving: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun update(transform: (OnboardingAnswers) -> OnboardingAnswers) {
        // Editing clears the error rather than leaving stale red text under a fixed field.
        _state.update { it.copy(answers = transform(it.answers), error = null) }
    }

    fun back() {
        _state.update {
            it.copy(step = Onboarding.previous(it.step, it.answers), error = null)
        }
    }

    fun next() {
        val current = _state.value
        val error = Onboarding.validate(current.step, current.answers)
        if (error != null) {
            _state.update { it.copy(error = error) }
            return
        }

        val nextStep = Onboarding.next(current.step, current.answers)
        _state.update { it.copy(step = nextStep, error = null) }
        if (nextStep == OnboardingStep.RESULTS) computePlan()
    }

    private fun computePlan() {
        val answers = _state.value.answers
        val input = answers.toPlanInput() ?: run {
            _state.update { it.copy(step = OnboardingStep.NAME, error = "Something was missing — let's go through that again") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(saving = true) }
            val plan = repository.saveProfileAndPlan(
                name = answers.name.trim(),
                notes = answers.notes.trim(),
                input = input,
            )
            _state.update { it.copy(plan = plan, saving = false) }
        }
    }
}
