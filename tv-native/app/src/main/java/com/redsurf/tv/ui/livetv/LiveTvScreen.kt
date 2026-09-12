package com.redsurf.tv.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.redsurf.tv.MainViewModel
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.ui.player.PlayerScreen
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.RedSurfType
import com.redsurf.tv.ui.theme.Surface
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
 *
 * Aggregates every loaded playlist, not one "active" one (user request, 2026-09-12 - add a
 * second playlist for testing without deleting the first, both showing up under Live TV). Groups
 * are keyed by (playlistId, groupName) - see [GroupKey] - since the same group name can
 * legitimately exist in two different providers' playlists.
 *
 * Browsing state survives entering/exiting fullscreen: the Row (groups/channels/preview) is
 * always composed, as a sibling of the fullscreen overlay in one Box, never behind an
 * if/early-return that would skip composing it. An earlier version used
 * `if (isFullscreen) { PlayerHost(...); return }`, which - despite reading like a simple guard -
 * is the same Compose conditional-composition trap as the one found in AppShell.kt: skipping a
 * composable call for a frame disposes everything `remember`ed inside it, so every fullscreen
 * toggle silently reset the channel list's scroll position and D-pad focus back to the top, even
 * after selectedGroup/focusedChannel themselves were confirmed surviving. Found live, 2026-09-12.
 * The fullscreen overlay grabs real focus and swallows all key events (no player HUD exists yet -
 * PHASE_1.md Non-goals) so D-pad input can't leak through to the still-composed, now-invisible
 * list underneath. Exiting fullscreen explicitly re-requests focus onto the channel that was
 * playing ([channelReturnFocus]) - the state above surviving isn't enough on its own: when the
 * fullscreen overlay (which held real focus) is removed, Compose's focus system still has to pick
 * *something* to focus next, and with nothing claiming it explicitly it picked the NavStrip's
 * first pill (the nearest focusable node from the top of the tree) - confusing, since a D-pad
 * press from there landed in an arbitrary category, not the one being watched. Found live,
 * 2026-09-12.
 */
