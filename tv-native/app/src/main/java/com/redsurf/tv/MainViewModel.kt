package com.redsurf.tv

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.redsurf.tv.backup.BackupManager
import com.redsurf.tv.data.Channel
import com.redsurf.tv.data.ChannelGroup
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.db.PlaylistEntity
import com.redsurf.tv.db.RedSurfDatabase
import com.redsurf.tv.parser.M3uParser
import com.redsurf.tv.search.GlobalSearchEngine
import com.redsurf.tv.settings.SettingsManager
import com.redsurf.tv.sync.CloudSyncManager
import com.redsurf.tv.vod.StalkerApi
import com.redsurf.tv.vod.XtreamApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.net.URL
import java.util.UUID

sealed class AppState {
    object Loading : AppState()
    data class Onboarding(val pairingCode: String?) : AppState()
    data class Loaded(val groups: List<ChannelGroup>, val playlists: List<PlaylistEntity>, val activePlaylistId: String?) : AppState()
    data class Error(val message: String) : AppState()
}

class MainViewModel : ViewModel() {
    private val firestoreDb = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), "ai-studio-streammateiptv-78859c44-ff72-4eb1-ad03-6166dc68ed30")
    private var localDb: RedSurfDatabase? = null
    
    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state
    
    private var currentPlaylistId: String? = null
    var searchEngine: GlobalSearchEngine? = null
    var backupManager: BackupManager? = null
    lateinit var settingsManager: SettingsManager
    lateinit var cloudSyncManager: CloudSyncManager
    
    private val _searchResults = MutableStateFlow<List<Channel>>(emptyList())
    val searchResults: StateFlow<List<Channel>> = _searchResults
    
    private var activePairingCode: String? = null
    private var pairingListener: ListenerRegistration? = null

    fun setDatabase(db: RedSurfDatabase, context: Context) {
        this.localDb = db
        this.searchEngine = GlobalSearchEngine(context)
        this.backupManager = BackupManager(context)
        this.settingsManager = SettingsManager(context)
        this.cloudSyncManager = CloudSyncManager(context)
        checkLocalCache()
    }

    private fun checkLocalCache() {
        viewModelScope.launch {
            val playlists = localDb?.playlistDao()?.getAllPlaylists()?.firstOrNull() ?: emptyList()
            if (playlists.isNotEmpty()) {
                val activeId = currentPlaylistId ?: playlists.first().id
                loadChannelsFromCache(activeId, playlists)
            } else {
                generatePairingCode()
            }
        }
    }
    
    fun switchPlaylist(playlistId: String) {
        currentPlaylistId = playlistId
        checkLocalCache()
    }

    private suspend fun loadChannelsFromCache(playlistId: String, allPlaylists: List<PlaylistEntity>) {
        val cachedChannels = localDb?.channelDao()?.getAllChannels()?.firstOrNull()?.filter { it.playlistId == playlistId }
        if (!cachedChannels.isNullOrEmpty()) {
            val grouped = cachedChannels.map {
                Channel(it.streamId, it.name, it.streamId, it.streamIcon ?: "", it.groupName, it.epgChannelId ?: "")
            }.groupBy { it.group }.map { ChannelGroup(it.key, it.value) }
            _state.value = AppState.Loaded(grouped, allPlaylists, playlistId)
        } else {
            _state.value = AppState.Loaded(emptyList(), allPlaylists, playlistId)
        }
    }

    private fun generatePairingCode() {
        val code = (100000..999999).random().toString()
        activePairingCode = code
        _state.value = AppState.Onboarding(code)
        val sessionRef = firestoreDb.collection("pairingSessions").document(code)
        
        viewModelScope.launch {
            try {
                sessionRef.set(mapOf("status" to "waiting", "createdAt" to System.currentTimeMillis())).await()
                pairingListener = sessionRef.addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    if (snapshot != null && snapshot.exists()) {
                        if (snapshot.getString("status") == "paired") {
                            val playlistType = snapshot.getString("playlistType") ?: "m3u"
                            val contentType = snapshot.getString("contentType") ?: "both"
                            
                            pairingListener?.remove()
                            pairingListener = null
                            
                            val name = snapshot.getString("name") ?: "New Playlist"
                            val server = snapshot.getString("server") ?: ""
                            val user = snapshot.getString("username") ?: ""
                            val pass = snapshot.getString("password") ?: ""
                            val userAgent = snapshot.getString("userAgent") // Feature 1: Get custom UA if present
                            val macAddress = snapshot.getString("macAddress") // Feature 5: Stalker support
                            val offset = snapshot.getDouble("epgOffsetHours")?.toFloat() ?: 0f

                            if (playlistType == "xtream") {
                                loadXtreamCodes(server, user, pass, name, code, userAgent, offset, contentType)
                            } else if (playlistType == "stalker") {
                                loadStalkerPortal(server, macAddress ?: "", name, code, userAgent, offset, contentType)
                            } else {
                                val url = snapshot.getString("url")
                                if (!url.isNullOrEmpty()) {
                                    loadPlaylist(url, name, url, "", "m3u", code, userAgent, offset, null, contentType)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadXtreamCodes(server: String, user: String, pass: String, name: String = "Xtream Codes", code: String? = null, userAgent: String? = null, offset: Float = 0f, contentType: String = "both") {
        val cleanServer = if (server.endsWith("/")) server.dropLast(1) else server
        
        // Use type=m3u_plus for live, or type=m3u_plus&output=ts depending on content type
        var urlType = "m3u_plus"
        if (contentType == "live") {
            urlType = "m3u_plus&type=live"
        } else if (contentType == "vod") {
            urlType = "m3u_plus&type=vod" 
            // In a real implementation we would fetch VOD natively using XtreamApi get_vod_categories
            // M3u parsing acts as a fallback.
        }

        val url = "$cleanServer/get.php?username=$user&password=$pass&type=$urlType&output=ts"
        loadPlaylist(url, name, server, user, "xtream", code, userAgent, offset, null, contentType)
    }

    // Feature 5: Stalker type IPTV portal load
    fun loadStalkerPortal(portalUrl: String, macAddress: String, name: String = "Stalker Portal", code: String? = null, userAgent: String? = null, offset: Float = 0f, contentType: String = "both") {
        _state.value = AppState.Loading
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

                // Handshake and get token/link
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
                
                code?.let { cleanupPairing(it) }
                currentPlaylistId = playlistId
                checkLocalCache()
            } catch (e: Exception) {
                _state.value = AppState.Error(e.message ?: "Failed to load stalker portal")
            }
        }
    }

    fun loadPlaylist(url: String, name: String, serverUrl: String, username: String, type: String, code: String? = null, userAgent: String? = null, offset: Float = 0f, macAddress: String? = null, contentType: String = "both") {
        _state.value = AppState.Loading
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
                
                var hiddenGroups = emptyList<String>()
                code?.let { hiddenGroups = cleanupPairing(it) }
                
                // Filter by content type logic for M3U
                val filteredChannels = channels.filter { it.group !in hiddenGroups }.filter { 
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

    private suspend fun cleanupPairing(code: String): List<String> {
        return try {
            val snap = firestoreDb.collection("pairingSessions").document(code).get().await()
            val hidden = snap.get("hiddenGroups") as? List<String> ?: emptyList()
            firestoreDb.collection("pairingSessions").document(code).delete().await()
            hidden
        } catch (e: Exception) {
            emptyList()
        }
    }
}
