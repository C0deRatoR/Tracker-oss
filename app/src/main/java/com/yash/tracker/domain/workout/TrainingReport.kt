package com.yash.tracker.domain.workout

import com.yash.tracker.data.local.entity.ExerciseEntity
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The muscles the catalogue names, folded into the ones worth reporting on.
 *
 * [isPriority] muscles are the ones a week of training is judged by. The rest are tracked and
 * shown, but never nagged about: forearms and lower back get their work from everything else,
 * and a suggestion to do ten sets of wrist curls is a suggestion nobody wants.
 */
enum class Muscle(val label: String, val sizeWeight: Double, val isPriority: Boolean = true) {
    CHEST("Chest", 1.0),
    SHOULDERS("Shoulders", 0.8),
    TRICEPS("Triceps", 0.6),
    BICEPS("Biceps", 0.6),
    LATS("Lats", 1.0),
    MIDDLE_BACK("Upper back", 1.0),
    QUADS("Quads", 1.0),
    HAMSTRINGS("Hamstrings", 1.0),
    GLUTES("Glutes", 1.0),
    ABS("Abs", 0.6),
    CALVES("Calves", 0.4, isPriority = false),
    TRAPS("Traps", 0.4, isPriority = false),
    LOWER_BACK("Lower back", 0.4, isPriority = false),
    FOREARMS("Forearms", 0.3, isPriority = false),
    HIPS("Hip ab/adductors", 0.3, isPriority = false),
    ;

    companion object {
        /** The catalogue's own spelling, from free-exercise-db. Unknown names are dropped. */
        fun ofCatalogue(name: String): Muscle? = when (name.trim().lowercase()) {
            "chest" -> CHEST
            "shoulders" -> SHOULDERS
            "triceps" -> TRICEPS
            "biceps" -> BICEPS
            "lats" -> LATS
            "middle back" -> MIDDLE_BACK
            "quadriceps" -> QUADS
            "hamstrings" -> HAMSTRINGS
            "glutes" -> GLUTES
            "abdominals" -> ABS
            "calves" -> CALVES
            "traps" -> TRAPS
            "lower back" -> LOWER_BACK
            "forearms" -> FOREARMS
            "adductors", "abductors" -> HIPS
            else -> null
        }

        /**
         * A custom exercise has only its coarse group. Arms split on force, because a curl
         * and a pushdown are not the same muscle; the rest take the group's main muscle.
         */
        fun ofGroup(group: String, force: String?): Muscle? = when (group.uppercase()) {
            "CHEST" -> CHEST
            "BACK" -> MIDDLE_BACK
            "SHOULDERS" -> SHOULDERS
            "ARMS" -> if (force.equals("push", ignoreCase = true)) TRICEPS else BICEPS
            "LEGS" -> QUADS
            "CORE" -> ABS
            else -> null
        }
    }
}

/** The movements a balanced week covers, derived from muscles and force, never from names. */
enum class Pattern(val label: String, val mainMuscle: Muscle) {
    SQUAT("Squat", Muscle.QUADS),
    HINGE("Hinge", Muscle.HAMSTRINGS),
    HORIZONTAL_PUSH("Horizontal push", Muscle.CHEST),
    VERTICAL_PUSH("Vertical push", Muscle.SHOULDERS),
    HORIZONTAL_PULL("Horizontal pull", Muscle.MIDDLE_BACK),
    VERTICAL_PULL("Vertical pull", Muscle.LATS),
    CORE("Core", Muscle.ABS),
}

enum class VolumeGrade { UNDER, LOW, PRODUCTIVE, HIGH }

enum class LiftStatus { PROGRESSING, STALLED, REGRESSING, NEW }

/** One working set from history, with what the analysis needs to know about its exercise. */
data class HistorySet(
    val sessionId: Long,
    val startedAt: Long,
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroup: String,
    val primaryMuscles: List<String>,
    val secondaryMuscles: List<String>,
    val force: String?,
    val mechanic: String?,
    val equipment: String,
    val reps: Int?,
    val weightKg: Double?,
    val rpe: Double?,
    val durationSec: Int?,
)

