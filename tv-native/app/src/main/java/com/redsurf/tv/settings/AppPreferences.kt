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

    // Settings -> Remote control "Digit keys": number keys type a channel number in fullscreen
    // (TiviMate's default). Off = number keys do nothing.
    private val _digitKeysEnabled = MutableStateFlow(prefs.getBoolean(KEY_DIGIT_KEYS, true))
    val digitKeysEnabled: StateFlow<Boolean> = _digitKeysEnabled.asStateFlow()

    fun setDigitKeysEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DIGIT_KEYS, enabled).apply()
        _digitKeysEnabled.value = enabled
    }

    // EPG_WRAPUP.md 1.7 - TiviMate's "double-height selected row": the focused guide row grows to
    // 2x so the name gets two lines and the playing/catch-up icons get room. Default on.
    private val _doubleHeightRow = MutableStateFlow(prefs.getBoolean(KEY_DOUBLE_HEIGHT_ROW, true))
    val doubleHeightRow: StateFlow<Boolean> = _doubleHeightRow.asStateFlow()

    fun setDoubleHeightRow(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DOUBLE_HEIGHT_ROW, enabled).apply()
        _doubleHeightRow.value = enabled
    }

    // EPG_WRAPUP.md 2.1 - fill channels the provider has no guide for from public XMLTV
    // (EPGSHARE01). Default on (the user's ask: "sick of seeing empty guides"). Read by
    // EpgSyncWorker, so it's a plain preference, not only a flow.
    // Settings -> EPG "Refresh every": the periodic guide sync's interval (EpgSyncScheduler).
    // Read by the scheduler on every enqueue, so a change applies on the next re-enqueue.
    private val _epgRefresh = MutableStateFlow(
        EpgRefresh.entries.firstOrNull { it.name == prefs.getString(KEY_EPG_REFRESH, null) } ?: EpgRefresh.Daily,
    )
    val epgRefresh: StateFlow<EpgRefresh> = _epgRefresh.asStateFlow()

    fun cycleEpgRefresh() {
        val next = EpgRefresh.entries[(_epgRefresh.value.ordinal + 1) % EpgRefresh.entries.size]
        prefs.edit().putString(KEY_EPG_REFRESH, next.name).apply()
        _epgRefresh.value = next
    }

    private val _publicEpgEnabled = MutableStateFlow(prefs.getBoolean(KEY_PUBLIC_EPG, true))
    val publicEpgEnabled: StateFlow<Boolean> = _publicEpgEnabled.asStateFlow()

    fun setPublicEpgEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PUBLIC_EPG, enabled).apply()
        _publicEpgEnabled.value = enabled
    }

    // EPG_WRAPUP.md 2.3 - the nav strip slides away after this many idle seconds; 0 = never.
    // Cycles 3 → 5 → 10 → Off in Settings → Appearance. Default 5s (scoped 2026-09-21).
    private val _navAutoHideSeconds = MutableStateFlow(prefs.getInt(KEY_NAV_AUTO_HIDE, 5))
    val navAutoHideSeconds: StateFlow<Int> = _navAutoHideSeconds.asStateFlow()

    fun cycleNavAutoHide() {
        val next = when (_navAutoHideSeconds.value) { 3 -> 5; 5 -> 10; 10 -> 0; else -> 3 }
        prefs.edit().putInt(KEY_NAV_AUTO_HIDE, next).apply()
        _navAutoHideSeconds.value = next
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

    // Preview-on-OK (docs/plans/PREVIEW.md, built 2026-09-20) - default ON: this is the new
    // TiviMate-parity default behavior (first OK previews in the browse view, second OK on the
    // same channel fullscreens it). Off restores the old single-OK-jumps-straight-to-fullscreen
    // behavior exactly, for anyone who'd rather not have the extra decoder/step.
    // TIVIMATE_LEARNINGS.md F17: TiviMate's answer to a poor provider is a user-chosen startup
    // buffer. Read once when the player is built; a change rebuilds it (LiveTvScreen's key).
    private val _bufferSize = MutableStateFlow(
        BufferSize.entries.firstOrNull { it.name == prefs.getString(KEY_BUFFER_SIZE, null) } ?: BufferSize.Small,
    )
    val bufferSize: StateFlow<BufferSize> = _bufferSize.asStateFlow()

    // PLAYER_ENGINEERING_BRIEF.md §2.4 "Audio output: Auto / Force stereo": forcing decodes
    // every track to stereo PCM instead of HDMI passthrough - the fix for a receiver/TV that
    // goes silent on AC3/E-AC3. Read when the player is built (LiveTvScreen's key).
    private val _forceStereo = MutableStateFlow(prefs.getBoolean(KEY_FORCE_STEREO, false))
    val forceStereo: StateFlow<Boolean> = _forceStereo.asStateFlow()

    fun setForceStereo(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FORCE_STEREO, enabled).apply()
        _forceStereo.value = enabled
    }

    fun cycleBufferSize() {
        val next = BufferSize.entries[(_bufferSize.value.ordinal + 1) % BufferSize.entries.size]
        prefs.edit().putString(KEY_BUFFER_SIZE, next.name).apply()
        _bufferSize.value = next
    }

    // Settings -> Playback "Default aspect": what each fullscreen session starts on; the
    // player's Aspect tile still cycles within the session (PHASE_2.md decision 9).
    private val _defaultAspect = MutableStateFlow(
        DefaultAspect.entries.firstOrNull { it.name == prefs.getString(KEY_DEFAULT_ASPECT, null) } ?: DefaultAspect.Fit,
    )
    val defaultAspect: StateFlow<DefaultAspect> = _defaultAspect.asStateFlow()

    fun cycleDefaultAspect() {
        val next = DefaultAspect.entries[(_defaultAspect.value.ordinal + 1) % DefaultAspect.entries.size]
        prefs.edit().putString(KEY_DEFAULT_ASPECT, next.name).apply()
        _defaultAspect.value = next
    }

    private val _previewOnSelect = MutableStateFlow(prefs.getBoolean(KEY_PREVIEW_ON_SELECT, true))
    val previewOnSelect: StateFlow<Boolean> = _previewOnSelect.asStateFlow()

    fun setPreviewOnSelect(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREVIEW_ON_SELECT, enabled).apply()
        _previewOnSelect.value = enabled
    }

    // Teleport Menu (docs/plans/TELEPORT_MENU.md decision 1, built 2026-09-19) - default OFF:
    // long-press Back already has real, working, TiviMate-parity behavior (see AppShell.kt); this
    // opts a user into the fuller quick-jump menu instead of changing established behavior out
    // from under them without asking.
    private val _teleportMenuEnabled = MutableStateFlow(prefs.getBoolean(KEY_TELEPORT_MENU_ENABLED, false))
    val teleportMenuEnabled: StateFlow<Boolean> = _teleportMenuEnabled.asStateFlow()

    fun setTeleportMenuEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TELEPORT_MENU_ENABLED, enabled).apply()
        _teleportMenuEnabled.value = enabled
    }

    /** Nav feedback hints (2026-10-26): how many times each one-off tip has been shown, so a
     * tip teaches a few times and then stays out of the way. Keyed by the tip's own name. */
    fun hintCount(name: String): Int = prefs.getInt(KEY_HINT_PREFIX + name, 0)

    fun countHint(name: String) {
        prefs.edit().putInt(KEY_HINT_PREFIX + name, hintCount(name) + 1).apply()
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

    /** Settings -> Playback "Preferred audio" -> back to Auto (the system locale list). */
    fun clearPreferredAudioLanguage() {
        prefs.edit().remove(KEY_PREFERRED_AUDIO_LANGUAGE).apply()
        _preferredAudioLanguage.value = null
    }

    /** Settings -> Playback "Preferred subtitles": the language of the subtitle track last picked in
     * the player's Subtitles picker, re-applied on every channel. Null = subtitles off (the
     * default - §2.8, subtitles are opt-in); picking "Off" in the player clears it. */
    private val _preferredSubtitleLanguage = MutableStateFlow(prefs.getString(KEY_PREFERRED_SUBTITLE_LANGUAGE, null))
    val preferredSubtitleLanguage: StateFlow<String?> = _preferredSubtitleLanguage.asStateFlow()

    fun setPreferredSubtitleLanguage(language: String?) {
        prefs.edit().apply {
            if (language == null) remove(KEY_PREFERRED_SUBTITLE_LANGUAGE) else putString(KEY_PREFERRED_SUBTITLE_LANGUAGE, language)
        }.apply()
        _preferredSubtitleLanguage.value = language
    }

    // Settings -> General "Start on": where a cold launch lands. LastUsed records Home/Live TV only
    // (the browse destinations; nobody wants to boot into Settings). Default Live TV - the
    // player is the app. "Auto-play last channel on launch" still wins when on.
    private val _startOn = MutableStateFlow(
        StartOn.entries.firstOrNull { it.name == prefs.getString(KEY_START_ON, null) } ?: StartOn.LiveTv,
    )
    val startOn: StateFlow<StartOn> = _startOn.asStateFlow()

    fun cycleStartOn() {
        val next = StartOn.entries[(_startOn.value.ordinal + 1) % StartOn.entries.size]
        prefs.edit().putString(KEY_START_ON, next.name).apply()
        _startOn.value = next
    }

    // Settings -> EPG "Time offset" (TiviMate's per-playlist "EPG time shift", kept global here):
    // whole hours added to every programme's start/end when shown, for a provider whose XMLTV
    // carries the wrong zone. OK cycles 0, +1 .. +12, -12 .. -1, back to 0.
    private val _epgTimeOffsetHours = MutableStateFlow(prefs.getInt(KEY_EPG_TIME_OFFSET, 0))
    val epgTimeOffsetHours: StateFlow<Int> = _epgTimeOffsetHours.asStateFlow()

    fun cycleEpgTimeOffset() {
        val cur = _epgTimeOffsetHours.value
        val next = when {
            cur == 12 -> -12
            cur == -1 -> 0
            else -> cur + 1
        }
        prefs.edit().putInt(KEY_EPG_TIME_OFFSET, next).apply()
        _epgTimeOffsetHours.value = next
    }

    // Settings -> Parental "Hidden categories" (TiviMate's group long-press "Hide group"). Keys are
    // "playlistId\u0000groupName", so a hidden category stays hidden across a re-import.
    private val _hiddenGroups = MutableStateFlow(prefs.getStringSet(KEY_HIDDEN_GROUPS, emptySet())!!.toSet())
    val hiddenGroups: StateFlow<Set<String>> = _hiddenGroups.asStateFlow()

    fun setGroupHidden(key: String, hidden: Boolean) {
        val next = if (hidden) _hiddenGroups.value + key else _hiddenGroups.value - key
        prefs.edit().putStringSet(KEY_HIDDEN_GROUPS, next).apply()
        _hiddenGroups.value = next
    }

    // Settings -> Parental "PIN" (TiviMate's parental PIN code). Stored as a salted SHA-256, never
    // the digits; when set, unhiding a category asks for it.
    private val _parentalPinSet = MutableStateFlow(prefs.getString(KEY_PARENTAL_PIN, null) != null)
    val parentalPinSet: StateFlow<Boolean> = _parentalPinSet.asStateFlow()

    fun setParentalPin(pin: String?) {
        prefs.edit().apply {
            if (pin == null) remove(KEY_PARENTAL_PIN) else putString(KEY_PARENTAL_PIN, hashPin(pin))
        }.apply()
        _parentalPinSet.value = pin != null
    }

    fun checkParentalPin(pin: String): Boolean = prefs.getString(KEY_PARENTAL_PIN, null) == hashPin(pin)

    private fun hashPin(pin: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest("redsurf-pin:$pin".toByteArray())
            .joinToString("") { "%02x".format(it) }

    /** "Home" or "LiveTv" - the destination Start on = Last used resumes; null before any. */
    fun getLastDestination(): String? = prefs.getString(KEY_LAST_DESTINATION, null)

    fun setLastDestination(name: String) {
        if (prefs.getString(KEY_LAST_DESTINATION, null) != name) {
            prefs.edit().putString(KEY_LAST_DESTINATION, name).apply()
        }
    }

    // Settings -> About "Auto-update". Off stops the background checks (resume + periodic); the
    // manual "Check for updates" row still works. Default on.
    private val _autoUpdate = MutableStateFlow(prefs.getBoolean(KEY_AUTO_UPDATE, true))
    val autoUpdate: StateFlow<Boolean> = _autoUpdate.asStateFlow()

    fun setAutoUpdate(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_UPDATE, enabled).apply()
        _autoUpdate.value = enabled
    }

    // Settings -> Playback "Auto frame rate". Default off, as TiviMate ships it: each switch
    // blanks an HDMI screen for a second or two, which the user has to opt into.
    private val _afrEnabled = MutableStateFlow(prefs.getBoolean(KEY_AFR_ENABLED, false))
    val afrEnabled: StateFlow<Boolean> = _afrEnabled.asStateFlow()

    fun setAfrEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AFR_ENABLED, enabled).apply()
        _afrEnabled.value = enabled
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
        private const val KEY_DIGIT_KEYS = "digit_keys_enabled"
        private const val KEY_SHOW_RAW_RESOLUTION = "show_raw_resolution"
        private const val KEY_DOUBLE_HEIGHT_ROW = "double_height_focused_row"
        private const val KEY_PUBLIC_EPG = "public_epg_supplement"
        private const val KEY_NAV_AUTO_HIDE = "nav_auto_hide_seconds"
        private const val KEY_AUTO_PLAY_LAST_CHANNEL = "auto_play_last_channel_on_launch"
        private const val KEY_LAST_PLAYLIST_ID = "last_watched_playlist_id"
        private const val KEY_LAST_STREAM_ID = "last_watched_stream_id"
        private const val KEY_PREFERRED_AUDIO_LANGUAGE = "preferred_audio_language"
        private const val KEY_TELEPORT_MENU_ENABLED = "teleport_menu_enabled"
        private const val KEY_HINT_PREFIX = "hint_shown_"
        private const val KEY_PREVIEW_ON_SELECT = "preview_on_select"
        private const val KEY_BUFFER_SIZE = "buffer_size"
        private const val KEY_FORCE_STEREO = "force_stereo"
        private const val KEY_DEFAULT_ASPECT = "default_aspect"
        private const val KEY_EPG_REFRESH = "epg_refresh"
        private const val KEY_AFR_ENABLED = "afr_enabled"
        private const val KEY_START_ON = "start_on"
        private const val KEY_EPG_TIME_OFFSET = "epg_time_offset"
        private const val KEY_HIDDEN_GROUPS = "hidden_groups"
        private const val KEY_PARENTAL_PIN = "parental_pin"
        private const val KEY_LAST_DESTINATION = "last_destination"
        private const val KEY_AUTO_UPDATE = "auto_update"
        private const val KEY_PREFERRED_SUBTITLE_LANGUAGE = "preferred_subtitle_language"
    }
}

