# RedSurf - Premium Android TV IPTV Player

RedSurf is an Android TV application engineered to achieve TiViMate parity, paired with a Next.js Mobile Companion App for seamless onboarding.

## Ecosystem
- **tv-native/**: The core Android TV application built with Kotlin, Jetpack Compose, ExoPlayer, and Room.
- **/**: The Next.js Companion App for entering M3U/Xtream credentials and pushing them to the TV via Firebase Firestore.

## Automated Releases & OTA
Every push to `main` triggers a GitHub Action that generates a Semantic Release (`vX.Y.Z`) and compiles `RedSurf-vX.Y.Z.apk`.
The Android TV app includes an Over-The-Air (OTA) update engine that checks this repository and prompts the user to install the latest APK.

## Core Engineering Completed
- Hardware-accelerated ExoPlayer with deep Audio/Subtitle track mapping.
- Room SQLite batching for massive XMLTV guides.
- Mobile-to-TV Firestore Pairing pipeline.
