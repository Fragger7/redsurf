package com.redsurf.tv.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * TiViMate Parity: Deep player tuning.
 * Low-end FireSticks require massive buffer sizes for 4K streams.
 */
object PlayerSettings {
    
    private const val PREFS_NAME = "redsurf_player_prefs"
    
    // Hardware Decoder vs Software (libVLC fallback)
    var useSoftwareDecoder: Boolean = false
    
    // ExoPlayer Buffer sizes
    var minBufferMs: Int = 15000     // Wait 15 seconds before starting playback if network is poor
    var maxBufferMs: Int = 50000     // Keep up to 50 seconds in memory
    var bufferForPlaybackMs: Int = 2500 // Start playing after 2.5 seconds are buffered
    
    // Audio offset for out-of-sync provider feeds
    var audioDelayMs: Long = 0L

    // Global EPG Time Offset (Feature 3)
    var globalEpgOffsetHours: Float = 0f

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        useSoftwareDecoder = prefs.getBoolean("useSoftwareDecoder", false)
        minBufferMs = prefs.getInt("minBufferMs", 15000)
        maxBufferMs = prefs.getInt("maxBufferMs", 50000)
        audioDelayMs = prefs.getLong("audioDelayMs", 0L)
        globalEpgOffsetHours = prefs.getFloat("globalEpgOffsetHours", 0f)
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        prefs.putBoolean("useSoftwareDecoder", useSoftwareDecoder)
        prefs.putInt("minBufferMs", minBufferMs)
        prefs.putInt("maxBufferMs", maxBufferMs)
        prefs.putLong("audioDelayMs", audioDelayMs)
        prefs.putFloat("globalEpgOffsetHours", globalEpgOffsetHours)
        prefs.apply()
    }
}
