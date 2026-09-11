package com.redsurf.tv

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.redsurf.tv.data.ChannelRepository
import com.redsurf.tv.db.RedSurfDatabase
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.db.PlaylistEntity
import com.redsurf.tv.parser.M3uParser
import com.redsurf.tv.vod.StalkerApi
import com.redsurf.tv.vod.XtreamApi
import com.redsurf.tv.server.PairingServer
import com.redsurf.tv.updater.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.UUID

/**
 * Idle: nothing checked yet this session. Checking: a request is in flight - Settings shows this
 * so "Check for updates" doesn't look like it did nothing while the network call is out.
 * UpToDate: checked, current version is already the latest. Available: an update exists - this
 * is the only state MainActivity turns into the install-prompt dialog.
 */
sealed class UpdateCheckStatus {
    object Idle : UpdateCheckStatus()
    object Checking : UpdateCheckStatus()
    object UpToDate : UpdateCheckStatus()
    data class Available(val info: UpdateManager.UpdateInfo) : UpdateCheckStatus()
}

sealed class AppState {
    data class Loading(val message: String = "Loading...") : AppState()
    data class Onboarding(val localIp: String, val port: Int) : AppState()
    // No longer carries groups: the Live TV screen collects its own group/channel flows from
    // MainViewModel.repository (PHASE_1.md #1.2). Holding every channel here is exactly the
    // memory pattern this phase removes.
    data class Loaded(
        val playlists: List<PlaylistEntity>,
        val activePlaylistId: String?,
    ) : AppState()
    data class Error(val message: String) : AppState()
}

class MainViewModel : ViewModel() {
    private var localDb: RedSurfDatabase? = null

    /** Set in [setDatabase]. Read-only from outside; the Live TV screen collects from this. */
    lateinit var repository: ChannelRepository
        private set

    private val _state = MutableStateFlow<AppState>(AppState.Loading("Initializing..."))
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var currentPlaylistId: String? = null
    private var pairingServer: PairingServer? = null

    private val _updateStatus = MutableStateFlow<UpdateCheckStatus>(UpdateCheckStatus.Idle)
    /** Owned here, not in MainActivity's Composable, so every trigger - the resume-time
     * auto-check, the periodic background check, and Settings' manual "Check for updates" button
     * (all user request, 2026-09-11) - shares one source of truth and one in-flight request. */
    val updateStatus: StateFlow<UpdateCheckStatus> = _updateStatus.asStateFlow()

    private var lastUpdateCheckAtMillis = 0L

    init {
        // "Auto check every few hours if people aren't watching" (user request, 2026-09-11) -
        // covers a session left open for a long stretch without ever backgrounding, which the
        // resume-triggered check in MainActivity can't reach since no resume ever fires. Dies
        // with the ViewModel (viewModelScope), same as everything else here - by design, this
        // can't reach the app while its process isn't running; that case is covered by checking
        // on every resume instead (MainActivity), which force-close + relaunch always triggers.
        viewModelScope.launch {
            while (true) {
                delay(PERIODIC_UPDATE_CHECK_INTERVAL_MS)
                checkForUpdates()
            }
        }
    }

    /**
     * Safe to call from anywhere, anytime - a check already in flight is a no-op, not a stacked
     * request, and [force]-less calls (every trigger except the Settings button) are throttled to
     * at most once per [MIN_UPDATE_CHECK_INTERVAL_MS] so resuming the app repeatedly in quick
     * succession doesn't hammer the GitHub API. A fresh process (force-close, or first launch)
     * always checks regardless - [lastUpdateCheckAtMillis] resets to 0 with the process, which is
     * exactly the case the user needs to be reliable.
     */
    fun checkForUpdates(force: Boolean = false) {
        if (_updateStatus.value is UpdateCheckStatus.Checking) return
        val now = System.currentTimeMillis()
        if (!force && now - lastUpdateCheckAtMillis < MIN_UPDATE_CHECK_INTERVAL_MS) return
        lastUpdateCheckAtMillis = now
        viewModelScope.launch {
            _updateStatus.value = UpdateCheckStatus.Checking
            val info = UpdateManager.checkForUpdates()
            _updateStatus.value = if (info != null && info.hasUpdate) {
                UpdateCheckStatus.Available(info)
            } else {
                UpdateCheckStatus.UpToDate
            }
        }
    }

    fun dismissUpdatePrompt() {
        _updateStatus.value = UpdateCheckStatus.Idle
    }

    companion object {
        private const val MIN_UPDATE_CHECK_INTERVAL_MS = 15 * 60 * 1000L
        private const val PERIODIC_UPDATE_CHECK_INTERVAL_MS = 4 * 60 * 60 * 1000L
    }

    fun setDatabase(db: RedSurfDatabase, context: Context) {
        localDb = db
        repository = ChannelRepository(db.channelDao(), db.playlistDao())
        checkLocalCache(context)
    }

