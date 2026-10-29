plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.redsurf.tv.benchmark"
    compileSdk = 36
    defaultConfig {
        minSdk = 28 // macrobenchmark's floor
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    targetProjectPath = ":app"
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

baselineProfile {
    // The TV is a real device reached over adb; no managed emulator.
    useConnectedDevices = true
}

// The baselineprofile plugin wires the on-device run into `:benchmark:assemble`, and on 2026-10-29
// that run uninstalled the app from the family TV. Anything that talks to a device needs an explicit
// `-Predsurf.deviceRun=true` (e.g. `./gradlew :app:generateBaselineProfile -Predsurf.deviceRun=true`).
val deviceRunAllowed = providers.gradleProperty("redsurf.deviceRun").orNull == "true"
tasks.matching { it.name.startsWith("connected") || it.name.startsWith("collect") }.configureEach {
    onlyIf("needs -Predsurf.deviceRun=true") { deviceRunAllowed }
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
}
