package com.yash.tracker.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.dao.ProfileDao
import com.yash.tracker.data.local.entity.TargetEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Setting the daily targets by hand.
 *
 * The plan is a suggestion, and someone told a number by a coach or a doctor needs to be able to
 * use theirs. Targets are already a history of revisions rather than one mutable row, so an
 * override is just another revision — which is what makes going back to the suggestion possible
 * rather than a second feature.
 */
@RunWith(RobolectricTestRunner::class)
class TargetOverrideTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ProfileDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.profileDao()
    }

    @After
    fun tearDown() = db.close()

    /** What the calculator worked out for a 78 kg body. */
    private val calculated = TargetEntity(
        computedAt = 1_757_000_000_000,
        basisWeightKg = 78.0,
        bmr = 1_720,
        tdee = 2_666,
        activityFactor = 1.55,
        kcal = 2_170,
        proteinG = 156.0,
        carbsG = 217.0,
        fatG = 62.0,
        waterMl = 3_100,
        weeksToGoal = 18,
        rateLbPerWeek = 1.0,
    )

    /** The override as the repository writes it: a new revision, body figures carried across. */
    private fun TargetEntity.overriddenTo(kcal: Int, waterMl: Int) = copy(
        id = 0,
        computedAt = computedAt + 86_400_000,
        kcal = kcal,
        waterMl = waterMl,
        isManualOverride = true,
    )

    @Test
    fun `a hand-set target becomes the active plan`() = runTest {
        dao.insertTarget(calculated)
        dao.insertTarget(calculated.overriddenTo(kcal = 1_900, waterMl = 4_000))

        val active = dao.getActiveTarget()!!
        assertEquals(1_900, active.kcal)
        assertEquals(4_000, active.waterMl)
        assertTrue("and is marked as the user's own", active.isManualOverride)
    }

    @Test
    fun `overriding keeps what the body does, not just what it is told to eat`() = runTest {
        dao.insertTarget(calculated)
        dao.insertTarget(calculated.overriddenTo(kcal = 1_900, waterMl = 4_000))

        val active = dao.getActiveTarget()!!
        assertEquals("eating less does not lower a BMR", 1_720, active.bmr)
        assertEquals(2_666, active.tdee)
        assertEquals(78.0, active.basisWeightKg, 0.01)
    }

    @Test
    fun `the calculated plan is not destroyed by an override`() = runTest {
        dao.insertTarget(calculated)
        dao.insertTarget(calculated.overriddenTo(kcal = 1_900, waterMl = 4_000))

        // Going back to the suggestion writes a fresh calculated revision on top.
        dao.insertTarget(calculated.copy(id = 0, computedAt = calculated.computedAt + 172_800_000))

        val active = dao.getActiveTarget()!!
        assertEquals("the suggestion is available again", 2_170, active.kcal)
        assertEquals(3_100, active.waterMl)
        assertFalse("and is no longer flagged as hand-set", active.isManualOverride)
    }
}
