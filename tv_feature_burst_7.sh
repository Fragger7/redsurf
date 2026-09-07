#!/bin/bash
set -e

BASE_DIR="tv-native/app/src/main/java/com/redsurf/tv"
mkdir -p "$BASE_DIR/player/multiview"
mkdir -p "$BASE_DIR/settings"
mkdir -p "$BASE_DIR/vod"

# 1. Multi-View Engine (Picture-in-Picture / Grid)
cat << 'KOTLIN' > "$BASE_DIR/player/multiview/MultiViewEngine.kt"
package com.redsurf.tv.player.multiview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.redsurf.tv.data.Channel
import com.redsurf.tv.player.ExoPlayerView

/**
 * TiViMate Parity: Multi-View (watching 2 to 9 streams simultaneously).
 * This requires multiple ExoPlayer instances and strict memory management.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MultiViewScreen(
    activeChannels: List<Channel>,
    onChannelSelect: (Int) -> Unit // Index of the focused screen to swap/change
) {
    // Grid calculation based on number of active streams (1, 2, 4, 9)
    val columns = when (activeChannels.size) {
        in 1..2 -> 2
        in 3..4 -> 2
        else -> 3
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            activeChannels.chunked(columns).forEachIndexed { rowIndex, rowChannels ->
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    rowChannels.forEachIndexed { colIndex, channel ->
                        val globalIndex = rowIndex * columns + colIndex
                        var isFocused by remember { mutableStateOf(false) }

                        Surface(
                            onClick = { onChannelSelect(globalIndex) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onFocusChanged { isFocused = it.isFocused }
                                .border(
                                    width = if (isFocused) 4.dp else 1.dp,
                                    color = if (isFocused) Color(0xFFE11D48) else Color.DarkGray
                                ),
                            colors = ClickableSurfaceDefaults.colors(containerColor = Color.Black)
                        ) {
                            // Each surface gets its own dedicated hardware player instance
                            ExoPlayerView(
                                streamUrl = channel.streamUrl,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
KOTLIN

# 2. VOD & Series Architecture (The Xtream API Engine)
cat << 'KOTLIN' > "$BASE_DIR/vod/XtreamApi.kt"
package com.redsurf.tv.vod

import com.redsurf.tv.network.IptvNetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class VodMovie(val id: String, val name: String, val cover: String, val rating: String, val streamExtension: String)
data class VodSeries(val id: String, val name: String, val cover: String, val rating: String)

/**
 * TiViMate Parity: True VOD and Series support.
 * M3U parsing is for Live TV. VOD requires hitting the actual Xtream Codes JSON API.
 */
object XtreamApi {
    
    suspend fun getMovies(serverUrl: String, user: String, pass: String, categoryId: String? = null): List<VodMovie> = withContext(Dispatchers.IO) {
        val action = if (categoryId == null) "get_vod_streams" else "get_vod_streams&category_id=\$categoryId"
        val url = "\$serverUrl/player_api.php?username=\$user&password=\$pass&action=\$action"
        
        val request = Request.Builder().url(url).build()
        val response = IptvNetworkModule.getOkHttpClient().newCall(request).execute()
        
        val movies = mutableListOf<VodMovie>()
        if (response.isSuccessful) {
            val jsonArray = JSONArray(response.body?.string() ?: "[]")
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                movies.add(
                    VodMovie(
                        id = obj.optString("stream_id"),
                        name = obj.optString("name"),
                        cover = obj.optString("stream_icon"),
                        rating = obj.optString("rating", "0.0"),
                        streamExtension = obj.optString("container_extension", "mp4")
                    )
                )
            }
        }
        movies
    }

    suspend fun getSeries(serverUrl: String, user: String, pass: String): List<VodSeries> = withContext(Dispatchers.IO) {
        val url = "\$serverUrl/player_api.php?username=\$user&password=\$pass&action=get_series"
        val request = Request.Builder().url(url).build()
        val response = IptvNetworkModule.getOkHttpClient().newCall(request).execute()
        
        val series = mutableListOf<VodSeries>()
        if (response.isSuccessful) {
            val jsonArray = JSONArray(response.body?.string() ?: "[]")
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                series.add(
                    VodSeries(
                        id = obj.optString("series_id"),
                        name = obj.optString("name"),
                        cover = obj.optString("cover"),
                        rating = obj.optString("rating", "0.0")
                    )
                )
            }
        }
        series
    }
}
KOTLIN

# 3. Buffer Settings & Video Decoder Overrides (The Expert Panel)
cat << 'KOTLIN' > "$BASE_DIR/settings/PlayerSettings.kt"
package com.redsurf.tv.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * TiViMate Parity: Deep player tuning.
 * Low-end FireSticks require massive buffer sizes for 4K streams.
 */
object PlayerSettings {
    
    private const val PREFS_NAME = "redsurf_player_prefs"
    
    // Hardware Decoder vs Software (libVLC fallback)
    var useSoftwareDecoder: Boolean = false
    
    // ExoPlayer Buffer sizes
    var minBufferMs: Int = 15000     // Wait 15 seconds before starting playback if network is poor
    var maxBufferMs: Int = 50000     // Keep up to 50 seconds in memory
    var bufferForPlaybackMs: Int = 2500 // Start playing after 2.5 seconds are buffered
    
    // Audio offset for out-of-sync provider feeds
    var audioDelayMs: Long = 0L

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        useSoftwareDecoder = prefs.getBoolean("useSoftwareDecoder", false)
        minBufferMs = prefs.getInt("minBufferMs", 15000)
        maxBufferMs = prefs.getInt("maxBufferMs", 50000)
        audioDelayMs = prefs.getLong("audioDelayMs", 0L)
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        prefs.putBoolean("useSoftwareDecoder", useSoftwareDecoder)
        prefs.putInt("minBufferMs", minBufferMs)
        prefs.putInt("maxBufferMs", maxBufferMs)
        prefs.putLong("audioDelayMs", audioDelayMs)
        prefs.apply()
    }
}
KOTLIN

chmod +x tv_feature_burst_7.sh
./tv_feature_burst_7.sh