data class MuscleVolume(
    val muscle: Muscle,
    /** Primary sets count whole, secondary sets half. */
    val sets: Double,
    /** Distinct days the muscle had any work. */
    val days: Int,
    /** Whole 24-hour periods since its last set, or null if none in the window. */
    val daysSince: Int?,
    val grade: VolumeGrade,
)

data class RepBands(val strength: Int, val hypertrophy: Int, val endurance: Int) {
    val total: Int get() = strength + hypertrophy + endurance
}

data class LiftProgress(
    val exerciseId: Long,
    val name: String,
    val sessions: Int,
    val latest: Effort,
    val best: Effort,
    val status: LiftStatus,
    /** Signed, latest against the best before it. Null for a first session. */
    val changeFraction: Double?,
)

enum class SuggestionKind {
    REGRESSING,
    UNDER_TRAINED,
    MISSING_PATTERN,
    IMBALANCE,
    STALLED,
    LOW_EFFORT,
    EXCESS,
    LOW_FREQUENCY,
    FEWER_SESSIONS,
}

data class TrainingSuggestion(
    val kind: SuggestionKind,
    val title: String,
    val detail: String,
    /** Higher is more worth doing. Only meaningful for ordering. */
    val priority: Double,
    val exercises: List<ExerciseEntity> = emptyList(),
    /** Set when the muscle this is about is still inside its recovery window. */
    val readyInDays: Int? = null,
)

/** A rolling week of training read back: where the work went, what moved, what is missing. */
data class TrainingReport(
    val muscles: List<MuscleVolume>,
    val patterns: Map<Pattern, Int>,
    val pushSets: Int,
    val pullSets: Int,
    val compoundShare: Double?,
    val repBands: RepBands,
    val averageRpe: Double?,
    /** What share of the week's sets had an RPE typed in. */
    val rpeCoverage: Double,
    val lifts: List<LiftProgress>,
    val workingSets: Int,
    val sessionsThisWeek: Int,
    val sessionsPerWeekUsual: Double,
    val suggestions: List<TrainingSuggestion>,
) {
    val isEmpty: Boolean get() = workingSets == 0 && lifts.isEmpty()

    fun volumeOf(muscle: Muscle): Double = muscles.firstOrNull { it.muscle == muscle }?.sets ?: 0.0
}

/**
 * Reads a rolling week of training against the evidence on what builds muscle.
 *
 * Deterministic, like every other suggestion in the app, and every threshold is a named
 * constant below where it can be argued with. The volume bands follow the dose-response work
 * (Schoenfeld et al. 2017; the "landmarks" of Israetel et al.): under ten hard sets a week a
 * muscle is leaving growth on the table, past twenty extra sets mostly buy fatigue. Everything
 * else — patterns, balance, progression — is comparison against this user's own history.
 */
object TrainingAnalyst {

    /**
     * @param sets every logged working set from the last six weeks or so, oldest first.
     * @param catalogue the exercise library, for suggesting what to do about a gap.
     */
    fun analyse(sets: List<HistorySet>, now: Long, catalogue: List<ExerciseEntity>): TrainingReport {
        val week = sets.filter { it.startedAt >= now - WEEK_MS && it.startedAt <= now }
        val muscles = muscleVolumes(week, now)
        val patterns = Pattern.entries.associateWith { pattern -> week.count { patternOf(it) == pattern } }
        val push = week.count { isUpperPush(it) }
        val pull = week.count { isUpperPull(it) }
        val compound = week.count { it.mechanic.equals("compound", ignoreCase = true) }
        val typed = week.count { it.mechanic != null }
        val rated = week.mapNotNull { it.rpe }
        val lifts = lifts(sets)

        val month = sets.filter { it.startedAt >= now - MONTH_MS && it.startedAt <= now }
        val sessionsThisWeek = week.map { it.sessionId }.distinct().size
        val usual = month.map { it.sessionId }.distinct().size / (MONTH_MS / WEEK_MS.toDouble())

        val report = TrainingReport(
            muscles = muscles,
            patterns = patterns,
            pushSets = push,
            pullSets = pull,
            compoundShare = if (typed > 0) compound.toDouble() / typed else null,
            repBands = RepBands(
                strength = week.count { (it.reps ?: 0) in 1..STRENGTH_MAX_REPS },
                hypertrophy = week.count { (it.reps ?: 0) in (STRENGTH_MAX_REPS + 1)..HYPERTROPHY_MAX_REPS },
                endurance = week.count { (it.reps ?: 0) > HYPERTROPHY_MAX_REPS },
            ),
            averageRpe = rated.takeIf { it.isNotEmpty() }?.average(),
            rpeCoverage = if (week.isEmpty()) 0.0 else rated.size.toDouble() / week.size,
            lifts = lifts,
            workingSets = week.size,
            sessionsThisWeek = sessionsThisWeek,
            sessionsPerWeekUsual = usual,
            suggestions = emptyList(),
        )
        if (sets.isEmpty()) return report

        return report.copy(suggestions = suggestions(report, sets, catalogue))
    }

