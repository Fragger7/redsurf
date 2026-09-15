package com.redsurf.tv.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Purpose-built persistence for the two toggles BACKLOG_SWEEP.md items #11/#12 flip live -
 * neither existing settings file in this package fits: [SettingsManager] is DNS/TMDB config,
 * [PlayerSettings] is non-reactive buffer/decoder tuning with manual `load()`/`save()` calls.
 * This one is `SharedPreferences`-backed but `StateFlow`-exposed, matching `MainViewModel`'s own
 * reactive pattern (state/focus discipline expects the UI to react to a change, not poll a `var`)
 * - and deliberately small, just these two booleans, not a general settings framework.
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

    companion object {
        private const val KEY_BLACK_SCREEN_BETWEEN_ZAPS = "black_screen_between_zaps"
        private const val KEY_SHOW_RAW_RESOLUTION = "show_raw_resolution"
    }
}
