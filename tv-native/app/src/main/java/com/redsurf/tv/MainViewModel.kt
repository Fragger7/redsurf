package com.redsurf.tv

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redsurf.tv.db.RedSurfDatabase
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.db.PlaylistEntity
import com.redsurf.tv.parser.M3uParser
import com.redsurf.tv.vod.StalkerApi
import com.redsurf.tv.server.PairingServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.UUID

sealed class AppState {
    data class Loading(val message: String = "Loading...") : AppState()
    data class Onboarding(val localIp: String, val port: Int) : AppState()
    data class Loaded(
        val groups: List<com.redsurf.tv.data.ChannelGroup>, 
        val playlists: List<PlaylistEntity>,
        val activePlaylistId: String?
    ) : AppState()
    data class Error(val message: String) : AppState()
}

class MainViewModel : ViewModel() {
    private var localDb: RedSurfDatabase? = null
    
    private val _state = MutableStateFlow<AppState>(AppState.Loading("Initializing..."))
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var currentPlaylistId: String? = null
    private var pairingServer: PairingServer? = null

    fun setDatabase(db: RedSurfDatabase, context: Context) {
        localDb = db
        checkLocalCache(context)
    }

    private fun checkLocalCache(context: Context? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val playlists: List<PlaylistEntity> = localDb?.playlistDao()?.getAllPlaylists()?.first() ?: emptyList()
            if (playlists.isEmpty()) {
                withContext(Dispatchers.Main) {
                    startPairingServer(context)
                }
            } else {
                val pId = currentPlaylistId ?: playlists.first().id
                currentPlaylistId = pId
                val channels = localDb?.channelDao()?.getAllChannels()?.first()?.filter { it.playlistId == pId } ?: emptyList()
                val groups = channels.groupBy { it.groupName }.map { entry -> 
                    com.redsurf.tv.data.ChannelGroup(
                        name = entry.key,
                        channels = entry.value.map { 
                            com.redsurf.tv.data.Channel(
                                id = it.streamId, 
                                name = it.name, 
                                streamUrl = it.streamUrl, 
                                logoUrl = it.logoUrl, 
                                group = it.groupName, 
                                epgId = it.epgId
                            ) 
                        }
                    )
                }.sortedBy { it.name }
                withContext(Dispatchers.Main) {
                    _state.value = AppState.Loaded(groups, playlists, pId)
                }
            }
        }
    }

    private fun startPairingServer(context: Context?) {
        if (context == null) return
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        
        if (pairingServer == null) {
            try {
                pairingServer = PairingServer(8080) { method, server, user, pass, m3u, contentType ->
                    viewModelScope.launch {
                        if (method == "xtream") {
                            // If user is empty, assume Stalker
                            if (user.isEmpty()) {
                                loadStalkerPortal(server, "", "Stalker Portal", null, 0f, contentType)
                            } else {
                                loadXtreamCodes(server, user, pass, "Xtream Playlist", null, 0f, contentType)
                            }
                        } else if (method == "m3u") {
                            loadPlaylist(m3u, "M3U Playlist", m3u, "", "m3u", null, 0f, null, contentType)
                        }
                    }
                }
                pairingServer?.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        _state.value = AppState.Onboarding(if (ipAddress == "0.0.0.0") "WiFi not connected" else ipAddress, 8080)
    }

    fun switchPlaylist(playlistId: String) {
        currentPlaylistId = playlistId
        checkLocalCache()
    }

    override fun onCleared() {
        super.onCleared()
        pairingServer?.stop()
        pairingServer = null
    }

    fun loadXtreamCodes(server: String, user: String, pass: String, name: String = "Xtream Codes", userAgent: String? = null, offset: Float = 0f, contentType: String = "both") {
        val cleanServer = if (server.endsWith("/")) server.dropLast(1) else server
        var urlType = "m3u_plus"
        if (contentType == "live") {
            urlType = "m3u_plus&type=live"
        } else if (contentType == "vod") {
            urlType = "m3u_plus&type=vod" 
        }
        val url = "$cleanServer/get.php?username=$user&password=$pass&type=$urlType&output=ts"
        loadPlaylist(url, name, server, user, "xtream", userAgent, offset, null, contentType)
    }

    fun loadStalkerPortal(portalUrl: String, macAddress: String, name: String = "Stalker Portal", userAgent: String? = null, offset: Float = 0f, contentType: String = "both") {
        _state.value = AppState.Loading("Downloading playlist data...")
        viewModelScope.launch {
            try {
                val playlistId = UUID.randomUUID().toString()
                localDb?.playlistDao()?.insertPlaylist(
                    PlaylistEntity(
                        id = playlistId, name = name, serverUrl = portalUrl, 
                        username = "", type = "stalker", userAgent = userAgent, 
                        epgOffsetHours = offset, macAddress = macAddress, contentType = contentType
                    )
                )
                
                val isOnline = StalkerApi.getHandshake(portalUrl, macAddress, userAgent)
                if(isOnline) {
                    val entities = mutableListOf<ChannelEntity>()
                    if (contentType == "both" || contentType == "live") {
                        val categories = StalkerApi.getCategories(portalUrl, macAddress, "live", userAgent)
                        categories.forEachIndexed { i, cat ->
                            entities.add(ChannelEntity(
                                streamId = "stalker_live_${cat.id}", playlistId = playlistId, groupId = "default", num = i, 
                                name = cat.name + " (Live)", streamType = "live", streamIcon = "", epgChannelId = "", groupName = "Stalker Live"
                            ))
                        }
                    }
                    if (contentType == "both" || contentType == "vod") {
                        val categories = StalkerApi.getCategories(portalUrl, macAddress, "vod", userAgent)
                        categories.forEachIndexed { i, cat ->
                            entities.add(ChannelEntity(
                                streamId = "stalker_vod_${cat.id}", playlistId = playlistId, groupId = "default", num = i, 
                                name = cat.name + " (VOD)", streamType = "vod", streamIcon = "", epgChannelId = "", groupName = "Stalker VOD"
                            ))
                        }
                    }
                    if (entities.isNotEmpty()) {
                        localDb?.channelDao()?.insertChannels(entities)
                    }
                }
                
                currentPlaylistId = playlistId
                checkLocalCache()
            } catch (e: Exception) {
                _state.value = AppState.Error(e.message ?: "Failed to load stalker portal")
            }
        }
    }

    fun loadPlaylist(url: String, name: String, serverUrl: String, username: String, type: String, userAgent: String? = null, offset: Float = 0f, macAddress: String? = null, contentType: String = "both") {
        _state.value = AppState.Loading("Downloading playlist data...")
        viewModelScope.launch {
            try {
                val playlistId = UUID.randomUUID().toString()
                localDb?.playlistDao()?.insertPlaylist(
                    PlaylistEntity(
                        id = playlistId, name = name, serverUrl = serverUrl, username = username, 
                        type = type, userAgent = userAgent, epgOffsetHours = offset, macAddress = macAddress,
                        contentType = contentType
                    )
                )
                
                val channels = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    M3uParser.parse(URL(url).openStream())
                }
                
                val filteredChannels = channels.filter { 
                    if (contentType == "live") it.streamUrl.contains("/live/") || !it.streamUrl.contains("/movie/")
                    else if (contentType == "vod") it.streamUrl.contains("/movie/") || it.streamUrl.contains("/series/")
                    else true
                }

                val entities = filteredChannels.map {
                    ChannelEntity(
                        streamId = it.streamUrl, playlistId = playlistId, groupId = "default", num = 0, 
                        name = it.name, streamType = "live", streamIcon = it.logoUrl, epgChannelId = it.epgId, groupName = it.group
                    )
                }
                
                localDb?.channelDao()?.insertChannels(entities)
                currentPlaylistId = playlistId
                checkLocalCache()
            } catch (e: Exception) {
                _state.value = AppState.Error(e.message ?: "Failed to load playlist")
            }
        }
    }
}
