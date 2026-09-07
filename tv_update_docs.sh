#!/bin/bash
set -e

cat << 'MD' > ARCHITECTURE.md
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
MD

cat << 'MD' > AGENTS.md
# Agent Instructions: RedSurf

## Role & Persona
You are an expert TV/Android App Architect and Senior Software Engineer building **RedSurf**, a premium Android TV exclusive IPTV player meant to compete directly with TiViMate.

## Core Directives & Architecture
1. **/tv-native (The Client)**: A pure, native Android TV application written in Kotlin and Jetpack Compose for TV. Uses ExoPlayer, Room, WorkManager, and includes an OTA update engine.
2. **Root Directory (The Companion App)**: A Next.js web application utilizing Firebase Firestore for real-time Mobile-to-TV QR/Code pairing.

## Strict Accountability Rule
- **NO SKELETONS, NO MOCKING**: Production-ready code only.
- **Absolute Truth**: Transparent reporting of completed features vs. roadmap items.

## Tech Stack
- **Native TV**: Kotlin, Compose for TV, Media3 ExoPlayer, Firebase SDK, Room, WorkManager.
- **Web**: Next.js, Tailwind, Firebase.
- **DevOps**: GitHub Actions (Semantic Release + APK builds).
MD

cat << 'MD' > GEMINI.md
# Gemini AI Context: RedSurf

- **App**: RedSurf (TiViMate alternative)
- **Architecture**: Monorepo. `/tv-native` (Kotlin/Compose) + `/` (Next.js Companion App).
- **Target OS**: Android TV (Fire OS, Google TV)
- **Primary Input**: Remote Control (D-Pad)

## State of the Project
- Core TV architecture is physically committed (Room batching, ExoPlayer track mapping, Xtream VOD).
- GitHub Actions Semantic Release and TV OTA Updater are fully integrated.
- Companion App (Firestore pairing) is active and implemented.
- Next Phase: Advanced TiViMate features (DoH DNS, Global Search, Auto Frame Rate, Multi-playlist management).
MD

cat << 'MD' > README.md
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
MD

git add ARCHITECTURE.md AGENTS.md GEMINI.md README.md
git commit -m "docs: Comprehensive update of project state, knowledge base, and architectures"
git push origin main