    /** The main muscle a set trained, with the catalogue's list first and the coarse group behind it. */
    fun primaryOf(set: HistorySet): List<Muscle> =
        set.primaryMuscles.mapNotNull(Muscle::ofCatalogue)
            .ifEmpty { listOfNotNull(Muscle.ofGroup(set.muscleGroup, set.force)) }

    fun patternOf(set: HistorySet): Pattern? {
        val primary = primaryOf(set).firstOrNull() ?: return null
        // A pattern is a compound movement: a fly trains the chest but is not a press, and a
        // straight-arm pulldown is not a pull-up. Only core work counts in isolation.
        if (primary != Muscle.ABS && set.mechanic.equals("isolation", ignoreCase = true)) return null
        val pull = set.force.equals("pull", ignoreCase = true)
        val push = set.force.equals("push", ignoreCase = true)

        return when (primary) {
            Muscle.LATS -> if (!push) Pattern.VERTICAL_PULL else null
            Muscle.MIDDLE_BACK -> if (!push) Pattern.HORIZONTAL_PULL else null
            Muscle.CHEST -> if (!pull) Pattern.HORIZONTAL_PUSH else null
            Muscle.SHOULDERS -> if (push) Pattern.VERTICAL_PUSH else null
            Muscle.QUADS -> Pattern.SQUAT
            Muscle.HAMSTRINGS, Muscle.GLUTES, Muscle.LOWER_BACK -> Pattern.HINGE
            Muscle.ABS -> Pattern.CORE
            else -> null
        }
    }

    private fun isUpperPush(set: HistorySet): Boolean =
        set.force.equals("push", ignoreCase = true) &&
            primaryOf(set).firstOrNull() in setOf(Muscle.CHEST, Muscle.SHOULDERS, Muscle.TRICEPS)

    private fun isUpperPull(set: HistorySet): Boolean =
        set.force.equals("pull", ignoreCase = true) &&
            primaryOf(set).firstOrNull() in
            setOf(Muscle.LATS, Muscle.MIDDLE_BACK, Muscle.BICEPS, Muscle.TRAPS, Muscle.SHOULDERS)

    private fun muscleVolumes(week: List<HistorySet>, now: Long): List<MuscleVolume> =
        Muscle.entries.map { muscle ->
            val primary = week.filter { muscle in primaryOf(it) }
            val secondary = week.filter { set ->
                muscle !in primaryOf(set) && set.secondaryMuscles.any { Muscle.ofCatalogue(it) == muscle }
            }
            val sets = primary.size + SECONDARY_CREDIT * secondary.size
            val touched = primary + secondary
            MuscleVolume(
                muscle = muscle,
                sets = sets,
                days = touched.map { it.startedAt / DAY_MS }.distinct().size,
                daysSince = touched.maxOfOrNull { it.startedAt }?.let { ((now - it) / DAY_MS).toInt() },
                grade = when {
                    sets < UNDER_SETS -> VolumeGrade.UNDER
                    sets < PRODUCTIVE_SETS -> VolumeGrade.LOW
                    sets <= HIGH_SETS -> VolumeGrade.PRODUCTIVE
                    else -> VolumeGrade.HIGH
                },
            )
        }

