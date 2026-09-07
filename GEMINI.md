# Gemini AI Context: RedSurf

- **App**: RedSurf (TiViMate alternative)
- **Architecture**: Monorepo. `/tv-native` (Kotlin/Compose for Android TV) + `/` (Next.js Companion App - currently paused to focus purely on deep TV features).
- **Target OS**: Android TV (Fire OS, Google TV)
- **Primary Input**: Remote Control (D-Pad)

## When writing TV Code (Kotlin):
- Use `androidx.tv.material3` for all UI components.
- Ensure `Modifier.focusable()` is properly handled for D-Pad navigation.
- Rely on `ExoPlayer` for all media playback. Do not use web views.
- **Production Code Only**: Do not write structural placeholders. EPG Parsing must happen via Kotlin Coroutines on background threads (`Dispatchers.IO`) with proper chunking/batching for Room Database inserts.
