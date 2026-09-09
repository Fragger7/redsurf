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

## Ultimate Goal: The "Cloud Moat" & Advanced Paywall

1. **Real-Time Cross-Device Sync**: Firebase/Firestore sync across TVs for favorites, settings, and playback state (resume).
2. **Global Netflix-Style Profiles**: Multi-user account isolation with PIN-protection on TV.
3. **TMDB VOD Hydration**: TMDB API integration to enrich Xtream VODs with metadata (posters, cast, IMDb ratings).
4. **DVR / Scheduled Local Recording**: Background `ForegroundService` to record `.ts` streams to USB/SMB drives.
5. **Smart Channel Failover**: Auto-switch between providers on buffering/errors by channel name matching.
6. **Multi-View (Quad-Screen)**: Hardware-accelerated 2-4 split screen live sports playback with audio focus shifting.
7. **True Catch-Up TV Scrubbing**: Netflix-style scrubber with thumbnail track (if available) for Flussonic/Xtream archives.
8. **Stripe / Google Play Billing**: Implement premium licensing (free tier vs premium unlocks).