    /**
     * Each exercise's best effort per session, and whether that line is still going up.
     *
     * Stalled means the last [STALL_SESSIONS] sessions have not beaten what came before them
     * by the level band — three flat sessions in a row is past the noise of a bad day.
     * Regressing means the last two are both clearly under the best before them, which is a
     * fatigue signal rather than a programming one.
     */
    private fun lifts(sets: List<HistorySet>): List<LiftProgress> =
        sets.groupBy { it.exerciseId }.mapNotNull { (exerciseId, exerciseSets) ->
            val perSession = exerciseSets
                .groupBy { it.sessionId }
                .values
                .sortedBy { it.first().startedAt }
                .mapNotNull { session ->
                    session.mapNotNull { Effort.of(it.reps, it.weightKg, it.durationSec) }.maxByOrNull { it.magnitude }
                }
            val latest = perSession.lastOrNull() ?: return@mapNotNull null
            val comparable = perSession.filter { it.comparableWith(latest) }
            val before = comparable.dropLast(1)
            val bestBefore = before.maxByOrNull { it.magnitude }

            val change = bestBefore?.takeIf { it.magnitude > 0 }
                ?.let { (latest.magnitude - it.magnitude) / it.magnitude }

            val status = when {
                bestBefore == null -> LiftStatus.NEW
                isRegressing(comparable) -> LiftStatus.REGRESSING
                isStalled(comparable) -> LiftStatus.STALLED
                else -> LiftStatus.PROGRESSING
            }

            LiftProgress(
                exerciseId = exerciseId,
                name = exerciseSets.first().exerciseName,
                sessions = comparable.size,
                latest = latest,
                best = comparable.maxBy { it.magnitude },
                status = status,
                changeFraction = change,
            )
        }.sortedWith(compareBy<LiftProgress> { it.status.ordinal }.thenByDescending { it.sessions })

    private fun isStalled(efforts: List<Effort>): Boolean {
        if (efforts.size <= STALL_SESSIONS) return false
        val recent = efforts.takeLast(STALL_SESSIONS)
        val bar = efforts.dropLast(STALL_SESSIONS).maxOf { it.magnitude } * (1 + LEVEL_BAND)
        return recent.none { it.magnitude > bar }
    }

    private fun isRegressing(efforts: List<Effort>): Boolean {
        if (efforts.size < REGRESS_SESSIONS + 1) return false
        val bar = efforts.dropLast(REGRESS_SESSIONS).maxOf { it.magnitude } * (1 - REGRESS_DROP)
        return efforts.takeLast(REGRESS_SESSIONS).all { it.magnitude < bar }
    }

