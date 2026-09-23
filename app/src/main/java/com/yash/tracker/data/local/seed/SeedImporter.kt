package com.yash.tracker.data.local.seed

import android.content.Context
import androidx.room.withTransaction
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.entity.AppStateEntity
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.data.local.entity.FoodEntity
import com.yash.tracker.data.local.entity.PortionMeasureEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SeedProgress {
    data object Idle : SeedProgress
    data class Working(val step: String, val done: Int, val total: Int) : SeedProgress
    data class Finished(val foods: Int, val exercises: Int) : SeedProgress
    data class Failed(val message: String) : SeedProgress
}

/**
 * Loads the bundled catalogue on first launch.
 *
 * TRD §4 puts this in WorkManager, but it is a one-time blocking step behind a progress
 * screen — it needs no deferred scheduling, and running the whole thing in a single
 * transaction already makes a retry safe. WorkManager stays for the backup reminder, which
 * genuinely needs it.
 */
@Singleton
class SeedImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val io: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun isSeeded(): Boolean =
        db.appStateDao().get(AppStateEntity.KEY_SEED_IMPORTED) == "true"

    suspend fun import(onProgress: (SeedProgress) -> Unit): SeedProgress = withContext(io) {
        try {
            if (isSeeded()) {
                topUpExercises()
                val existing = db.foodDao().count()
                return@withContext SeedProgress.Finished(existing, db.exerciseDao().count())
            }

            val foodFiles = listOf(
                "seed/dishes_indian.json" to "Indian dishes",
                "seed/dishes_fndds.json" to "Indian dishes",
                "seed/foods_usda.json" to "Generic foods",
            )

            val parsed = foodFiles.map { (asset, label) ->
                onProgress(SeedProgress.Working("Reading $label", 0, 0))
                label to decode<SeedFoodFile>(asset).foods
            }
            val measures = decode<SeedPortionFile>("seed/portions.json").measures
            val library = decode<SeedExerciseFile>("seed/exercises.json")
            val exercises = library.exercises

            val totalFoods = parsed.sumOf { it.second.size }
            var written = 0
            val now = System.currentTimeMillis()

            // One transaction for the lot: a crash mid-import leaves no half-filled
            // catalogue, so the seeded flag and the rows can never disagree.
            db.withTransaction {
                for ((label, foods) in parsed) {
                    foods.chunked(CHUNK).forEach { chunk ->
                        db.foodDao().insertAll(chunk.map { it.toEntity(now) })
                        written += chunk.size
                        onProgress(SeedProgress.Working(label, written, totalFoods))
                    }
                }

                onProgress(SeedProgress.Working("Household measures", written, totalFoods))
                db.foodDao().insertPortions(
                    measures.map { PortionMeasureEntity(foodId = null, label = it.label, grams = it.grams) },
                )

                onProgress(SeedProgress.Working("Exercises", written, totalFoods))
                db.exerciseDao().insertAll(exercises.map { it.toEntity() })

                db.appStateDao().put(AppStateEntity(AppStateEntity.KEY_SEED_IMPORTED, "true"))
                db.appStateDao().put(
                    AppStateEntity(
                        AppStateEntity.KEY_EXERCISE_LIBRARY_VERSION,
                        library.version.toString(),
                    ),
                )
            }

            SeedProgress.Finished(written, exercises.size)
        } catch (e: Exception) {
            SeedProgress.Failed(e.message ?: e::class.java.simpleName)
        }
    }

    /**
     * Adds exercises that shipped after this install first seeded. The catalogue grew from 96
     * rows to eight hundred-odd, and a phone that already holds a food diary cannot be re-seeded from
     * scratch to get them — so the library carries a version and only the names that are
     * missing are inserted. Rows the user added themselves are matched by name and left alone.
     */
    private suspend fun topUpExercises() {
        val stored = db.appStateDao().get(AppStateEntity.KEY_EXERCISE_LIBRARY_VERSION)?.toIntOrNull() ?: 1
        val library = decode<SeedExerciseFile>("seed/exercises.json")
        if (library.version <= stored) return

        val existing = db.exerciseDao().getAll().associateBy { it.name.matchKey() }
        val missing = library.exercises.filterNot { it.name.matchKey() in existing }

        db.withTransaction {
            missing.chunked(CHUNK).forEach { db.exerciseDao().insertAll(it.map(SeedExercise::toEntity)) }

            // Rows that were seeded before the library carried muscles and instructions are
            // updated in place, or a phone that has been here since v1 would be the only one
            // without them. Everything the user owns — custom rows, an edited rest — is kept.
            //
            // The name is rewritten too, which matters more than it sounds. matchKey folds
            // punctuation, so the Strong row "Pull Up" matches a phone's existing "Pull-Up"
            // and is never inserted — without this the upgrade would leave fifteen movements
            // wearing their old free-exercise-db names ("Running, Treadmill") while carrying
            // the new artwork. Any duplicate this creates is merged directly below.
            library.exercises.forEach { seed ->
                val row = existing[seed.name.matchKey()] ?: return@forEach
                if (row.isCustom) return@forEach
                val detail = seed.toEntity()
                db.exerciseDao().update(
                    row.copy(
                        name = seed.name,
                        primaryMuscles = detail.primaryMuscles,
                        secondaryMuscles = detail.secondaryMuscles,
                        instructions = detail.instructions,
                        force = detail.force,
                        mechanic = detail.mechanic,
                        level = detail.level,
                        aliases = detail.aliases,
                        art = detail.art,
                        muscleGroup = detail.muscleGroup,
                        equipment = detail.equipment,
                        type = detail.type,
                    ),
                )
            }

            absorbRenamedExercises()
            mergeDuplicateExercises(library)
            retireExercises(library)

            db.appStateDao().put(
                AppStateEntity(
                    AppStateEntity.KEY_EXERCISE_LIBRARY_VERSION,
                    library.version.toString(),
                ),
            )
        }
    }

    /**
     * Moves the history off the movements the old catalogue named differently.
     *
     * Retiring keeps a dropped row when something points at it, which is right in general but
     * leaves the user looking at "Pec Deck" and "Leverage Incline Chest Press" sitting beside
     * the library's own names for the same two machines. These are the ones that were actually
     * trained, so their sets are moved onto the library row and the old row is deleted.
     *
     * Written out one by one rather than matched by similarity. The nearest name to "Overhead
     * Triceps Extension" is "Triceps Extension (Cable)", but the sets logged against it were
     * 5 kg — a dumbbell, not the 15–18 kg this user pushes on the cable stack — so it belongs
     * on the dumbbell row. Attaching history to the wrong variant is worse than leaving it be.
     *
     * "One-Arm Side Laterals" is the cable variant, not the dumbbell one — the owner does it on
     * a cable, and only they could say so. It is the one entry here that a weight cannot tell
     * you, since both are loaded in the same range.
     *
     * Matched on the exact old name, which is also what a row the user created themselves
     * would be matched on. These spellings are free-exercise-db's rather than anything a
     * person would type, so that is a trade worth making for a one-time cleanup.
     */
    private suspend fun absorbRenamedExercises() {
        val byName = db.exerciseDao().getAll().associateBy { it.name }

        RENAMED.forEach { (old, replacement) ->
            val stale = byName[old] ?: return@forEach
            val survivor = byName[replacement] ?: return@forEach
            if (stale.id == survivor.id) return@forEach

            db.workoutDao().repointExercise(from = stale.id, to = survivor.id)
            db.exerciseDao().deleteById(stale.id)
        }
    }

    /**
     * Drops the rows the library no longer carries.
     *
     * The catalogue was rebuilt from Strong's, which trades eight hundred-odd rows for the
     * two hundred-odd movements a gym actually has equipment for. Topping up only ever added,
     * so without this a phone that seeded before the rebuild would hold both sets at once and
     * be worse off than a fresh install.
     *
     * A retired row is only deleted when nothing points at it. Anything with a logged set, a
     * routine slot or a record is kept and marked as the user's own instead: exercise_id has
     * no foreign key, so deleting it would not fail — the history would just stop rendering.
     */
    private suspend fun retireExercises(library: SeedExerciseFile) {
        val kept = library.exercises.mapTo(mutableSetOf()) { it.name.matchKey() }
        val referenced = db.workoutDao().referencedExerciseIds().toSet()

        val retiring = db.exerciseDao().getAll()
            .filterNot { it.isCustom }
            .filterNot { it.name.matchKey() in kept }

        retiring.forEach { row ->
            if (row.id in referenced) {
                db.exerciseDao().update(row.copy(isCustom = true))
            } else {
                db.exerciseDao().deleteById(row.id)
            }
        }
    }

    /**
     * The catalogue used to ship "Seated Cable Row" and "Seated Cable Rows" as separate rows —
     * the two sources were merged on a key that folded punctuation but not plurals. The seed no
     * longer produces them, but a phone that already imported them still holds both.
     *
     * The stale one is not simply deleted: sets and routines may point at it, and exercise_id
     * carries no foreign key, so deleting would leave history pointing at nothing. Anything
     * referencing it is repointed at the row the library kept, and only then is it removed.
     */
    private suspend fun mergeDuplicateExercises(library: SeedExerciseFile) {
        val kept = library.exercises.associateBy { it.name.matchKey() }
        val byKey = db.exerciseDao().getAll()
            .filterNot { it.isCustom }
            .groupBy { it.name.matchKey() }

        byKey.forEach { (matchKey, rows) ->
            if (rows.size < 2) return@forEach
            val survivorName = kept[matchKey]?.name
            val survivor = rows.firstOrNull { it.name == survivorName } ?: rows.first()

            rows.filter { it.id != survivor.id }.forEach { stale ->
                db.workoutDao().repointExercise(from = stale.id, to = survivor.id)
                db.exerciseDao().deleteById(stale.id)
            }
        }
    }

    // Plain JSON, not gzipped: AAPT2 gunzips .gz assets at packaging time and strips the
    // extension, and the APK's own deflate compresses them just as well.
    private inline fun <reified T> decode(asset: String): T =
        context.assets.open(asset).use { json.decodeFromString<T>(it.readBytes().decodeToString()) }

    private companion object {
        const val CHUNK = 500

        /** Old catalogue name to the library row its history belongs on. */
        val RENAMED = mapOf(
            "Lateral Raise" to "Lateral Raise (Dumbbell)",
            "One-Arm Side Laterals" to "Lateral Raise (Cable)",
            "Leverage Incline Chest Press" to "Incline Chest Press (Machine)",
            "Overhead Triceps Extension" to "Triceps Extension (Dumbbell)",
            "Pec Deck" to "Pec Deck (Machine)",
            "Treadmill Walking" to "Walking",
            "Triceps Pushdown - Rope Attachment" to "Triceps Pushdown (Cable - Straight Bar)",
        )
    }
}

