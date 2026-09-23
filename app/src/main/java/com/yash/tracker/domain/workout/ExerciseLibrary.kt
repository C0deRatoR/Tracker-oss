package com.yash.tracker.domain.workout

import com.yash.tracker.data.local.entity.ExerciseEntity

/**
 * The two axes the catalogue is navigated by.
 *
 * The seed only ever writes these values, but it is a file on disk rather than a type, so both
 * enums resolve leniently and fall back rather than throwing at a row that says something new.
 */
enum class MuscleGroup(val stored: String, val label: String) {
    CHEST("CHEST", "Chest"),
    BACK("BACK", "Back"),
    SHOULDERS("SHOULDERS", "Shoulders"),
    ARMS("ARMS", "Arms"),
    LEGS("LEGS", "Legs"),
    CORE("CORE", "Core"),
    FULL_BODY("FULL_BODY", "Full body"),
    CARDIO("CARDIO", "Cardio"),
    ;

    companion object {
        fun of(stored: String?): MuscleGroup? =
            entries.firstOrNull { it.stored.equals(stored?.trim(), ignoreCase = true) }

        fun label(stored: String?): String =
            of(stored)?.label ?: stored?.lowercase()?.replace('_', ' ').orEmpty()
    }
}

enum class Equipment(val stored: String, val label: String) {
    BARBELL("BARBELL", "Barbell"),
    DUMBBELL("DUMBBELL", "Dumbbell"),
    CABLE("CABLE", "Cable"),
    MACHINE("MACHINE", "Machine"),
    BODYWEIGHT("BODYWEIGHT", "Bodyweight"),
    OTHER("OTHER", "Other"),
    ;

    companion object {
        fun of(stored: String?): Equipment? =
            entries.firstOrNull { it.stored.equals(stored?.trim(), ignoreCase = true) }

        fun label(stored: String?): String =
            of(stored)?.label ?: stored?.lowercase()?.replace('_', ' ').orEmpty()
    }
}

/**
 * What the library is currently showing. Empty sets mean "everything", which is why this is a
 * set rather than a nullable single value — "barbell or dumbbell" is a real thing to want when
 * you are standing in a free-weights area.
 */
data class ExerciseFilter(
    val query: String = "",
    val muscles: Set<MuscleGroup> = emptySet(),
    val equipment: Set<Equipment> = emptySet(),
) {
    val isEmpty: Boolean get() = query.isBlank() && muscles.isEmpty() && equipment.isEmpty()

    val activeCount: Int get() = muscles.size + equipment.size
}

/**
 * Filtering runs in memory rather than in SQL.
 *
 * The whole catalogue is eight hundred-odd rows of five short fields, which is nothing to hold or to scan,
 * and doing it here means a keystroke re-filters within a frame instead of round-tripping to
 * the database. It also keeps the ranking rule below testable without a database at all.
 */
object ExerciseSearch {

    fun apply(all: List<ExerciseEntity>, filter: ExerciseFilter): List<ExerciseEntity> {
        val terms = filter.query.trim().lowercase().split(WHITESPACE).filter { it.isNotEmpty() }

        return all
            .asSequence()
            .filter { exercise ->
                (filter.muscles.isEmpty() || MuscleGroup.of(exercise.muscleGroup) in filter.muscles) &&
                    (filter.equipment.isEmpty() || Equipment.of(exercise.equipment) in filter.equipment) &&
                    terms.all { term -> exercise.matches(term) }
            }
            .sortedWith(ranking(terms))
            .toList()
    }

    /** A term matches the name or any of the gym names the movement also goes by. */
    private fun ExerciseEntity.matches(term: String): Boolean =
        name.contains(term, ignoreCase = true) ||
            aliasList.any { it.contains(term, ignoreCase = true) }

    /**
     * "Curl" should put "Barbell Curl" above "Alternating Hammer Curl With Rotation". A name
     * that starts with what you typed is almost always the one you meant, and after that the
     * shorter name is the more general movement.
     */
    private fun ranking(terms: List<String>): Comparator<ExerciseEntity> {
        val first = terms.firstOrNull()
        return compareBy(
            { exercise ->
                when {
                    first == null -> 0
                    exercise.name.startsWith(first, true) -> 0
                    // An alias hit is a deliberate match — "pec dec" should not rank below
                    // everything whose name merely contains the letters.
                    exercise.aliasList.any { it.startsWith(first, true) } -> 0
                    else -> 1
                }
            },
            { exercise -> if (first == null) 0 else exercise.name.length },
            { exercise -> exercise.name.lowercase() },
        )
    }

    /**
     * The gym name a row matched on, when that is not its own name.
     *
     * Searching "pec dec" and being shown "Butterfly" is only helpful if the row says why —
     * otherwise the match looks like a mistake, and the name you searched for looks missing.
     */
    fun matchedAlias(exercise: ExerciseEntity, query: String): String? {
        val terms = query.trim().lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return null

        return exercise.aliasList.firstOrNull { alias ->
            terms.any { term ->
                alias.contains(term, ignoreCase = true) &&
                    !exercise.name.contains(term, ignoreCase = true)
            }
        }
    }

    /** Section headings for the browse view, in the catalogue's own order. */
    fun groupByMuscle(exercises: List<ExerciseEntity>): List<Pair<String, List<ExerciseEntity>>> =
        exercises
            .groupBy { MuscleGroup.label(it.muscleGroup) }
            .toList()
            .sortedBy { (label, _) -> label }

    private val WHITESPACE = Regex("\\s+")
}