@Composable
fun LiveTvScreen(viewModel: MainViewModel, onFullscreenChanged: (Boolean) -> Unit) {
    val groups by viewModel.repository.liveGroups().collectAsState(initial = emptyList())
    var selectedGroup by remember { mutableStateOf<GroupKey?>(null) }
    var focusedChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(groups) {
        if ((selectedGroup == null || groups.none { it.key() == selectedGroup }) && groups.isNotEmpty()) {
            selectedGroup = groups.first().key()
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

    // Debounced the same way, and for the same reason (found live, 2026-09-12 - reported as
    // "choppy," "loss of scrolling," "random behavior" holding UP/DOWN through Categories):
    // GroupsColumn's own highlight tracks selectedGroup instantly so the UI still feels
    // responsive, but actually rebuilding the paged channel list - a new Pager, a new initial
    // page load, ChannelsColumn's whole TvLazyColumn resetting - is real work that a fast D-pad
    // key-repeat was triggering once per row flown over. queriedGroup is what actually drives
    // the middle column; only the group the user settles on for 200ms gets a real query.
    var queriedGroup by remember { mutableStateOf<GroupKey?>(null) }
    LaunchedEffect(selectedGroup) {
        val group = selectedGroup
        if (group == null) {
            queriedGroup = null
        } else {
            delay(200)
            queriedGroup = group
        }
    }

    // Back-while-fullscreen moved into PlayerScreen itself (PHASE_2.md #2.1b) - it now owns real
    // overlay state to peel through first (decision 4), not just "exit fullscreen" in one step.
    // onExitFullscreen below is what PlayerScreen calls once nothing is left to peel.

    val fullscreenFocus = remember { FocusRequester() }
    val channelReturnFocus = remember { FocusRequester() }
    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            fullscreenFocus.requestFocus()
        } else if (focusedChannel != null) {
            delay(100)
            runCatching { channelReturnFocus.requestFocus() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Column proportions from references/streamvault/LiveTV.png: ~28 / 34 / 30 with 20dp
        // gutters. Gutters live here, not as trailing padding inside each column, so every
        // column's own card/background spans exactly its slot. Always composed (see doc above) -
        // never torn down by the fullscreen overlay that sits on top of it.
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
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

            val currentGroup = queriedGroup
            if (currentGroup != null) {
                val channelsFlow = remember(currentGroup) {
                    viewModel.repository.liveChannels(currentGroup.playlistId, currentGroup.groupName)
                        .cachedIn(viewModel.viewModelScope)
                }
                val pagedChannels = channelsFlow.collectAsLazyPagingItems()
                val groupInfo = groups.firstOrNull { it.key() == currentGroup }
                val multiplePlaylists = remember(groups) { groups.map { it.playlistId }.distinct().size > 1 }
                val groupTitle = groupInfo?.let {
                    val name = formatGroupName(it.groupName)
                    if (multiplePlaylists) "${it.playlistName} › $name" else name
                } ?: ""

                ChannelsColumn(
                    groupTitle = groupTitle,
                    groupCount = groupInfo?.count ?: 0,
                    channels = pagedChannels,
                    focusedChannelId = focusedChannel?.streamId,
                    onChannelFocused = { focusedChannel = it },
                    returnFocusRequester = channelReturnFocus,
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
                    modifier = Modifier.weight(1.2f),
                )
            }

            PreviewStub(channel = focusedChannel, modifier = Modifier.weight(1.05f))
        }

        if (isFullscreen) {
            // Extracted to ui/player/PlayerScreen.kt (PHASE_2.md #2.1) - it now owns the overlay
            // state machine and all Back handling while fullscreen, calling onExitFullscreen only
            // once there's nothing left to peel (decision 4).
            val breadcrumb = remember(focusedChannel, groups) {
                val channel = focusedChannel
                val info = channel?.let { c ->
                    groups.firstOrNull { it.playlistId == c.playlistId && it.groupName == c.groupName }
                }
                if (info != null) "${info.playlistName} › ${formatGroupName(info.groupName)}" else ""
            }
            PlayerScreen(
                streamUrl = previewUrl,
                focusRequester = fullscreenFocus,
                onExitFullscreen = {
                    isFullscreen = false
                    onFullscreenChanged(false)
                },
                currentChannel = focusedChannel,
                repository = viewModel.repository,
                onChannelChanged = { channel ->
                    focusedChannel = channel
                    // Bypass the browse-debounce, same reason as onChannelOpen above: zapping is
                    // a deliberate action, not a D-pad fly-by, and waiting on it would reintroduce
                    // the "duplicate 2-step" bug that debounce-bypass already fixed once.
                    previewUrl = channel.streamId
                },
                breadcrumb = breadcrumb,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Right column: the preview card (references/streamvault/LiveTV.png) - a 16:9 thumbnail area
 * (the channel's initial until embedded live preview lands, see the scope note above), title,
 * programme line, and an accent-coloured action hint, all inside one panel card. The header is
 * accent-coloured like the reference's, which is what visually ties this column to the focus
 * ring and the LIVE badge - the three places the brand red appears on this screen besides the
 * active nav pill.
 */
@Composable
private fun PreviewStub(channel: ChannelEntity?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(Surface, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(
            "Channel Preview",
            style = RedSurfType.sectionTitle,
            color = Accent,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (channel != null) {
                Text(
                    channel.name.take(1).uppercase(),
                    style = MaterialTheme.typography.displayMedium,
                    color = TextSecondary.copy(alpha = 0.5f),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Accent)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text("LIVE", style = RedSurfType.badge, color = TextPrimary)
                }
            } else {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(40.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        if (channel != null) {
            Text(
                channel.name,
                style = RedSurfType.heroTitle,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("No schedule information", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Press OK again to open this channel", style = RedSurfType.rowSecondary, color = Accent)
        } else {
            Text("Focus a channel to preview it here", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}
