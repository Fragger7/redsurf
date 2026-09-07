#!/bin/bash
set -e

BASE_DIR="tv-native/app/src/main/java/com/redsurf/tv"
mkdir -p "$BASE_DIR/data"
mkdir -p "$BASE_DIR/parser"
mkdir -p "$BASE_DIR/player"
mkdir -p "$BASE_DIR/ui"

# 1. Data Models
cat << 'KOTLIN' > "$BASE_DIR/data/Channel.kt"
package com.redsurf.tv.data

data class Channel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String = "",
    val group: String = "Uncategorized",
    val epgId: String = ""
)

data class ChannelGroup(
    val name: String,
    val channels: List<Channel>
)
KOTLIN

# 2. M3U Parser (Optimized for massive lists)
cat << 'KOTLIN' > "$BASE_DIR/parser/M3uParser.kt"
package com.redsurf.tv.parser

import com.redsurf.tv.data.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.UUID

object M3uParser {
    suspend fun parse(inputStream: InputStream): List<Channel> = withContext(Dispatchers.IO) {
        val channels = mutableListOf<Channel>()
        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        var currentEpgId = ""

        inputStream.bufferedReader().useLines { lines ->
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                if (trimmed.startsWith("#EXTINF:")) {
                    // Extract tvg-logo
                    val logoRegex = "tvg-logo=\"([^\"]+)\"".toRegex()
                    currentLogo = logoRegex.find(trimmed)?.groupValues?.get(1) ?: ""

                    // Extract group-title
                    val groupRegex = "group-title=\"([^\"]+)\"".toRegex()
                    currentGroup = groupRegex.find(trimmed)?.groupValues?.get(1) ?: "Uncategorized"

                    // Extract tvg-id (EPG)
                    val idRegex = "tvg-id=\"([^\"]+)\"".toRegex()
                    currentEpgId = idRegex.find(trimmed)?.groupValues?.get(1) ?: ""

                    // Extract name (after the last comma)
                    currentName = trimmed.substringAfterLast(",").trim()
                } else if (!trimmed.startsWith("#")) {
                    // It's a URL
                    channels.add(
                        Channel(
                            id = UUID.randomUUID().toString(),
                            name = currentName.ifEmpty { "Unknown Channel" },
                            streamUrl = trimmed,
                            logoUrl = currentLogo,
                            group = currentGroup,
                            epgId = currentEpgId
                        )
                    )
                    // Reset
                    currentName = ""
                    currentLogo = ""
                    currentGroup = ""
                    currentEpgId = ""
                }
            }
        }
        channels
    }
}
KOTLIN

