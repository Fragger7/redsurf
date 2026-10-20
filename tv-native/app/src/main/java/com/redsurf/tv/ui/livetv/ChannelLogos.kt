package com.redsurf.tv.ui.livetv

import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult

/**
 * Channel logos for the guide's painted rows (performance salvage, 2026-10-20). A Coil
 * `AsyncImage` per row was ~2.5ms of composition each - most of a row's cost when a category
 * change builds a screenful of rows. Rows now draw a cached bitmap; a miss loads through Coil's
 * own loader (its memory and disk caches still apply) once, off the frame.
 */
internal object ChannelLogos {
    // Small bitmaps (a 26dp chip): a few hundred stay well under a megabyte or two.
    private val cache = LruCache<String, ImageBitmap>(400)

    fun peek(url: String): ImageBitmap? = cache.get(url)

    suspend fun load(context: Context, url: String, sizePx: Int): ImageBitmap? {
        cache.get(url)?.let { return it }
        val request = ImageRequest.Builder(context).data(url).size(sizePx).build()
        val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable ?: return null
        return runCatching { drawable.toBitmap().asImageBitmap() }.getOrNull()?.also { cache.put(url, it) }
    }
}
