buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.13.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.2.21")
        // Baseline-profile generation (PERFORMANCE_MAX.md item 4). Applied by :app and :benchmark.
        classpath("androidx.benchmark:benchmark-baseline-profile-gradle-plugin:1.4.1")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