    private fun suggestions(
        report: TrainingReport,
        history: List<HistorySet>,
        catalogue: List<ExerciseEntity>,
    ): List<TrainingSuggestion> {
        val picker = ExercisePicker(history, catalogue)
        val byMuscle = report.muscles.associateBy { it.muscle }
        fun readyIn(muscle: Muscle): Int? = byMuscle[muscle]?.daysSince
            ?.let { NextWorkoutPlanner.RECOVERY_DAYS - it }
            ?.takeIf { it > 0 }

        return buildList {
            report.lifts.filter { it.status == LiftStatus.REGRESSING }.forEach { lift ->
                val drop = ((lift.changeFraction ?: 0.0) * -100).roundToInt()
                add(
                    TrainingSuggestion(
                        kind = SuggestionKind.REGRESSING,
                        title = "${lift.name} is down $drop% on its best",
                        detail = "Two sessions running under your best usually means fatigue, not " +
                            "weakness. Take a lighter week — about 60% of your usual sets — and " +
                            "check sleep and protein before adding anything.",
                        priority = PRIORITY_REGRESSING,
                    ),
                )
            }

            report.muscles
                .filter { it.muscle.isPriority && it.sets < PRODUCTIVE_SETS }
                .forEach { volume ->
                    val short = PRODUCTIVE_SETS - volume.sets
                    val perSession = if (short > 6) "two sessions of 3–4 sets" else "${short.roundUp()} more sets"
                    add(
                        TrainingSuggestion(
                            kind = SuggestionKind.UNDER_TRAINED,
                            title = "${volume.muscle.label}: ${volume.sets.format()} sets this week",
                            detail = "Aim for $PRODUCTIVE_SETS–$HIGH_SETS hard sets a week. " +
                                "$perSession gets you there.".replaceFirstChar(Char::uppercase),
                            priority = PRIORITY_UNDER * volume.muscle.sizeWeight * short / PRODUCTIVE_SETS,
                            exercises = picker.forMuscle(volume.muscle),
                            readyInDays = readyIn(volume.muscle),
                        ),
                    )
                }

            // A pattern is only worth naming when its main muscle is otherwise covered — chest
            // done entirely with flyes. When the muscle itself is short, the line above says so.
            report.patterns.filter { (pattern, sets) ->
                sets == 0 && (byMuscle[pattern.mainMuscle]?.sets ?: 0.0) >= PRODUCTIVE_SETS
            }.keys.forEach { pattern ->
                add(
                    TrainingSuggestion(
                        kind = SuggestionKind.MISSING_PATTERN,
                        title = "No ${pattern.label.lowercase()} this week",
                        detail = patternWhy(pattern),
                        priority = PRIORITY_PATTERN,
                        exercises = picker.forPattern(pattern),
                    ),
                )
            }

            val push = report.pushSets
            val pull = report.pullSets
            if (push + pull >= MIN_BALANCE_SETS) {
                if (pull * MAX_RATIO < push) {
                    add(
                        TrainingSuggestion(
                            kind = SuggestionKind.IMBALANCE,
                            title = "Pull is behind push: $pull vs $push sets",
                            detail = "Upper-body pulling should roughly match pushing to keep " +
                                "shoulders healthy. Add ${(push - pull) / 2} or more sets of rows or pulldowns.",
                            priority = PRIORITY_IMBALANCE + (push - pull),
                            exercises = picker.forPattern(Pattern.HORIZONTAL_PULL) +
                                picker.forPattern(Pattern.VERTICAL_PULL).take(1),
                        ),
                    )
                } else if (push * MAX_RATIO < pull) {
                    add(
                        TrainingSuggestion(
                            kind = SuggestionKind.IMBALANCE,
                            title = "Push is behind pull: $push vs $pull sets",
                            detail = "Pressing work has fallen behind your pulling. " +
                                "Add ${(pull - push) / 2} or more sets of presses.",
                            priority = PRIORITY_IMBALANCE + (pull - push),
                            exercises = picker.forPattern(Pattern.HORIZONTAL_PUSH) +
                                picker.forPattern(Pattern.VERTICAL_PUSH).take(1),
                        ),
                    )
                }
            }

            val quads = report.volumeOf(Muscle.QUADS)
            val hamstrings = report.volumeOf(Muscle.HAMSTRINGS)
            if (quads >= MIN_QUAD_SETS && quads > hamstrings * MAX_QUAD_HAM) {
                add(
                    TrainingSuggestion(
                        kind = SuggestionKind.IMBALANCE,
                        title = "Quads ${quads.format()} vs hamstrings ${hamstrings.format()} sets",
                        detail = "Hamstrings are well under half your quad work. A hinge or a leg " +
                            "curl on leg day evens it out and protects the knees.",
                        priority = PRIORITY_IMBALANCE,
                        exercises = picker.forMuscle(Muscle.HAMSTRINGS),
                        readyInDays = readyIn(Muscle.HAMSTRINGS),
                    ),
                )
            }

            report.lifts.filter { it.status == LiftStatus.STALLED }.forEach { lift ->
                val typicalReps = history.filter { it.exerciseId == lift.exerciseId }
                    .mapNotNull { it.reps }
                    .takeIf { it.isNotEmpty() }
                    ?.average()
                val fix = if (typicalReps != null && typicalReps <= STRENGTH_MAX_REPS) {
                    "Switch to sets of 8–12 for three weeks, then come back to heavy."
                } else {
                    "Add one set, or add 2.5 kg and accept fewer reps — or swap to a variation for a few weeks."
                }
                add(
                    TrainingSuggestion(
                        kind = SuggestionKind.STALLED,
                        title = "${lift.name} has stalled for $STALL_SESSIONS sessions",
                        detail = fix,
                        priority = PRIORITY_STALLED,
                        exercises = picker.variationsOf(lift.exerciseId),
                    ),
                )
            }

            val rpe = report.averageRpe
            if (rpe != null && rpe < LOW_RPE && report.rpeCoverage >= MIN_RPE_COVERAGE &&
                report.workingSets >= MIN_RATED_SETS
            ) {
                add(
                    TrainingSuggestion(
                        kind = SuggestionKind.LOW_EFFORT,
                        title = "Most sets were far from failure (RPE ${"%.1f".format(rpe)})",
                        detail = "Sets end up growing muscle when they finish 1–3 reps short of " +
                            "failure — RPE 7 to 9. Add weight or reps until the last reps are hard.",
                        priority = PRIORITY_LOW_EFFORT,
                    ),
                )
            }

            report.muscles.filter { it.grade == VolumeGrade.HIGH }.forEach { volume ->
                add(
                    TrainingSuggestion(
                        kind = SuggestionKind.EXCESS,
                        title = "${volume.muscle.label}: ${volume.sets.format()} sets this week",
                        detail = "Past about $HIGH_SETS a week, extra sets mostly add fatigue. " +
                            "Trim ${(volume.sets - HIGH_SETS).roundUp()} and put the time into " +
                            "something that is behind.",
                        priority = PRIORITY_EXCESS,
                    ),
                )
            }

            report.muscles
                .filter { it.muscle.isPriority && it.days == 1 && it.sets >= SPLIT_WORTHY_SETS }
                .forEach { volume ->
                    add(
                        TrainingSuggestion(
                            kind = SuggestionKind.LOW_FREQUENCY,
                            title = "${volume.muscle.label} trained on one day only",
                            detail = "The same ${volume.sets.format()} sets split over two days " +
                                "grows more than one long session of them.",
                            priority = PRIORITY_FREQUENCY,
                        ),
                    )
                }

            if (report.sessionsPerWeekUsual >= MIN_USUAL_SESSIONS &&
                report.sessionsThisWeek < report.sessionsPerWeekUsual * FEWER_SESSIONS_SHARE
            ) {
                add(
                    TrainingSuggestion(
                        kind = SuggestionKind.FEWER_SESSIONS,
                        title = "${report.sessionsThisWeek} ${"session".plural(report.sessionsThisWeek)} " +
                            "in the last 7 days",
                        detail = "You usually manage about ${"%.1f".format(report.sessionsPerWeekUsual)} " +
                            "a week. Even a short one keeps the streak of progress going.",
                        priority = PRIORITY_SESSIONS,
                    ),
                )
            }
        }.sortedByDescending { it.priority }
    }

