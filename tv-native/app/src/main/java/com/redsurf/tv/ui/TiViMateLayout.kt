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
