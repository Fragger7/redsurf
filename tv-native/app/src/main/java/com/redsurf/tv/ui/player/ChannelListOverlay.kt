package com.redsurf.tv.ui.player

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import com.redsurf.tv.data.ChannelRepository
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.ui.livetv.ChannelsColumn
import com.redsurf.tv.ui.livetv.GroupKey
import com.redsurf.tv.ui.livetv.GroupsColumn
import com.redsurf.tv.ui.livetv.formatGroupName
import com.redsurf.tv.ui.livetv.key
import com.redsurf.tv.ui.theme.Background
import kotlinx.coroutines.delay

private const val TAG = "ChannelListOverlay"

/**
 * PHASE_2.md decision 11 - LEFT's real overlay: Categories + Channels as *new* composable
 * instances (not the browse screen's own, which stays composed underneath, untouched, per
 * decision 18 - state preservation), seeded with the currently-playing channel's group/id so
 * focus lands there rather than the top of the list. A 62% left-side panel over a scrim; the
 * video stays visible on the right, matching TiviMate's own left-rail-over-video convention.
 *
 * Two live `Pager`s at once (this one's and the browse screen's underneath) is fine - Paging
 * holds pages, not whole playlists (decision 11's own note); #2.6 measures the actual memory
 * cost.
 */
@Composable
fun ChannelListOverlay(
    repository: ChannelRepository,
    currentChannel: ChannelEntity?,
    onChannelSelected: (ChannelEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (currentChannel == null) return
    val scope = rememberCoroutineScope()
    val groups by repository.liveGroups().collectAsState(initial = emptyList())

    var selectedGroup by remember {
        mutableStateOf(GroupKey(currentChannel.playlistId, currentChannel.groupName))
    }
    var focusedChannel by remember { mutableStateOf<ChannelEntity?>(currentChannel) }

    // Same debounce reasoning as LiveTvScreen's own queriedGroup (found live, 2026-09-12 - a fast
    // D-pad key-repeat through Categories must not rebuild the paged Channels list once per row
    // flown over).
    var queriedGroup by remember { mutableStateOf<GroupKey?>(selectedGroup) }
    LaunchedEffect(selectedGroup) {
        delay(200)
        queriedGroup = selectedGroup
    }

    val channelReturnFocus = remember { FocusRequester() }

    // Decision 11: "Focus lands on the current channel row" - not the category row `GroupsColumn`
    // would otherwise claim on its own (its own internal initial-focus effect). Same retry-loop
    // shape as LiveTvScreen's cold-launch focus fix and for the same reason: this overlay's own
    // fresh `ChannelsColumn`/Pager instance needs a beat to load before the row exists to focus -
    // `GroupsColumn`'s claim fires first (fast, in-memory), this one fires later and correctly
    // wins, landing on the channel as decision 11 asks for.
    LaunchedEffect(currentChannel.streamId) {
        repeat(20) { attempt ->
            delay(300)
            val result = runCatching { channelReturnFocus.requestFocus() }
            Log.d(TAG, "channelReturnFocus attempt=$attempt success=${result.isSuccess} " +
                "exception=${result.exceptionOrNull()?.javaClass?.simpleName}")
            if (result.isSuccess) return@LaunchedEffect
        }
        Log.d(TAG, "channelReturnFocus gave up after 20 attempts")
    }

    Box(
        // Single layer, lower alpha (user request, 2026-09-17: "consider displaying this menu
        // with transparency like TiviMate does this") - the original two stacked layers
        // (0.6 black + 0.85 Background) compounded to nearly opaque, hiding the still-playing
        // video almost entirely instead of showing it dimmed through the panel the way TiviMate's
        // own left rail does.
        modifier = modifier.background(Background.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.fillMaxHeight().fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            GroupsColumn(
                groups = groups,
                selectedGroup = selectedGroup,
                onGroupFocused = { group ->
                    if (group != selectedGroup) {
                        selectedGroup = group
                        focusedChannel = null
                    }
                },
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.45f),
            )

            val currentGroup = queriedGroup
            if (currentGroup != null) {
                // Seeds the Pager to start at the channel actually playing, not page 1 -
                // `ChannelDao.offsetInGroup`'s own doc comment has the full reasoning. Only
                // computed for the group this overlay opened on (the one `currentChannel` is
                // actually in); any other group the user navigates to within this overlay starts
                // from the top like the browse screen always has, since there's no "current
                // channel" to seed toward there. `null` while the offset query is in flight -
                // `ChannelsColumn` isn't rendered until it resolves, the same brief gap the Pager's
                // own first-page load would already cost.
                val isHomeGroup = currentGroup == GroupKey(currentChannel.playlistId, currentChannel.groupName)
                var seedOffset by remember(currentGroup) { mutableStateOf<Int?>(null) }
                LaunchedEffect(currentGroup) {
                    seedOffset = if (isHomeGroup) {
                        repository.channelOffsetInGroup(
                            currentChannel.playlistId,
                            currentChannel.groupName,
                            currentChannel.num,
                            currentChannel.name,
                        )
                    } else {
                        0
                    }
                    Log.d(TAG, "seedOffset resolved to $seedOffset for channel=${currentChannel.name} " +
                        "num=${currentChannel.num} isHomeGroup=$isHomeGroup")
                }
                val resolvedSeed = seedOffset
                if (resolvedSeed != null) {
                    val channelsFlow = remember(currentGroup, resolvedSeed) {
                        repository.liveChannels(currentGroup.playlistId, currentGroup.groupName, resolvedSeed)
                            .cachedIn(scope)
                    }
                    val pagedChannels = channelsFlow.collectAsLazyPagingItems()
                    val groupInfo = groups.firstOrNull { it.key() == currentGroup }
                    val groupTitle = groupInfo?.let { formatGroupName(it.groupName) } ?: ""

                    ChannelsColumn(
                        groupTitle = groupTitle,
                        groupCount = groupInfo?.count ?: 0,
                        channels = pagedChannels,
                        focusedChannelId = focusedChannel?.streamId,
                        onChannelFocused = { focusedChannel = it },
                        returnFocusRequester = channelReturnFocus,
                        onChannelOpen = onChannelSelected,
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(),
                    )
                }
            }
        }
    }
}
