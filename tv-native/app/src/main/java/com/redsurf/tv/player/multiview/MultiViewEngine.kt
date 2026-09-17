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
import com.redsurf.tv.player.PlayerHost
import com.redsurf.tv.player.rememberPlayerController

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
                            // Each surface gets its own dedicated hardware player instance.
                            // This screen is unreferenced dead code (PHASE_1.md Non-goals -
                            // multiview is a later phase; PLAYER_ENGINEERING_BRIEF.md §3.2/§9
                            // explicitly defers a real rewrite of this file to when multiview
                            // becomes a real phase - a shared allocator across tiles, per-tile
                            // budgets, device-derived tile caps, none of which belong here yet);
                            // updated only to keep it compiling after the PlayerController hoist.
                            PlayerHost(
                                controller = rememberPlayerController(),
                                streamUrl = channel.streamUrl,
                                fullscreen = false,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}
