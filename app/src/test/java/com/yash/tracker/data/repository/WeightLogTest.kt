package com.yash.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class WeightLogTest {

    private lateinit var db: AppDatabase
    private lateinit var profiles: ProfileRepository

    private val today = LocalDate.of(2026, 9, 13)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()

        profiles = ProfileRepository(db.profileDao(), db.weightDao(), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `re-weighing on the same day corrects the reading instead of adding one`() = runTest {
        profiles.logWeight(today, 82.4)
        profiles.logWeight(today, 81.9)

        val log = profiles.observeWeightLog().first()
        assertEquals("one weigh-in per day", 1, log.size)
        assertEquals(81.9, log.single().weightKg, 0.001)
    }

    @Test
    fun `weigh-ins on different days both stand`() = runTest {
        profiles.logWeight(today.minusDays(1), 82.4)
        profiles.logWeight(today, 81.9)

        assertEquals(2, profiles.observeWeightLog().first().size)
    }

    @Test
    fun `a weigh-in can be deleted`() = runTest {
        profiles.logWeight(today, 82.4)
        val id = profiles.observeWeightLog().first().single().id

        profiles.deleteWeight(id)

        assertEquals(0, profiles.observeWeightLog().first().size)
    }

    @Test
    fun `the latest weight is the most recent date, not the most recent write`() = runTest {
        profiles.logWeight(today, 81.9)
        profiles.logWeight(today.minusDays(3), 84.0)

        assertEquals(81.9, profiles.latestWeightKg()!!, 0.001)
    }
}
