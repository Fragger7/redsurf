package com.redsurf.tv.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.redsurf.tv.BuildConfig
import com.redsurf.tv.network.IptvNetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * TiViMate Parity: Seamless OTA updates.
 * Checks the GitHub API for the latest release, downloads the APK silently, 
 * and prompts the Android TV Package Installer.
 */
object UpdateManager {
    
    private const val GITHUB_API_URL = "https://api.github.com/repos/Fragger7/redsurf/releases/latest"
    
    data class UpdateInfo(val hasUpdate: Boolean, val newVersion: String, val downloadUrl: String)

    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(GITHUB_API_URL).build()
            val response = IptvNetworkModule.getOkHttpClient().newCall(request).execute()
            
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val latestTag = json.optString("tag_name") // e.g. "v1.0.5"
                
                // Compare with current version
                val currentVersion = BuildConfig.VERSION_NAME

                if (latestTag.isNotEmpty() && isNewerVersion(latestTag, currentVersion)) {
                    val assets = json.optJSONArray("assets")
                    if (assets != null && assets.length() > 0) {
                        val downloadUrl = assets.getJSONObject(0).optString("browser_download_url")
                        return@withContext UpdateInfo(true, latestTag, downloadUrl)
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to check for updates", e)
            null
        }
    }

    fun downloadAndInstall(context: Context, downloadUrl: String, versionName: String) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(downloadUrl)
        
        val fileName = "RedSurf-$versionName.apk"
        val request = DownloadManager.Request(uri)
            .setTitle("Downloading RedSurf Update")
            .setDescription("Version $versionName")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(ctxt, fileName)
                    ctxt.unregisterReceiver(this)
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }

    private fun installApk(context: Context, fileName: String) {
        try {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to launch package installer", e)
        }
    }

    /**
     * Whether the OS will currently let this app trigger the package installer.
     * On API 26+, "install unknown apps" is a per-app toggle the user must grant
     * explicitly (this is the prompt observed live: "your TV currently isn't allowed
     * to install unknown apps from this source"). Below API 26 there is no such
     * per-app toggle - REQUEST_INSTALL_PACKAGES in the manifest is sufficient.
     */
    fun canInstallUnknownApps(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /** Sends the user to the system settings screen to grant install permission for this app. */
    fun requestInstallUnknownAppsPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /**
     * True only if [remote] is a strictly newer semantic version than [current].
     * Tags look like "v0.17.4"; a leading "v" is optional and missing trailing
     * components are treated as 0 (e.g. "v1.0" == "v1.0.0"). Any tag that doesn't
     * parse as dot-separated integers returns false rather than risking a false
     * positive - the version this replaces used plain string inequality, which
     * treated a locally-built "v1.0.0" as different from (and therefore "newer"
     * than) every published tag, downgrading the app on every single launch.
     */
    internal fun isNewerVersion(remote: String, current: String): Boolean {
        fun parse(tag: String): List<Int>? {
            val cleaned = tag.removePrefix("v").trim()
            if (cleaned.isEmpty()) return null
            return cleaned.split(".").map { it.toIntOrNull() ?: return null }
        }

        val remoteParts = parse(remote) ?: return false
        val currentParts = parse(current) ?: return false

        val length = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }
}
