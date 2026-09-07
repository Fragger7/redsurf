# RedSurf Architecture

## Overview
RedSurf is an Android TV exclusive IPTV player built to achieve strict feature parity with TiViMate. It relies on a dual-environment architecture:
1. **tv-native/**: The core Android TV application.
2. **/**: (Future) The Next.js Companion App for mobile onboarding.

## The tv-native Core
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose for TV (`androidx.tv.material3`).
- **Video Engine**: Media3 ExoPlayer with custom `HttpDataSource.Factory` for User-Agent spoofing to bypass ISP/Provider blocks.
- **Database**: Room (SQLite) for caching massive M3U playlists and XMLTV EPG data.
- **Parsing**: 
  - M3U Parsing via `BufferedReader` and Coroutines (`Dispatchers.IO`).
  - XMLTV Parsing via `XmlPullParser` handling streamed parsing and batch inserts to avoid OOM exceptions on low-memory Android TV devices.
- **Background Sync**: `WorkManager` scheduled tasks to download EPG data silently.
- **VOD/Series**: Direct integration with the Xtream Codes JSON API (`player_api.php`) for rich metadata.

## Current Status
We are actively converting structural skeletons into production-ready, deep implementations. Zero mocking or stubbing is permitted. All features must be fully functional for real-world IPTV streams.
