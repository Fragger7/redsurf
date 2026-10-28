package com.redsurf.tv.ui.livetv

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * Channel logos for the guide's painted rows (performance salvage, 2026-10-20). A Coil
 * `AsyncImage` per row was ~2.5ms of composition each - most of a row's cost when a category
 * change builds a screenful of rows. Rows now draw a bitmap straight from Coil's memory cache;
 * a miss loads through the app's one loader (`RedSurfApp.newImageLoader`) once, off the frame.
 * Perf Max step 4: the old private `LruCache` (400 entries, unbounded bytes) is gone - the
 * loader's byte-sized cache is the only copy, keyed by URL so [peek] can find it synchronously.
 */
internal object ChannelLogos {
    fun peek(context: Context, url: String): ImageBitmap? =
        context.imageLoader.memoryCache?.get(MemoryCache.Key(url))?.bitmap?.asImageBitmap()

    suspend fun load(context: Context, url: String, sizePx: Int): ImageBitmap? {
        peek(context, url)?.let { return it }
        val request = ImageRequest.Builder(context).data(url).size(sizePx).memoryCacheKey(url).build()
        val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable ?: return null
        return runCatching { drawable.toBitmap().asImageBitmap() }.getOrNull()
    }
}