private fun SeedFood.toEntity(now: Long) = FoodEntity(
    name = name,
    altNames = altNames.takeIf { it.isNotEmpty() }?.joinToString(" "),
    category = category,
    source = source,
    sourceRef = sourceRef,
    isComposite = isComposite,
    kcal100g = per100g.kcal,
    protein100g = per100g.protein,
    carbs100g = per100g.carbs,
    fat100g = per100g.fat,
    fibre100g = per100g.fibre,
    sugar100g = per100g.sugar,
    sodiumMg100g = per100g.sodiumMg,
    defaultPortionG = defaultPortionG,
    portionLabel = portionLabel,
    isVerified = isVerified,
    createdAt = now,
)

/**
 * Names match on letters, digits and number, so "Ab Roller" and "ab-roller" are one exercise —
 * and so are "Seated Cable Row" and "Seated Cable Rows".
 *
 * The plural fold is the same rule tools/build_exercises.py merges its two sources on. Without
 * it the catalogue shipped nine pairs differing only by an "s", and a phone that imported them
 * would keep both forever, because the top-up would see each as a name it had never seen.
 */
private fun String.matchKey(): String = lowercase()
    .split(NON_ALPHANUMERIC)
    .filter { it.isNotEmpty() }
    .joinToString("") { word ->
        if (word.length > 3 && word.endsWith("s") && !word.endsWith("ss")) word.dropLast(1) else word
    }

private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

private fun SeedExercise.toEntity() = ExerciseEntity(
    name = name,
    muscleGroup = muscleGroup,
    equipment = equipment,
    type = type,
    metValue = metValue,
    isCustom = false,
    defaultRestSec = defaultRestSec,
    notes = null,
    primaryMuscles = primaryMuscles.joined(),
    secondaryMuscles = secondaryMuscles.joined(),
    instructions = instructions.joined(),
    force = force,
    mechanic = mechanic,
    level = level,
    aliases = aliases.joined(),
    art = art,
)

/** Newline-delimited, which is how [ExerciseEntity] reads them back. */
private fun List<String>.joined(): String? =
    map(String::trim).filter(String::isNotEmpty).takeIf { it.isNotEmpty() }?.joinToString("\n")
