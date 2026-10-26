package com.redsurf.tv.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout

// PREVIEW.md's dedicated preview ExoPlayer (PreviewPlayerController) was retired 2026-10-20 (L5):
// the preview pane now shows Live TV's one shared PlayerController - its reconnect handling is the
// fullscreen engine's own, which also covers PREVIEW.md's "keep retrying" ask (Sprint 2, item 3).

/**
 * Live TV's one video view (S1, 2026-10-26) - shows the shared [controller] both in the hero's
 * preview box and fullscreen. The caller only moves and resizes it; it is never torn down between
 * the two, so going fullscreen and back no longer creates a new SurfaceView, re-attaches the
 * decoder's output and waits for a fresh first frame (82ms in / 174ms out before). Display only:
 * Live TV and the fullscreen PlayerHost (showView = false) drive the stream.
 */
@OptIn(UnstableApi::class)
@Composable
fun LiveVideoView(
    controller: PlayerController,
    fullscreen: Boolean,
    blackScreenBetweenZaps: Boolean,
    modifier: Modifier = Modifier,
) {
    val resizeMode by controller.resizeMode.collectAsState()
    AndroidView(
        factory = {
            inflatePlayerView(it).apply {
                player = controller.exoPlayer
                useController = false
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { view ->
            // The preview crops to its 16:9 box; fullscreen fits and honours the Aspect action.
            view.resizeMode = if (fullscreen) resizeMode else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            // No-black-screen zapping applies to fullscreen zaps (PHASE_2.md #2.2); the preview
            // always holds the last frame.
            view.setKeepContentOnPlayerReset(!fullscreen || !blackScreenBetweenZaps)
            // Decoding video isn't user activity to Android's idle timer (found live 2026-09-12).
            view.keepScreenOn = fullscreen
        },
        onRelease = { view -> view.player = null },
        modifier = modifier,
    )
}
