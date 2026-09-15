import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"
}

// Release signing.
// Locally: ~/.redsurf/keys/signing.properties (created by scripts/setup-signing.sh, never committed).
// In CI:   env vars decoded from GitHub Actions secrets.
// If neither is present the release build simply stays unsigned rather than failing — so a fresh
// clone can still run `assembleDebug` without any keystore.
val signingProps = Properties().apply {
    val f = File(System.getProperty("user.home"), ".redsurf/keys/signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingOf(prop: String, env: String): String? =
    signingProps.getProperty(prop) ?: System.getenv(env)

val releaseStorePath: String? = signingOf("storeFile", "KEYSTORE_FILE")
val hasReleaseSigning: Boolean = releaseStorePath != null && File(releaseStorePath).exists()

android {
    namespace = "com.redsurf.tv"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.redsurf.tv"
        minSdk = 23
        targetSdk = 34
        versionCode = (project.findProperty("versionCode") as? String)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as? String) ?: "v1.0.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        buildConfig = true
        compose = true 
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = File(releaseStorePath!!)
                storePassword = signingOf("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signingOf("keyAlias", "KEY_ALIAS")
                keyPassword = signingOf("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // R8 stays off for now. Turn it on as its own isolated change, so a shrinking
            // bug is never mistaken for a feature bug.
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            // Debug builds share the release signing key (user decision, 2026-09-15 - no live
            // users, single family device, the usual reason to keep them apart doesn't apply
            // here) - AGP's default debug keystore and the release one can't coexist on one
            // device (different signatures), which is what forced every sprint to uninstall the
            // release build before installing a debug one, and uninstall-then-reinstall-release
            // at hand-off. With both signed the same, `adb install -r -d` works across debug and
            // release in either direction - sprints keep the user's real data/playlists intact
            // the whole time, no wipe required. Falls back to AGP's own debug keystore when no
            // signing.properties/env vars are present (e.g. a fresh clone with no keys yet) -
            // `debuggable` (set by the `debug` build type itself, unrelated to this) is what
            // `run-as`/DB inspection actually depend on, not the signing key.
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.tv:tv-foundation:1.0.0-alpha10")
    implementation("androidx.tv:tv-material:1.0.0-alpha10")
    implementation("androidx.compose.material3:material3:1.2.0")
    
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")

    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.room:room-paging:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Paging for per-group channel lists - never load a whole playlist into memory (PHASE_1.md #2).
    implementation("androidx.paging:paging-compose:3.2.1")

    // Channel logos, bounded to a 24 MB memory cache (PHASE_1.md #1.5). The phase's one new
    // non-paging dependency.
    implementation("io.coil-kt:coil-compose:2.6.0")

    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")

    // Local Web Server for Mobile Pairing
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    
    // QR Code Generation - pure-Java encoder (no Android deps in zxing:core itself), so the
    // BitMatrix -> Bitmap conversion below is done by hand rather than via zxing:android.
    implementation("com.google.zxing:core:3.5.3")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=androidx.media3.common.util.UnstableApi"
        )
    }
}
