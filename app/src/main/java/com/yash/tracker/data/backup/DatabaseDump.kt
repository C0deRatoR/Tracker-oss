package com.yash.tracker.data.backup

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads and writes every table generically, from `sqlite_master` rather than from a list of
 * entities.
 *
 * Twenty-one tables would otherwise mean twenty-one serializers to write and one to forget —
 * and the one forgotten is the one silently missing from every backup. Working off the live
 * schema means a table added later is included without anyone remembering to add it here.
 */
object DatabaseDump {

    /** Rows are arrays, not objects: at 8,000 foods, repeating column names triples the file. */
    fun write(db: SupportSQLiteDatabase, schemaVersion: Int): JsonObject = buildJsonObject {
        put(SCHEMA_VERSION, JsonPrimitive(schemaVersion))
        put(CREATED_AT, JsonPrimitive(System.currentTimeMillis()))
        put(
            TABLES,
            buildJsonObject {
                tablesIn(db).forEach { table -> put(table, dumpTable(db, table)) }
            },
        )
    }

    fun schemaVersionOf(dump: JsonObject): Int =
        dump[SCHEMA_VERSION]?.jsonPrimitive?.int ?: error("backup has no schema version")

    /**
     * Replaces the database contents with the dump. Destructive by design, and run inside one
     * transaction so a failure halfway leaves the existing data untouched.
     */
    fun read(db: SupportSQLiteDatabase, dump: JsonObject) {
        val tables = dump[TABLES]?.jsonObject ?: error("backup has no tables")
        val present = tablesIn(db)

        db.beginTransaction()
        try {
            // Rows arrive in whatever order the dump lists tables, so a child can land before
            // its parent. Deferring enforcement to commit checks the same constraints without
            // demanding a topological sort of the schema.
            db.execSQL("PRAGMA defer_foreign_keys = ON")
            present.forEach { db.execSQL("DELETE FROM `$it`") }

            tables.forEach { (table, content) ->
                // A table in the file that this build no longer has is skipped rather than
                // failing the restore: the alternative is an unrecoverable backup.
                if (table in present) restoreTable(db, table, content.jsonObject)
            }

            rebuildFullTextIndexes(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun dumpTable(db: SupportSQLiteDatabase, table: String): JsonObject {
        db.query("SELECT * FROM `$table`").use { cursor ->
            val columns = cursor.columnNames.toList()
            val rows = buildJsonArray {
                while (cursor.moveToNext()) {
                    add(buildJsonArray { columns.indices.forEach { add(cursor.valueAt(it)) } })
                }
            }
            return buildJsonObject {
                put(COLUMNS, JsonArray(columns.map(::JsonPrimitive)))
                put(ROWS, rows)
            }
        }
    }

    private fun restoreTable(db: SupportSQLiteDatabase, table: String, content: JsonObject) {
        val columns = content[COLUMNS]?.jsonArray?.map { it.jsonPrimitive.content } ?: return
        val rows = content[ROWS]?.jsonArray ?: return
        if (rows.isEmpty()) return

        val live = columnsOf(db, table)
        // Only columns this build still has. A dropped column would otherwise abort the whole
        // restore over data nothing reads any more.
        val keep = columns.withIndex().filter { it.value in live }
        if (keep.isEmpty()) return

        val sql = "INSERT INTO `$table` (${keep.joinToString(",") { "`${it.value}`" }}) " +
            "VALUES (${keep.joinToString(",") { "?" }})"

        db.compileStatement(sql).use { statement ->
            rows.forEach { row ->
                val values = row.jsonArray
                statement.clearBindings()
                keep.forEachIndexed { position, (sourceIndex, _) ->
                    statement.bindJson(position + 1, values[sourceIndex])
                }
                statement.executeInsert()
            }
        }
    }

    /**
     * FTS content is derived from the table it mirrors, so it is rebuilt rather than carried in
     * the file — smaller backups, and no way for the index to arrive disagreeing with the data.
     */
    private fun rebuildFullTextIndexes(db: SupportSQLiteDatabase) {
        virtualTablesIn(db).forEach { db.execSQL("INSERT INTO `$it`(`$it`) VALUES('rebuild')") }
    }

    private fun tablesIn(db: SupportSQLiteDatabase): List<String> {
        val virtual = virtualTablesIn(db)
        return db.query(
            """
            SELECT name FROM sqlite_master
            WHERE type = 'table'
              AND name NOT LIKE 'sqlite_%'
              AND name NOT LIKE 'room_%'
              AND name != 'android_metadata'
            ORDER BY name
            """,
        ).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }.filterNot { name ->
            // The virtual table itself and the shadow tables SQLite keeps beside it
            // (_segments, _segdir, _docsize, _stat) are all rebuilt, never restored.
            name in virtual || virtual.any { name.startsWith("${it}_") }
        }
    }

    private fun virtualTablesIn(db: SupportSQLiteDatabase): List<String> =
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND sql LIKE 'CREATE VIRTUAL TABLE%'")
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildSet {
                val nameIndex = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameIndex))
            }
        }

    private fun Cursor.valueAt(index: Int): JsonElement = when (getType(index)) {
        Cursor.FIELD_TYPE_NULL -> JsonNull
        Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(getLong(index))
        Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(getDouble(index))
        else -> JsonPrimitive(getString(index))
    }

    private fun androidx.sqlite.db.SupportSQLiteStatement.bindJson(index: Int, value: JsonElement) {
        val primitive = value as? JsonPrimitive
        when {
            primitive == null || primitive is JsonNull -> bindNull(index)
            primitive.isString -> bindString(index, primitive.content)
            primitive.content == "true" -> bindLong(index, 1)
            primitive.content == "false" -> bindLong(index, 0)
            primitive.content.contains('.') || primitive.content.contains('e', ignoreCase = true) ->
                bindDouble(index, primitive.content.toDouble())
            else -> primitive.content.toLongOrNull()
                ?.let { bindLong(index, it) }
                ?: bindString(index, primitive.content)
        }
    }

    private const val SCHEMA_VERSION = "schemaVersion"
    private const val CREATED_AT = "createdAt"
    private const val TABLES = "tables"
    private const val COLUMNS = "columns"
    private const val ROWS = "rows"
}
