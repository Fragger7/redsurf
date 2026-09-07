package com.redsurf.tv.settings

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("redsurf_settings", Context.MODE_PRIVATE)

    var useSecureDns: Boolean
        get() = prefs.getBoolean("use_secure_dns", false)
        set(value) = prefs.edit().putBoolean("use_secure_dns", value).apply()

    var tmdbApiKey: String
        get() = prefs.getString("tmdb_api_key", "") ?: ""
        set(value) = prefs.edit().putString("tmdb_api_key", value).apply()
}
