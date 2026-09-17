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
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
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
import kotlinx.coroutines.launch

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
fun LiveTvScreen(
    viewModel: MainViewModel,
    onFullscreenChanged: (Boolean) -> Unit,
    // BACKLOG_SWEEP.md #8: lets AppShell exclude its own Home-jump BackHandler while real focus
    // is in the Channels column, mirroring the existing fullscreen mutual-exclusivity mechanism
    // (see AppShell.kt's own doc comment) rather than introducing a second, competing pattern.
    onChannelsFocusChanged: (Boolean) -> Unit = {},
    // BACKLOG_SWEEP.md #11/#12 - threaded straight through to PlayerScreen; LiveTvScreen has no
    // use for either value itself, it's just the composable in between AppShell (which owns
    // AppPreferences) and PlayerScreen (which actually reads them).
    blackScreenBetweenZaps: Boolean = false,
    showRawResolution: Boolean = false,
    // Hoisted to AppShell (found live, 2026-09-15, user report) - same conditional-composition
    // trap SettingsScreen's own selectedCategory already had to escape: this composable is torn
    // down and recomposed fresh every time `destination` switches away from Live TV and back, so
    // a plain `remember` here reset the category, the focused channel, and the recent-tiles row
    // to nothing on every re-entry instead of keeping where the user actually was. Plain
    // value/`onXChanged` hoisting (Kotlin local `var`s can't take custom accessors, unlike a
    // class property) - every assignment site below calls the matching `onXChanged` instead of
    // reassigning a local `var` directly; reads use the parameter as-is.
    selectedGroup: GroupKey? = null,
    onSelectedGroupChanged: (GroupKey?) -> Unit = {},
    focusedChannel: ChannelEntity? = null,
    onFocusedChannelChanged: (ChannelEntity?) -> Unit = {},
    // AGENTS.md backlog, corrected 2026-09-15 - "Auto-play last channel on launch." An explicit
    // one-shot signal from AppShell, not inferred from focusedChannel changing (which also fires
    // on a user's first ordinary browse-focus after a genuinely fresh launch, with nothing to
    // restore - inferring from that would wrongly auto-play whatever they merely focused). Only
    // ever set true once, by AppShell's own cold-launch restore effect; consumed back to false via
    // [onAutoPlayTriggerConsumed] so it can't refire later in the session.
    autoPlayTrigger: Boolean = false,
    onAutoPlayTriggerConsumed: () -> Unit = {},
    // AGENTS.md backlog, 2026-09-16: the cold-launch resume restore (above) sets `selectedGroup`/
    // `focusedChannel` as *data* and correctly pre-selects the right area, but nothing ever
    // claimed real D-pad focus onto that channel's row - the two existing focus-claim mechanisms
    // (ChannelsColumn's own `onFocusChanged` redirect, and this screen's own fullscreen-exit
    // effect below) are both purely reactive, triggered by focus *arriving* from somewhere else
    // or by *leaving* fullscreen - neither ever fires on a cold launch, where nothing has focus
    // yet at all. An explicit one-shot trigger, same shape as [autoPlayTrigger] and for the same
    // reason: inferring this from `focusedChannel` becoming non-null would also fire on every
    // ordinary focus change during normal browsing, repeatedly stealing focus back mid-session.
    claimInitialFocusTrigger: Boolean = false,
    onClaimInitialFocusTriggerConsumed: () -> Unit = {},
) {
    val groups by viewModel.repository.liveGroups().collectAsState(initial = emptyList())
    // PLAYER_ENGINEERING_BRIEF.md §6/§9 - resolved here, reactively, well before any fullscreen
    // entry is possible (the user has to browse/focus/press OK first), not inside PlayerScreen
    // itself - see PlayerScreen.kt's own doc comment for why the timing matters.
    val playlists by viewModel.repository.playlists().collectAsState(initial = emptyList())
    // PHASE_2.md decision 14's real table now, not the old in-memory stand-in - reactive, and
    // (unlike the stand-in) survives a real relaunch, not just switching destinations within one
    // session. No longer hoisted to AppShell: Room is already the single source of truth across
    // recompositions, so there's nothing left for AppShell to preserve on this screen's behalf.
    val recentChannels by viewModel.repository.recentChannels().collectAsState(initial = emptyList())
    var isFullscreen by remember { mutableStateOf(false) }
    var channelsHasFocus by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    fun recordRecent(channel: ChannelEntity) {
        scope.launch { viewModel.repository.recordRecentChannel(channel.playlistId, channel.streamId) }
    }

    LaunchedEffect(groups) {
        if ((selectedGroup == null || groups.none { it.key() == selectedGroup }) && groups.isNotEmpty()) {
            onSelectedGroupChanged(groups.first().key())
        }
    }

    LaunchedEffect(channelsHasFocus) {
        onChannelsFocusChanged(channelsHasFocus)
    }

    // Debounced: a D-pad flying down the list must not start a stream per row it passes over.
    // Only the channel the user rests on for 500ms actually loads.
    var previewUrl by remember { mutableStateOf<String?>(null) }

    // Auto-play last channel on launch, the trigger's actual effect - see the parameter doc
    // above for why this reacts to the dedicated trigger, not to focusedChannel itself. By the
    // time this fires, AppShell has already set focusedChannel in the same coroutine/recomposition
    // as the trigger, so it's available here already. Sets previewUrl directly rather than
    // waiting on the debounced effect below, same reason onChannelOpen/onChannelChanged already
    // do - this is a deliberate open, not a fly-by.
    LaunchedEffect(autoPlayTrigger) {
        if (autoPlayTrigger) {
            val channel = focusedChannel
            if (channel != null) {
                previewUrl = channel.streamId
                recordRecent(channel)
                isFullscreen = true
                onFullscreenChanged(true)
            }
            onAutoPlayTriggerConsumed()
        }
    }

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

    // Cold-launch resume: claim real focus (see [claimInitialFocusTrigger]'s own doc comment for
    // why the two existing mechanisms above never do this on their own). Reuses
    // [channelReturnFocus] - the same `FocusRequester` ChannelsColumn already attaches to
    // whichever row matches `focusedChannelId` - rather than introducing a second one. Longer
    // delay than the fullscreen-exit case above: that one only waits on this already-composed
    // screen's own recomposition; this one is racing the group's paged channel list loading for
    // the very first time and ChannelsColumn's own scroll-to-item effect bringing the target row
    // into TvLazyColumn's composed window before a `FocusRequester` attached to it has any node to
    // find. `runCatching` is still the real safety net if that hasn't happened yet - same as
    // every other focus-claim in this screen, never a silent app-level assumption that it worked.
    LaunchedEffect(claimInitialFocusTrigger) {
        if (claimInitialFocusTrigger) {
            if (focusedChannel != null) {
                // Retries, not one fixed delay: the target row only becomes focus-requestable
                // once the paged channel list has loaded far enough AND ChannelsColumn's own
                // scroll-to-item effect has run AND TvLazyColumn has actually recomposed to place
                // that row inside its composed window - a multi-step async chain with no single
                // signal this composable can observe directly. Measured live on the real device/
                // playlist (2026-09-17): Paging's initial load for a 123-channel group took
                // ~3.4s end to end before the row existed at all - a single fixed delay (400ms
                // was tried first) reliably failed with "FocusRequester is not initialized";
                // 300ms x 20 (6s ceiling) comfortably covers it without leaving the user staring
                // at nothing for long if it somehow never does.
                var claimed = false
                repeat(20) {
                    if (claimed) return@repeat
                    delay(300)
                    claimed = runCatching { channelReturnFocus.requestFocus() }.isSuccess
                }
            }
            onClaimInitialFocusTriggerConsumed()
        }
    }

    // Back retraces this screen's own path instead of flat-hopping straight to Home
    // (BACKLOG_SWEEP.md #8, user-verified against real TiviMate 2026-09-14): Channels -> back to
    // Categories first, Home only on the next Back from there. `focusManager.moveFocus(Left)`, not
    // a direct `requestFocus()` on GroupsColumn's internal selected-row requester - that requester
    // isn't exposed outside GroupsColumn, and SettingsScreen.kt's own doc comment already found
    // cross-branch `requestFocus()` calls unreliable; default `moveFocus` crossing into GroupsColumn
    // is what its own onFocusChanged entry-redirect (GroupsColumn.kt) is built to catch and correct
    // to the actually-selected group, so this reuses that instead of a second mechanism. Disabled
    // whenever fullscreen owns Back (PlayerScreen's own BackHandler) or focus isn't in Channels at
    // all (AppShell's Home-jump BackHandler takes over then, kept mutually exclusive the same way
    // via onChannelsFocusChanged).
    BackHandler(enabled = !isFullscreen && channelsHasFocus) {
        focusManager.moveFocus(FocusDirection.Left)
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
                        onSelectedGroupChanged(group)
                        onFocusedChannelChanged(null)
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
                    onChannelFocused = { onFocusedChannelChanged(it) },
                    returnFocusRequester = channelReturnFocus,
                    onFocusStateChanged = { channelsHasFocus = it },
                    onChannelOpen = { channel ->
                        onFocusedChannelChanged(channel)
                        // Bypass the 500ms browse-debounce above: opening is a deliberate action,
                        // not a D-pad fly-by, so PlayerHost must get the URL on this same frame.
                        // Without this, isFullscreen flips true immediately but previewUrl (what
                        // PlayerHost actually plays) only catches up after the debounce delay,
                        // which read to the user as a "duplicate 2-step" to get a channel playing.
                        previewUrl = channel.streamId
                        recordRecent(channel)
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
                    onFocusedChannelChanged(channel)
                    // Bypass the browse-debounce, same reason as onChannelOpen above: zapping is
                    // a deliberate action, not a D-pad fly-by, and waiting on it would reintroduce
                    // the "duplicate 2-step" bug that debounce-bypass already fixed once.
                    previewUrl = channel.streamId
                    recordRecent(channel)
                    // BACKLOG_SWEEP.md #7: a channel change originating from inside the player
                    // (ordinary zapping stays within the same group, so this is a no-op; picking a
                    // recent-channel tile from a *different* category doesn't) must also move
                    // selectedGroup, not just focusedChannel - otherwise Categories keeps
                    // whatever group was selected before fullscreen, and Back after a
                    // cross-category tile pick lands on the wrong category (GroupsColumn's own
                    // entry-redirect, see its doc comment, faithfully returns to that stale
                    // selection instead of the new channel's actual group).
                    val newGroup = GroupKey(channel.playlistId, channel.groupName)
                    if (newGroup != selectedGroup) onSelectedGroupChanged(newGroup)
                },
                breadcrumb = breadcrumb,
                recentChannels = recentChannels.filter { it.streamId != focusedChannel?.streamId },
                blackScreenBetweenZaps = blackScreenBetweenZaps,
                showRawResolution = showRawResolution,
                playlistUserAgent = remember(focusedChannel?.playlistId, playlists) {
                    playlists.firstOrNull { it.id == focusedChannel?.playlistId }?.userAgent
                },
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
