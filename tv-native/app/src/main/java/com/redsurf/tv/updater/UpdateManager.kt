package com.redsurf.tv.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.app.PendingIntent
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
    
    private const val TAG = "UpdateManager"

    /** [dmUrl]: the release's dex-metadata asset (baseline profile), or null if it has none. */
    data class UpdateInfo(val hasUpdate: Boolean, val newVersion: String, val downloadUrl: String, val dmUrl: String? = null)

    /**
     * Picks the APK and optional .dm out of a release's (name, url) assets. The APK is the first
     * `.apk` by name, falling back to the first asset (the pre-.dm behaviour) so a release without
     * a recognisable name still updates. Pure, so it is unit-tested.
     */
    internal fun pickAssets(assets: List<Pair<String, String>>): Pair<String, String?>? {
        val apk = assets.firstOrNull { it.first.endsWith(".apk", ignoreCase = true) } ?: assets.firstOrNull() ?: return null
        val dm = assets.firstOrNull { it.first.endsWith(".dm", ignoreCase = true) }
        return apk.second to dm?.second
    }

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
                        val list = (0 until assets.length()).map {
                            val a = assets.getJSONObject(it)
                            a.optString("name") to a.optString("browser_download_url")
                        }
                        val (downloadUrl, dmUrl) = pickAssets(list) ?: return@withContext null
                        return@withContext UpdateInfo(true, latestTag, downloadUrl, dmUrl)
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to check for updates", e)
            null
        }
    }

    /** Returns the DownloadManager id, for [downloadProgress]. */
    fun downloadAndInstall(context: Context, downloadUrl: String, versionName: String, dmUrl: String? = null): Long {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(downloadUrl)
        
        val fileName = "RedSurf-$versionName.apk"
        val request = DownloadManager.Request(uri)
            .setTitle("Downloading RedSurf Update")
            .setDescription("Version $versionName")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)

        val downloadId = downloadManager.enqueue(request)

        // The profile rides alongside; the progress chip stays APK-only. Best effort - any
        // problem just means the plain install below.
        val dmName = "RedSurf-$versionName.dm"
        val dmId = if (dmUrl.isNullOrEmpty()) -1L else try {
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), dmName).delete()
            downloadManager.enqueue(
                DownloadManager.Request(Uri.parse(dmUrl))
                    .setTitle("RedSurf profile")
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, dmName)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            )
        } catch (e: Exception) {
            Log.w(TAG, "dm download not started", e)
            -1L
        }

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val dmFile = if (dmId >= 0 && isDownloadSuccessful(ctxt, dmId)) {
                        File(ctxt.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), dmName).takeIf { it.length() > 0 }
                    } else null
                    installApk(ctxt, fileName, dmFile)
                    ctxt.unregisterReceiver(this)
                }
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
        return downloadId
    }

    /** Fraction done (0..1, or -1 while the size is still unknown), or null once the download has
     * finished, failed or vanished - the backlog's "OTA download goes silent" item: the user saw
     * nothing between "Install now" and the system installer sheet. */
    fun downloadProgress(context: Context, downloadId: Long): Float? {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(downloadId))?.use { c ->
            if (!c.moveToFirst()) return null
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) return null
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            return if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else -1f
        }
        return null
    }

    private fun isDownloadSuccessful(context: Context, id: Long): Boolean {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            return c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
        }
        return false
    }

    /**
     * Install via a PackageInstaller session carrying the APK and its .dm (ART then compiles the
     * baseline profile at install time) when a .dm arrived; otherwise, and on ANY failure of the
     * session path, the exact ACTION_VIEW install this app has always used.
     */
    private fun installApk(context: Context, fileName: String, dmFile: File?) {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (dmFile == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.i(TAG, "install path=ACTION_VIEW (no dm or API < 28)")
            installViaIntent(context, file)
            return
        }
        Thread {
            try {
                installViaSession(context.applicationContext, file, dmFile)
            } catch (e: Exception) {
                Log.e(TAG, "install path=session failed to start, falling back to ACTION_VIEW", e)
                installViaIntent(context, file)
            }
        }.start()
    }

    private fun installViaSession(context: Context, apk: File, dm: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val sessionId = installer.createSession(params)
        Log.i(TAG, "install path=session id=$sessionId apk=${apk.length()}B dm=${dm.length()}B")
        val action = "${context.packageName}.INSTALL_STATUS.$sessionId"

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.i(TAG, "session $sessionId status=$status $msg")
                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        @Suppress("DEPRECATION")
                        val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        if (confirm != null) {
                            try {
                                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                ctx.startActivity(confirm)
                            } catch (e: Exception) {
                                Log.e(TAG, "could not launch install confirmation, falling back to ACTION_VIEW", e)
                                ctx.unregisterReceiver(this)
                                installViaIntent(ctx, apk)
                            }
                        }
                    }
                    PackageInstaller.STATUS_SUCCESS -> ctx.unregisterReceiver(this)
                    else -> {
                        ctx.unregisterReceiver(this)
                        Log.w(TAG, "session install failed (status=$status), falling back to ACTION_VIEW")
                        installViaIntent(ctx, apk)
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, IntentFilter(action))
        }

        try {
            installer.openSession(sessionId).use { session ->
                // A .dm is paired to its APK by base name: "base.apk" + "base.dm".
                for ((name, f) in listOf("base.apk" to apk, "base.dm" to dm)) {
                    f.inputStream().use { input ->
                        session.openWrite(name, 0, f.length()).use { out ->
                            input.copyTo(out)
                            session.fsync(out)
                        }
                    }
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val pi = PendingIntent.getBroadcast(context, sessionId, Intent(action).setPackage(context.packageName), flags)
                session.commit(pi.intentSender)
            }
        } catch (e: Exception) {
            runCatching { context.unregisterReceiver(receiver) }
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }

    private fun installViaIntent(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
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
