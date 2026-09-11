package com.redsurf.tv.ui.livetv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.redsurf.tv.MainViewModel
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary

/**
 * The screen this whole phase is built to prove (docs/plans/PHASE_1.md #1.4). Three columns
 * matching references/streamvault/LiveTV.png: categories, the selected group's channels, and a
 * preview of whichever channel currently has D-pad focus.
 *
 * Real playback is #1.5 - the preview column here is a static info stub (name + "No schedule
 * information" + the "press OK again" hint) until PlayerHost exists.
 */
@Composable
fun LiveTvScreen(viewModel: MainViewModel, playlistId: String) {
    val groups by viewModel.repository.liveGroups(playlistId).collectAsState(initial = emptyList())
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var focusedChannel by remember { mutableStateOf<ChannelEntity?>(null) }

    // Default to the first group once groups load; never override the user's own selection.
    LaunchedEffect(groups) {
        if (selectedGroup == null && groups.isNotEmpty()) {
            selectedGroup = groups.first().groupName
        }
    }

    Row(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
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
                onChannelOpen = { /* fullscreen playback - PHASE_1.md #1.5 */ },
                modifier = Modifier.weight(1.4f),
            )
        }

        PreviewStub(channel = focusedChannel, modifier = Modifier.weight(1.2f))
    }
}

@Composable
private fun PreviewStub(channel: ChannelEntity?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(start = 16.dp)) {
        Text("Channel Preview", style = MaterialTheme.typography.titleMedium, color = TextSecondary)
        if (channel != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(channel.name, style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            Text("No schedule information", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Press OK again to open this channel", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
    }
}
