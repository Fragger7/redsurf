package com.redsurf.tv.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.redsurf.tv.network.IptvNetworkModule
import com.redsurf.tv.player.tracks.TrackManager
import com.redsurf.tv.player.tuning.AfrManager
import kotlin.math.roundToInt

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The live stream's current characteristics, for the zap-banner/info-block badges
 * (PHASE_2.md #2.2, decision 7). A field is null - never a placeholder value - whenever ExoPlayer
 * hasn't reported it yet; decision 7 is explicit that an unknown badge is omitted, not shown
 * empty. Delivered via [PlayerHost]'s `onStreamInfo` callback rather than a caller-owned
 * `StateFlow` (the brief's literal suggestion) - every other composable in this codebase reports
 * upward the same way (`onFullscreenChanged`, `onChannelFocused`, …), and matching that idiom
 * beats introducing a second pattern for one composable.
 */
data class StreamInfo(
    val resolutionClass: String? = null, // "SD" | "HD" | "FHD" | "4K"
    val rawResolution: String? = null, // "1920x1080" - user request, 2026-09-12: a badge showing
    // the actual detected pixel resolution as an alternative to the SD/HD/FHD/4K class, toggled
    // in Settings. Captured now since it's free alongside resolutionClass; the toggle itself
    // waits on Settings having a real place to put it (AGENTS.md backlog).
    val frameRate: Int? = null,
    val audioChannels: String? = null, // "STEREO" | "5.1" | "N ch"
    val audioCodec: String? = null, // "AAC" | "AC3" | "EAC3" | "MP3"
    val videoCodec: String? = null, // "H.264" | "H.265" | "VP9" - not in decision 7's badge list,
    // captured anyway since it's free here and decision 9's "Video info" picker wants it later.
)

private fun resolutionClassOf(height: Int): String? = when {
    height <= 0 -> null
    height < 720 -> "SD"
    height < 1080 -> "HD"
    height < 2160 -> "FHD"
    else -> "4K"
}

private fun audioChannelsLabelOf(channelCount: Int): String? = when {
    channelCount <= 0 -> null
    channelCount == 2 -> "STEREO"
    channelCount == 6 -> "5.1"
    else -> "$channelCount ch"
}

private fun audioCodecOf(mimeType: String?): String? = when (mimeType) {
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_AC3 -> "AC3"
    MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "EAC3"
    MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L1, MimeTypes.AUDIO_MPEG_L2 -> "MP3"
    else -> null
}

private fun videoCodecOf(mimeType: String?): String? = when (mimeType) {
    MimeTypes.VIDEO_H264 -> "H.264"
    MimeTypes.VIDEO_H265 -> "H.265"
    MimeTypes.VIDEO_VP9 -> "VP9"
    else -> null
}

/**
 * Owns one ExoPlayer for as long as this composable stays in composition (PHASE_1.md #1.5,
 * Decisions #5). The predecessor, ExoPlayerView, released its player inside
 * DisposableEffect(streamUrl)'s onDispose - so it only ever worked for a single URL; the second
 * one it was ever handed hit a released player. This swaps media items on the same player
 * instead of recreating it.
 *
 * [fullscreen] enables AFR (Decisions #6 - never mid-scroll, only when actually watching).
 * [onStreamInfo] fires whenever the current track's characteristics become known or change - see
 * [StreamInfo].
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerHost(
    streamUrl: String?,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
    onStreamInfo: (StreamInfo) -> Unit = {},
) {
    val context = LocalContext.current

    val exoPlayer = remember {
        val dataSourceFactory = IptvNetworkModule.getDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val trackManager = TrackManager(context)
        // Fast-zap buffering (PHASE_2.md #2.2, decision 13, PRODUCT_VISION.md #4): start playback
        // the moment there's 500ms of buffer rather than ExoPlayer's much larger default, while
        // still building a real cushion (up to 15s) in the background to survive jitter. These
        // are a starting point - #2.6 measures actual key-press-to-first-frame latency on the
        // real list and they're only retuned from that measurement, not guessed again.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2_500, 15_000, 500, 1_500)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackManager.trackSelector)
            .setLoadControl(loadControl)
            .build()
            .apply { playWhenReady = true }
    }

    val afrManager = remember {
        val manager = AfrManager(context, exoPlayer)
        val activity = context.findActivity()
        manager.onModeFound = { modeId ->
            activity?.window?.attributes = activity?.window?.attributes?.apply {
                preferredDisplayModeId = modeId
            }
        }
        manager
    }

    LaunchedEffect(fullscreen) {
        afrManager.isEnabled = fullscreen
        if (!fullscreen) afrManager.restoreOriginalMode()
    }

    val lastUrl = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(streamUrl) {
        if (streamUrl != null && streamUrl != lastUrl.value) {
            exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
            exoPlayer.prepare()
            lastUrl.value = streamUrl
        }
    }

    // Home button during playback left audio running until the app was force-closed (found
    // live, 2026-09-12) - Compose composition doesn't track the Activity going to the
    // background on its own, so nothing told ExoPlayer to stop. This app has no background-
    // playback feature (no MediaSession, no foreground service) - pause is the correct,
    // conservative behavior here, not a workaround. Resumes automatically on return, from
    // wherever the buffer left off, since pause() (unlike release()) doesn't drop position.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose {
            afrManager.restoreOriginalMode()
            exoPlayer.release()
        }
    }

    // Stream badges (PHASE_2.md #2.2, decision 7) - recomputed from whatever ExoPlayer currently
    // reports whenever a track or the video size changes, which covers both the very first
    // format becoming known and a mid-stream change (e.g. the provider switching resolution).
    DisposableEffect(exoPlayer) {
        fun report() {
            val video = exoPlayer.videoFormat
            val audio = exoPlayer.audioFormat
            onStreamInfo(
                StreamInfo(
                    resolutionClass = video?.height?.let(::resolutionClassOf),
                    rawResolution = video?.takeIf { it.width > 0 && it.height > 0 }?.let { "${it.width}x${it.height}" },
                    frameRate = video?.frameRate?.takeIf { it > 0f }?.roundToInt(),
                    audioChannels = audio?.channelCount?.let(::audioChannelsLabelOf),
                    audioCodec = audioCodecOf(audio?.sampleMimeType),
                    videoCodec = videoCodecOf(video?.sampleMimeType),
                ),
            )
        }
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) = report()
            override fun onVideoSizeChanged(videoSize: VideoSize) = report()
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                // No-black-screen zapping (PHASE_2.md #2.2, decision 13,
                // PRODUCT_VISION.md #3's "black screen minimizer"): hold the outgoing channel's
                // last frame - on a solid black shutter, never a stale video frame from whatever
                // was on screen even earlier - until the next stream's first frame is ready,
                // instead of clearing to a blank/default surface the instant media is reset.
                setKeepContentOnPlayerReset(true)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                // Screensaver/screen-off kicking in mid-playback (found live, 2026-09-12) -
                // decoding/rendering video isn't "user activity" as far as Android's idle timer
                // is concerned, so the OS has no reason not to sleep the screen. This is the
                // standard fix (same as ExoPlayer's own demo app): tied to the View, so it goes
                // away on its own when this View is torn down on exiting fullscreen - no
                // separate cleanup needed, unlike a Window-level flag.
                keepScreenOn = true
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
