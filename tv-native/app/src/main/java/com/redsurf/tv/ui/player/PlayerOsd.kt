package com.redsurf.tv.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.*
import com.redsurf.tv.data.Channel
import com.redsurf.tv.db.EpgProgramEntity
import kotlinx.coroutines.delay

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerOsdOverlay(
    channel: Channel,
    currentProgram: EpgProgramEntity?,
    nextProgram: EpgProgramEntity?,
    exoPlayer: ExoPlayer,
    isVisible: Boolean,
    onTimeout: () -> Unit
) {
    // Auto-hide the OSD after 5 seconds of inactivity
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(5000)
            onTimeout()
        }
    }

    var showTrackSelector by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(animationSpec = tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (showTrackSelector) {
                TrackSelectionMenu(exoPlayer = exoPlayer, onClose = { showTrackSelector = false })
            } else {
                OsdInfoPanel(
                    channel = channel,
                    currentProgram = currentProgram,
                    nextProgram = nextProgram,
                    onOpenTracks = { showTrackSelector = true }
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OsdInfoPanel(
    channel: Channel,
    currentProgram: EpgProgramEntity?,
    nextProgram: EpgProgramEntity?,
    onOpenTracks: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        onClick = {},
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF09090B).copy(alpha = 0.85f),
            focusedContainerColor = Color(0xFF09090B).copy(alpha = 0.85f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Channel & Show Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                if (currentProgram != null) {
                    Text(
                        text = currentProgram.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentProgram.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text("No EPG Data Available", color = Color.Gray)
                }
            }

            // Right: Quick Actions (Audio / Subtitles)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OsdButton(
                    text = "Audio / Subs",
                    modifier = Modifier.focusRequester(focusRequester),
                    onClick = onOpenTracks
                )
                // Additional future buttons: Record, PiP, Catch-up
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun OsdButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    
    Surface(
        onClick = onClick,
        modifier = modifier
            .width(150.dp)
            .height(50.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF27272A),
            focusedContainerColor = Color(0xFFE11D48) // Rose-600
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TrackSelectionMenu(exoPlayer: ExoPlayer, onClose: () -> Unit) {
    // This reads live track data from the ExoPlayer instance
    val currentTracks = exoPlayer.currentTracks
    val audioGroup = currentTracks.groups.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
    val textGroup = currentTracks.groups.firstOrNull { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }

    Surface(
        onClick = {},
        modifier = Modifier.fillMaxWidth().height(250.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF09090B).copy(alpha = 0.95f)
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text("Audio Tracks", color = Color.White, style = MaterialTheme.typography.titleLarge)
            LazyRow(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (audioGroup == null || audioGroup.length == 0) {
                    item { Text("No alternate audio available", color = Color.Gray) }
                } else {
                    items(audioGroup.length) { i ->
                        val format = audioGroup.getTrackFormat(i)
                        val language = format.language ?: "Track \${i + 1}"
                        OsdButton(
                            text = language.uppercase(),
                            onClick = {
                                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                                    .buildUpon()
                                    .setOverrideForType(TrackSelectionOverride(audioGroup.mediaTrackGroup, i))
                                    .build()
                                onClose()
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Subtitles (CC)", color = Color.White, style = MaterialTheme.typography.titleLarge)
            // Implementation for Subtitles follows same pattern as audio...
        }
    }
}
