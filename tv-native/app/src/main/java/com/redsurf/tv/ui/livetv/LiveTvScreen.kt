package com.redsurf.tv.ui.livetv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.redsurf.tv.MainViewModel
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.player.PlayerHost
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.SurfaceRaised
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * The screen this whole phase is built to prove (docs/plans/PHASE_1.md #1.4/#1.5). Three columns
 * matching references/streamvault/LiveTV.png: categories, the selected group's channels, and a
 * preview of whichever channel currently has D-pad focus. OK opens the focused channel
 * fullscreen with real playback.
 *
 * Scope note vs. the original #1.5 text: the preview column is a static info stub (name +
 * "No schedule information" + the "press OK again" hint), not an embedded live video thumbnail.
 * Sharing one ExoPlayer between a small embedded preview and a fullscreen view without ever
 * having two decoders alive at once needs pixel-exact overlay positioning; deferred as its own
 * pass rather than rushed here. Fullscreen playback itself - the thing actually reported broken
 * (OK did nothing) - is fully real: PlayerHost is created once per fullscreen session and
 * released on exit, never duplicated.
 */
@Composable
fun LiveTvScreen(viewModel: MainViewModel, playlistId: String, onFullscreenChanged: (Boolean) -> Unit) {
    val groups by viewModel.repository.liveGroups(playlistId).collectAsState(initial = emptyList())
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var focusedChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(groups) {
        if (selectedGroup == null && groups.isNotEmpty()) {
            selectedGroup = groups.first().groupName
        }
    }

    // Debounced: a D-pad flying down the list must not start a stream per row it passes over.
    // Only the channel the user rests on for 500ms actually loads.
    var previewUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(focusedChannel) {
        val channel = focusedChannel
        if (channel == null) {
            previewUrl = null
        } else {
            delay(500)
            previewUrl = channel.streamId
        }
    }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
        onFullscreenChanged(false)
    }

    if (isFullscreen) {
        PlayerHost(streamUrl = previewUrl, fullscreen = true, modifier = Modifier.fillMaxSize())
        return
    }

    Row(modifier = Modifier.fillMaxSize()) {
        GroupsColumn(
            groups = groups,
            selectedGroup = selectedGroup,
            onGroupFocused = { group ->
                if (group != selectedGroup) {
                    selectedGroup = group
                    focusedChannel = null
                }
            },
            modifier = Modifier.weight(1f),
        )

        val currentGroup = selectedGroup
        if (currentGroup != null) {
            val channelsFlow = remember(playlistId, currentGroup) {
                viewModel.repository.liveChannels(playlistId, currentGroup).cachedIn(viewModel.viewModelScope)
            }
            val pagedChannels = channelsFlow.collectAsLazyPagingItems()
            val groupCount = groups.firstOrNull { it.groupName == currentGroup }?.count ?: 0

            ChannelsColumn(
                groupName = currentGroup,
                groupCount = groupCount,
                channels = pagedChannels,
                focusedChannelId = focusedChannel?.streamId,
                onChannelFocused = { focusedChannel = it },
                onChannelOpen = { channel ->
                    focusedChannel = channel
                    // Bypass the 500ms browse-debounce above: opening is a deliberate action,
                    // not a D-pad fly-by, so PlayerHost must get the URL on this same frame.
                    // Without this, isFullscreen flips true immediately but previewUrl (what
                    // PlayerHost actually plays) only catches up after the debounce delay,
                    // which read to the user as a "duplicate 2-step" to get a channel playing.
                    previewUrl = channel.streamId
                    isFullscreen = true
                    onFullscreenChanged(true)
                },
                modifier = Modifier.weight(1.4f),
            )
        }

        PreviewStub(channel = focusedChannel, modifier = Modifier.weight(1.2f))
    }
}

/**
 * Right column: a real preview card (references/streamvault/LiveTV.png), not bare text - a 16:9
 * thumbnail area (the channel's initial until embedded live preview lands, see the scope note
 * above), title, metadata, and an accent-colored action hint so the column reads as an actual
 * part of the product rather than a debug stub.
 */
@Composable
private fun PreviewStub(channel: ChannelEntity?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(start = 8.dp)) {
        Text(
            "Channel Preview",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 20.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (channel != null) {
                Text(
                    channel.name.take(1).uppercase(),
                    style = MaterialTheme.typography.displayLarge,
                    color = TextSecondary,
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Accent)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text("LIVE", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                }
            }
        }

        if (channel != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(channel.name, style = MaterialTheme.typography.headlineSmall, color = TextPrimary, maxLines = 2)
            Spacer(modifier = Modifier.height(6.dp))
            Text("No schedule information", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Press OK again to open this channel", style = MaterialTheme.typography.bodyMedium, color = Accent)
        } else {
            Spacer(modifier = Modifier.height(20.dp))
            Text("Focus a channel to preview it here", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}