    private fun patternWhy(pattern: Pattern): String = when (pattern) {
        Pattern.SQUAT -> "A knee-dominant lift builds the quads and carries over to everything on your feet."
        Pattern.HINGE -> "A hip hinge trains the hamstrings, glutes and lower back together — the back of the body."
        Pattern.HORIZONTAL_PUSH -> "A press in front of you is the main chest builder."
        Pattern.VERTICAL_PUSH -> "An overhead press keeps the shoulders strong through their full range."
        Pattern.HORIZONTAL_PULL -> "Rows build the upper back and balance out pressing."
        Pattern.VERTICAL_PULL -> "Pulldowns and pull-ups are what builds width through the lats."
        Pattern.CORE -> "Direct core work — a plank or a crunch — is two sets on the end of any session."
    }

    /** Where volume changes band. Hard sets per muscle per week. */
    const val UNDER_SETS = 4.0
    const val PRODUCTIVE_SETS = 10
    const val HIGH_SETS = 20

    /** A secondary muscle does work, but less of it: half a set is the usual credit. */
    private const val SECONDARY_CREDIT = 0.5

    private const val STRENGTH_MAX_REPS = 5
    private const val HYPERTROPHY_MAX_REPS = 12

    private const val STALL_SESSIONS = 3
    private const val LEVEL_BAND = 0.02
    private const val REGRESS_SESSIONS = 2
    private const val REGRESS_DROP = 0.05

    /** Past this ratio either way, push and pull are out of balance. */
    private const val MAX_RATIO = 1.5
    private const val MIN_BALANCE_SETS = 8
    private const val MAX_QUAD_HAM = 2.0
    private const val MIN_QUAD_SETS = 6.0

    private const val LOW_RPE = 7.0
    private const val MIN_RPE_COVERAGE = 0.5
    private const val MIN_RATED_SETS = 6

    /** Enough sets that splitting them across two days is worth saying. */
    private const val SPLIT_WORTHY_SETS = 8.0

