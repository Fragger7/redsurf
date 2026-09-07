package com.redsurf.tv.sync

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class CloudSyncManager(private val context: Context) {
    private val db = Firebase.firestore
    
    @SuppressLint("HardwareIds")
    private val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_tv"
    private val deviceName = "\${Build.MANUFACTURER} \${Build.MODEL}"

    // Push real-time playback state to Firebase (What is this TV currently watching?)
    fun reportPlaybackState(title: String, isPlaying: Boolean, type: String = "Live TV") {
        val deviceRef = db.collection("family_devices").document(deviceId)
        val data = hashMapOf(
            "deviceName" to deviceName,
            "currentTitle" to title,
            "isPlaying" to isPlaying,
            "type" to type,
            "lastActive" to System.currentTimeMillis()
        )
        
        deviceRef.set(data, SetOptions.merge())
            .addOnFailureListener { e -> Log.e("CloudSync", "Failed to report state", e) }
    }

    // Save VOD resume point for cross-room playback
    fun saveVodResumePoint(streamId: String, title: String, positionMs: Long) {
        if (positionMs < 10000) return // Don't save if watched less than 10 seconds
        
        val historyRef = db.collection("family_history").document(streamId)
        val data = hashMapOf(
            "title" to title,
            "resumePositionMs" to positionMs,
            "lastWatchedBy" to deviceName,
            "timestamp" to System.currentTimeMillis()
        )
        
        historyRef.set(data, SetOptions.merge())
    }

    suspend fun getVodResumePoint(streamId: String): Long {
        return try {
            val snapshot = db.collection("family_history").document(streamId).get().await()
            snapshot.getLong("resumePositionMs") ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
