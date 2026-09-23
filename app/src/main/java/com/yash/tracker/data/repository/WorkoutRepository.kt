package com.yash.tracker.data.repository

import com.yash.tracker.data.local.dao.MuscleRecency
import com.yash.tracker.data.local.dao.PreviousSet
import com.yash.tracker.data.local.dao.RoutineWithExercises
import com.yash.tracker.data.local.dao.SessionWithSets
import com.yash.tracker.data.local.dao.WorkoutDao
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.data.local.entity.PersonalRecordEntity
import com.yash.tracker.data.local.entity.RoutineEntity
import com.yash.tracker.data.local.entity.RoutineExerciseEntity
import com.yash.tracker.data.local.entity.WorkoutSessionEntity
import com.yash.tracker.data.local.entity.WorkoutSetEntity
import com.yash.tracker.data.local.entity.isLogged
import com.yash.tracker.domain.diary.DiaryDate
import com.yash.tracker.domain.workout.AnalysedSet
import com.yash.tracker.domain.workout.Effort
import com.yash.tracker.domain.workout.MetCalories
import com.yash.tracker.domain.workout.MuscleStanding
import com.yash.tracker.domain.workout.NextWorkout
import com.yash.tracker.domain.workout.NextWorkoutPlanner
import com.yash.tracker.domain.workout.PersonalRecords
import com.yash.tracker.domain.workout.RecordType
import com.yash.tracker.domain.workout.ScoredSet
import com.yash.tracker.domain.workout.SessionAnalysis
import com.yash.tracker.domain.workout.SessionAnalyst
import com.yash.tracker.domain.workout.SessionChange
import com.yash.tracker.domain.workout.VolumeCalculator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** What to train next, and what to do about it. */
data class NextWorkoutAdvice(
    val plan: NextWorkout,
    /**
     * Movements for the suggested group: the ones this user actually trains, or the
     * catalogue's compounds for a group they have never touched.
     */
    val movements: List<ExerciseEntity> = emptyList(),
)

data class FinishedSession(
    val sessionId: Long,
    val durationSec: Int,
    val volumeKg: Double,
    val setsCompleted: Int,
    val kcalBurned: Int,
    val newRecords: List<PersonalRecordEntity>,
)

