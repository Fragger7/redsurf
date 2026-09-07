# Gemini AI Context: RedSurf

This file provides system context for the Gemini AI agent when generating features or modifying code for RedSurf.

- **App**: RedSurf (TiViMate alternative)
- **Architecture**: Monorepo. `/tv-native` (Kotlin/Compose for Android TV) + `/` (Next.js Companion App)
- **Target OS**: Android TV (Fire OS, Google TV)
- **Primary Input**: Remote Control (D-Pad)

## When writing TV Code (Kotlin):
- Use `androidx.tv.material3` for all UI components.
- Ensure `Modifier.focusable()` is properly handled for D-Pad navigation.
- Rely on `ExoPlayer` for all media playback. Do not use web views.
- EPG Parsing must happen via Kotlin Coroutines on background threads (`Dispatchers.IO`).

## When writing Web Code (Next.js):
- This is the Companion App. Its primary role is Mobile Pairing (`/pair/[code]`) and acting as a proxy server.
- Optimize the UI for mobile screens, as this is where the user inputs their Xtream Codes or M3U links.
