# Agent instructions — RedSurf

RedSurf is a **TiViMate-grade IPTV player for Android TV**, built for one family's own use, with a
free-tier cloud account for managing playlists. Native Kotlin + Compose for TV in `tv-native/`;
a Next.js web portal at the repo root.

## Read these first, in this order

| Path | What it is |
|---|---|
| `docs/vision/PRODUCT_VISION.md` | **The product.** D-pad control matrix, "The Player IS The App", the cloud credential-locker model. Outranks everything else. |
| `docs/vision/UI_SPEC.md` | The design language: palette, focus model, screen layouts. |
| `docs/vision/IPTV_DOMAIN_KNOWLEDGE.md` | Hard-won IPTV engineering knowledge. Read before touching parsing, networking or playback. |
| `docs/plans/HARDWARE.md` | The target device's real, measured limits. |
| `docs/plans/PHASE_*.md` | What is being built right now, with a status board and acceptance criteria. |
| `docs/plans/WORKFLOW.md` | Which model does which work, when to escalate, and the phase gates. |
| `docs/vision/references/` | Screenshots: `streamvault/` for layout, `tivimate/` for workflow, `../mockups/` for colour. |

**`docs/archive/` is superseded — do not trust it.** It is previous agents' claims, most of which
were false. It is kept for provenance only.

## The one rule that matters

**Never claim something works because you wrote plausible code for it.**

This project was rebuilt because previous agents repeatedly reported features as complete when
they were hollow. Compiling is not working. A green CI badge is not an install. "I implemented X"
is only true after you watched X happen.

State plainly which of these you did:
- *builds clean* — compiled, nothing more
- *tests pass* — the tests ran and you read the output
- *verified on device* — you installed it and observed the behaviour

If you cannot verify something (needs the TV, needs the user's IPTV provider), **say so and stop**
rather than asserting success.

## Binding technical constraints

1. **No Dagger Hilt.** It caused the infinite KSP compiler loops that killed the predecessor
   project (`docs/archive/`… see `docs/vision/TVMIME_POST_MORTEM.md`). Use manual constructor
   injection. This project is small enough that it is genuinely the right call.
2. **Borrow techniques, never transplant UI.** Wholesale-porting StreamVault's UI is what produced
   the "Frankenstein" codebase that ended the last attempt.
3. **Memory discipline is mandatory.** The target device has **1.89 GB RAM, ~449 MB free**. Never
   load a full playlist into memory; stream-parse into Room and page from SQLite. See
   `docs/plans/HARDWARE.md`.
4. **Every focusable element needs a visible focus state.** On a TV the focus ring *is* the
   interface. See `docs/vision/UI_SPEC.md` §2.
5. **Never commit signing material.** `*.jks`, `*.keystore`, `signing.properties` are gitignored.
   The repo is public.

## Build & test

```bash
export JAVA_HOME="$HOME/.local/opt/jdk17/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd tv-native
./gradlew :app:assembleDebug        # ~2m40s cold
./gradlew :app:assembleRelease      # signed, if ~/.redsurf/keys/signing.properties exists
./gradlew :app:testDebugUnitTest
```

Toolchain: Temurin JDK 17, Gradle 8.7 (wrapper committed), AGP 8.2.2, Kotlin 1.9.22,
compileSdk 34, minSdk 23. SDK footprint is deliberately minimal — no Studio, no emulator.

**Test device** (only when it's powered on — don't assume it is):
```bash
adb connect 192.172.7.160:35631
adb install -r tv-native/app/build/outputs/apk/release/app-release.apk
adb shell monkey -p com.redsurf.tv -c android.intent.category.LEANBACK_LAUNCHER 1
adb exec-out screencap -p > /tmp/screen.png     # look at what you built
adb logcat -s RedSurf
```
When the TV is off, iterate with local builds and cut a GitHub release for the user to install
via OTA. CI is free — the repo is public.

## Verified state as of 2026-09-10

**Real and working:** builds clean (debug + release); release signing verified end to end — CI
publishes signed releases, and **a genuine in-app OTA update has been observed completing on the
Chromecast** (v0.17.6 → v0.17.7, versionCode 61→62, no manual step); installs and launches (41 MB
PSS at onboarding); M3U parser with genuine unit tests; DoH / custom User-Agent networking;
NanoHttpd LAN pairing server; Room schema; ExoPlayer + track selection; AFR logic wired into the
player. The 17 `\$` interpolation bugs are fixed and the duplicate `EpgSyncWorker` stub is deleted
(0.2). The OTA updater does real semantic version comparison, shows a consent dialog before
installing anything, and checks/explains the install-unknown-apps permission instead of silently
failing — all three **device-verified**, including the negative path (permission denied → the
app's own explainer dialog, not a raw OS block). Two more bugs were caught only by live testing and
are also fixed: the consent dialogs didn't actually trap D-pad focus (a same-composition overlay
`Box` draws on top but doesn't own input focus — fixed by using a real
`androidx.compose.ui.window.Dialog`), and `tv-material3`'s default `Surface` color is light, making
white dialog text unreadable (fixed with explicit dark styling). See `docs/plans/PHASE_0.md` §0.7
for the full account, including how the first test attempt was invalid (it tested v0.17.5, built
*before* these fixes existed) and was caught by checking the running version rather than trusting
the result. `lib/firebase.ts` no longer has hardcoded fallbacks; it now fails loudly if env vars
are missing (0.5, verified directly against the compiled file).

**Built but never wired up:** `GlobalSearchEngine`, `BackupManager`, `CatchupEngine`, `XtreamApi`
(VOD/series), `TmdbApi`, `SettingsManager`, `PlayerSettings`. Favourites, hide and group-management
DAO methods are all dead. `VodDashboard` is hardcoded film titles.

**Known broken:** EPG never populates (no `WorkManager` enqueue exists, so neither sync worker ever
runs); `firestore.rules` leaves `pairingSessions` world-readable while the web app writes IPTV
credentials into it; the cloud dashboard cannot save a playlist because the rules and the app
disagree on the schema (0.4, not started); no design system, so nothing shows a focus state; the
web app cannot currently be built locally on this machine — a pre-existing, unrelated native-binary
install issue with `@tailwindcss/oxide`/`lightningcss`, see `docs/plans/PHASE_0.md`.

Update this section when the facts change. It is the file everyone reads first, so a stale claim
here is worse than none.
