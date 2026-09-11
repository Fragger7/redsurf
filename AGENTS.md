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

## Current phase: Phase 1 — `docs/plans/PHASE_1.md`

Design system + Live TV screen. The brief makes every architectural call up front (theme, focus
model, per-group paged Room queries, one-player-per-screen, Coil, no nav library) so execution is
Sonnet-lane; Opus reviews at the two screenshot checkpoints. Read the brief's "Decisions already
made" section before touching anything — those are settled.

**1.1–1.5 done and device-confirmed (2026-09-11).** Playback works end to end on the real TV with
a real playlist: Mobile Phone pairing → playlist loads → 3-pane Live TV screen renders → a channel
plays. Checkpoint B is now 5 rounds of user-reports-a-bug → root-cause-on-device → fix, all logged
in `PHASE_1.md` — read its "Checkpoint B, round N" entries for the full history, and its latest
"what to check on the real TV" list for what's still open and awaiting the user's next test pass.

As of round 5, fixed: accent color (was reading pink, not red — corrected by pixel-sampling the
project's own mockups, not by eye), QR code on the Mobile Phone pairing screen, one-step channel
playback, Back preserving Live TV state across the fullscreen toggle (root cause was a genuine
Compose pitfall — `AppShell` composed `LiveTvScreen` from two different structural call sites
depending on fullscreen state, which are different composition groups and silently wipe all
`remember`ed state on every toggle; see `AppShell.kt`'s doc comment before touching that file),
Back stopping at a Home destination before exiting the app instead of exiting outright, and OTA
now checking on every resume plus a 4h periodic background check plus a manual Settings button.

**Next up, not yet started:** a real visual/spacing pass — the user's own assessment is it's still
far from target despite one prior polish attempt. References given:
`docs/vision/references/streamvault/LiveTV.png` for proportions,
`docs/vision/references/tivimate/RedThemedEPGLiveTVScreen.jpg` (note: TiviMate has no separate
EPG/Guide screen — Live TV does that job directly). Explicit preference: StreamVault's **top nav**
over TiviMate's **left rail** — `NavStrip` already does this, no change needed there. The user
floated getting Opus's design judgment on this pass specifically (not the rest of Phase 1) —
undecided as of this writing; ask if it's still unstated when you pick this up.

**Also still open:** one unresolved report of the app returning to Onboarding with no sign of the
previously-loaded playlist after an update — checked, not a Room schema bump (version's been 6,
unchanged, since before v0.19.1), no confirmed cause yet; and OTA reported "erratic, might be good
enough for now" with no reproducible specifics — don't chase either without new detail from the
user (which version → which version, force-closed or not, etc).

**Workflow, from user feedback this session:** stop doing per-task ADB screenshot round-trips — too
expensive. Build + verify with compile/tests + a signed `assembleRelease` only, batch several
changes together, hand off a build for the user to test on the real TV via OTA instead. Targeted
ADB debugging of one specific reported bug is fine when the user says so explicitly (happened twice
this phase, both times solved a real bug) — that's different from routine verification loops.
**Never install an ad-hoc-versioned local build to the test device** — an early local build with
default versionName `v1.0.0` once out-ranked every real release by semver, permanently blocking OTA
until manually fixed; always verify a local `assembleRelease` with an explicit low/obviously-fake
version like `-PversionName=v0.0.0-local-verify`, check its signature, then delete it rather than
`adb install` it. Standing permission to merge to `main` freely is granted (no live users) — don't
ask before merging.

One real bug found live in 1.1–1.4, still not fixed (disclosed, out of scope): the M3U/Xtream
onboarding forms use a non-TV-aware text field that traps D-pad focus — a real remote user would
get stuck unable to submit. Documented in `PHASE_1.md` for a later pass.

## Product decisions on record

- **Cloud pairing: revive** (user, 2026-09-10). Background: `pairingSessions` was locked down for
  security, which surfaced that nothing reads it — the TV app moved to local NanoHttpd pairing and
  was never reconnected, so both web pairing flows (`app/dashboard`'s push, `app/pair/[code]`)
  currently write to a dead end. Decision: keep them; the TV-side Firestore listener gets built in
  the cloud phase. Until then, don't delete anything cloud-related, and don't build on it either.

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

If your shell tool runs each command in a fresh process (true for Claude Code's `Bash` tool),
`export JAVA_HOME=...` does not persist between calls — re-export it in the *same* call as every
`gradlew`/`apksigner` invocation, or it fails with "Unable to locate a Java Runtime" even though
you exported it moments ago. `apksigner` lives at
`~/Library/Android/sdk/build-tools/34.0.0/apksigner` on this machine (`$ANDROID_HOME` may be
unset in your shell — use the absolute path if so).

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
runs); no design system, so nothing shows a focus state; the web app cannot currently be built
locally on this machine — a pre-existing, unrelated native-binary install issue with
`@tailwindcss/oxide`/`lightningcss`, see `docs/plans/PHASE_0.md`.

**Fixed 2026-09-10, verified against the live Firestore project with real HTTP requests, not just
reviewed:** `firestore.rules` no longer leaves `pairingSessions` world-readable, and the playlist
schema now matches what the dashboard actually writes, so "Add Playlist" works. One thing this
surfaced but didn't resolve: nothing reads `pairingSessions` anymore — the native app's Firebase
pairing was replaced by local NanoHttpd pairing and never reconnected — so the two web pairing
flows are currently a dead end. Revive or remove is a product decision, not made here.

**Phase 0 is complete as of 2026-09-10.** Every item in `docs/plans/PHASE_0.md` is done and
verified — most against the actual running system, not just a green build. Phase 1 (the design
system) may begin.

Update this section when the facts change. It is the file everyone reads first, so a stale claim
here is worse than none.
