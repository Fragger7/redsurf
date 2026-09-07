# Agent Handoff Document: RedSurf

## Context
**RedSurf** is a TiViMate clone being built as a modern web application (Next.js 15) capable of running as an Android APK (via Capacitor) or a PWA. The primary UI mimics standard TV interfaces using D-pad navigation.

## Current State
1. **Core UI/UX**: The application uses a TV-optimized, dark-mode design overlaying an HLS video player. 
2. **Key Components**:
   - `TVInterface.tsx`: The heart of the app, containing left sidebar, groups list, channel list, and background video player.
   - `useTVNavigation.ts`: Custom hook intercepting D-pad/Arrow keys for navigation.
   - `m3uParser.ts`: Parses remote `.m3u` / `.m3u8` playlists into groups and channels.
   - `api/proxy/route.ts`: Proxies M3U fetching to avoid CORS issues on the client.
3. **Database**: We are utilizing Firebase Firestore. Setup is in `lib/firebase.ts`. 

## Next Immediate Steps
1. **Firebase Persistence**: When a user inputs an M3U link in the Settings panel, instead of just pushing it to local React state, save the parsed channel list or the remote M3U URL itself to Firestore under the user's document/collection so it persists across reloads.
2. **Authentication**: Users need a way to log in (Firebase Auth) if we want multi-device syncing. For now, an anonymous login or basic Google Auth is required.
3. **EPG Parsing**: Integrate an XMLTV parser so that the right-side EPG preview can show current and upcoming shows for the focused channel.

## Git / CI Notes
- The user provided a PAT for GitHub integration (`https://github.com/Fragger7/redsurf.git`).
- *Note:* The user wants GitHub Actions to build the APK. The workflow file was removed because their token lacked the `workflow` scope. You must instruct the user to check "workflow" in their GitHub PAT settings before you can commit `.github/workflows/*.yml` files.

## Local API Keys & ENV
- The app relies on Next.js `APP_URL` and `GEMINI_API_KEY` (if used later for smart features).
- The Firebase config in `lib/firebase.ts` connects to `fourth-surge-1txfk`.