    /**
     * Decides Onboarding vs Loaded from playlist existence only - it never touches the channels
     * table. Counting/grouping channels in memory (the previous version's getAllChannels() +
     * groupBy) is exactly the O(playlist size) pattern PHASE_1.md #1.2 removes; the Live TV
     * screen queries its own groups/channels directly from [repository].
     */
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
                withContext(Dispatchers.Main) {
                    _state.value = AppState.Loaded(playlists, pId)
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

    /**
     * Testing-enablement, not full multi-playlist management (user request, 2026-09-11): wipes
     * every saved playlist and its channels, then re-checks the cache, which correctly finds
     * zero playlists and returns to Onboarding so a different M3U/Xtream/Stalker source can be
     * tried without reinstalling the app.
     */
    fun resetAndAddNewPlaylist() {
        viewModelScope.launch(Dispatchers.IO) {
            localDb?.channelDao()?.deleteAllChannels()
            localDb?.playlistDao()?.deleteAllPlaylists()
            currentPlaylistId = null
            withContext(Dispatchers.Main) { checkLocalCache() }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pairingServer?.stop()
        pairingServer = null
    }

    /**
     * Xtream providers go through player_api.php (get_live_categories + get_live_streams),
     * never the M3U (PHASE_1.md #2c) - the user's real provider's M3U is 327 MB / 1.23M entries;
     * the JSON API returns ~28K objects for the same live channels. Phase 1 only imports live
     * channels regardless of [contentType]; VOD/series aren't stored yet (see Non-goals).
     */
    fun loadXtreamCodes(server: String, user: String, pass: String, name: String = "Xtream Codes", userAgent: String? = null, offset: Float = 0f, contentType: String = "both") {
        val cleanServer = if (server.endsWith("/")) server.dropLast(1) else server
        _state.value = AppState.Loading("Connecting to $name...")
        viewModelScope.launch {
            try {
                val playlistId = UUID.randomUUID().toString()
                localDb?.playlistDao()?.insertPlaylist(
                    PlaylistEntity(
                        id = playlistId, name = name, serverUrl = cleanServer, username = user,
                        type = "xtream", userAgent = userAgent, epgOffsetHours = offset, contentType = contentType
                    )
                )

                var imported = 0
                XtreamApi.getLiveStreams(cleanServer, user, pass, userAgent) { batch ->
                    val entities = batch.map { s ->
                        ChannelEntity(
                            streamId = "$cleanServer/live/$user/$pass/${s.streamId}.ts",
                            playlistId = playlistId,
                            groupId = "default",
                            num = s.num,
                            name = s.name,
                            streamType = "live",
                            streamIcon = s.streamIcon,
                            epgChannelId = s.epgChannelId,
                            groupName = s.groupName,
                        )
                    }
                    localDb?.channelDao()?.insertChannels(entities)
                    imported += entities.size
                    _state.value = AppState.Loading("Importing $imported channels...")
                }

                currentPlaylistId = playlistId
                checkLocalCache()
            } catch (e: Exception) {
                _state.value = AppState.Error(e.message ?: "Failed to load Xtream playlist")
            }
        }
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
                                name = cat.name + " (VOD)", streamType = "live", streamIcon = "", epgChannelId = "", groupName = "Stalker VOD"
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

    /**
     * M3U-only providers. Streams and batches (PHASE_1.md #2b) rather than materializing the
     * whole playlist - the same 327 MB / 1.23M-entry provider that makes Xtream go through the
     * JSON API instead would OOM here otherwise. Only live-classified rows are stored; VOD/series
     * are counted and dropped (see Non-goals - VOD isn't stored until the VOD phase).
     */
    fun loadPlaylist(url: String, name: String, serverUrl: String, username: String, type: String, userAgent: String? = null, offset: Float = 0f, macAddress: String? = null, contentType: String = "both") {
        _state.value = AppState.Loading("Downloading playlist data...")
        viewModelScope.launch {
            try {
                val cleanUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "http://$url" else url
                val cleanServerUrl = if (!serverUrl.startsWith("http://") && !serverUrl.startsWith("https://")) "http://$serverUrl" else serverUrl
                val playlistId = UUID.randomUUID().toString()
                localDb?.playlistDao()?.insertPlaylist(
                    PlaylistEntity(
                        id = playlistId, name = name, serverUrl = cleanServerUrl, username = username,
                        type = type, userAgent = userAgent, epgOffsetHours = offset, macAddress = macAddress,
                        contentType = contentType
                    )
                )

                var liveIndex = 0
                var imported = 0
                var skipped = 0

                withContext(Dispatchers.IO) {
                    URL(cleanUrl).openStream().use { stream ->
                        M3uParser.parse(stream) { batch ->
                            val liveEntities = batch.mapNotNull { channel ->
                                if (channel.streamType != "live") {
                                    skipped++
                                    return@mapNotNull null
                                }
                                ChannelEntity(
                                    streamId = channel.streamUrl,
                                    playlistId = playlistId,
                                    groupId = "default",
                                    num = liveIndex++,
                                    name = channel.name,
                                    streamType = "live",
                                    streamIcon = channel.logoUrl,
                                    epgChannelId = channel.epgId,
                                    groupName = channel.group,
                                )
                            }
                            if (liveEntities.isNotEmpty()) {
                                localDb?.channelDao()?.insertChannels(liveEntities)
                                imported += liveEntities.size
                            }
                            _state.value = AppState.Loading("Importing $imported channels...")
                        }
                    }
                }

                currentPlaylistId = playlistId
                checkLocalCache()
            } catch (e: Exception) {
                _state.value = AppState.Error(e.message ?: "Failed to load playlist")
            }
        }
    }
}
