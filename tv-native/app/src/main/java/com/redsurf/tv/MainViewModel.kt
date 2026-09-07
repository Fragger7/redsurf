package com.redsurf.tv

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.UUID

sealed class AppState {
    object Loading : AppState()
    data class Onboarding(val pairingCode: String?) : AppState()
    data class Loaded(val groups: List<ChannelGroup>, val playlists: List<PlaylistEntity>, val activePlaylistId: String?) : AppState()
    data class Error(val message: String) : AppState()
}

class MainViewModel : ViewModel() {
    private val firestoreDb = Firebase.firestore
    private var localDb: RedSurfDatabase? = null
    
    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state
    
    private var currentPlaylistId: String? = null
    private var searchEngine: GlobalSearchEngine? = null
    private var backupManager: BackupManager? = null
    lateinit var settingsManager: SettingsManager

    private val _searchResults = MutableStateFlow<List<Channel>>(emptyList())
    val searchResults: StateFlow<List<Channel>> = _searchResults
    
    private var activePairingCode: String? = null
    private var pairingListener: ListenerRegistration? = null

    fun setDatabase(db: RedSurfDatabase, context: Context) {
        this.localDb = db
        this.searchEngine = GlobalSearchEngine(context)
        this.backupManager = BackupManager(context)
        this.settingsManager = SettingsManager(context)
        checkLocalCache()
    }

    fun backupData(): Boolean = backupManager?.backupDatabase() ?: false
    fun restoreData(): Boolean = backupManager?.restoreDatabase() ?: false

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

    fun performSearch(query: String) {
        viewModelScope.launch {
            val results = searchEngine?.search(query)?.liveChannels ?: emptyList()
            _searchResults.value = results.map {
                Channel(it.streamId, it.name, it.streamId, it.streamIcon ?: "", it.groupName, it.epgChannelId ?: "")
            }
        }
    }

    fun clearSearch() { _searchResults.value = emptyList() }

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
                            val url = snapshot.getString("url")
                            if (!url.isNullOrEmpty()) {
                                // IMPORTANT: Free Tier Architecture
                                // Disconnect realtime listener immediately once paired. 
                                // This prevents draining the 50k reads/day Firebase limit.
                                pairingListener?.remove()
                                pairingListener = null
                                
                                loadPlaylist(url, "Mobile Paired Playlist")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    fun loadXtreamCodes(server: String, user: String, pass: String, name: String = "Xtream Codes") {
        val cleanServer = if (server.endsWith("/")) server.dropLast(1) else server
        val url = "\$cleanServer/get.php?username=\$user&password=\$pass&type=m3u_plus&output=ts"
        loadPlaylist(url, name, server, user, "xtream")
    }

    fun loadPlaylist(url: String, name: String = "M3U Playlist", serverUrl: String = url, username: String = "", type: String = "m3u") {
        _state.value = AppState.Loading
        viewModelScope.launch {
            try {
                val playlistId = UUID.randomUUID().toString()
                
                val newPlaylist = PlaylistEntity(
                    id = playlistId,
                    name = name,
                    serverUrl = serverUrl,
                    username = username,
                    type = type
                )
                
                localDb?.playlistDao()?.insertPlaylist(newPlaylist)

                val channels = withContext(Dispatchers.IO) {
                    val inputStream = URL(url).openStream()
                    M3uParser.parse(inputStream)
                }

                var hiddenGroups = emptyList<String>()
                activePairingCode?.let { code ->
                    try {
                        val snap = firestoreDb.collection("pairingSessions").document(code).get().await()
                        hiddenGroups = snap.get("hiddenGroups") as? List<String> ?: emptyList()
                        
                        // Delete the document after successful extraction to keep DB size 0
                        firestoreDb.collection("pairingSessions").document(code).delete().await()
                    } catch (e: Exception) {}
                }

                val filteredChannels = channels.filter { it.group !in hiddenGroups }

                val entities = filteredChannels.map {
                    ChannelEntity(
                        streamId = it.streamUrl, 
                        playlistId = playlistId, 
                        groupId = "default", 
                        num = 0, 
                        name = it.name, 
                        streamType = "live", 
                        streamIcon = it.logoUrl, 
                        epgChannelId = it.epgId, 
                        groupName = it.group
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
