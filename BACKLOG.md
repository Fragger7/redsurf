# RedSurf Backlog & Roadmap

## Current Progress (Completed)
- **Core TV Architecture**: Jetpack Compose TV UI, ExoPlayer integration, Room Database batching, Xtream JSON VOD parsing, Live TV playback.
- **Over-The-Air (OTA) Updates**: Integrated `UpdateManager.kt` via GitHub Releases/APK downloads.
- **CI/CD Pipeline**: GitHub Actions for semantic releases and building the TV APK automatically.
- **Companion Web Portal (Phase 1)**: Next.js + Firebase web app for cloud-based QR code pairing. Allows adding M3U and Xtream credentials.
- **Fixes**: Cleaned up API regressions, resolved DoH DNS namespace collisions, built and successfully published the v0.13.1 release.
- **Web Portal Refresh**: Pushed latest `app/` and `auth/` directory to Vercel via GitHub to ensure login works on the live site.
- **Playlist Toggling**: Added a toggle on the Dashboard to mark connections as Active/Inactive, preventing inactive playlists from being pushed to the TV.

## Active & Pending Fixes
- **TV App Branding**: Added Android TV Manifest proper banner (`android:banner`) and icon so the Leanback Launcher displays it correctly.
- **Firebase Alignment**: `google-services.json` on the TV was pointing to a dummy project. Updated to point to the correct AI Studio Firebase instance.

## Future Architecture (User Feedback)
- **Local LAN Pairing (QR Server)**: 
  - Instead of a cloud-hosted companion app, we will explore running a local NanoHttpd/Ktor web server directly on the Android TV app.
  - The TV will display a QR code pointing to `http://<tv-local-ip>:<port>`. 
  - The mobile device scans the code, loads the webpage served by the TV, and POSTs credentials directly over the local network. 
  - This removes the need for Firebase Cloud Firestore as a middleman for pairing.
