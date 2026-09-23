package com.yash.tracker.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.yash.tracker.data.local.AppDatabase
import com.yash.tracker.data.local.entity.ExerciseEntity
import com.yash.tracker.data.local.entity.FoodEntity
import com.yash.tracker.data.local.entity.WeightLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The whole path, end to end: pack, encrypt, write, read, decrypt, unpack, restore.
 *
 * The parts have their own tests, but a backup that cannot be restored is worse than no backup
 * at all — and the failure would only be discovered on the day it mattered.
 */
@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var backups: BackupRepository

    private val codes = object : BackupCodeStore {
        private var stored: String? = null
        override suspend fun backupCode() = stored
        override suspend fun setBackupCode(code: String) {
            stored = code
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backups = BackupRepository(context, db, codes, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = db.close()

    private fun target(name: String): Uri =
        Uri.fromFile(File(context.cacheDir, name).also { it.delete() })

    private suspend fun seed() {
        db.foodDao().insertAll(
            listOf(
                FoodEntity(
                    name = "Roti", altNames = null, category = "DISH", source = "INDB",
                    sourceRef = "t", isComposite = true, kcal100g = 297.0, protein100g = 9.6,
                    carbs100g = 58.0, fat100g = 3.3, fibre100g = null, sugar100g = null,
                    sodiumMg100g = null, defaultPortionG = 40.0, portionLabel = "roti",
                    isVerified = false, createdAt = 1L,
                ),
            ),
        )
        db.weightDao().upsert(
            WeightLogEntity(date = "2026-09-13", weightKg = 78.0, note = null, loggedAt = 1L),
        )
        db.workoutDao().insertExercise(
            ExerciseEntity(
                name = "Overhead Press", muscleGroup = "SHOULDERS", equipment = "BARBELL",
                type = "STRENGTH", metValue = 6.0, defaultRestSec = 150, notes = null,
            ),
        )
        File(context.filesDir, "photos").mkdirs()
        File(context.filesDir, "photos/1_meal.jpg").writeBytes(byteArrayOf(1, 2, 3, 4, 5))
    }

    @Test
    fun `a backup restores everything it packed`() = runTest {
        seed()
        val file = target("round-trip.ntbak")
        val code = backups.backupCode()

        val exported = backups.export(file)
        assertEquals(1, exported.photos)

        // Lose everything, the way a new phone would have it.
        db.openHelper.writableDatabase.execSQL("DELETE FROM food")
        db.openHelper.writableDatabase.execSQL("DELETE FROM weight_log")
        db.openHelper.writableDatabase.execSQL("DELETE FROM exercise")
        File(context.filesDir, "photos/1_meal.jpg").delete()

        backups.restore(file, code)

        assertEquals(1, db.foodDao().count())
        assertEquals(1, db.exerciseDao().count())
        assertEquals(78.0, db.weightDao().latest()!!.weightKg, 0.001)
        assertTrue("search must work again", db.foodDao().search("roti").isNotEmpty())
        assertArrayEqualsPhoto(byteArrayOf(1, 2, 3, 4, 5))
    }

    @Test
    fun `the exported file is encrypted, not a readable zip`() = runTest {
        seed()
        val file = target("sealed.ntbak")

        backups.export(file)

        val bytes = File(file.path!!).readBytes()
        assertEquals("NTBAK001", String(bytes.copyOfRange(0, 8)))
        assertTrue("no plaintext may survive", !bytes.contains("Roti".toByteArray()))
    }

    @Test
    fun `the wrong code does not destroy what is already here`() = runTest {
        seed()
        val file = target("wrong-code.ntbak")
        backups.export(file)

        assertThrows(BackupFailure.WrongCode::class.java) {
            kotlinx.coroutines.runBlocking {
                backups.restore(file, "abandon abandon abandon abandon abandon abandon")
            }
        }

        assertEquals("the existing diary must survive a failed restore", 1, db.foodDao().count())
    }

    @Test
    fun `a backup written by a newer schema is refused rather than half-applied`() = runTest {
        seed()
        val file = target("future.ntbak")
        backups.export(file)

        // Pretend this phone is the old one.
        db.openHelper.writableDatabase.version = 0

        assertThrows(BackupFailure.UnsupportedVersion::class.java) {
            kotlinx.coroutines.runBlocking { backups.restore(file, backups.backupCode()) }
        }
    }

    @Test
    fun `the backup code is minted once and then reused`() = runTest {
        val first = backups.backupCode()

        assertEquals(first, backups.backupCode())
        assertEquals(6, first.split(" ").size)
    }

    private fun assertArrayEqualsPhoto(expected: ByteArray) {
        val restored = File(context.filesDir, "photos/1_meal.jpg")
        assertTrue("the photo should be back", restored.exists())
        assertTrue("and unchanged", restored.readBytes().contentEquals(expected))
    }
}

private fun ByteArray.contains(needle: ByteArray): Boolean =
    indices.any { start ->
        start + needle.size <= size &&
            needle.indices.all { this[start + it] == needle[it] }
    }
