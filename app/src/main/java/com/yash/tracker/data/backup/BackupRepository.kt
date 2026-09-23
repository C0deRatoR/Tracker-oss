package com.yash.tracker.data.backup

import android.content.Context
import android.net.Uri
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.entity.AppStateEntity
import com.yash.tracker.domain.backup.BackupCode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class BackupSummary(val bytes: Long, val photos: Int)

/**
 * Where the backup code is kept. An interface because the real implementation encrypts through
 * the Android Keystore, which does not exist off-device — the same reason GeminiConfig exists.
 */
interface BackupCodeStore {
    suspend fun backupCode(): String?
    suspend fun setBackupCode(code: String)
}

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val codes: BackupCodeStore,
    private val io: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun wordlist(): List<String> = withContext(io) {
        context.assets.open(WORDLIST).bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
    }

    /**
     * The code this phone seals its backups with, generated on first use. Kept so Settings can
     * show it again — a code that only ever appeared once is a code nobody has.
     */
    suspend fun backupCode(): String = withContext(io) {
        codes.backupCode() ?: BackupCode.generate(wordlist()).also { codes.setBackupCode(it) }
    }

    suspend fun lastBackupAt(): Long? = withContext(io) {
        db.appStateDao().get(AppStateEntity.KEY_LAST_BACKUP_AT)?.toLongOrNull()
    }

    fun suggestedFileName(): String = "tracker-${LocalDate.now()}.ntbak"

    /**
     * Packs the database and photos into a zip, encrypts it, and writes it to wherever the
     * user pointed the file picker.
     *
     * TRD §8 writes to `Downloads/` via MediaStore and then opens a share sheet. The system
     * create-document picker does the same job in one step, reaches Drive and every other
     * provider directly, and needs no storage permission at any API level.
     */
    suspend fun export(target: Uri): BackupSummary = withContext(io) {
        val code = backupCode()
        val staged = File(context.cacheDir, "backup-staging.zip")

        try {
            val photos = staged.outputStream().use { file ->
                ZipOutputStream(file.buffered()).use { zip ->
                    zip.putNextEntry(ZipEntry(DATA_ENTRY))
                    val dump = DatabaseDump.write(db.openHelper.writableDatabase, schemaVersion())
                    zip.write(json.encodeToString(JsonObject.serializer(), dump).toByteArray())
                    zip.closeEntry()

                    photoFiles().onEach { photo ->
                        zip.putNextEntry(ZipEntry("$PHOTO_PREFIX${photo.name}"))
                        photo.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }.size
                }
            }

            val written = context.contentResolver.openOutputStream(target)?.use { out ->
                staged.inputStream().use { BackupCrypto.seal(it, out.buffered(), code) }
                staged.length()
            } ?: error("could not write to the file you picked")

            db.appStateDao().put(
                AppStateEntity(AppStateEntity.KEY_LAST_BACKUP_AT, System.currentTimeMillis().toString()),
            )

            BackupSummary(bytes = written, photos = photos)
        } finally {
            staged.delete()
        }
    }

    /**
     * Replaces everything on this phone with the contents of the file. The caller confirms
     * first — there is no undo, and the data being overwritten may be the only copy.
     */
    suspend fun restore(source: Uri, code: String): BackupSummary = withContext(io) {
        val staged = File(context.cacheDir, "restore-staging.zip")

        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                staged.outputStream().use { BackupCrypto.open(input.buffered(), it, code) }
            } ?: error("could not read the file you picked")

            var dump: JsonObject? = null
            val photos = mutableMapOf<String, ByteArray>()

            ZipInputStream(staged.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.name == DATA_ENTRY ->
                            dump = json.parseToJsonElement(zip.readBytes().decodeToString()) as JsonObject
                        entry.name.startsWith(PHOTO_PREFIX) ->
                            photos[entry.name.removePrefix(PHOTO_PREFIX)] = zip.readBytes()
                    }
                    zip.closeEntry()
                }
            }

            val data = dump ?: throw BackupFailure.NotABackup()
            val version = DatabaseDump.schemaVersionOf(data)
            if (version > schemaVersion()) throw BackupFailure.UnsupportedVersion(version)

            DatabaseDump.read(db.openHelper.writableDatabase, data)
            replacePhotos(photos)

            BackupSummary(bytes = staged.length(), photos = photos.size)
        } finally {
            staged.delete()
        }
    }

    private fun replacePhotos(photos: Map<String, ByteArray>) {
        val dir = File(context.filesDir, PHOTO_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        // A name from inside the archive must not be able to write outside the photo folder.
        photos.forEach { (name, bytes) ->
            val safe = File(name).name
            if (safe.isNotBlank()) File(dir, safe).writeBytes(bytes)
        }
    }

    private fun photoFiles(): List<File> =
        File(context.filesDir, PHOTO_DIR).listFiles()?.filter { it.isFile }.orEmpty()

    private fun schemaVersion(): Int = db.openHelper.readableDatabase.version

    private companion object {
        const val WORDLIST = "backup/wordlist.txt"
        const val DATA_ENTRY = "data.json"
        const val PHOTO_PREFIX = "photos/"
        const val PHOTO_DIR = "photos"
    }
}
