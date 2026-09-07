#!/bin/bash
set -e

mkdir -p tv-native/app/src/main/java/com/redsurf/tv/ui/theme
mkdir -p tv-native/app/src/main/res/values
mkdir -p tv-native/app/src/main/res/mipmap-anydpi-v26
mkdir -p tv-native/gradle/wrapper

# Root build.gradle.kts
cat << 'GRADLE_ROOT' > tv-native/build.gradle.kts
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
        classpath("com.google.gms:google-services:4.4.1")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
GRADLE_ROOT

# settings.gradle.kts
cat << 'SETTINGS' > tv-native/settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "RedSurf TV"
include(":app")
SETTINGS

# App build.gradle.kts
cat << 'APP_GRADLE' > tv-native/app/build.gradle.kts
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // id("com.google.gms.google-services") // Uncomment when google-services.json is added
}

android {
    namespace = "com.redsurf.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.redsurf.tv"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    // Jetpack Compose for TV
    implementation("androidx.tv:tv-foundation:1.0.0-alpha10")
    implementation("androidx.tv:tv-material:1.0.0-alpha10")
    
    // ExoPlayer Media3
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")

    // Firebase (BOM)
    implementation(platform("com.google.firebase:firebase-bom:32.7.1"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
}
APP_GRADLE

# AndroidManifest.xml
cat << 'MANIFEST' > tv-native/app/src/main/AndroidManifest.xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    
    <!-- Required for TV apps -->
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />
    <uses-feature android:name="android.software.leanback" android:required="true" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="RedSurf"
        android:supportsRtl="true"
        android:theme="@style/Theme.RedSurf">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.RedSurf">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
MANIFEST

# MainActivity.kt
cat << 'MAIN_ACTIVITY' > tv-native/app/src/main/java/com/redsurf/tv/MainActivity.kt
package com.redsurf.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import androidx.tv.material3.MaterialTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF09090B)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "RedSurf Native TV Edition",
                        color = Color.White,
                        style = MaterialTheme.typography.displayMedium
                    )
                }
            }
        }
    }
}
MAIN_ACTIVITY

# styles.xml
cat << 'STYLES' > tv-native/app/src/main/res/values/styles.xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.RedSurf" parent="android:Theme.NoTitleBar.Fullscreen">
        <item name="android:windowBackground">@android:color/black</item>
    </style>
</resources>
STYLES

# Basic ic_launcher (xml)
cat << 'ICON' > tv-native/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@android:color/black"/>
    <foreground android:drawable="@android:color/white"/>
</adaptive-icon>
ICON

chmod +x init_tv_native.sh
./init_tv_native.sh

