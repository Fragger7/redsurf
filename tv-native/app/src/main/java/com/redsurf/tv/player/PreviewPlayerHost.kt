package com.redsurf.tv.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout

// PREVIEW.md's dedicated preview ExoPlayer (PreviewPlayerController) was retired 2026-10-20 (L5):
// the preview pane now shows Live TV's one shared PlayerController - its reconnect handling is the
// fullscreen engine's own, which also covers PREVIEW.md's "keep retrying" ask (Sprint 2, item 3).

/**
 * The preview pane's view onto the one shared Live TV player (L5, 2026-10-20) - display only: it
 * never starts or switches streams (Live TV drives [controller]). Going fullscreen and back moves
 * the video surface between this view and the fullscreen one; the stream keeps playing, with no
 * second connection (the provider allows one) and no re-buffering.
 */
@Composable
fun SharedPreviewHost(controller: PlayerController, modifier: Modifier = Modifier) {
    AndroidView(
        factory = {
            inflatePlayerView(it).apply {
                player = controller.exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setKeepContentOnPlayerReset(true)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                VideoFrames.registerPreview(this)
            }
        },
        onRelease = { view ->
            view.player = null
            VideoFrames.registerPreview(null)
        },
        modifier = modifier,
    )
}
