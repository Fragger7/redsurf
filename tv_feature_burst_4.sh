#!/bin/bash
set -e

BASE_DIR="tv-native/app/src/main/java/com/redsurf/tv"
mkdir -p "$BASE_DIR/network"
mkdir -p "$BASE_DIR/player/vlc"

# 1. Custom Network Interceptor (Bypassing ISP/Provider Blocks)
cat << 'KOTLIN' > "$BASE_DIR/network/IptvNetworkModule.kt"
package com.redsurf.tv.network

import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * World-class IPTV apps must spoof User-Agents and handle aggressive HTTP redirects.
 * Many cheap IPTV panels block default ExoPlayer user-agents or require specific referers.
 */
object IptvNetworkModule {
    
    // Default to mimicking a standard browser or VLC to avoid provider 403 blocks
    var currentUserAgent = "VLC/3.0.18 LibVLC/3.0.18"

    fun getOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithHeaders = originalRequest.newBuilder()
                    .header("User-Agent", currentUserAgent)
                    .header("Accept", "*/*")
                    // Some providers block empty referers on TS files
                    .header("Referer", originalRequest.url.host) 
                    .build()
                chain.proceed(requestWithHeaders)
            }
            .build()
    }

    // Custom DataSource.Factory for ExoPlayer
    fun getDataSourceFactory(): HttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent(currentUserAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to currentUserAgent,
                    "Accept" to "*/*"
                )
            )
    }
}
KOTLIN

# 2. Dual Engine Interface
cat << 'KOTLIN' > "$BASE_DIR/player/PlayerEngine.kt"
package com.redsurf.tv.player

enum class PlayerEngineType {
    EXO_PLAYER, // Best for modern HLS/Dash, hardware acceleration
    LIB_VLC     // Best fallback for heavily interlaced MPEG-TS, weird AC3 audio codecs
}

interface RedSurfPlayer {
    fun play(url: String)
    fun pause()
    fun stop()
    fun release()
    fun setVolume(volume: Float)
    fun setAudioTrack(trackId: String)
}
KOTLIN

# 3. Update ExoPlayer to use the Custom Network Stack
cat << 'KOTLIN' > "$BASE_DIR/player/ExoPlayerView.kt"
package com.redsurf.tv.player

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

@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerView(
    streamUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val exoPlayer = remember {
        // Inject our custom Anti-Block Network Module here
        val dataSourceFactory = IptvNetworkModule.getDataSourceFactory()
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playWhenReady = true
            }
    }

    DisposableEffect(streamUrl) {
        if (streamUrl.isNotEmpty()) {
            val mediaItem = MediaItem.fromUri(streamUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
        }
        onDispose {
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
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        modifier = modifier
    )
}
KOTLIN

chmod +x tv_feature_burst_4.sh
./tv_feature_burst_4.sh
