# RedSurf Architecture

## Overview
RedSurf is an Android TV exclusive IPTV player built to achieve strict feature parity with TiViMate, alongside a Next.js Companion App for seamless onboarding.
1. **tv-native/**: The core Android TV application.
2. **/**: The Next.js Companion App for mobile onboarding via Firestore pairing.

## The tv-native Core
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose for TV (`androidx.tv.material3`).
- **Video Engine**: Media3 ExoPlayer with hardware Audio/Subtitle track extraction (`TrackManager`), multi-view, and custom `HttpDataSource.Factory`.
- **Network**: DNS-over-HTTPS (DoH) via OkHttp to bypass ISP blocking (`IptvNetworkModule`).
- **Database**: Room (SQLite) for caching M3U playlists and XMLTV EPG data.
- **Hierarchy Engine**: Deep schema separating Playlists, Channel Groups, and Channels, supporting "Hide", "Favorite", and "Multi-Playlist Merging".
- **Parsing**: Memory-mapped XMLTV Parsing via `XmlPullParser`, batching inserts every 1000 rows.
- **Player Tuning**: Auto Frame Rate (AFR) matching to perfectly sync TV panel Hz with stream FPS (`AfrManager`).
- **Global Search**: Matrix search across live TV and VOD (`GlobalSearchEngine`).
- **Data Backup**: Room SQLite file export to USB storage (`BackupManager`).
- **OTA Updates**: Native `UpdateManager` polling GitHub Releases for seamless Android TV package installation.
- **CI/CD Regression**: JUnit suite executing against deep logic layers before Semantic Release compilation.

## The Companion App
- **Framework**: Next.js (React)
- **Database**: Firebase Firestore (`pairingSessions`)
- **Flow**: User enters a 6-digit code on mobile -> inputs Xtream/M3U credentials -> Firestore SnapshotListener on TV immediately downloads the payload.
