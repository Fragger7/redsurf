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
import androidx.compose.ui.viewinterop.AndroidView
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
            }
        },
        modifier = modifier.fillMaxSize(),
    )
}
