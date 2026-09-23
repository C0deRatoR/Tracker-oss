package com.yash.tracker.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.decode.BitmapFactoryDecoder
import coil3.request.ImageRequest
import com.yash.tracker.data.remote.ExerciseMedia
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The preview image for one movement, falling back to its equipment mark.
 *
 * [art] is an ExerciseDB id. The still is the first frame of its animation, decoded as a plain
 * bitmap — two hundred-odd rows all moving at once would make the library unreadable. The mark
 * sits underneath and shows until the picture lands, or for good when it cannot: offline, a
 * row with no match, one the user added. A row never changes height or flashes empty.
 */
@Composable
fun ExerciseArt(
    art: String?,
    equipment: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(modifier.size(size).clip(MaterialTheme.shapes.medium)) {
        EquipmentMark(equipment, size = size)

        val url = rememberExerciseGifUrl(art) ?: return@Box
        val media = rememberExerciseMedia()
        val context = LocalContext.current
        val still = remember(url) {
            ImageRequest.Builder(context)
                .data(url)
                .decoderFactory(BitmapFactoryDecoder.Factory())
                // Same URL as the animated one on the detail screen; without its own key the
                // memory cache would hand the still to that screen, or the animation to this.
                .memoryCacheKey("$url#still")
                .build()
        }
        AsyncImage(
            model = still,
            // The name is already beside it; announcing the picture too would read it twice.
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { if (art != null) media?.forget(art) },
            modifier = Modifier.size(size),
        )
    }
}

/**
 * The movement itself, animated, from a URL [rememberExerciseGifUrl] produced for [art].
 *
 * Only the id is bundled, so this is always a live fetch; the caller decides what to show
 * while there is no URL, and says where the animation came from, which the free tier requires.
 */
@Composable
fun ExerciseAnimation(
    art: String,
    url: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val media = rememberExerciseMedia()

    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        onError = { media?.forget(art) },
        modifier = modifier.clip(MaterialTheme.shapes.medium),
    )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ExerciseMediaEntryPoint {
    fun exerciseMedia(): ExerciseMedia
}

/** Null in a layout preview, where there is no Hilt graph to ask. */
@Composable
private fun rememberExerciseMedia(): ExerciseMedia? {
    if (LocalInspectionMode.current) return null
    val app = LocalContext.current.applicationContext
    return remember(app) {
        EntryPointAccessors.fromApplication(app, ExerciseMediaEntryPoint::class.java).exerciseMedia()
    }
}

/** A current animation URL for an ExerciseDB id, or null until there is one. */
@Composable
fun rememberExerciseGifUrl(art: String?): String? {
    val media = rememberExerciseMedia() ?: return null
    val url by produceState(initialValue = art?.let(media::peek), art) {
        if (value == null && art != null) value = media.gifUrl(art)
    }
    return url
}
