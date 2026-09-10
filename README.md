# RedSurf

A premium IPTV player for Android TV, built for personal and family use — aiming at TiViMate's
depth with a modern, red-and-black D-pad-native interface, and a free-tier cloud account for
managing playlists across devices.

Not a commercial product. Built to be used, not sold.

## Layout

| Path | What |
|---|---|
| `tv-native/` | The Android TV app — Kotlin, Compose for TV, Media3 ExoPlayer, Room |
| `app/`, `lib/` | The Next.js web portal — landing page, account, playlist management |
| `docs/vision/` | **What RedSurf is meant to be** — product vision, UI spec, IPTV domain knowledge, mockups |
| `docs/plans/` | What's being built now, with acceptance criteria |
| `docs/archive/` | Superseded documents from an earlier attempt. Kept for provenance; **not accurate** |
| `scripts/` | Setup tooling (release keystore) |

## Three ways to get a playlist onto the TV

1. **Cloud account** — sign in on the web portal, add your Xtream/M3U/Stalker connections, mark
   which are active, and push them to the TV.
2. **LAN pairing** — the TV serves a form on `http://<tv-ip>:8080`. Open it on your phone, type
   the credentials there instead of on a remote. Works fully offline, no cloud needed.
3. **Direct entry** — type them on the TV itself.

Each connection can be set to Live TV only, VOD only, or both.

## Building

Requires Temurin JDK 17 and the Android SDK (cmdline-tools, platform-tools, platform 34,
build-tools 34.0.0). No Android Studio needed.

```bash
export JAVA_HOME="$HOME/.local/opt/jdk17/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd tv-native && ./gradlew :app:assembleDebug
```

Pushing to `main` triggers a semantic release: CI builds a signed APK and publishes it to GitHub
Releases, and the app updates itself from there.

## Status

Under active reconstruction. The build, release signing, and on-device install are verified
working; large parts of the feature set are scaffolding that was never wired up. `AGENTS.md`
carries an honest, current breakdown of what is real and what is not — it is deliberately blunt,
because the previous iteration of this project failed by claiming otherwise.

## Contributing

If you're an AI agent working in this repo, read `AGENTS.md` first.
