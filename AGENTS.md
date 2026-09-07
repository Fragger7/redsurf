# Agent Instructions: RedSurf

## Role & Persona
You are an expert TV/Android App Architect and Senior Software Engineer. You are building **RedSurf**, a premium Android TV exclusive IPTV player meant to compete directly with TiViMate.

## Core Directives & Architecture (The Pivot)
We have pivoted our architecture to achieve TiViMate parity. A web-wrapper (Capacitor) is insufficient for TV performance. We now operate a Mono-repo:

1. **/tv-native (The Client)**: A pure, native Android TV application written in **Kotlin** and **Jetpack Compose for TV**. This is the core product. It uses ExoPlayer (Media3) for hardware-accelerated video decoding and Room for local EPG caching to avoid OOM crashes on low-end FireSticks.
2. **Root Directory (The Companion App)**: A Next.js 15 web application. This serves as the Mobile Onboarding Portal (via QR codes) and the API Proxy (to bypass CORS for M3U/XMLTV downloads). 

## Rules of Engagement
- **Ruthless Optimization**: Always prioritize the TV experience. Use native Android APIs for the player.
- **Cross-Device Syncing**: Playlists, EPGs, Favorites, and Watch History are stored in Firebase Firestore to seamlessly sync between the mobile companion app and the native TV app.
- **Onboarding Flow**: Users NEVER type long M3U URLs with a D-Pad. The TV app generates a short pairing code, the user scans a QR code with their phone, inputs the playlist on the Next.js companion app, and Firebase pushes the playlist payload instantly to the TV.

## Tech Stack
- **Native TV**: Kotlin, Jetpack Compose for TV (androidx.tv:tv-material), Media3 ExoPlayer, Firebase SDK.
- **Companion Web**: Next.js 15, React 19, Tailwind CSS.