    private const val MIN_USUAL_SESSIONS = 2.0
    private const val FEWER_SESSIONS_SHARE = 0.5

    /** Above anything else: going backwards is a fatigue problem, and more work makes it worse. */
    private const val PRIORITY_REGRESSING = 110.0
    private const val PRIORITY_UNDER = 100.0
    private const val PRIORITY_PATTERN = 55.0
    private const val PRIORITY_IMBALANCE = 50.0
    private const val PRIORITY_STALLED = 40.0
    private const val PRIORITY_LOW_EFFORT = 35.0
    private const val PRIORITY_EXCESS = 30.0
    private const val PRIORITY_FREQUENCY = 25.0
    private const val PRIORITY_SESSIONS = 20.0

    const val DAY_MS = 24L * 60 * 60 * 1000
    const val WEEK_MS = 7 * DAY_MS
    const val MONTH_MS = 28 * DAY_MS

    /** How far back the analysis needs history: long enough to see a lift stall. */
    const val HISTORY_MS = 42 * DAY_MS
}

/**
 * What to do about a gap: the user's own exercises first, the catalogue after.
 *
 * Someone who does lat pulldowns should be told to do more lat pulldowns, not introduced to a
 * kipping pull-up. Only when their history has nothing for a muscle does the catalogue fill in
 * — compounds first, on equipment they already use, simplest first.
 */
private class ExercisePicker(history: List<HistorySet>, private val catalogue: List<ExerciseEntity>) {

    private val usage: Map<Long, Int> = history.groupingBy { it.exerciseId }.eachCount()
    private val equipment: Set<String> = history.map { it.equipment }.toSet()
    private val byId = catalogue.associateBy { it.id }

    fun forMuscle(muscle: Muscle): List<ExerciseEntity> = pick { exercise ->
        exercise.primaryMuscleList.firstOrNull()?.let(Muscle::ofCatalogue) == muscle
    }

    fun forPattern(pattern: Pattern): List<ExerciseEntity> = pick { exercise ->
        TrainingAnalyst.patternOf(exercise.asProbe()) == pattern
    }

    /** Other ways to train what a stalled lift trains, for a few weeks away from it. */
    fun variationsOf(exerciseId: Long): List<ExerciseEntity> {
        val source = byId[exerciseId] ?: return emptyList()
        val muscle = source.primaryMuscleList.firstOrNull() ?: return emptyList()
        return pick(limit = 2) { exercise ->
            exercise.id != exerciseId &&
                exercise.primaryMuscleList.firstOrNull() == muscle &&
                exercise.mechanic == source.mechanic
        }
    }

    private fun pick(limit: Int = PICKS, matches: (ExerciseEntity) -> Boolean): List<ExerciseEntity> {
        val candidates = catalogue.filter(matches)
        val own = candidates.filter { it.id in usage }.sortedByDescending { usage.getValue(it.id) }
        val fresh = candidates
            .filter { it.id !in usage }
            .sortedWith(
                compareBy<ExerciseEntity>(
                    { if (it.mechanic.equals("compound", ignoreCase = true)) 0 else 1 },
                    { if (equipment.isEmpty() || it.equipment in equipment) 0 else 1 },
                    { LEVELS.indexOf(it.level).let { index -> if (index < 0) LEVELS.size else index } },
                    { it.name.length },
                ),
            )
        return (own + fresh).take(limit)
    }

    private companion object {
        const val PICKS = 3
        val LEVELS = listOf("beginner", "intermediate", "expert")
    }
}

private fun ExerciseEntity.asProbe() = HistorySet(
    sessionId = 0,
    startedAt = 0,
    exerciseId = id,
    exerciseName = name,
    muscleGroup = muscleGroup,
    primaryMuscles = primaryMuscleList,
    secondaryMuscles = secondaryMuscleList,
    force = force,
    mechanic = mechanic,
    equipment = equipment,
    reps = null,
    weightKg = null,
    rpe = null,
    durationSec = null,
)

private fun Double.format(): String =
    if (this == this.roundToInt().toDouble()) roundToInt().toString() else "%.1f".format(this)

private fun Double.roundUp(): Int = max(1, kotlin.math.ceil(this).toInt())

private fun String.plural(count: Int): String = if (count == 1) this else "${this}s"
