package com.redsurf.tv.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.redsurf.tv.network.IptvNetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * `docs/plans/PREVIEW.md` - the answer to `LiveTvScreen.kt`'s own long-standing scope note about
 * "sharing one ExoPlayer between a small embedded preview and a fullscreen view without ever
 * having two decoders alive at once": don't share one - use two, mutually exclusive in time. This
 * is the preview half - a second, dedicated, deliberately minimal ExoPlayer instance, never the
 * same instance `PlayerController`/`PlayerHost` build for fullscreen. It has no stall watchdog, no
 * reconnect ladder, no error-presentation surface - PREVIEW.md is explicit that a preview error
 * should just fall back to the static stub silently, not surface a branded panel meant for a
 * long-running fullscreen session. Always released well before/as a fullscreen session starts, so
 * only one decoder is ever alive at once on this hardware (`HARDWARE.md`'s ~449MB ceiling).
 */
@OptIn(UnstableApi::class)
class PreviewPlayerController(
    context: android.content.Context,
    playlistUserAgent: String? = null,
) {
    val exoPlayer: ExoPlayer = run {
        val dataSourceFactory = IptvNetworkModule.getDataSourceFactory(playlistUserAgent)
        val extractorsFactory = DefaultExtractorsFactory()
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        // Smaller than the fullscreen controller's buffer on purpose - a preview should feel
        // instant to switch, not survive minutes of network jitter; it's replaced or released
        // again within seconds of being opened, never a long-running session.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1_000, 6_000, 500, 1_000)
            .setTargetBufferBytes(8 * 1024 * 1024)
            .build()
        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .build()
            .apply { playWhenReady = true }
    }

    private val _hasError = MutableStateFlow(false)
    val hasError: StateFlow<Boolean> = _hasError.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    // Sprint 2, 2026-09-23 (SEQUENCING.md item 3) - PREVIEW.md originally shipped with no
    // reconnect at all ("keep it simple... fall back to the static stub silently"); the user has
    // since clarified preview should run perpetually until they navigate away or close the app,
    // not give up on the first transient error. A light reconnect, deliberately not the
    // fullscreen controller's full stall-watchdog ladder: on error, keep retrying the *same* URL
    // on a fixed interval for as long as this controller is alive - `release()` cancels the scope,
    // which is the natural "stop trying" signal (leaving Live TV, promoting to fullscreen, or
    // switching the preview to a different channel all release/replace this controller already).
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var reconnectJob: Job? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                _hasError.value = true
                armReconnect()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _isReady.value = true
                    _hasError.value = false
                    reconnectJob?.cancel()
                    reconnectJob = null
                }
            }
        })
    }

    private fun armReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            while (true) {
                delay(RECONNECT_INTERVAL_MS)
                val url = lastUrl ?: return@launch
                exoPlayer.stop()
                exoPlayer.setMediaItem(MediaItem.fromUri(url))
                exoPlayer.prepare()
                // Give this attempt real time to either reach STATE_READY (the listener above
                // cancels this job) or error again (onPlayerError re-arms - already a no-op here
                // since this job is still active) before trying again.
                delay(RECONNECT_INTERVAL_MS)
            }
        }
    }

    private var lastUrl: String? = null

    fun play(url: String) {
        if (url == lastUrl) return
        reconnectJob?.cancel()
        reconnectJob = null
        lastUrl = url
        _hasError.value = false
        _isReady.value = false
        exoPlayer.stop()
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
    }

    fun release() {
        scope.cancel()
        exoPlayer.release()
    }

    private companion object {
        const val RECONNECT_INTERVAL_MS = 3_000L
    }
}

/** One instance per composition of the preview area - torn down (and the decoder released) the
 * moment the caller stops composing this, whether that's promotion to fullscreen or leaving Live
 * TV entirely (`PREVIEW.md` point 6). Never `remember`ed across a channel change by design - a new
 * controller per distinct preview session mirrors `rememberPlayerController`'s own "one per
 * fullscreen session" shape, just at preview's much shorter timescale. */
@Composable
fun rememberPreviewPlayerController(playlistUserAgent: String?): PreviewPlayerController {
    val context = LocalContext.current
    val controller = remember { PreviewPlayerController(context, playlistUserAgent) }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    return controller
}

/** The thin `PlayerView` binding for the preview area - deliberately far simpler than
 * `PlayerHost`: no AFR, no black-screen-between-zaps overlay, no aspect-ratio cycling. A preview
 * is a small, short-lived glance, not the thing being watched. */
@OptIn(UnstableApi::class)
@Composable
fun PreviewPlayerHost(
    controller: PreviewPlayerController,
    streamUrl: String,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(streamUrl, controller) {
        controller.play(streamUrl)
    }
    AndroidView(
        factory = {
            PlayerView(it).apply {
                player = controller.exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { view -> view.player = controller.exoPlayer },
        modifier = modifier,
    )
}
