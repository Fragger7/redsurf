package com.redsurf.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.redsurf.tv.data.Channel
import com.redsurf.tv.data.ChannelGroup
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.db.RedSurfDatabase
import com.redsurf.tv.parser.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.URL

sealed class AppState {
    object Loading : AppState()
    data class Onboarding(val pairingCode: String?) : AppState()
    data class Loaded(val groups: List<ChannelGroup>) : AppState()
    data class Error(val message: String) : AppState()
}

class MainViewModel : ViewModel() {
    private val firestoreDb = Firebase.firestore
    private var localDb: RedSurfDatabase? = null
    
    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state

    fun setDatabase(db: RedSurfDatabase) {
        this.localDb = db
        checkLocalCache()
    }

    private fun checkLocalCache() {
        viewModelScope.launch {
            val cachedChannels = localDb?.channelDao()?.getAllChannels()?.firstOrNull()
            if (!cachedChannels.isNullOrEmpty()) {
                val grouped = cachedChannels.map {
                    Channel(it.id, it.name, it.streamUrl, it.logoUrl, it.groupName, it.epgId)
                }.groupBy { it.group }.map { ChannelGroup(it.key, it.value) }
                
                _state.value = AppState.Loaded(grouped)
            } else {
                generatePairingCode()
            }
        }
    }

    private fun generatePairingCode() {
        val code = (100000..999999).random().toString()
        _state.value = AppState.Onboarding(code)

        val sessionRef = firestoreDb.collection("pairingSessions").document(code)
        
        viewModelScope.launch {
            try {
                sessionRef.set(mapOf("status" to "waiting", "createdAt" to System.currentTimeMillis())).await()

                sessionRef.addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    if (snapshot != null && snapshot.exists()) {
                        if (snapshot.getString("status") == "paired") {
                            val url = snapshot.getString("url")
                            if (!url.isNullOrEmpty()) loadPlaylist(url)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore silent firestore fails, they can still use native UI
            }
        }
    }

    fun loadXtreamCodes(server: String, user: String, pass: String) {
        val cleanServer = if (server.endsWith("/")) server.dropLast(1) else server
        val url = "\$cleanServer/get.php?username=\$user&password=\$pass&type=m3u_plus&output=ts"
        loadPlaylist(url)
    }

    fun loadPlaylist(url: String) {
        _state.value = AppState.Loading
        viewModelScope.launch {
            try {
                val channels = withContext(Dispatchers.IO) {
                    val inputStream = URL(url).openStream()
                    M3uParser.parse(inputStream)
                }

                // Cache in Room Database for instant next boot
                val entities = channels.map {
                    ChannelEntity(it.id, it.name, it.streamUrl, it.logoUrl, it.group, it.epgId)
                }
                localDb?.channelDao()?.clearAll()
                localDb?.channelDao()?.insertChannels(entities)

                val grouped = channels.groupBy { it.group }
                    .map { ChannelGroup(it.key, it.value) }
                    .sortedBy { it.name }

                _state.value = AppState.Loaded(grouped)
            } catch (e: Exception) {
                _state.value = AppState.Error(e.message ?: "Failed to load playlist")
            }
        }
    }
}
