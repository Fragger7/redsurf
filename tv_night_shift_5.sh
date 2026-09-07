#!/bin/bash
set -e

BASE_DIR="tv-native/app/src/main/java/com/redsurf/tv"
mkdir -p "$BASE_DIR/player/tuning"

cat << 'KOTLIN' > "$BASE_DIR/player/tuning/AfrManager.kt"
package com.redsurf.tv.player.tuning

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Format
import androidx.media3.common.Player

/**
 * PRODUCTION AUTO FRAME RATE (AFR) ENGINE:
 * Dynamically switches the TV hardware refresh rate to match the video stream.
 * E.g., if a stream is 50fps, the TV panel switches to 50Hz to eliminate judder.
 */
class AfrManager(private val context: Context, private val player: ExoPlayer) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var originalMode: Display.Mode? = null
    
    var isEnabled = true

    init {
        player.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                super.onVideoSizeChanged(videoSize)
                if (!isEnabled) return
                
                // Get the frame rate from the format tracks
                val format = player.videoFormat
                if (format != null && format.frameRate > 0) {
                    val frameRate = format.frameRate
                    Log.d("AfrManager", "Detected stream frame rate: \$frameRate")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        switchRefreshRate(frameRate)
                    }
                }
            }
        })
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun switchRefreshRate(targetFps: Float) {
        val display = windowManager.defaultDisplay
        if (originalMode == null) {
            originalMode = display.mode
        }

        val supportedModes = display.supportedModes
        var bestMode: Display.Mode? = null
        var minDiff = Float.MAX_VALUE

        // Find the display mode that most closely matches the stream FPS
        // Multiplying by 1000 and rounding handles floating point inaccuracies (e.g. 59.94Hz)
        for (mode in supportedModes) {
            val diff = Math.abs(mode.refreshRate - targetFps)
            if (diff < minDiff && diff < 2.0f) { // Within 2Hz tolerance
                minDiff = diff
                bestMode = mode
            }
        }

        bestMode?.let { mode ->
            Log.d("AfrManager", "Switching TV panel to: \${mode.refreshRate}Hz")
            // In a real activity, this is applied to window.attributes.preferredDisplayModeId
            // We expose this so the MainActivity can observe and apply it.
            onModeFound?.invoke(mode.modeId)
        }
    }
    
    var onModeFound: ((Int) -> Unit)? = null
    
    fun restoreOriginalMode() {
        originalMode?.let {
            onModeFound?.invoke(it.modeId)
        }
    }
}
KOTLIN
