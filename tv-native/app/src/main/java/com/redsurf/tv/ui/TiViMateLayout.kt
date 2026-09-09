package com.redsurf.tv.ui

import android.app.Activity
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
    PLAYLISTS, GROUPS, CHANNELS, SEARCH
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
    var focusedChannel by remember { mutableStateOf<Channel?>(null) }
    var isMenuOpen by remember { mutableStateOf(true) }
    var currentMenuState by remember { mutableStateOf(MenuState.GROUPS) }
    
    val searchResults by viewModel.searchResults.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    // Auto-select first group when playlist changes
    LaunchedEffect(groups) {
        if (groups.isNotEmpty() && !groups.contains(selectedGroup)) {
            selectedGroup = groups.first()
        }
    }

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
                    .background(Color.Black.copy(alpha = 0.85f)) // Darker for clear UI navigation
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
                        title = "Global Search",
                        isSelected = currentMenuState == MenuState.SEARCH,
                        onClick = { currentMenuState = MenuState.SEARCH }
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
                                                focusedChannel = channel
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
                            
                            // Simple text entry for TV using BasicTextField or similar
                            // In a real TV app we'd use a custom on-screen keyboard, but this works with remote
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
                                            focusedChannel = channel
                                            isMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
