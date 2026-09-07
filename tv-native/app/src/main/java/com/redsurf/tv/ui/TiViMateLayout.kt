package com.redsurf.tv.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.*
import com.redsurf.tv.MainViewModel
import com.redsurf.tv.data.Channel
import com.redsurf.tv.data.ChannelGroup
import com.redsurf.tv.db.PlaylistEntity
import com.redsurf.tv.player.ExoPlayerView

enum class MenuState {
    PLAYLISTS, GROUPS, CHANNELS, SEARCH, SETTINGS
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TiViMateLayout(
    groups: List<ChannelGroup>,
    playlists: List<PlaylistEntity>,
    activePlaylistId: String?,
    viewModel: MainViewModel,
    activity: Activity
) {
    var selectedGroup by remember { mutableStateOf(groups.firstOrNull()) }
    val focusedChannels = remember { mutableStateListOf<Channel>() }
    var isMenuOpen by remember { mutableStateOf(true) }
    var currentMenuState by remember { mutableStateOf(MenuState.GROUPS) }
    
    val searchResults by viewModel.searchResults.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    LaunchedEffect(groups) {
        if (groups.isNotEmpty() && !groups.contains(selectedGroup)) {
            selectedGroup = groups.first()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // Multi-View Video Player Grid
        if (focusedChannels.isNotEmpty()) {
            if (focusedChannels.size == 1) {
                ExoPlayerView(
                    streamUrl = focusedChannels[0].streamUrl,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Multi-View Grid support
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        ExoPlayerView(streamUrl = focusedChannels[0].streamUrl, modifier = Modifier.weight(1f).fillMaxHeight())
                        if (focusedChannels.size > 1) {
                            ExoPlayerView(streamUrl = focusedChannels[1].streamUrl, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    if (focusedChannels.size > 2) {
                        Row(modifier = Modifier.weight(1f)) {
                            ExoPlayerView(streamUrl = focusedChannels[2].streamUrl, modifier = Modifier.weight(1f).fillMaxHeight())
                            if (focusedChannels.size > 3) {
                                ExoPlayerView(streamUrl = focusedChannels[3].streamUrl, modifier = Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            }
        }

        // Overlay & Menu
        if (isMenuOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
            )
            
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Bar: Navigation
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .background(Color(0xFF1E1E1E).copy(alpha = 0.9f))
                ) {
                    TabItem(
                        title = "Playlists",
                        isSelected = currentMenuState == MenuState.PLAYLISTS,
                        onClick = { currentMenuState = MenuState.PLAYLISTS }
                    )
                    TabItem(
                        title = "Live TV",
                        isSelected = currentMenuState == MenuState.GROUPS || currentMenuState == MenuState.CHANNELS,
                        onClick = { currentMenuState = MenuState.GROUPS }
                    )
                    TabItem(
                        title = "Search",
                        isSelected = currentMenuState == MenuState.SEARCH,
                        onClick = { currentMenuState = MenuState.SEARCH }
                    )
                    TabItem(
                        title = "Settings",
                        isSelected = currentMenuState == MenuState.SETTINGS,
                        onClick = { currentMenuState = MenuState.SETTINGS }
                    )
                }

                when (currentMenuState) {
                    MenuState.PLAYLISTS -> {
                        TvLazyColumn(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            items(playlists) { playlist ->
                                PlaylistItem(
                                    playlist = playlist,
                                    isActive = playlist.id == activePlaylistId,
                                    onClick = {
                                        viewModel.switchPlaylist(playlist.id)
                                        currentMenuState = MenuState.GROUPS
                                    }
                                )
                            }
                        }
                    }
                    MenuState.GROUPS, MenuState.CHANNELS -> {
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
                                            onFocus = { },
                                            onClick = { 
                                                focusedChannels.clear()
                                                focusedChannels.add(channel)
                                                isMenuOpen = false 
                                            },
                                            onLongClick = {
                                                if (focusedChannels.size < 4 && !focusedChannels.contains(channel)) {
                                                    focusedChannels.add(channel)
                                                }
                                                isMenuOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    MenuState.SEARCH -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp)
                        ) {
                            Text(
                                "Global Search",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { 
                                    searchQuery = it
                                    if (it.length >= 2) viewModel.performSearch(it)
                                    else viewModel.clearSearch()
                                },
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = androidx.compose.ui.unit.sp.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp)),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = { viewModel.performSearch(searchQuery) }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.DarkGray)
                                    .padding(16.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(32.dp))
                            
                            TvLazyColumn(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(searchResults) { channel ->
                                    ChannelItem(
                                        channel = channel,
                                        onFocus = { },
                                        onClick = {
                                            focusedChannels.clear()
                                            focusedChannels.add(channel)
                                            isMenuOpen = false
                                        },
                                        onLongClick = {
                                            if (focusedChannels.size < 4 && !focusedChannels.contains(channel)) {
                                                focusedChannels.add(channel)
                                            }
                                            isMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    MenuState.SETTINGS -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp)
                        ) {
                            Text(
                                "Settings & Backup",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 32.dp)
                            )
                            
                            SettingsActionItem(
                                title = "Backup Database (Export to Local Storage)",
                                description = "Save playlists, favorites, and settings to local storage.",
                                onClick = {
                                    val success = viewModel.backupData()
                                    Toast.makeText(activity, if (success) "Backup Successful!" else "Backup Failed", Toast.LENGTH_SHORT).show()
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            SettingsActionItem(
                                title = "Restore Database (Import from Local Storage)",
                                description = "Restore your previous configuration. Requires app restart.",
                                onClick = {
                                    val success = viewModel.restoreData()
                                    Toast.makeText(activity, if (success) "Restore Successful! Restart App." else "No Backup Found", Toast.LENGTH_LONG).show()
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(32.dp))
                            
                            Text(
                                "Tip: Long-press a channel in the Live TV list to add it to Multi-View PiP Mode (Up to 4 streams).",
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodyLarge
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
fun SettingsActionItem(title: String, description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF222222),
            focusedContainerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(text = description, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TabItem(title: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.padding(8.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            color = if (isSelected) Color.White else Color.Gray
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlaylistItem(playlist: PlaylistEntity, isActive: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isActive) Color(0xFF333333) else Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface
        )
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "\${playlist.name} (\${playlist.type})",
                style = MaterialTheme.typography.titleMedium,
                color = if (isActive) Color.White else Color.LightGray
            )
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
fun ChannelItem(channel: Channel, onFocus: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit = {}) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
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