/**
 * TiviMate's five "Buffer size" steps (F17). Small is the buffer RedSurf always shipped, so the
 * default changes nothing. [startMs] is how much video must be downloaded before playback starts
 * (the channel-switch cost), [rebufferMs] the same after a stall; [maxMs] and [maxBytes] rise
 * together so the byte ceiling stays a real ceiling on a 1.89 GB device (HARDWARE.md).
 */
enum class BufferSize(
    val label: String,
    val minMs: Int,
    val maxMs: Int,
    val startMs: Int,
    val rebufferMs: Int,
    val maxBytes: Int,
) {
    None("None", 1_000, 8_000, 250, 1_000, 12 * 1024 * 1024),
    Small("Small", 2_500, 15_000, 500, 2_500, 20 * 1024 * 1024),
    Medium("Medium", 5_000, 25_000, 2_000, 4_000, 32 * 1024 * 1024),
    Large("Large", 10_000, 40_000, 4_000, 7_000, 48 * 1024 * 1024),
    VeryLarge("Very large", 15_000, 60_000, 8_000, 10_000, 64 * 1024 * 1024),
}

/** The player's three resize modes, in the same order its Aspect tile cycles them. */
enum class StartOn(val label: String) { LiveTv("Live TV"), Home("Home"), LastUsed("Last used") }

enum class DefaultAspect(val label: String, val resizeMode: Int) {
    Fit("Fit", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT),
    Fill("Fill", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL),
    Zoom("Zoom", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
}

/** How often each playlist's guide re-syncs. A 67MB feed on a 1.89 GB box: no shorter than 6h. */
enum class EpgRefresh(val label: String, val hours: Long) {
    Every6h("Every 6 hours", 6),
    Every12h("Every 12 hours", 12),
    Daily("Daily", 24),
    Every2Days("Every 2 days", 48),
}
