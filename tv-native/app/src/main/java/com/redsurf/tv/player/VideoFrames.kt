package com.redsurf.tv.player

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/**
 * Preview ↔ fullscreen grow/shrink (EPG_WRAPUP.md 2.4): the frame-based version. The preview and
 * the fullscreen player are still two separate ExoPlayers (Preview-on-OK's original design), so the
 * transition animates a *captured frame* of whichever is playing between the hero box and the full
 * screen while the other one starts underneath. The true single-persistent-player version (one
 * video continuously resizing) is logged follow-up work - see EPG_WRAPUP.md.
 *
 * Process-wide registry of the two live `PlayerView`s (only one of each exists at a time), so the
 * transition can grab a frame without threading a new parameter through PlayerScreen.
 */
@UnstableApi
object VideoFrames {
    private var preview: WeakReference<PlayerView>? = null
    private var fullscreen: WeakReference<PlayerView>? = null

    fun registerPreview(view: PlayerView?) { preview = view?.let { WeakReference(it) } }
    fun registerFullscreen(view: PlayerView?) { fullscreen = view?.let { WeakReference(it) } }

    suspend fun grabPreview(): Bitmap? = grab(preview?.get())
    suspend fun grabFullscreen(): Bitmap? = grab(fullscreen?.get())

    /** PixelCopy from the PlayerView's SurfaceView (API 24+). Null on any failure - the caller
     * then simply skips the animation. Downscaled: it's a ~0.3s transition, not a still. */
    private suspend fun grab(view: PlayerView?): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        val surfaceView = view?.videoSurfaceView as? SurfaceView ?: return null
        if (surfaceView.width <= 0 || surfaceView.height <= 0 || !surfaceView.holder.surface.isValid) return null
        val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
        return suspendCancellableCoroutine { cont ->
            runCatching {
                PixelCopy.request(surfaceView, bitmap, { result ->
                    if (cont.isActive) cont.resume(if (result == PixelCopy.SUCCESS) bitmap else null)
                }, Handler(Looper.getMainLooper()))
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }
    }
}
