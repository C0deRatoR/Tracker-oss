package com.yash.tracker.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema changes against a database that already holds someone's training history.
 *
 * Every migration here is additive. Nothing in this app is worth dropping a column for, and a
 * destructive fallback would throw away a food diary to add a text field.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // The detail that makes an exercise identifiable: which muscles it works, how it is
        // performed, and what it is called on a gym floor. All nullable, because the rows
        // already in the table have none of it until the seed tops them up.
        listOf(
            "primary_muscles",
            "secondary_muscles",
            "instructions",
            "force",
            "mechanic",
            "level",
            "aliases",
        ).forEach { column ->
            db.execSQL("ALTER TABLE exercise ADD COLUMN $column TEXT DEFAULT NULL")
        }
    }
}

/**
 * What is actually in a packet, alongside what it is worth per 100 g.
 *
 * The ingredients column already existed and was being filled from every label scan; nothing
 * ever showed it. These two carry the reading of that list.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("ingredient_flags", "ingredient_verdict").forEach { column ->
            db.execSQL("ALTER TABLE product ADD COLUMN $column TEXT DEFAULT NULL")
        }
    }
}

/**
 * The file name of an exercise's preview image in assets/exercise_art.
 *
 * A name rather than the bytes: the images ship with the APK, so the database only has to
 * say which one belongs to which row, and a row that has no picture simply holds null.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercise ADD COLUMN art TEXT DEFAULT NULL")
    }
}

/**
 * Whether a cached reading outlives the thirty-day window.
 *
 * Set when the reading was saved to the diary: a sentence someone actually logged is one they
 * will type again, and letting it expire means paying for the same words twice. Existing rows
 * default to 0, which is what they were before — nothing was pinned.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE ai_cache ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * The rest of the nutrition panel, carried onto everything the diary writes.
 *
 * The catalogue and the product table have held fibre, sugar and sodium since v1 — they were
 * read off the seed and then dropped on the floor at save time, because the diary only ever
 * copied the four macros. These columns are where they land now.
 *
 * All nullable, because a source that never stated a sugar figure must not be recorded as
 * having stated zero. [micro_kcal] is the honest counterweight: how much of the entry's
 * energy came from items that stated anything at all, so a meal reviewed on one known item
 * out of four can say so. Rows written before this migration keep null and 0, which reads
 * exactly right — nothing was known about them.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("log_item", "log_entry", "meal_template", "meal_template_item").forEach { table ->
            listOf("fibre_g", "sugar_g", "sodium_mg").forEach { column ->
                db.execSQL("ALTER TABLE $table ADD COLUMN $column REAL DEFAULT NULL")
            }
        }
        listOf("log_entry", "meal_template").forEach { table ->
            db.execSQL("ALTER TABLE $table ADD COLUMN micro_kcal REAL NOT NULL DEFAULT 0")
        }
    }
}

/**
 * Fills in the fibre, sugar and salt of everything logged before there were columns for them.
 *
 * Not a rewrite of history. The four macros a diary row was saved with are untouched — this
 * only writes the three that were never written at all, from the very rows the entry already
 * points at. An item that named a product takes the packet's figures; one that named a
 * catalogue food takes the catalogue's; one that named neither is matched on an exact name,
 * and only when exactly one food answers to it. That is the same order of trust the live
 * recognition path uses, applied backwards.
 *
 * Plenty stays blank, and that is the honest outcome: roughly half of one real diary's rows
 * point at foods learned from a reading before the model was asked for these figures, so there
 * is nothing behind them to copy. [micro_kcal] is recomputed from what did land, which is what
 * lets a part-filled meal say how much of itself it is speaking for rather than pretending to
 * a whole.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {

    override fun migrate(db: SupportSQLiteDatabase) {
        // (row table, the column naming its source, the table that source lives in)
        listOf(
            Triple("log_item", "product_id", "product"),
            Triple("log_item", "food_id", "food"),
            Triple("meal_template_item", "product_id", "product"),
            Triple("meal_template_item", "food_id", "food"),
        ).forEach { (table, idColumn, source) ->
            db.execSQL(fillFrom(table, idColumn, source))
        }

        db.execSQL(FILL_LOG_ITEM_BY_NAME)
        db.execSQL(rollUp("log_entry", "log_item", "entry_id"))
        db.execSQL(rollUp("meal_template", "meal_template_item", "template_id"))
    }

    /**
     * One row's three figures, scaled from its source's per-100 g panel.
     *
     * COALESCE rather than a WHERE on each column: a source may state sugar and not fibre, and
     * each of the three has to be filled or left alone on its own merits. Anything already
     * written wins, so running this twice changes nothing.
     *
     * Weightless rows are skipped. Scaling by zero grams would write a confident 0 g of sugar,
     * which is the one thing these columns exist not to say.
     */
    private fun fillFrom(table: String, idColumn: String, source: String) = """
        UPDATE $table SET
            fibre_g   = COALESCE(fibre_g,   (SELECT s.fibre_100g     * $table.grams / 100.0
                                             FROM $source s WHERE s.id = $table.$idColumn)),
            sugar_g   = COALESCE(sugar_g,   (SELECT s.sugar_100g     * $table.grams / 100.0
                                             FROM $source s WHERE s.id = $table.$idColumn)),
            sodium_mg = COALESCE(sodium_mg, (SELECT s.sodium_mg_100g * $table.grams / 100.0
                                             FROM $source s WHERE s.id = $table.$idColumn))
        WHERE $idColumn IS NOT NULL AND grams > 0
    """.trimIndent()

    /**
     * The parent's totals, and how much of its energy they speak for.
     *
     * SUM over rows that all state nothing is NULL, not zero, which is exactly the distinction
     * these columns carry — so the totals need no COALESCE. micro_kcal does: it is a count of
     * what landed, and none landing is a real zero.
     */
    private fun rollUp(parent: String, child: String, parentKey: String) = """
        UPDATE $parent SET
            fibre_g    = (SELECT SUM(c.fibre_g)   FROM $child c WHERE c.$parentKey = $parent.id),
            sugar_g    = (SELECT SUM(c.sugar_g)   FROM $child c WHERE c.$parentKey = $parent.id),
            sodium_mg  = (SELECT SUM(c.sodium_mg) FROM $child c WHERE c.$parentKey = $parent.id),
            micro_kcal = COALESCE((SELECT SUM(c.kcal) FROM $child c
                                   WHERE c.$parentKey = $parent.id
                                     AND (c.fibre_g IS NOT NULL OR c.sugar_g IS NOT NULL
                                          OR c.sodium_mg IS NOT NULL)), 0.0)
    """.trimIndent()
}

