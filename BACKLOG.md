# RedSurf TiViMate Parity Backlog

We have built the architectural skeleton for a world-class IPTV player. To achieve true 1:1 parity with TiViMate, the following deep features must be implemented next:

## 1. Network & Connectivity Optimization
- [x] **Custom DNS (DoH)**: Bypass ISP domain blocking by integrating OkHttp DNS-over-HTTPS (e.g., Cloudflare 1.1.1.1, Google 8.8.8.8) natively.
- [ ] **Custom User-Agent per Playlist**: Allow users to spoof different hardware/player agents for different providers.

## 2. Advanced Hierarchy & Organization
- [x] **Multi-Playlist Support**: Merge and manage multiple Xtream/M3U accounts simultaneously.
- [x] **Group Management**: Ability to Hide, Rename, or Pin Channel Groups (e.g., hiding international categories).
- [x] **Channel Management**: Ability to Hide or Rename specific channels within a group.
- [x] **Global Favorites Engine**: Aggregate favorite channels across multiple playlists into a master "Favorites" tab.
- [ ] **VOD & Series Hierarchy**: Present VODs via strict Xtream Categories -> Movies/Series rather than a flat M3U list.

## 3. Deep Search
- [x] **Global Search Matrix**: Unified search that queries Live Channels, VODs, Series, and EPG Program titles simultaneously.

## 4. Player Tuning & Experience
- [x] **Auto Frame Rate (AFR)**: Query device capabilities and dynamically switch the TV's hardware refresh rate (e.g., 24Hz, 50Hz, 60Hz) to match the broadcast stream's FPS, eliminating judder.
- [ ] **EPG Time Offset**: Global and per-playlist sliders (+/- hours) for providers whose EPGs are misaligned with the video feed.
- [x] **Data Backup - [ ] **Data Backup & Restore** Restore**: Local and SMB-share export of the Room DB (Favorites, Hidden Groups, Settings).

## 5. Deployment & Updates
- [x] **Semantic Release CI/CD**: GitHub Actions auto-bumping versions and releasing `RedSurf-vX.Y.Z.apk`.
- [x] **OTA Update Engine**: Native Android TV package installer polling GitHub Releases to auto-update the app without Play Store intervention.
