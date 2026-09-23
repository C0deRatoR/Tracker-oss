package com.yash.tracker.domain.workout

/** One muscle group's standing: when it was last worked, and how much lately. */
data class MuscleStanding(
    val group: String,
    /** Whole days since the last working set. Null when it has never been trained. */
    val daysSince: Int?,
    val setsLastWeek: Int,
)

/**
 * What to train next, or why not to.
 *
 * Separate cases rather than one nullable suggestion: "you are due for legs", "everything is
 * still recovering" and "there is no history to go on" are three different things to read, and
 * collapsing them loses the only one that is actually advice.
 */
sealed interface NextWorkout {

    data class Train(
        val group: String,
        val daysSince: Int?,
        val setsLastWeek: Int,
        /** Other groups equally due, so the card is a suggestion rather than an instruction. */
        val alsoDue: List<String>,
        val reason: String,
    ) : NextWorkout

    /** Everything trainable was worked inside its recovery window. */
    data class Recovering(val readyInDays: Int) : NextWorkout

    /** Nothing has been logged yet, so there is nothing to reason from. */
    data object NoHistory : NextWorkout
}

/**
 * Picks the muscle group with the best claim on the next session.
 *
 * Rest first, then neglect. A group worked yesterday is not a candidate however little it has
 * had lately — forty-eight hours is the usual floor for training a muscle again, and an app
 * that suggests chest the morning after chest is worse than one that suggests nothing. Among
 * what is actually rested, the one that has had least work in the last week wins, because that
 * is the imbalance a week of training accumulates.
 *
 * Deterministic, like every other suggestion in this app. What is due is arithmetic over dates
 * and set counts, and a number the user can check beats a sentence they have to trust.
 */
object NextWorkoutPlanner {

    /**
     * The groups worth being told to train.
     *
     * CARDIO and FULL_BODY are deliberately absent. Both are things the user chooses to do
     * rather than things that come due — nothing is out of balance for want of a treadmill —
     * and a full-body session is precisely the one that is not about a single group. They
     * still count as work when they are logged, through the sets they contribute.
     */
    private val TRAINABLE = listOf("CHEST", "BACK", "LEGS", "SHOULDERS", "ARMS", "CORE")

    fun plan(standings: List<MuscleStanding>): NextWorkout {
        if (standings.none { it.daysSince != null }) return NextWorkout.NoHistory

        val byGroup = standings.associateBy { it.group }
        val considered = TRAINABLE.map { group ->
            byGroup[group] ?: MuscleStanding(group, daysSince = null, setsLastWeek = 0)
        }

        val rested = considered.filter { (it.daysSince ?: Int.MAX_VALUE) >= RECOVERY_DAYS }
        if (rested.isEmpty()) {
            val soonest = considered.minOf { it.daysSince ?: 0 }
            return NextWorkout.Recovering(readyInDays = (RECOVERY_DAYS - soonest).coerceAtLeast(1))
        }

        // Least worked first, and the longest rested breaks the tie. Untrained groups sort to
        // the front of both, which is right: nothing is more overdue than never.
        val ranked = rested.sortedWith(
            compareBy<MuscleStanding> { it.setsLastWeek }
                .thenByDescending { it.daysSince ?: Int.MAX_VALUE },
        )
        val pick = ranked.first()

        return NextWorkout.Train(
            group = pick.group,
            daysSince = pick.daysSince,
            setsLastWeek = pick.setsLastWeek,
            // Capped at two. On a fresh diary four groups tie at nothing, and naming them all
            // turns a suggestion into a list of everything the user has not done.
            alsoDue = ranked.drop(1)
                .filter { it.setsLastWeek == pick.setsLastWeek }
                .map { it.group }
                .take(MAX_ALSO_DUE),
            reason = reasonFor(pick),
        )
    }

    private fun reasonFor(standing: MuscleStanding): String = when {
        standing.daysSince == null -> "Nothing logged for it yet."
        standing.setsLastWeek == 0 -> "No working sets in the last week."
        standing.daysSince >= STALE_DAYS ->
            "${standing.daysSince} days since the last one, and only " +
                "${standing.setsLastWeek} ${"set".plural(standing.setsLastWeek)} this week."
        else ->
            "${standing.setsLastWeek} ${"set".plural(standing.setsLastWeek)} this week, " +
                "fewer than anything else that is rested."
    }

    /** The usual floor before training a muscle group again. */
    const val RECOVERY_DAYS = 2

    /** Past this, the gap itself is the thing worth saying rather than the set count. */
    private const val STALE_DAYS = 5

    private const val MAX_ALSO_DUE = 2
}

private fun String.plural(count: Int): String = if (count == 1) this else "${this}s"
