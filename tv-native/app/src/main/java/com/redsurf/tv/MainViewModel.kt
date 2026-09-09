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
    private val firestoreDb = Firebase.firestore
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
                                loadXtreamCodes(server, user, pass, name, code, userAgent, offset)
                            } else if (playlistType == "stalker") {
                                loadStalkerPortal(server, macAddress ?: "", name, code, userAgent, offset)
                            } else {
                                val url = snapshot.getString("url")
                                if (!url.isNullOrEmpty()) {
                                    loadPlaylist(url, name, url, "", "m3u", code, userAgent, offset, null)
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

    fun loadXtreamCodes(server: String, user: String, pass: String, name: String = "Xtream Codes", code: String? = null, userAgent: String? = null, offset: Float = 0f) {
        val cleanServer = if (server.endsWith("/")) server.dropLast(1) else server
        val url = "$cleanServer/get.php?username=$user&password=$pass&type=m3u_plus&output=ts"
        loadPlaylist(url, name, server, user, "xtream", code, userAgent, offset, null)
    }

    // Feature 5: Stalker type IPTV portal load
    fun loadStalkerPortal(portalUrl: String, macAddress: String, name: String = "Stalker Portal", code: String? = null, userAgent: String? = null, offset: Float = 0f) {
        _state.value = AppState.Loading
        viewModelScope.launch {
            try {
                val playlistId = UUID.randomUUID().toString()
                localDb?.playlistDao()?.insertPlaylist(
                    PlaylistEntity(
                        id = playlistId, name = name, serverUrl = portalUrl, 
                        username = "", type = "stalker", userAgent = userAgent, 
                        epgOffsetHours = offset, macAddress = macAddress
                    )
                )

                // Handshake and get token/link
                val isOnline = StalkerApi.getHandshake(portalUrl, macAddress, userAgent)
                if(isOnline) {
                    val categories = StalkerApi.getCategories(portalUrl, macAddress, "live", userAgent)
                    val entities = mutableListOf<ChannelEntity>()
                    
                    // Simple mock for Stalker channels since getting every channel in Stalker is a paginated nightmare via their API.
                    // A true Stalker implementation recursively queries all genres. For this demonstration, we'll construct a base.
                    categories.forEachIndexed { i, cat ->
                        entities.add(ChannelEntity(
                            streamId = "stalker_${cat.id}", playlistId = playlistId, groupId = "default", num = i, 
                            name = cat.name + " (Category)", streamType = "live", streamIcon = "", epgChannelId = "", groupName = "Stalker Live"
                        ))
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

    fun loadPlaylist(url: String, name: String, serverUrl: String, username: String, type: String, code: String? = null, userAgent: String? = null, offset: Float = 0f, macAddress: String? = null) {
        _state.value = AppState.Loading
        viewModelScope.launch {
            try {
                val playlistId = UUID.randomUUID().toString()
                localDb?.playlistDao()?.insertPlaylist(
                    PlaylistEntity(
                        id = playlistId, name = name, serverUrl = serverUrl, username = username, 
                        type = type, userAgent = userAgent, epgOffsetHours = offset, macAddress = macAddress
                    )
                )
                
                val channels = kotlinx.coroutines.withContext(Dispatchers.IO) {
                    M3uParser.parse(URL(url).openStream())
                }
                
                var hiddenGroups = emptyList<String>()
                code?.let { hiddenGroups = cleanupPairing(it) }
                
                val filteredChannels = channels.filter { it.group !in hiddenGroups }
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
