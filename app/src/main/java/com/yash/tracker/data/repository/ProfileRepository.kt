package com.yash.tracker.data.repository

import com.yash.tracker.data.local.dao.ProfileDao
import com.yash.tracker.data.local.dao.WeightDao
import com.yash.tracker.data.local.entity.ProfileEntity
import com.yash.tracker.data.local.entity.TargetEntity
import com.yash.tracker.data.local.entity.WeightLogEntity
import com.yash.tracker.domain.diary.DiaryDate
import com.yash.tracker.domain.model.ActivityLevel
import com.yash.tracker.domain.model.EatingStyle
import com.yash.tracker.domain.model.Goal
import com.yash.tracker.domain.model.Pace
import com.yash.tracker.domain.model.Plan
import com.yash.tracker.domain.model.PlanInput
import com.yash.tracker.domain.model.Sex
import com.yash.tracker.domain.nutrition.PlanCalculator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val dao: ProfileDao,
    private val weightDao: WeightDao,
    private val io: CoroutineDispatcher,
) {
    fun observeProfile(): Flow<ProfileEntity?> = dao.observeProfile()

    fun observeActiveTarget(): Flow<TargetEntity?> = dao.observeActiveTarget()

    suspend fun hasProfile(): Boolean = withContext(io) { dao.getProfile() != null }

    suspend fun dayStartHour(): Int = withContext(io) { dao.getProfile()?.dayStartHour ?: 4 }

    suspend fun observeProfileOnce(): ProfileEntity? = withContext(io) { dao.getProfile() }

    suspend fun latestWeightKg(): Double? = withContext(io) { weightDao.latest()?.weightKg }

    fun observeWeightLog(): Flow<List<WeightLogEntity>> = weightDao.observeAll()

    /**
     * Records a weigh-in. One per date: re-entering today's weight corrects it rather than
     * adding a second reading that would drag the average.
     */
    suspend fun logWeight(date: LocalDate, weightKg: Double, note: String? = null) =
        withContext(io) { upsertWeight(date, weightKg, note) }

    /**
     * Room's @Upsert falls back to updating by primary key, and a new row carries id 0 — so a
     * second weigh-in on a date that already has one has to be given the existing row's id or
     * it collides with the unique index on date and is lost.
     */
    private suspend fun upsertWeight(date: LocalDate, weightKg: Double, note: String?) {
        val key = DiaryDate.format(date)
        weightDao.upsert(
            WeightLogEntity(
                id = weightDao.forDate(key)?.id ?: 0,
                date = key,
                weightKg = weightKg,
                note = note?.takeIf { it.isNotBlank() },
                loggedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun deleteWeight(id: Long) = withContext(io) { weightDao.delete(id) }

    /**
     * Rebuilds the plan from the saved answers and the most recent weigh-in. PRD §5.2 offers
     * this when logged weight has drifted more than 2 kg from what the plan was built on.
     */
    suspend fun recalculateFromProfile(): Plan? = withContext(io) {
        val profile = dao.getProfile() ?: return@withContext null
        val input = planInputFor(profile) ?: return@withContext null

        saveProfileAndPlan(name = profile.name, notes = profile.notes, input = input)
    }

    /**
     * What the calculator would say right now, worked out but not written.
     *
     * This is what "go back to the suggested plan" is offered against: a hand-set target is
     * only a considered choice if the user can see the number they are choosing not to use.
     */
    suspend fun suggestedPlan(): Plan? = withContext(io) {
        val profile = dao.getProfile() ?: return@withContext null
        PlanCalculator.calculate(planInputFor(profile) ?: return@withContext null)
    }

    /**
     * Records a hand-set plan as a new revision, flagged so it can be told from a calculated one.
     *
     * The body figures ride across untouched: BMR, TDEE and the timeline describe what this body
     * does, and deciding to eat more or less than suggested changes none of them. Keeping them
     * is also what lets the suggestion still be offered afterwards.
     */
    suspend fun overrideTarget(
        kcal: Int,
        proteinG: Double,
        carbsG: Double,
        fatG: Double,
        waterMl: Int,
    ): TargetEntity? = withContext(io) {
        val current = dao.getActiveTarget() ?: return@withContext null
        val revision = current.copy(
            id = 0,
            computedAt = System.currentTimeMillis(),
            kcal = kcal,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            waterMl = waterMl,
            isManualOverride = true,
        )

        dao.insertTarget(revision)
        revision
    }

    /** The interview answers as the calculator wants them, against the most recent weigh-in. */
    private suspend fun planInputFor(profile: ProfileEntity): PlanInput? {
        val weightKg = weightDao.latest()?.weightKg ?: return null

        return PlanInput(
            sex = Sex.valueOf(profile.sex),
            age = profile.age,
            heightCm = profile.heightCm,
            weightKg = weightKg,
            goal = Goal.valueOf(profile.goal),
            goalWeightKg = profile.goalWeightKg,
            activity = ActivityLevel.valueOf(profile.activity),
            pace = Pace.valueOf(profile.pace),
            eatingStyle = EatingStyle.valueOf(profile.eatingStyle),
        )
    }

    /**
     * Persists the interview answers and the plan they produce. The plan is stored as a new
     * [TargetEntity] revision rather than an update, so recalculating later never erases what
     * the previous plan said.
     */
    suspend fun saveProfileAndPlan(
        name: String,
        notes: String?,
        input: PlanInput,
    ): Plan = withContext(io) {
        val plan = PlanCalculator.calculate(input)
        val now = System.currentTimeMillis()
        val existing = dao.getProfile()

        dao.upsertProfile(
            ProfileEntity(
                name = name,
                age = input.age,
                sex = input.sex.name,
                heightCm = input.heightCm,
                goal = input.goal.name,
                goalWeightKg = input.goalWeightKg,
                activity = input.activity.name,
                pace = input.pace.name,
                eatingStyle = input.eatingStyle.name,
                notes = notes?.takeIf { it.isNotBlank() },
                dayStartHour = existing?.dayStartHour ?: 4,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )

        dao.insertTarget(
            TargetEntity(
                computedAt = now,
                basisWeightKg = input.weightKg,
                bmr = plan.bmr,
                tdee = plan.tdee,
                activityFactor = plan.activityFactor,
                kcal = plan.kcal,
                proteinG = plan.proteinG,
                carbsG = plan.carbsG,
                fatG = plan.fatG,
                waterMl = plan.waterMl,
                weeksToGoal = plan.weeksToGoal,
                rateLbPerWeek = plan.rateLbPerWeek,
                deficitCapped = plan.deficitCapped,
                floorApplied = plan.floorApplied,
            ),
        )

        // The starting weight is a real weigh-in: it anchors the plan and gives the weight
        // chart its first point.
        upsertWeight(LocalDate.now(), input.weightKg, note = null)

        plan
    }
}