# 3. ExoPlayer Compose Wrapper
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
@Composable
fun ExoPlayerView(
    streamUrl: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
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
                useController = false // We build our own OSD (On-Screen Display)
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

# 4. The TiViMate Layout (Main Screen)
cat << 'KOTLIN' > "$BASE_DIR/ui/TiViMateLayout.kt"
package com.redsurf.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import com.redsurf.tv.data.Channel
import com.redsurf.tv.data.ChannelGroup
import com.redsurf.tv.player.ExoPlayerView

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TiViMateLayout(groups: List<ChannelGroup>) {
    var selectedGroup by remember { mutableStateOf(groups.firstOrNull()) }
    var focusedChannel by remember { mutableStateOf<Channel?>(null) }
    var isMenuOpen by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Background Video Player
        focusedChannel?.streamUrl?.let { url ->
            ExoPlayerView(
                streamUrl = url,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Overlay & Menu
        if (isMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)) // Classic TiViMate dim
            )

            Row(modifier = Modifier.fillMaxSize()) {
                // Left Panel: Groups
                TvLazyColumn(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(250.dp)
                        .background(Color(0xFF1E1E1E).copy(alpha = 0.9f))
                ) {
                    items(groups) { group ->
                        GroupItem(
                            group = group,
                            isSelected = selectedGroup == group,
                            onFocus = { selectedGroup = group }
                        )
                    }
                }

                // Right Panel: Channels in Group
                selectedGroup?.let { group ->
                    TvLazyColumn(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        items(group.channels) { channel ->
                            ChannelItem(
                                channel = channel,
                                onFocus = { focusedChannel = channel },
                                onClick = { isMenuOpen = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GroupItem(group: ChannelGroup, isSelected: Boolean, onFocus: () -> Unit) {
    Surface(
        onClick = { },
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .onFocusChanged { if (it.isFocused) onFocus() },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Text(
            text = group.name,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
            color = if (isSelected) Color.White else Color.Gray
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelItem(channel: Channel, onFocus: () -> Unit, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp)
            .onFocusChanged { if (it.isFocused) onFocus() },
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface
        )
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
KOTLIN

# 5. ViewModel & Firebase Pairing Logic
cat << 'KOTLIN' > "$BASE_DIR/MainViewModel.kt"
package com.redsurf.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.redsurf.tv.data.Channel
import com.redsurf.tv.data.ChannelGroup
import com.redsurf.tv.parser.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.URL
import kotlin.random.Random

sealed class AppState {
    object Loading : AppState()
    data class Pairing(val code: String) : AppState()
    data class Loaded(val groups: List<ChannelGroup>) : AppState()
    data class Error(val message: String) : AppState()
}

class MainViewModel : ViewModel() {
    private val db = Firebase.firestore
    
    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state

    init {
        generatePairingCode()
    }

    private fun generatePairingCode() {
        val code = (100000..999999).random().toString()
        _state.value = AppState.Pairing(code)

        val sessionRef = db.collection("pairingSessions").document(code)
        
        viewModelScope.launch {
            try {
                sessionRef.set(mapOf(
                    "status" to "waiting",
                    "createdAt" to System.currentTimeMillis()
                )).await()

                // Listen for mobile completion
                sessionRef.addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    if (snapshot != null && snapshot.exists()) {
                        val status = snapshot.getString("status")
                        if (status == "paired") {
                            val url = snapshot.getString("url")
                            if (!url.isNullOrEmpty()) {
                                loadPlaylist(url)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _state.value = AppState.Error("Failed to initialize pairing.")
            }
        }
    }

    private fun loadPlaylist(url: String) {
        _state.value = AppState.Loading
        viewModelScope.launch {
            try {
                val channels = withContext(Dispatchers.IO) {
                    val inputStream = URL(url).openStream()
                    M3uParser.parse(inputStream)
                }

                // Group channels
                val grouped = channels.groupBy { it.group }
                    .map { ChannelGroup(it.key, it.value) }
                    .sortedBy { it.name }

                _state.value = AppState.Loaded(grouped)
            } catch (e: Exception) {
                _state.value = AppState.Error(e.message ?: "Failed to load playlist")
            }
        }
    }
}
KOTLIN

# 6. Update MainActivity
cat << 'KOTLIN' > "$BASE_DIR/MainActivity.kt"
package com.redsurf.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.redsurf.tv.ui.TiViMateLayout

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val state by viewModel.state.collectAsState()

                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFF09090B)),
                    contentAlignment = Alignment.Center
                ) {
                    when (val s = state) {
                        is AppState.Loading -> {
                            Text("Loading...", color = Color.White)
                        }
                        is AppState.Pairing -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("RedSurf TV Pairing", style = MaterialTheme.typography.displayMedium, color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Go to redsurf.app on your phone and enter this code:", color = Color.Gray)
                                Spacer(modifier = Modifier.height(32.dp))
                                Text(s.code, style = MaterialTheme.typography.displayLarge, color = Color(0xFFE11D48)) // Rose-600
                            }
                        }
                        is AppState.Loaded -> {
                            TiViMateLayout(groups = s.groups)
                        }
                        is AppState.Error -> {
                            Text("Error: ${s.message}", color = Color.Red)
                        }
                    }
                }
            }
        }
    }
}
KOTLIN

chmod +x tv_feature_burst.sh
./tv_feature_burst.sh

