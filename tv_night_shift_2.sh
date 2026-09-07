#!/bin/bash
set -e

BASE_DIR="tv-native/app/src/main/java/com/redsurf/tv"
mkdir -p "$BASE_DIR/updater"
mkdir -p ".github/workflows"
mkdir -p "tv-native/app/src/main/res/xml"

# 1. Update build.gradle.kts to accept version overrides from GitHub Actions
cat << 'GRADLE' > tv-native/app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
}

android {
    namespace = "com.redsurf.tv"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.redsurf.tv"
        minSdk = 23
        targetSdk = 34
        
        // Inject from GitHub Actions, fallback to defaults
        versionCode = (project.findProperty("versionCode") as? String)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as? String) ?: "v1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        buildConfig = true // Needed to access VERSION_NAME in code
        compose = true 
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.tv:tv-foundation:1.0.0-alpha10")
    implementation("androidx.tv:tv-material:1.0.0-alpha10")
    implementation("androidx.compose.material3:material3:1.2.0")
    
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")

    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:\$room_version")
    implementation("androidx.room:room-ktx:\$room_version")
    ksp("androidx.room:room-compiler:\$room_version")
    
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation(platform("com.google.firebase:firebase-bom:32.7.1"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
}
GRADLE

# 2. FileProvider XML for OTA APK Installation
cat << 'XML' > tv-native/app/src/main/res/xml/file_paths.xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-path name="external_files" path="." />
    <external-files-path name="external_files_dir" path="." />
</paths>
XML

# 3. Update AndroidManifest to support OTA installs
cat << 'XML' > tv-native/app/src/main/AndroidManifest.xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.redsurf.tv">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    <uses-feature android:name="android.software.leanback" android:required="true" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="RedSurf"
        android:supportsRtl="true"
        android:theme="@style/Theme.RedSurf"
        android:banner="@mipmap/ic_launcher">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
XML

# 4. GitHub Release Engine (Semantic Versioning & APK Build)
cat << 'EOF_YAML' > .github/workflows/release.yml
name: Semantic Release & APK

on:
  push:
    branches: [ "master", "main" ]

permissions:
  contents: write

jobs:
  release:
    name: Build & Release RedSurf
    runs-on: ubuntu-latest
    steps:
      - name: Checkout Code
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Generate Semantic Version
        id: version
        uses: mathieudutour/github-tag-action@v6.1
        with:
          github_token: ${{ secrets.GITHUB_TOKEN }}
          default_bump: patch
          release_branches: "master,main"
          create_annotated_tag: true

      - name: Setup JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'gradle'

      - name: Build APK with Versioning
        run: |
          cd tv-native
          chmod +x gradlew
          ./gradlew assembleDebug -PversionName=${{ steps.version.outputs.new_tag }} -PversionCode=${{ github.run_number }}
          
      - name: Rename APK
        run: |
          mv tv-native/app/build/outputs/apk/debug/app-debug.apk tv-native/app/build/outputs/apk/debug/RedSurf-${{ steps.version.outputs.new_tag }}.apk

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v1
        with:
          tag_name: ${{ steps.version.outputs.new_tag }}
          name: RedSurf ${{ steps.version.outputs.new_tag }}
          generate_release_notes: true
          files: tv-native/app/build/outputs/apk/debug/RedSurf-${{ steps.version.outputs.new_tag }}.apk
EOF_YAML

rm -f .github/workflows/android-build.yml

# 5. The Android OTA Update Engine (Kotlin)
cat << 'KOTLIN' > "$BASE_DIR/updater/UpdateManager.kt"
package com.redsurf.tv.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
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
                
                if (latestTag.isNotEmpty() && latestTag != currentVersion) {
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
        
        val fileName = "RedSurf-\$versionName.apk"
        val request = DownloadManager.Request(uri)
            .setTitle("Downloading RedSurf Update")
            .setDescription("Version \$versionName")
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
            val uri = FileProvider.getUriForFile(context, "\${BuildConfig.APPLICATION_ID}.fileprovider", file)
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to launch package installer", e)
        }
    }
}
KOTLIN

# 6. Backlog documentation for remaining TiViMate Parity
cat << 'BACKLOG' > BACKLOG.md
# RedSurf TiViMate Parity Backlog

We have built the architectural skeleton for a world-class IPTV player. To achieve true 1:1 parity with TiViMate, the following deep features must be implemented next:

## 1. Network & Connectivity Optimization
- [ ] **Custom DNS (DoH)**: Bypass ISP domain blocking by integrating OkHttp DNS-over-HTTPS (e.g., Cloudflare 1.1.1.1, Google 8.8.8.8) natively.
- [ ] **Custom User-Agent per Playlist**: Allow users to spoof different hardware/player agents for different providers.

## 2. Advanced Hierarchy & Organization
- [ ] **Multi-Playlist Support**: Merge and manage multiple Xtream/M3U accounts simultaneously.
- [ ] **Group Management**: Ability to Hide, Rename, or Pin Channel Groups (e.g., hiding international categories).
- [ ] **Channel Management**: Ability to Hide or Rename specific channels within a group.
- [ ] **Global Favorites Engine**: Aggregate favorite channels across multiple playlists into a master "Favorites" tab.
- [ ] **VOD & Series Hierarchy**: Present VODs via strict Xtream Categories -> Movies/Series rather than a flat M3U list.

## 3. Deep Search
- [ ] **Global Search Matrix**: Unified search that queries Live Channels, VODs, Series, and EPG Program titles simultaneously.

## 4. Player Tuning & Experience
- [ ] **Auto Frame Rate (AFR)**: Query device capabilities and dynamically switch the TV's hardware refresh rate (e.g., 24Hz, 50Hz, 60Hz) to match the broadcast stream's FPS, eliminating judder.
- [ ] **EPG Time Offset**: Global and per-playlist sliders (+/- hours) for providers whose EPGs are misaligned with the video feed.
- [ ] **Data Backup & Restore**: Local and SMB-share export of the Room DB (Favorites, Hidden Groups, Settings).

## 5. Deployment & Updates
- [x] **Semantic Release CI/CD**: GitHub Actions auto-bumping versions and releasing `RedSurf-vX.Y.Z.apk`.
- [x] **OTA Update Engine**: Native Android TV package installer polling GitHub Releases to auto-update the app without Play Store intervention.
BACKLOG

chmod +x tv_night_shift_2.sh
