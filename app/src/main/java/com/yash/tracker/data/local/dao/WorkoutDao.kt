package com.yash.tracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.data.local.entity.PersonalRecordEntity
import com.yash.tracker.data.local.entity.RoutineEntity
import com.yash.tracker.data.local.entity.RoutineExerciseEntity
import com.yash.tracker.data.local.entity.WorkoutSessionEntity
import com.yash.tracker.data.local.entity.WorkoutSetEntity
import kotlinx.coroutines.flow.Flow

data class RoutineWithExercises(
    @Embedded val routine: RoutineEntity,
    @Relation(parentColumn = "id", entityColumn = "routine_id")
    val exercises: List<RoutineExerciseEntity>,
)

data class SessionWithSets(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(parentColumn = "id", entityColumn = "session_id")
    val sets: List<WorkoutSetEntity>,
)

/** When a muscle group was last worked, and how much of it there has been lately. */
data class MuscleRecency(
    val muscleGroup: String,
    val lastTrainedAt: Long,
    val setsLastWeek: Int,
)

/** The last numbers logged for an exercise, which prefill the next session's set rows. */
data class PreviousSet(
    val setIndex: Int,
    val reps: Int?,
    val weightKg: Double?,
    val distanceM: Double?,
    val durationSec: Int?,
)

@Dao
interface WorkoutDao {

    // --- exercises ---------------------------------------------------------------------

