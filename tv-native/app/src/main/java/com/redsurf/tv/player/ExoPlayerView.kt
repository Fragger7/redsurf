package com.redsurf.tv.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.redsurf.tv.player.tuning.AfrManager

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerView(
    streamUrl: String,
    useSecureDns: Boolean = false,
    dnsProvider: String = "Cloudflare",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val exoPlayer = remember(useSecureDns, dnsProvider) {
        val dataSourceFactory = IptvNetworkModule.buildDataSourceFactory(context, useSecureDns, dnsProvider)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        player.playWhenReady = true
        player
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

    DisposableEffect(streamUrl) {
        if (streamUrl.isNotEmpty()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
            exoPlayer.prepare()
        }
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
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        },
        modifier = modifier
    )
}
