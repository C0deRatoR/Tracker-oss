package com.yash.tracker.ui.dashboard

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.entity.LogEntryEntity
import com.yash.tracker.data.local.entity.LogItemEntity
import com.yash.tracker.data.repository.LogRepository
import com.yash.tracker.data.repository.MealRepository
import com.yash.tracker.data.repository.ProfileRepository
import com.yash.tracker.data.repository.SuggestionRepository
import com.yash.tracker.data.repository.WorkoutRepository
import com.yash.tracker.domain.diary.DiaryDate
import com.yash.tracker.domain.model.ActivityLevel
import com.yash.tracker.domain.model.EatingStyle
import com.yash.tracker.domain.model.Goal
import com.yash.tracker.domain.model.Pace
import com.yash.tracker.domain.model.PlanInput
import com.yash.tracker.domain.model.Sex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DashboardViewModelTest {

    private lateinit var db: AppDatabase
    private lateinit var logRepository: LogRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var mealRepository: MealRepository
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var suggestionRepository: SuggestionRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        logRepository = LogRepository(db.logDao(), Dispatchers.Unconfined)
        mealRepository = MealRepository(db.mealDao(), db.logDao(), Dispatchers.Unconfined)
        profileRepository = ProfileRepository(
            db.profileDao(),
            db.weightDao(),
            Dispatchers.Unconfined,
        )
        suggestionRepository = SuggestionRepository(
            foodDao = db.foodDao(),
            productDao = db.productDao(),
            mealDao = db.mealDao(),
            logDao = db.logDao(),
            meals = mealRepository,
            io = Dispatchers.Unconfined,
        )
        workoutRepository = WorkoutRepository(
            db.workoutDao(),
            profileRepository,
            Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun givenAPlan() = profileRepository.saveProfileAndPlan(
        name = "Yash",
        notes = null,
        input = PlanInput(
            sex = Sex.MALE,
            age = 27,
            heightCm = 178.0,
            weightKg = 78.0,
            goal = Goal.LOSE,
            goalWeightKg = 72.0,
            activity = ActivityLevel.MODERATELY,
            pace = Pace.STEADY,
            eatingStyle = EatingStyle.HIGH_PROTEIN,
        ),
    )

    private suspend fun logMeal(date: LocalDate, meal: String, kcal: Double, name: String) {
        db.logDao().insertEntryWithItems(
            LogEntryEntity(
                date = DiaryDate.format(date),
                loggedAt = System.currentTimeMillis(),
                mealType = meal,
                source = "MANUAL",
                photoUri = null,
                userHint = null,
                note = null,
                groundingSource = null,
                kcal = kcal,
                proteinG = 20.0,
                carbsG = 30.0,
                fatG = 10.0,
            ),
            listOf(
                LogItemEntity(
                    entryId = 0,
                    foodId = null,
                    productId = null,
                    name = name,
                    quantity = 1.0,
                    unit = "G",
                    grams = 100.0,
                    kcal = kcal,
                    proteinG = 20.0,
                    carbsG = 30.0,
                    fatG = 10.0,
                    aiConfidence = null,
                ),
            ),
        )
    }

    private fun viewModel() =
        DashboardViewModel(
            logRepository,
            mealRepository,
            suggestionRepository,
            profileRepository,
            workoutRepository,
        )

    @Test
    fun `the dashboard shows the active plan as its target`() = runTest {
        givenAPlan()
        val state = viewModel().state.first { it.target != null }

        assertEquals(2230, state.target!!.kcal)
        assertEquals("Yash", state.name)
        assertTrue(state.isToday)
    }

    @Test
    fun `an untouched day reads zero rather than blank`() = runTest {
        givenAPlan()
        val state = viewModel().state.first { it.target != null }

        assertEquals(0.0, state.totals.kcal, 0.001)
        assertEquals(2230, state.remainingKcal)
        assertEquals(0, state.waterMl)
        assertTrue(state.entries.isEmpty())
    }

    @Test
    fun `logged meals reduce what is left and group under their meal`() = runTest {
        givenAPlan()
        val today = logRepository.today(4)
        logMeal(today, "BREAKFAST", 400.0, "Poha")
        logMeal(today, "LUNCH", 700.0, "Dal chawal")

        val state = viewModel().state.first { it.entries.size == 2 }

        assertEquals(1100.0, state.totals.kcal, 0.001)
        assertEquals(2230 - 1100, state.remainingKcal)
        assertEquals(listOf("BREAKFAST", "LUNCH"), state.entries.map { it.entry.mealType })
    }

    @Test
    fun `going over target leaves a negative remainder rather than clamping`() = runTest {
        givenAPlan()
        logMeal(logRepository.today(4), "DINNER", 2600.0, "Biryani")

        val state = viewModel().state.first { it.totals.kcal > 0 }
        assertEquals(-370, state.remainingKcal)
    }

    @Test
    fun `water accumulates and the last glass can be taken back`() = runTest {
        givenAPlan()
        val vm = viewModel()
        vm.state.first { it.target != null }

        vm.addWater()
        vm.addWater()
        assertEquals(500, vm.state.first { it.waterMl == 500 }.waterMl)

        vm.removeWater()
        assertEquals(250, vm.state.first { it.waterMl == 250 }.waterMl)
    }

    @Test
    fun `the history strip is always Sunday through Saturday of the current week`() = runTest {
        givenAPlan()
        val today = logRepository.today(4)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        logMeal(weekStart.plusDays(2), "LUNCH", 1800.0, "Thali")

        val state = viewModel().state.first { it.target != null && it.history.isNotEmpty() }

        assertEquals(7, state.history.size)
        assertEquals(weekStart, state.history.first().date)
        assertEquals(weekStart.plusDays(6), state.history.last().date)
        assertEquals(1800.0, state.history.first { it.date == weekStart.plusDays(2) }.kcal, 0.001)
    }

    @Test
    fun `the history strip does not move when a past day is picked`() = runTest {
        givenAPlan()
        val today = logRepository.today(4)
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

        val vm = viewModel()
        vm.state.first { it.target != null }
        vm.selectDate(today.minusDays(10))

        val state = vm.state.first { it.date == today.minusDays(10) }
        assertEquals(weekStart, state.history.first().date)
        assertEquals(weekStart.plusDays(6), state.history.last().date)
    }

    @Test
    fun `picking a past day switches the diary to it`() = runTest {
        givenAPlan()
        val today = logRepository.today(4)
        logMeal(today.minusDays(1), "DINNER", 900.0, "Khichdi")

        val vm = viewModel()
        vm.state.first { it.target != null }
        vm.selectDate(today.minusDays(1))

        val state = vm.state.first { it.date == today.minusDays(1) && it.entries.isNotEmpty() }
        assertEquals(900.0, state.totals.kcal, 0.001)
        assertTrue(!state.isToday)
    }

    @Test
    fun `water logged on one day does not leak into another`() = runTest {
        givenAPlan()
        val today = logRepository.today(4)
        val vm = viewModel()
        vm.state.first { it.target != null }

        vm.addWater()
        vm.state.first { it.waterMl == 250 }

        vm.selectDate(today.minusDays(1))
        assertEquals(0, vm.state.first { it.date == today.minusDays(1) }.waterMl)
    }
}
