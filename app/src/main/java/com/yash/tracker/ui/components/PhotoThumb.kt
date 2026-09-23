package com.yash.tracker.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/**
 * The meal-photo tile in the ledger.
 *
 * No image library: these are app-private JPEGs at a known small size, read once into a
 * thumbnail and cached by the composition. A loader would earn its keep for remote images and
 * long scrolling galleries; this is neither.
 */
@Composable
fun PhotoThumb(
    path: String?,
    fallbackIcon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    contentDescription: String? = null,
) {
    val pixels = with(LocalDensity.current) { size.roundToPx() }
    var bitmap by remember(path, pixels) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(path, pixels) {
        bitmap = path?.let { decodeThumbnail(it, pixels) }?.asImageBitmap()
    }

    val loaded = bitmap
    if (loaded == null) {
        IconPlate(fallbackIcon, modifier, size = size, contentDescription = contentDescription)
    } else {
        Box(
            modifier
                .size(size)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = loaded,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}

/**
 * Two passes: measure, then decode at the smallest power-of-two that still covers the tile.
 * A full 1024px meal photo decoded for a 56dp square is 4 MB of heap for 12 KB of pixels.
 */
private suspend fun decodeThumbnail(path: String, target: Int): Bitmap? = withContext(Dispatchers.IO) {
    val file = File(path)
    if (!file.exists()) return@withContext null

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

    var sample = 1
    val longest = max(bounds.outWidth, bounds.outHeight)
    while (longest / (sample * 2) >= target) sample *= 2

    runCatching {
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
