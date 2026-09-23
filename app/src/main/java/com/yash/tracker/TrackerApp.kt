package com.yash.tracker

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.request.CachePolicy
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TrackerApp : Application(), SingletonImageLoader.Factory {

    /**
     * Coil only draws ExerciseDB's gifs, whose free tier forbids storing what it serves — so
     * nothing is written to disk, and the in-memory cache is all a session gets.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
}
