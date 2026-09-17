package com.redsurf.tv.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Purpose-built persistence for the small reactive settings that don't fit [SettingsManager]
 * (DNS/TMDB config). `SharedPreferences`-backed but `StateFlow`-exposed, matching
 * `MainViewModel`'s own reactive pattern (state/focus discipline expects the UI to react to a
 * change, not poll a `var`) - deliberately small, not a general settings framework. (The dead,
 * unwired `PlayerSettings` this class doc used to be contrasted against was deleted
 * PLAYER_ENGINEERING_BRIEF.md §7, 2026-09-17 - its real buffer/decoder rows belong here or
 * directly in `PlayerController`, not a separate manual load()/save() file.)
 */
class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("redsurf_app_prefs", Context.MODE_PRIVATE)

    private val _blackScreenBetweenZaps =
        MutableStateFlow(prefs.getBoolean(KEY_BLACK_SCREEN_BETWEEN_ZAPS, false))
    val blackScreenBetweenZaps: StateFlow<Boolean> = _blackScreenBetweenZaps.asStateFlow()

    fun setBlackScreenBetweenZaps(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BLACK_SCREEN_BETWEEN_ZAPS, enabled).apply()
        _blackScreenBetweenZaps.value = enabled
    }

    private val _showRawResolution =
        MutableStateFlow(prefs.getBoolean(KEY_SHOW_RAW_RESOLUTION, false))
    val showRawResolution: StateFlow<Boolean> = _showRawResolution.asStateFlow()

    fun setShowRawResolution(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_RAW_RESOLUTION, enabled).apply()
        _showRawResolution.value = enabled
    }

    // Auto-play last channel on launch (AGENTS.md backlog, user decision 2026-09-15, corrected
    // same day from an earlier pass that had this backwards). Landing on Live TV with the
    // last-watched channel/category pre-selected is now the **unconditional default** - not
    // gated by this toggle at all, see AppShell.kt's restore effect. This toggle's only job is
    // whether that pre-selected channel should *also* start playing immediately on launch, on
    // top of the always-on pre-select. Just the toggle here - the actual last-watched identity is
    // setLastWatchedChannel/getLastWatchedChannel below, written independently of whether the
    // toggle is on so turning it on later doesn't start from nothing.
    private val _autoPlayLastChannelOnLaunch =
        MutableStateFlow(prefs.getBoolean(KEY_AUTO_PLAY_LAST_CHANNEL, false))
    val autoPlayLastChannelOnLaunch: StateFlow<Boolean> = _autoPlayLastChannelOnLaunch.asStateFlow()

    fun setAutoPlayLastChannelOnLaunch(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_PLAY_LAST_CHANNEL, enabled).apply()
        _autoPlayLastChannelOnLaunch.value = enabled
    }

    /** PLAYER_ENGINEERING_BRIEF.md §2.8 - the language of whatever audio track the user last
     * explicitly picked from the player's Audio picker, across every channel/playlist (a
     * `TrackSelectionOverride` is scoped to one track group's identity and doesn't survive a
     * channel change, so without persisting the *language* itself, "give me the English feed"
     * would need re-picking after every zap). Null until the user picks one for the first time -
     * [TrackManager] falls back to the system's own locale list until then. */
    private val _preferredAudioLanguage = MutableStateFlow(prefs.getString(KEY_PREFERRED_AUDIO_LANGUAGE, null))
    val preferredAudioLanguage: StateFlow<String?> = _preferredAudioLanguage.asStateFlow()

    fun setPreferredAudioLanguage(language: String) {
        prefs.edit().putString(KEY_PREFERRED_AUDIO_LANGUAGE, language).apply()
        _preferredAudioLanguage.value = language
    }

    /** Composite (playlistId, streamId) - matches ChannelEntity's own primary key
     * (BACKLOG_SWEEP.md #13) so a stale/removed channel just fails the lookup cleanly rather than
     * resolving to the wrong provider's channel. */
    fun setLastWatchedChannel(playlistId: String, streamId: String) {
        prefs.edit()
            .putString(KEY_LAST_PLAYLIST_ID, playlistId)
            .putString(KEY_LAST_STREAM_ID, streamId)
            .apply()
    }

    /** Null if nothing's been watched yet this install. The caller (AppShell) still has to look
     * this id pair up against the real channel table - it may no longer exist. */
    fun getLastWatchedChannel(): Pair<String, String>? {
        val playlistId = prefs.getString(KEY_LAST_PLAYLIST_ID, null) ?: return null
        val streamId = prefs.getString(KEY_LAST_STREAM_ID, null) ?: return null
        return playlistId to streamId
    }

    companion object {
        private const val KEY_BLACK_SCREEN_BETWEEN_ZAPS = "black_screen_between_zaps"
        private const val KEY_SHOW_RAW_RESOLUTION = "show_raw_resolution"
        private const val KEY_AUTO_PLAY_LAST_CHANNEL = "auto_play_last_channel_on_launch"
        private const val KEY_LAST_PLAYLIST_ID = "last_watched_playlist_id"
        private const val KEY_LAST_STREAM_ID = "last_watched_stream_id"
        private const val KEY_PREFERRED_AUDIO_LANGUAGE = "preferred_audio_language"
    }
}
