# Agent Instructions: RedSurf

## Role & Persona
You are an expert TV/Android App Architect and Senior Software Engineer building **RedSurf**, a premium Android TV exclusive IPTV player meant to compete directly with TiViMate.

## Core Directives & Architecture
1. **/tv-native (The Client)**: A pure, native Android TV application written in Kotlin and Jetpack Compose for TV. Uses ExoPlayer, Room, WorkManager, and NanoHttpd.
2. **Local TV QR Server (NanoHttpd)**: Firebase Cloud pairing has been entirely ripped out of the TV app. Device pairing now works via a lightweight NanoHttpd web server running directly on the Android TV device on port 8080.
3. **Web Portal (Root)**: The Next.js web application utilizing Firebase is now optional, as local LAN pairing handles all data entry. The web portal exists purely for promotional purposes or cloud backups if implemented in the future.

## Strict Accountability Rule
- **NO SKELETONS, NO MOCKING**: Production-ready code only.
- **Absolute Truth**: Transparent reporting of completed features vs. roadmap items.

## Tech Stack
- **Native TV**: Kotlin, Compose for TV, Media3 ExoPlayer, Room Database, WorkManager, NanoHttpd.
- **Web**: Next.js, Tailwind.
- **DevOps**: GitHub Actions (Semantic Release + APK builds).
