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
