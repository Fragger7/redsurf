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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
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
    
    // Settings States
    var useDoH by remember { mutableStateOf(viewModel.settingsManager.useSecureDns) }
    var tmdbKey by remember { mutableStateOf(viewModel.settingsManager.tmdbApiKey) }

    LaunchedEffect(groups) {
        if (groups.isNotEmpty() && !groups.contains(selectedGroup)) {
            selectedGroup = groups.first()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (focusedChannels.isNotEmpty()) {
            ExoPlayerView(
                streamUrl = focusedChannels[0].streamUrl,
                useSecureDns = useDoH,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isMenuOpen) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f))
            )
            
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).background(Color(0xFF1E1E1E).copy(alpha = 0.9f))
                ) {
                    TabItem("Live TV", currentMenuState == MenuState.GROUPS) { currentMenuState = MenuState.GROUPS }
                    TabItem("Search", currentMenuState == MenuState.SEARCH) { currentMenuState = MenuState.SEARCH }
                    TabItem("Settings", currentMenuState == MenuState.SETTINGS) { currentMenuState = MenuState.SETTINGS }
                }

                when (currentMenuState) {
                    MenuState.GROUPS, MenuState.CHANNELS -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            TvLazyColumn(modifier = Modifier.fillMaxHeight().width(250.dp).background(Color(0xFF1E1E1E))) {
                                items(groups) { group ->
                                    GroupItem(group, selectedGroup == group) { selectedGroup = group }
                                }
                            }
                            selectedGroup?.let { group ->
                                TvLazyColumn(modifier = Modifier.fillMaxHeight().weight(1f).padding(start = 16.dp)) {
                                    items(group.channels) { channel ->
                                        ChannelItem(channel, {}, { 
                                            focusedChannels.clear(); focusedChannels.add(channel); isMenuOpen = false 
                                        })
                                    }
                                }
                            }
                        }
                    }
                    MenuState.SEARCH -> {
                        // Omitted for brevity in this snippet, same as before
                    }
                    MenuState.SETTINGS -> {
                        Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
                            Text("Advanced Settings", style = MaterialTheme.typography.headlineMedium, color = Color.White, modifier = Modifier.padding(bottom = 32.dp))
                            
                            // DoH Toggle
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
                                Checkbox(checked = useDoH, onCheckedChange = { 
                                    useDoH = it
                                    viewModel.settingsManager.useSecureDns = it
                                    Toast.makeText(activity, "DoH: \${if(it) "Enabled" else "Disabled"}. Restart streams.", Toast.LENGTH_SHORT).show()
                                })
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Use Secure DNS (DoH)", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                    Text("Bypass ISP throttling/blocking by routing DNS over HTTPS (Cloudflare 1.1.1.1)", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                                }
                            }

                            // TMDB Input
                            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                                Text("TMDB API Key (Optional)", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                Text("Enter your own TMDB API key to unlock rich VOD/Series metadata & posters", color = Color.Gray, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 8.dp))
                                
                                androidx.compose.foundation.text.BasicTextField(
                                    value = tmdbKey,
                                    onValueChange = { 
                                        tmdbKey = it
                                        viewModel.settingsManager.tmdbApiKey = it
                                    },
                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                                    modifier = Modifier.fillMaxWidth(0.5f).background(Color.DarkGray).padding(16.dp)
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TabItem(title: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.padding(8.dp), colors = ClickableSurfaceDefaults.colors(containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)) {
        Text(title, modifier = Modifier.padding(16.dp, 8.dp), color = if (isSelected) Color.White else Color.Gray)
    }
}
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GroupItem(group: ChannelGroup, isSelected: Boolean, onFocus: () -> Unit) {
    Surface(onClick = {}, modifier = Modifier.fillMaxWidth().padding(8.dp).onFocusChanged { if (it.isFocused) onFocus() }, colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Text(group.name, modifier = Modifier.padding(16.dp), color = if (isSelected) Color.White else Color.Gray)
    }
}
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChannelItem(channel: Channel, onFocus: () -> Unit, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(4.dp, 16.dp).onFocusChanged { if (it.isFocused) onFocus() }, colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = MaterialTheme.colorScheme.inverseSurface)) {
        Text(channel.name, modifier = Modifier.padding(16.dp), color = Color.White)
    }
}
