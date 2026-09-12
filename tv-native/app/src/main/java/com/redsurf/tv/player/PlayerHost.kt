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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.redsurf.tv.network.IptvNetworkModule
import com.redsurf.tv.player.tracks.TrackManager
import com.redsurf.tv.player.tuning.AfrManager

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
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
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerHost(streamUrl: String?, fullscreen: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val exoPlayer = remember {
        val dataSourceFactory = IptvNetworkModule.getDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val trackManager = TrackManager(context)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackManager.trackSelector)
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
