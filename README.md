# RedSurf - IPTV Player

![RedSurf](public/icon.png)

RedSurf is a modern, cross-platform IPTV player with TiViMate feature parity, inspired by the redshift of the cosmos.

## Features
- **Cross-Platform:** Works on Android TV, Mobile (iOS/Android), and Web browsers.
- **TiViMate Inspired UI:** Clean, dark, high-contrast user interface optimized for spatial D-Pad navigation (TV remotes) and touch.
- **M3U Playlist Parsing:** Automatically parses `.m3u` and `.m3u8` playlists, extracting categories/groups and channel logos.
- **Robust Video Engine:** Powered by `hls.js` with transparent fallback to native Apple HLS capabilities.
- **Cloud Persistence (Firebase):** Saves playlists and preferences to Google Firebase for seamless synchronization across all devices.

## Tech Stack
- **Framework:** Next.js 15 (App Router) + React 19
- **Styling:** Tailwind CSS v4 + Motion (Framer)
- **Database:** Firebase Firestore
- **Mobile/TV Wrapper:** Capacitor

## Getting Started

### Development
1. Clone the repository
2. Run `npm install`
3. Copy `.env.example` to `.env` and fill in your Firebase credentials.
4. Run `npm run dev` to start the local server on `http://localhost:3000`.

### Android Build
This repository uses GitHub Actions to automatically build APKs when a new version tag (e.g. `v1.0.0`) is pushed.
To build locally:
1. `npm run build` (Ensure `next.config.ts` is set to `output: 'export'`)
2. `npx cap sync android`
3. Open the `android` folder in Android Studio and build the APK.