/**
 * The last resort for a row that named neither a product nor a catalogue food.
 *
 * Only where exactly one food answers to the name. A diary row is not worth guessing at: two
 * foods sharing a name would have the older half of someone's history quietly attributed to
 * whichever the query happened to reach first.
 */
private val FILL_LOG_ITEM_BY_NAME = """
    UPDATE log_item SET
        fibre_g   = COALESCE(fibre_g,   (SELECT f.fibre_100g     * log_item.grams / 100.0
                                         FROM food f WHERE f.name = log_item.name COLLATE NOCASE)),
        sugar_g   = COALESCE(sugar_g,   (SELECT f.sugar_100g     * log_item.grams / 100.0
                                         FROM food f WHERE f.name = log_item.name COLLATE NOCASE)),
        sodium_mg = COALESCE(sodium_mg, (SELECT f.sodium_mg_100g * log_item.grams / 100.0
                                         FROM food f WHERE f.name = log_item.name COLLATE NOCASE))
    WHERE food_id IS NULL AND product_id IS NULL AND grams > 0
      AND (SELECT COUNT(*) FROM food f WHERE f.name = log_item.name COLLATE NOCASE) = 1
""".trimIndent()

/**
 * Undoes the part of [MIGRATION_6_7] that trusted `food_id` as an identity. It is not one.
 *
 * A recognised row keeps the fuzzy search's best candidate in `food_id` so the confirm screen
 * can offer it as a swap. That is a good enough guess to put in front of someone and nowhere
 * near good enough to copy numbers from: on one real diary, both "Milk" and "Banana" were
 * pointing at a row called "Banana milkshake", and the backfill duly gave a glass of milk a
 * milkshake's sugar.
 *
 * So the fill now requires the row's name to actually be that food's — its own name or one of
 * its transliterations, the same test [FoodDao.findByExactName] uses to decide whether a typed
 * food can be logged without asking the model. Anything already written on a row that fails
 * that test is cleared first, because a figure copied from the wrong food is worse than no
 * figure at all. Products are untouched: one gets onto a row only by being chosen.
 *
 * The live path never had this problem — it has always matched on the name — so this is only
 * repairing what the backfill wrote.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {

    override fun migrate(db: SupportSQLiteDatabase) {
        listOf("log_item", "meal_template_item").forEach { table ->
            db.execSQL(clearMisattributed(table))
            db.execSQL(refillFromFood(table))
        }

        db.execSQL(ROLL_UP_LOG_ENTRY)
        db.execSQL(ROLL_UP_MEAL_TEMPLATE)
    }

    /** A row whose name is not this food's cannot have got its figures honestly. */
    private fun clearMisattributed(table: String) = """
        UPDATE $table SET fibre_g = NULL, sugar_g = NULL, sodium_mg = NULL
        WHERE product_id IS NULL AND food_id IS NOT NULL AND NOT (${namesMatch(table)})
    """.trimIndent()

    private fun refillFromFood(table: String) = """
        UPDATE $table SET
            fibre_g   = COALESCE(fibre_g,   (SELECT f.fibre_100g     * $table.grams / 100.0
                                             FROM food f WHERE f.id = $table.food_id)),
            sugar_g   = COALESCE(sugar_g,   (SELECT f.sugar_100g     * $table.grams / 100.0
                                             FROM food f WHERE f.id = $table.food_id)),
            sodium_mg = COALESCE(sodium_mg, (SELECT f.sodium_mg_100g * $table.grams / 100.0
                                             FROM food f WHERE f.id = $table.food_id))
        WHERE food_id IS NOT NULL AND grams > 0 AND ${namesMatch(table)}
    """.trimIndent()

    /** The row names the food it points at, by that food's name or one of its alt names. */
    private fun namesMatch(table: String) = """
        EXISTS (SELECT 1 FROM food f WHERE f.id = $table.food_id
                  AND (f.name = $table.name COLLATE NOCASE
                       OR ' ' || f.alt_names || ' ' LIKE '% ' || $table.name || ' %'))
    """.trimIndent()
}

private val ROLL_UP_LOG_ENTRY = rollUpParent("log_entry", "log_item", "entry_id")
private val ROLL_UP_MEAL_TEMPLATE = rollUpParent("meal_template", "meal_template_item", "template_id")

/** The same rollup [MIGRATION_6_7] ends with, run again over the corrected rows. */
private fun rollUpParent(parent: String, child: String, parentKey: String) = """
    UPDATE $parent SET
        fibre_g    = (SELECT SUM(c.fibre_g)   FROM $child c WHERE c.$parentKey = $parent.id),
        sugar_g    = (SELECT SUM(c.sugar_g)   FROM $child c WHERE c.$parentKey = $parent.id),
        sodium_mg  = (SELECT SUM(c.sodium_mg) FROM $child c WHERE c.$parentKey = $parent.id),
        micro_kcal = COALESCE((SELECT SUM(c.kcal) FROM $child c
                               WHERE c.$parentKey = $parent.id
                                 AND (c.fibre_g IS NOT NULL OR c.sugar_g IS NOT NULL
                                      OR c.sodium_mg IS NOT NULL)), 0.0)
""".trimIndent()
