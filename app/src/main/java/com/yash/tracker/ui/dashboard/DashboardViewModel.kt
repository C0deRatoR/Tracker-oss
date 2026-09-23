package com.yash.tracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yash.tracker.data.local.dao.DayTotals
import com.yash.tracker.data.local.dao.EntryWithItems
import com.yash.tracker.data.local.dao.MealWithItems
import com.yash.tracker.data.local.entity.TargetEntity
import com.yash.tracker.data.local.entity.WorkoutSessionEntity
import com.yash.tracker.data.remote.CoachNotes
import com.yash.tracker.data.remote.prompts.CoachNote
import com.yash.tracker.data.repository.LogRepository
import com.yash.tracker.data.repository.MealRepository
import com.yash.tracker.data.repository.ProfileRepository
import com.yash.tracker.data.repository.SuggestionRepository
import com.yash.tracker.data.repository.WorkoutRepository
import com.yash.tracker.domain.diary.DiaryDate
import com.yash.tracker.domain.diary.MealType
import com.yash.tracker.domain.nutrition.MacroSuggestion
import com.yash.tracker.domain.nutrition.Macros
import com.yash.tracker.domain.nutrition.Micros
import com.yash.tracker.domain.nutrition.Plate
import com.yash.tracker.ui.coach.CoachNoteState
import com.yash.tracker.ui.coach.ask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

const val DEFAULT_GLASS_ML = 250

data class DayBar(val date: LocalDate, val kcal: Double)

data class DashboardUiState(
    val date: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val name: String = "",
    val target: TargetEntity? = null,
    val totals: DayTotals = DayTotals(0.0, 0.0, 0.0, 0.0),
    val waterMl: Int = 0,
    val entries: List<EntryWithItems> = emptyList(),
    val history: List<DayBar> = emptyList(),
    /** The finished session on this day, if there was one. Shown, never subtracted. */
    val workout: WorkoutSessionEntity? = null,
) {
    val isToday: Boolean get() = date == today

    /** Exercise calories are shown but never folded into the target, so the ring stays honest. */
    val remainingKcal: Int?
        get() = target?.let { (it.kcal - totals.kcal).toInt() }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val logRepository: LogRepository,
    private val mealRepository: MealRepository,
    private val suggestionRepository: SuggestionRepository,
    profileRepository: ProfileRepository,
    workoutRepository: WorkoutRepository,
    private val coach: CoachNotes,
) : ViewModel() {

    /** Null means "follow today", so the dashboard rolls over without being re-opened. */
    private val pickedDate = MutableStateFlow<LocalDate?>(null)

    val state: StateFlow<DashboardUiState> = combine(
        profileRepository.observeProfile(),
        profileRepository.observeActiveTarget(),
        pickedDate,
    ) { profile, target, picked -> Triple(profile, target, picked) }
        .flatMapLatest { (profile, target, picked) ->
            val today = logRepository.today(profile?.dayStartHour ?: 4)
            val date = picked ?: today
            // Sunday through Saturday of the real current week, independent of whichever day is
            // being viewed — picking a past day to log a missed meal should not shift the strip.
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            val weekEnd = weekStart.plusDays(6)

            combine(
                logRepository.observeTotals(date),
                logRepository.observeWater(date),
                logRepository.observeEntries(date),
                logRepository.observeDailyKcal(weekStart, weekEnd),
                workoutRepository.observeRecentSessions(),
            ) { totals, water, entries, daily, sessions ->
                val byDate = daily.associate { DiaryDate.parse(it.date) to it.kcal }
                DashboardUiState(
                    date = date,
                    today = today,
                    name = profile?.name.orEmpty(),
                    target = target,
                    totals = totals,
                    waterMl = water,
                    entries = entries,
                    history = (0..6).map { offset ->
                        val day = weekStart.plusDays(offset.toLong())
                        DayBar(day, byDate[day] ?: 0.0)
                    },
                    workout = sessions
                        .map { it.session }
                        .firstOrNull { it.date == DiaryDate.format(date) },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private val _coachNote = MutableStateFlow<CoachNoteState>(CoachNoteState.Idle)
    val coachNote: StateFlow<CoachNoteState> = _coachNote

    /**
     * What the day still has room for, recomputed as the diary changes.
     *
     * Only ever for today: a suggestion for last Tuesday is advice about a meal that has
     * already been eaten. Keyed on the totals and the meals logged rather than on the whole
     * state, so scrolling or a water tap does not re-run the search.
     */
    val suggestion: StateFlow<MacroSuggestion?> = state
        .map { current ->
            SuggestionInput(
                target = current.target.takeIf { current.isToday },
                date = current.date,
                totals = current.totals,
                loggedMeals = current.entries.mapNotNullTo(mutableSetOf()) { entry ->
                    runCatching { MealType.valueOf(entry.entry.mealType) }.getOrNull()
                },
            )
        }
        .distinctUntilChanged()
        // A new set of plates makes the old explanation wrong, so it goes with them.
        .onEach { _coachNote.value = CoachNoteState.Idle }
        .mapLatest { input ->
            val target = input.target ?: return@mapLatest null
            suggestionRepository.suggest(
                target = target,
                eaten = Macros(
                    kcal = input.totals.kcal,
                    proteinG = input.totals.proteinG,
                    carbsG = input.totals.carbsG,
                    fatG = input.totals.fatG,
                ),
                hour = LocalTime.now().hour,
                alreadyLogged = input.loggedMeals,
                date = input.date,
                eatenMicros = Micros(
                    fibreG = input.totals.fibreG,
                    sugarG = input.totals.sugarG,
                    sodiumMg = input.totals.sodiumMg,
                ),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun explainSuggestion() {
        val plates = suggestion.value as? MacroSuggestion.Plates ?: return
        viewModelScope.launch { _coachNote.ask(coach, CoachNote.forMeal(plates)) }
    }

    fun logSuggestion(meal: MealType, plate: Plate) {
        viewModelScope.launch { suggestionRepository.logPlate(state.value.date, meal, plate) }
    }

    fun selectDate(date: LocalDate) {
        pickedDate.value = if (date == state.value.today) null else date
    }

    fun addWater(ml: Int = DEFAULT_GLASS_ML) {
        viewModelScope.launch { logRepository.addWater(state.value.date, ml) }
    }

    fun removeWater() {
        viewModelScope.launch { logRepository.removeLastWater(state.value.date) }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { logRepository.deleteEntry(id) }
    }

    val savedMeals: StateFlow<List<MealWithItems>> = mealRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The second of the two taps from the dashboard (PRD §5.4c). */
    fun logSavedMeal(mealId: Long) {
        viewModelScope.launch { mealRepository.logMeal(mealId, state.value.date) }
    }

    fun saveEntryAsMeal(entryId: Long, name: String) {
        viewModelScope.launch { mealRepository.saveEntryAsMeal(entryId, name) }
    }

    fun deleteSavedMeal(mealId: Long) {
        viewModelScope.launch { mealRepository.delete(mealId) }
    }
}

/** Only the parts of the day a suggestion actually depends on. */
private data class SuggestionInput(
    val target: TargetEntity?,
    val date: LocalDate,
    val totals: DayTotals,
    val loggedMeals: Set<MealType>,
)
