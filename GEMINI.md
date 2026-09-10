# Gemini AI Context: RedSurf

- **App**: RedSurf (TiViMate alternative)
- **Architecture**: Monorepo. `/tv-native` (Kotlin/Compose) + `/` (Next.js Companion App).
- **Target OS**: Android TV (Fire OS, Google TV)
- **Primary Input**: Remote Control (D-Pad)

## Project Memory
You must read `PROJECT_VISION_AND_HISTORY.md` to understand all past decisions, completed features, and the remaining backlog.

## State of the Project
- Core TV architecture is physically committed (Room batching, ExoPlayer track mapping, Xtream VOD).
- GitHub Actions Semantic Release and TV OTA Updater are fully integrated.
- Local TV QR Server (NanoHttpd) is functional, bypassing the need for cloud pairing.
- Next Phase: Advanced TiViMate features (DoH DNS, Global Search, Auto Frame Rate, Multi-playlist management).
