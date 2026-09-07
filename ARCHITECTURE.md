# RedSurf Architecture

## Overview
RedSurf is an Android TV exclusive IPTV player built to achieve strict feature parity with TiViMate, alongside a Next.js Companion App for seamless onboarding.
1. **tv-native/**: The core Android TV application.
2. **/**: The Next.js Companion App for mobile onboarding via Firestore pairing.

## The tv-native Core
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose for TV (`androidx.tv.material3`).
- **Video Engine**: Media3 ExoPlayer with hardware Audio/Subtitle track extraction (`TrackManager`), multi-view, and custom `HttpDataSource.Factory`.
- **Database**: Room (SQLite) for caching M3U playlists and XMLTV EPG data.
- **Parsing**: 
  - Memory-mapped XMLTV Parsing via `XmlPullParser`, batching inserts every 1000 rows to prevent OOM.
- **Background Sync**: `WorkManager` scheduled EPG data downloads.
- **VOD/Series**: Xtream Codes JSON API (`player_api.php`) integration.
- **OTA Updates**: Native `UpdateManager` polling GitHub Releases for seamless Android TV package installation.

## The Companion App
- **Framework**: Next.js (React)
- **Database**: Firebase Firestore (`pairingSessions`)
- **Flow**: User enters a 6-digit code on mobile -> inputs Xtream/M3U credentials -> Firestore SnapshotListener on TV immediately downloads the payload.

## Current Status
Core TiViMate parity foundation is complete. Advancing into expert-level parity (Custom DNS, Global Search, AFR).