@Singleton
class WorkoutRepository @Inject constructor(
    private val dao: WorkoutDao,
    private val profiles: ProfileRepository,
    private val io: CoroutineDispatcher,
) {
    fun observeExercises(): Flow<List<ExerciseEntity>> = dao.observeExercises()

    suspend fun searchExercises(term: String): List<ExerciseEntity> =
        withContext(io) { dao.searchExercises(term.trim()) }

    fun observeRoutines(): Flow<List<RoutineWithExercises>> = dao.observeRoutines()

    fun observeRecentSessions(): Flow<List<SessionWithSets>> = dao.observeRecentSessions()

    fun observeSession(id: Long): Flow<SessionWithSets?> = dao.observeSession(id)

    suspend fun activeSessionId(): Long? = withContext(io) { dao.activeSessionId() }

    suspend fun session(id: Long) = withContext(io) { dao.session(id) }

    suspend fun exercise(id: Long) = withContext(io) { dao.exercise(id) }

    fun observeExercise(id: Long): Flow<ExerciseEntity?> = dao.observeExercise(id)

    /** Every finished session that included this exercise, newest first. */
    fun observeHistoryFor(exerciseId: Long): Flow<List<SessionWithSets>> =
        dao.observeSessionsWith(exerciseId)

    fun observeCompletedSetCount(exerciseId: Long): Flow<Int> =
        dao.observeCompletedSetCount(exerciseId)

    suspend fun createRoutine(name: String, exerciseIds: List<Long>): Long = withContext(io) {
        dao.insertRoutineWithExercises(
            routine = RoutineEntity(name = name.trim(), note = null, createdAt = System.currentTimeMillis()),
            exercises = exerciseIds.toRoutineRows(),
        )
    }

    private fun List<Long>.toRoutineRows(): List<RoutineExerciseEntity> = mapIndexed { index, id ->
        RoutineExerciseEntity(
            routineId = 0,
            exerciseId = id,
            // Position is the list's order, so reordering is just saving a different list.
            position = index,
            targetSets = DEFAULT_TARGET_SETS,
            targetRepsLow = null,
            targetRepsHigh = null,
        )
    }

    suspend fun deleteRoutine(id: Long) = withContext(io) { dao.deleteRoutine(id) }

    /** Renames a routine and rewrites its exercises in the order given. */
    suspend fun updateRoutine(id: Long, name: String, exerciseIds: List<Long>) = withContext(io) {
        dao.renameRoutine(id, name.trim())
        dao.replaceRoutineExercises(id, exerciseIds.toRoutineRows())
    }

    /**
     * A copy to diverge from, which is how most routines actually get written: last week's,
     * with one thing swapped.
     */
    suspend fun duplicateRoutine(id: Long): Long? = withContext(io) {
        val source = dao.routine(id) ?: return@withContext null
        dao.insertRoutineWithExercises(
            routine = RoutineEntity(
                name = "${source.routine.name} copy",
                note = source.routine.note,
                createdAt = System.currentTimeMillis(),
            ),
            exercises = source.exercises.sortedBy { it.position }.map { it.copy(id = 0, routineId = 0) },
        )
    }

    suspend fun routineLastPerformed(routineId: Long): Long? =
        withContext(io) { dao.routineLastPerformed(routineId) }

    /**
     * Starts a session, prefilling each exercise's sets from the last time it was trained.
     * That prefill is what makes a repeat set one tap (PRD §7.7).
     */
    suspend fun startSession(routineId: Long?, date: LocalDate): Long = withContext(io) {
        val routine = routineId?.let { dao.routine(it) }
        val now = System.currentTimeMillis()

        val sessionId = dao.insertSession(
            WorkoutSessionEntity(
                routineId = routineId,
                name = routine?.routine?.name ?: "Workout",
                date = DiaryDate.format(date),
                startedAt = now,
                endedAt = null,
                note = null,
                isFinished = false,
            ),
        )

        routine?.exercises?.sortedBy { it.position }?.forEach { row ->
            val previous = dao.previousSetsFor(row.exerciseId)
            val count = maxOf(previous.size, row.targetSets ?: DEFAULT_TARGET_SETS, 1)

            dao.insertSets(
                (0 until count).map { index ->
                    val prior = previous.getOrNull(index) ?: previous.lastOrNull()
                    WorkoutSetEntity(
                        sessionId = sessionId,
                        exerciseId = row.exerciseId,
                        position = row.position,
                        setIndex = index,
                        reps = prior?.reps,
                        weightKg = prior?.weightKg,
                        rpe = null,
                        distanceM = prior?.distanceM,
                        durationSec = prior?.durationSec,
                        isWarmup = false,
                        isCompleted = false,
                    )
                },
            )
        }

        sessionId
    }

    suspend fun previousSetsFor(exerciseId: Long): List<PreviousSet> =
        withContext(io) { dao.previousSetsFor(exerciseId) }

    suspend fun addExerciseToSession(sessionId: Long, exerciseId: Long) = withContext(io) {
        val session = dao.session(sessionId) ?: return@withContext
        val position = (session.sets.maxOfOrNull { it.position } ?: -1) + 1
        val previous = dao.previousSetsFor(exerciseId)

        dao.insertSets(
            (0 until maxOf(previous.size, 1)).map { index ->
                val prior = previous.getOrNull(index)
                WorkoutSetEntity(
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    position = position,
                    setIndex = index,
                    reps = prior?.reps,
                    weightKg = prior?.weightKg,
                    rpe = null,
                    distanceM = prior?.distanceM,
                    durationSec = prior?.durationSec,
                    isWarmup = false,
                    isCompleted = false,
                )
            },
        )
    }

    suspend fun addSet(sessionId: Long, exerciseId: Long) = withContext(io) {
        val session = dao.session(sessionId) ?: return@withContext
        val forExercise = session.sets.filter { it.exerciseId == exerciseId }
        val last = forExercise.maxByOrNull { it.setIndex }

        dao.insertSet(
            WorkoutSetEntity(
                sessionId = sessionId,
                exerciseId = exerciseId,
                position = last?.position ?: 0,
                setIndex = (last?.setIndex ?: -1) + 1,
                reps = last?.reps,
                weightKg = last?.weightKg,
                rpe = null,
                distanceM = last?.distanceM,
                durationSec = last?.durationSec,
                isWarmup = false,
                isCompleted = false,
            ),
        )
    }

    suspend fun updateSet(set: WorkoutSetEntity) = withContext(io) { dao.updateSet(set) }

    suspend fun deleteSet(set: WorkoutSetEntity) = withContext(io) { dao.deleteSetAndReindex(set) }

    /** Clamped so a stray tap cannot store a rest of zero or of twenty minutes. */
    suspend fun setDefaultRest(exerciseId: Long, seconds: Int) = withContext(io) {
        dao.setDefaultRest(exerciseId, seconds.coerceIn(REST_RANGE))
    }

    suspend fun discardSession(sessionId: Long) = withContext(io) { dao.deleteSession(sessionId) }

    /**
     * Closes the session and works out what it achieved. A record only counts when it beats
     * everything stored before this session, so finishing twice cannot invent new PRs.
     */
    suspend fun finishSession(sessionId: Long): FinishedSession? = withContext(io) {
        val loaded = dao.session(sessionId) ?: return@withContext null
        val now = System.currentTimeMillis()
        val scored = loaded.sets.map { it.toScored() }

        val durationSec = ((now - loaded.session.startedAt) / 1000).toInt().coerceAtLeast(0)
        val volume = VolumeCalculator.totalKg(scored)
        val completed = scored.count { it.isCompleted }

        val bodyweight = profiles.latestWeightKg() ?: DEFAULT_BODYWEIGHT_KG
        val kcal = loaded.sets
            .filter { it.isCompleted && it.durationSec != null }
            .sumOf { set ->
                val met = dao.exercise(set.exerciseId)?.metValue ?: 5.0
                MetCalories.burned(met, bodyweight, set.durationSec ?: 0)
            }
            .let { cardio ->
                // Resistance work has no duration per set, so it is scored over the session.
                if (cardio > 0) cardio else MetCalories.burned(5.0, bodyweight, durationSec)
            }

        val records = PersonalRecords.bestsIn(scored)
            .filter { candidate ->
                val best = dao.bestRecord(candidate.exerciseId, candidate.type.name)
                best == null || candidate.value > best
            }
            .map {
                PersonalRecordEntity(
                    exerciseId = it.exerciseId,
                    type = it.type.name,
                    value = it.value,
                    date = loaded.session.date,
                    sessionId = sessionId,
                )
            }

        dao.insertRecords(records)
        dao.updateSession(
            loaded.session.copy(
                endedAt = now,
                totalVolumeKg = volume,
                kcalBurned = kcal,
                isFinished = true,
            ),
        )

        FinishedSession(sessionId, durationSec, volume, completed, kcal, records)
    }

    /**
     * Reads a finished session back: where the work went, and whether each lift moved.
     *
     * One query per exercise for its previous best, which is a handful of rows for a handful
     * of movements — cheaper than the join that would fetch them together, and this runs once
     * when a summary opens rather than on every frame.
     *
     * Sets whose exercise has since been deleted are dropped: the analysis is about muscle
     * groups and named lifts, and a row that can no longer say which it was has nothing to
     * contribute.
     */
    suspend fun analyse(sessionId: Long): SessionAnalysis? = withContext(io) {
        val loaded = dao.session(sessionId) ?: return@withContext null
        val startedAt = loaded.session.startedAt

        val exercises = loaded.sets
            .map { it.exerciseId }
            .distinct()
            .mapNotNull { dao.exercise(it) }
            .associateBy { it.id }

        val sets = loaded.sets.mapNotNull { set ->
            val exercise = exercises[set.exerciseId] ?: return@mapNotNull null
            AnalysedSet(
                exerciseId = set.exerciseId,
                exerciseName = exercise.name,
                muscleGroup = exercise.muscleGroup,
                reps = set.reps,
                weightKg = set.weightKg,
                durationSec = set.durationSec,
                isWarmup = set.isWarmup,
                isCompleted = set.isCompleted,
            )
        }

        val previousBests = exercises.keys.mapNotNull { exerciseId ->
            dao.previousSetsBefore(exerciseId, startedAt)
                .mapNotNull { Effort.of(it.reps, it.weightKg, it.durationSec) }
                .maxByOrNull { it.magnitude }
                ?.let { exerciseId to it }
        }.toMap()

        val previous = dao.previousSessionNamed(loaded.session.name, startedAt)
        val change = previous?.let {
            SessionChange(
                volumeKg = loaded.session.totalVolumeKg,
                previousVolumeKg = it.session.totalVolumeKg,
                workingSets = loaded.sets.count { set -> set.isLogged && !set.isWarmup },
                previousWorkingSets = it.sets.count { set -> set.isLogged && !set.isWarmup },
            )
        }

        SessionAnalyst.analyse(sets, previousBests, change)
    }

    /**
     * What is due, recomputed whenever a set changes.
     *
     * The week boundary is read once per subscription rather than per emission: it moves by a
     * day at a time, and the screen is re-subscribed long before the difference could matter.
     */
    fun observeNextWorkout(now: Long = System.currentTimeMillis()): Flow<NextWorkoutAdvice> {
        val weekStart = now - WEEK_MILLIS

        return dao.observeMuscleRecency(weekStart).map { recency ->
            val plan = NextWorkoutPlanner.plan(recency.map { it.toStanding(now) })
            NextWorkoutAdvice(
                plan = plan,
                movements = (plan as? NextWorkout.Train)?.let { movementsFor(it.group) }.orEmpty(),
            )
        }
    }

    private suspend fun movementsFor(group: String): List<ExerciseEntity> =
        dao.mostTrainedIn(group).ifEmpty { dao.startingPointsFor(group) }

    fun observeRecords(exerciseId: Long) = dao.observeRecords(exerciseId)

    suspend fun recordsForSession(sessionId: Long) = withContext(io) { dao.recordsForSession(sessionId) }

    /**
     * The records this session actually broke. The first time an exercise is trained every
     * metric is trivially a best, and announcing four PRs for one baseline set is noise — the
     * rows are still stored so the next session has something to beat.
     */
    suspend fun brokenRecordsForSession(sessionId: Long): List<PersonalRecordEntity> =
        withContext(io) {
            val startedAt = dao.session(sessionId)?.session?.startedAt ?: return@withContext emptyList()
            dao.recordsForSession(sessionId).filter {
                dao.recordsStandingBefore(it.exerciseId, it.type, sessionId, startedAt) > 0
            }
        }

    private companion object {
        const val DEFAULT_TARGET_SETS = 3
        const val DEFAULT_BODYWEIGHT_KG = 70.0
        const val WEEK_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}

/**
 * Elapsed 24-hour periods rather than calendar days.
 *
 * Recovery is a matter of hours, not of which side of midnight something fell on: a session
 * that finished at ten last night is fourteen hours old, and calling it "yesterday" would have
 * the app suggesting the same muscle group again over breakfast.
 */
private fun MuscleRecency.toStanding(now: Long) = MuscleStanding(
    group = muscleGroup,
    daysSince = ((now - lastTrainedAt) / (24L * 60 * 60 * 1000)).toInt().coerceAtLeast(0),
    setsLastWeek = setsLastWeek,
)

fun WorkoutSetEntity.toScored() = ScoredSet(
    exerciseId = exerciseId,
    reps = reps,
    weightKg = weightKg,
    durationSec = durationSec,
    isWarmup = isWarmup,
    isCompleted = isCompleted,
)

/** Record types as the summary screen labels them. */
/** The rest values a set button can reach, in seconds. */
val REST_RANGE = 15..600

fun RecordType.label(): String = when (this) {
    RecordType.MAX_WEIGHT -> "heaviest set"
    RecordType.EST_1RM -> "best estimated 1RM"
    RecordType.MAX_VOLUME -> "best set volume"
    RecordType.MAX_REPS -> "most reps"
}
