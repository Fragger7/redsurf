package com.redsurf.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.redsurf.tv.diag.CrashLog

/** Installs [CrashLog] before anything else runs - including WorkManager workers that start with
 * no Activity at all, which MainActivity.onCreate would never cover - and owns the app's one Coil
 * [ImageLoader]. */
class RedSurfApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }

    /**
     * Perf Max step 4: one loader for every logo. Software bitmaps (`allowHardware(false)`) - a
     * hardware bitmap has to be copied back before the guide's painted rows can draw it, and on a
     * 1.89 GB device the GPU-side copy is the scarcer memory. The memory cache is sized in bytes
     * (~8 MB holds well over a thousand 52px chips), not a count, so a few large hero logos can't
     * crowd it past its budget; the disk cache spares the provider a re-fetch on every launch.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .allowHardware(false)
        .crossfade(false)
        .memoryCache { MemoryCache.Builder(this).maxSizeBytes(8 * 1024 * 1024).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(32L * 1024 * 1024)
                .build()
        }
        .build()
}