    @Query("SELECT * FROM exercise ORDER BY name ASC")
    fun observeExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE id = :id")
    suspend fun exercise(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercise WHERE id = :id")
    fun observeExercise(id: Long): Flow<ExerciseEntity?>

    /**
     * The rest an exercise defaults to. Seeded per movement — three minutes for a bench, less
     * for an isolation — but the seed's guess is not the user's, so it is writable.
     */
    @Query("UPDATE exercise SET default_rest_sec = :seconds WHERE id = :id")
    suspend fun setDefaultRest(id: Long, seconds: Int)

    /**
     * Moves everything that points at one exercise onto another, so a duplicate row can be
     * retired without orphaning the sets logged against it. exercise_id carries no foreign
     * key, so nothing would have complained — the history would simply have stopped rendering.
     */
    @Transaction
    suspend fun repointExercise(from: Long, to: Long) {
        repointSets(from, to)
        repointRoutineExercises(from, to)
        repointRecords(from, to)
    }

    @Query("UPDATE workout_set SET exercise_id = :to WHERE exercise_id = :from")
    suspend fun repointSets(from: Long, to: Long)

    @Query("UPDATE routine_exercise SET exercise_id = :to WHERE exercise_id = :from")
    suspend fun repointRoutineExercises(from: Long, to: Long)

    @Query("UPDATE personal_record SET exercise_id = :to WHERE exercise_id = :from")
    suspend fun repointRecords(from: Long, to: Long)

    /**
     * Every exercise something still points at — a logged set, a routine, or a record.
     *
     * Asked for in one query rather than per row: retiring the old catalogue tests eight
     * hundred rows against it, and exercise_id carries no foreign key, so this is the only
     * thing standing between a prune and history that renders as blank lines.
     */
    @Query(
        """
        SELECT DISTINCT exercise_id FROM workout_set
        UNION SELECT DISTINCT exercise_id FROM routine_exercise
        UNION SELECT DISTINCT exercise_id FROM personal_record
        """,
    )
    suspend fun referencedExerciseIds(): List<Long>

    @Query("SELECT * FROM exercise WHERE name LIKE '%' || :term || '%' ORDER BY name ASC LIMIT :limit")
    suspend fun searchExercises(term: String, limit: Int = 60): List<ExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercise(exercise: ExerciseEntity): Long

    // --- routines ----------------------------------------------------------------------

    @Transaction
    @Query("SELECT * FROM routine ORDER BY created_at DESC")
    fun observeRoutines(): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("SELECT * FROM routine WHERE id = :id")
    suspend fun routine(id: Long): RoutineWithExercises?

    @Insert
    suspend fun insertRoutine(routine: RoutineEntity): Long

    @Insert
    suspend fun insertRoutineExercises(rows: List<RoutineExerciseEntity>)

    @Transaction
    suspend fun insertRoutineWithExercises(
        routine: RoutineEntity,
        exercises: List<RoutineExerciseEntity>,
    ): Long {
        val id = insertRoutine(routine)
        insertRoutineExercises(exercises.map { it.copy(routineId = id) })
        return id
    }

    @Query("DELETE FROM routine WHERE id = :id")
    suspend fun deleteRoutine(id: Long)

    @Query("UPDATE routine SET name = :name WHERE id = :id")
    suspend fun renameRoutine(id: Long, name: String)

    @Query("DELETE FROM routine_exercise WHERE routine_id = :routineId")
    suspend fun clearRoutineExercises(routineId: Long)

    /**
     * Replaces a routine's exercises wholesale rather than diffing them.
     *
     * The list carries an order, and every row's position shifts when one moves, so a diff
     * would rewrite almost all of them anyway. Deleting and re-inserting inside one transaction
     * is the same write with none of the bookkeeping — and the rows hold no history worth
     * preserving, since sessions copy what they need at the time.
     */
    @Transaction
    suspend fun replaceRoutineExercises(routineId: Long, exercises: List<RoutineExerciseEntity>) {
        clearRoutineExercises(routineId)
        insertRoutineExercises(exercises.map { it.copy(routineId = routineId) })
    }

    /** When a routine was last performed, for the "2 days ago" line on its card. */
    @Query("SELECT MAX(started_at) FROM workout_session WHERE routine_id = :routineId AND is_finished = 1")
    suspend fun routineLastPerformed(routineId: Long): Long?

    // --- sessions ----------------------------------------------------------------------

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :id")
    fun observeSession(id: Long): Flow<SessionWithSets?>

    @Transaction
    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun session(id: Long): SessionWithSets?

    /** An unfinished session is resumed rather than started again. */
    @Query("SELECT id FROM workout_session WHERE is_finished = 0 ORDER BY started_at DESC LIMIT 1")
    suspend fun activeSessionId(): Long?

    @Transaction
    @Query("SELECT * FROM workout_session WHERE is_finished = 1 ORDER BY started_at DESC LIMIT :limit")
    fun observeRecentSessions(limit: Int = 50): Flow<List<SessionWithSets>>

    @Query("DELETE FROM workout_session WHERE id = :id")
    suspend fun deleteSession(id: Long)

    // --- sets --------------------------------------------------------------------------

    @Insert
    suspend fun insertSet(set: WorkoutSetEntity): Long

    @Insert
    suspend fun insertSets(sets: List<WorkoutSetEntity>)

    @Update
    suspend fun updateSet(set: WorkoutSetEntity)

    @Delete
    suspend fun deleteSet(set: WorkoutSetEntity)

    @Query(
        """
        SELECT * FROM workout_set
        WHERE session_id = :sessionId AND exercise_id = :exerciseId
        ORDER BY set_index ASC
        """,
    )
    suspend fun setsFor(sessionId: Long, exerciseId: Long): List<WorkoutSetEntity>

    /**
     * Deleting the middle set of three used to leave them numbered 1, 2, 4 — set_index is the
     * label as well as the key that lines a row up with the same set from last time, so the
     * survivors are renumbered rather than left with a hole.
     */
    @Transaction
    suspend fun deleteSetAndReindex(set: WorkoutSetEntity) {
        deleteSet(set)
        setsFor(set.sessionId, set.exerciseId).forEachIndexed { index, row ->
            if (row.setIndex != index) updateSet(row.copy(setIndex = index))
        }
    }

    /**
     * The completed sets from the last finished session that included this exercise. Warmups
     * are excluded so the prefill reflects working weight.
     */
    @Query(
        """
        SELECT set_index AS setIndex, reps, weight_kg AS weightKg,
               distance_m AS distanceM, duration_sec AS durationSec
        FROM workout_set
        WHERE exercise_id = :exerciseId AND is_completed = 1 AND is_warmup = 0
          AND session_id = (
            SELECT s.id FROM workout_session s
            JOIN workout_set ws ON ws.session_id = s.id
            WHERE ws.exercise_id = :exerciseId AND s.is_finished = 1 AND ws.is_completed = 1
            ORDER BY s.started_at DESC LIMIT 1
          )
        ORDER BY set_index ASC
        """,
    )
    suspend fun previousSetsFor(exerciseId: Long): List<PreviousSet>

    /**
     * The same, but from before a given session — what the exercise was doing last time, as
     * seen from a session that has since been finished.
     *
     * [previousSetsFor] cannot answer this: it reads the newest finished session, which is the
     * one being analysed, so every lift would be compared against itself and tie.
     */
    @Query(
        """
        SELECT set_index AS setIndex, reps, weight_kg AS weightKg,
               distance_m AS distanceM, duration_sec AS durationSec
        FROM workout_set
        WHERE exercise_id = :exerciseId AND is_completed = 1 AND is_warmup = 0
          AND session_id = (
            SELECT s.id FROM workout_session s
            JOIN workout_set ws ON ws.session_id = s.id
            WHERE ws.exercise_id = :exerciseId AND s.is_finished = 1 AND ws.is_completed = 1
              AND s.started_at < :startedAt
            ORDER BY s.started_at DESC LIMIT 1
          )
        ORDER BY set_index ASC
        """,
    )
    suspend fun previousSetsBefore(exerciseId: Long, startedAt: Long): List<PreviousSet>

    /**
     * The last session that went by this name, for "against last time".
     *
     * Matched on name rather than routine_id so an ad-hoc session still has something to
     * compare against, and so renaming a routine does not sever its own history.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM workout_session
        WHERE is_finished = 1 AND name = :name AND started_at < :startedAt
        ORDER BY started_at DESC LIMIT 1
        """,
    )
    suspend fun previousSessionNamed(name: String, startedAt: Long): SessionWithSets?

    /**
     * Every muscle group that has ever been worked, with when and how much lately.
     *
     * One grouped query rather than a pass per group: there are only ever a handful of groups,
     * and the joins are the expensive part. Warmups and ticked-but-empty rows are excluded on
     * the same rule the rest of the app uses — a set that records nothing is not work.
     */
    @Query(
        """
        SELECT e.muscle_group AS muscleGroup,
               MAX(s.started_at) AS lastTrainedAt,
               SUM(CASE WHEN s.started_at >= :weekStart THEN 1 ELSE 0 END) AS setsLastWeek
        FROM workout_set ws
        JOIN workout_session s ON s.id = ws.session_id
        JOIN exercise e ON e.id = ws.exercise_id
        WHERE s.is_finished = 1 AND ws.is_completed = 1 AND ws.is_warmup = 0
          AND (ws.reps IS NOT NULL OR ws.weight_kg IS NOT NULL
               OR ws.distance_m IS NOT NULL OR ws.duration_sec IS NOT NULL)
        GROUP BY e.muscle_group
        """,
    )
    fun observeMuscleRecency(weekStart: Long): Flow<List<MuscleRecency>>

    /** The movements this user actually trains for a group, most-used first. */
    @Query(
        """
        SELECT e.* FROM exercise e
        JOIN workout_set ws ON ws.exercise_id = e.id
        JOIN workout_session s ON s.id = ws.session_id
        WHERE e.muscle_group = :group AND s.is_finished = 1
          AND ws.is_completed = 1 AND ws.is_warmup = 0
        GROUP BY e.id
        ORDER BY COUNT(*) DESC
        LIMIT :limit
        """,
    )
    suspend fun mostTrainedIn(group: String, limit: Int = 3): List<ExerciseEntity>

    /**
     * What the catalogue would start you on for a group you have never trained.
     *
     * Compounds, free weights before machines, and the simplest of those first — roughly the
     * order a coach would give them in. Equipment has to lead: sorting on level alone leaves
     * alphabetical order to decide, which answers "how do I train legs" with a box jump.
     */
    @Query(
        """
        SELECT * FROM exercise
        WHERE muscle_group = :group AND mechanic = 'compound'
        ORDER BY CASE equipment
                   WHEN 'BARBELL' THEN 0 WHEN 'DUMBBELL' THEN 1 WHEN 'BODYWEIGHT' THEN 2
                   ELSE 3
                 END,
                 CASE level WHEN 'beginner' THEN 0 WHEN 'intermediate' THEN 1 ELSE 2 END,
                 name ASC
        LIMIT :limit
        """,
    )
    suspend fun startingPointsFor(group: String, limit: Int = 3): List<ExerciseEntity>

    // --- personal records ---------------------------------------------------------------

    @Insert
    suspend fun insertRecords(records: List<PersonalRecordEntity>)

    @Query("SELECT MAX(value) FROM personal_record WHERE exercise_id = :exerciseId AND type = :type")
    suspend fun bestRecord(exerciseId: Long, type: String): Double?

    @Query("SELECT * FROM personal_record WHERE session_id = :sessionId")
    suspend fun recordsForSession(sessionId: Long): List<PersonalRecordEntity>

    /**
     * How many records of this type already stood for the exercise before this session began.
     * Zero means the session only set the first baseline, which is not a record it broke.
     */
    @Query(
        """
        SELECT COUNT(*) FROM personal_record r
        JOIN workout_session s ON s.id = r.session_id
        WHERE r.exercise_id = :exerciseId AND r.type = :type
          AND r.session_id <> :sessionId AND s.started_at <= :startedAt
        """,
    )
    suspend fun recordsStandingBefore(
        exerciseId: Long,
        type: String,
        sessionId: Long,
        startedAt: Long,
    ): Int

    @Query("SELECT * FROM personal_record WHERE exercise_id = :exerciseId ORDER BY date DESC")
    fun observeRecords(exerciseId: Long): Flow<List<PersonalRecordEntity>>

    // --- one exercise's own history ------------------------------------------------------

    /**
     * Every finished session that included this exercise, newest first.
     *
     * Returns whole sessions and filters their sets in [observeHistoryFor], rather than
     * selecting sets and re-joining: Room's @Relation cannot take a parameter, so the filter
     * has to happen somewhere, and doing it in Kotlin keeps one query instead of two.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM workout_session
        WHERE is_finished = 1
          AND id IN (SELECT session_id FROM workout_set WHERE exercise_id = :exerciseId)
        ORDER BY started_at DESC
        LIMIT :limit
        """,
    )
    fun observeSessionsWith(exerciseId: Long, limit: Int = 60): Flow<List<SessionWithSets>>

    /** Ticked *and* filled in — see [com.yash.tracker.data.local.entity.isLogged]. */
    @Query(
        """
        SELECT COUNT(*) FROM workout_set
        WHERE exercise_id = :exerciseId AND is_completed = 1
          AND (reps IS NOT NULL OR weight_kg IS NOT NULL
               OR distance_m IS NOT NULL OR duration_sec IS NOT NULL)
        """,
    )
    fun observeCompletedSetCount(exerciseId: Long): Flow<Int>
}
