#!/bin/bash
set -e

# Opt-in to experimental material3-tv APIs globally to fix compilation errors
cat << 'KOTLIN' >> tv-native/app/build.gradle.kts

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.media3.common.util.UnstableApi"
        )
    }
}
KOTLIN

git add tv-native/app/build.gradle.kts
git commit -m "build: opt-in to experimental tv material3 apis to fix compiler errors"
git push origin main
