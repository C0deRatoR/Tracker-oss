package com.yash.tracker.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ProcessedPhoto(val jpeg: ByteArray, val base64: String) {
    // ByteArray needs these spelled out; identity is the bytes.
    override fun equals(other: Any?) = other is ProcessedPhoto && jpeg.contentEquals(other.jpeg)
    override fun hashCode() = jpeg.contentHashCode()
}

/**
 * Capture, shrink and store meal photos.
 *
 * TRD §1 lists CameraX, but handing capture to the system camera needs no CAMERA permission
 * at all, gives the phone's full capture pipeline for free, and leaves no preview UI to build
 * that the visual design would replace. Photos never leave app-private storage (TRD §7).
 */
@Singleton
class PhotoStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val io: CoroutineDispatcher,
) {
    /** A FileProvider URI the system camera can write into. */
    fun newCaptureTarget(): Pair<Uri, File> {
        val dir = File(context.cacheDir, "capture").apply { mkdirs() }
        val file = File(dir, "meal_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
        return uri to file
    }

    /**
     * Rotates by EXIF, scales the longest edge to 1024 px and compresses to JPEG 85, which
     * keeps an upload near 200-400 KB (TRD §5.1).
     */
    suspend fun process(uri: Uri, longestEdge: Int = UPLOAD_EDGE): ProcessedPhoto? = withContext(io) {
        val original = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        } ?: return@withContext null

        val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        val scaled = scale(original, longestEdge)
        val upright = if (rotation == 0f) scaled else Bitmap.createBitmap(
            scaled,
            0,
            0,
            scaled.width,
            scaled.height,
            Matrix().apply { postRotate(rotation) },
            true,
        )

        val bytes = ByteArrayOutputStream().use { out ->
            upright.compress(Bitmap.CompressFormat.JPEG, UPLOAD_QUALITY, out)
            out.toByteArray()
        }

        ProcessedPhoto(bytes, Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    /** Writes the processed JPEG into app-private storage once the entry is actually saved. */
    suspend fun persist(photo: ProcessedPhoto, entryId: Long): String = withContext(io) {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        val file = File(dir, "${entryId}_${System.currentTimeMillis()}.jpg")
        file.writeBytes(photo.jpeg)
        file.absolutePath
    }

    fun clearCaptureCache() {
        File(context.cacheDir, "capture").listFiles()?.forEach { it.delete() }
    }

    private fun scale(bitmap: Bitmap, longestEdge: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= longestEdge) return bitmap
        val factor = longestEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * factor).toInt(),
            (bitmap.height * factor).toInt(),
            true,
        )
    }

    companion object {
        const val UPLOAD_EDGE = 1024
        const val UPLOAD_QUALITY = 85

        /**
         * Nutrition panels are small print on a curved jar. At 1024 px the digits blur into
         * each other, and a model asked to transcribe them returns nothing — which is what
         * happened the first time this was tried on a real packet.
         */
        const val LABEL_EDGE = 1600
    }
}
