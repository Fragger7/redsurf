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

## Phase 1 — done. No Phase 2 brief written yet.

`docs/plans/PHASE_1.md` (design system + Live TV screen) **closed 2026-09-11** at 1.6: memory
verified flat across playlist size (large real list used *less* PSS than the small one - 112.8 MB
vs 145.7 MB, paging genuinely works), all 7 acceptance-sweep lines pass, 13/13 tests, signed
release. Real end-to-end playback confirmed on the TV. Six Checkpoint B rounds fixed real,
device-found bugs on top of the original 1.1–1.5 build (state loss across the fullscreen toggle, a
nav-strip overflow bug that was the main cause of "looks unprofessional," one-step playback, OTA
reliability, Back navigation, the accent color, the QR code, a full visual pass against the
StreamVault/TiviMate references) - full history in `PHASE_1.md`'s "Checkpoint B, round N" entries
if you need it; you shouldn't need to re-derive any of it.

**No next phase is written.** Two backlog candidates exist, both already recorded with rationale -
read the entries rather than re-deciding from scratch:
- The onboarding M3U/Xtream text-field D-pad focus trap (a real bug, disclosed, deferred since
  1.1 - see "One real bug" note below).
- The Live TV/Guide merge - StreamVault's top-pill nav kept, but TiviMate's merged
  live+EPG-grid layout instead of the current simple channel list, plus an auto-hiding nav. Real
  idea, not urgent, blocked on EPG data existing first - see "Product decisions on record" below.

Ask the user which (or something else) becomes Phase 2, rather than assuming.

**Still open, not chased further without new detail:** one report of the app returning to
Onboarding with no sign of the previously-loaded playlist after an update — checked, not a Room
schema bump (version's been 6, unchanged, since before v0.19.1), no confirmed cause; and OTA
reported "erratic, might be good enough for now" with no reproducible specifics.

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

- **Live TV/Guide merge, deferred to its own phase** (user, 2026-09-11). Vision: keep
  StreamVault's top-pill nav (not TiviMate's left drawer - "modern Google TV style" per the user),
  but redesign the Live TV screen itself on TiviMate's proven layout instead of StreamVault's:
  categories left, a real EPG timeline grid on the right (not the current simple channel-list
  column), a live preview strip up top. Bonus, same request: the top nav auto-hides (slides up,
  disappears after an idle timeout - configurable) to give the preview strip more room, exactly
  how TiviMate's left rail behaves. References: `references/tivimate/RedThemedEPGLiveTVScreen.jpg`
  (the target layout), `references/streamvault/Home.png` (the nav style to keep).
  **Why not now:** the grid needs real EPG programme data to not be an empty fake grid, and EPG
  isn't populated yet (no sync worker scheduled - a Non-goal of Phase 1, see `PHASE_1.md`). Do EPG
  data + this merged-screen redesign + the auto-hide nav together as one phase, not the visual
  layout before the data exists. Not urgent - the user is in no rush.

## State and focus discipline (user directive, 2026-09-12 - binding, not a suggestion)

The user was explicit that losing continuity while testing is actively frustrating and named this
a standing rule, not a one-off fix: **every new screen, navigation path, and control must
preserve where the user was and what had focus, by default - check this before calling any
navigation feature done, the same way "builds clean" and "tests pass" are checked.** This
followed a pattern of real bugs: fullscreen-exit focus landing on the NavStrip instead of the
channel just watched; a debounce gap letting fast D-pad key-repeat rebuild an expensive Pager once
per row flown over. The scope is broad on purpose - which destination/menu item the user was on,
which control they used to navigate away, and where D-pad focus was, left-right and up-down, not
just whatever app-level data state happens to look correct.

Surviving `remember`ed data (per the Compose conditional-composition trap documented in
`AppShell.kt`/`LiveTvScreen.kt`) is necessary but not sufficient - Compose's focus system still
needs something to explicitly claim focus when a focused subtree is torn down, or it picks
whatever's nearest in the tree, which is rarely what the user actually wants. When building any
screen with more than one focusable region or a modal/fullscreen state, ask explicitly: "when the
user leaves this and comes back, where does focus land, and is that actually where they were?" -
don't assume the answer is yes without a `FocusRequester` actually wired to prove it.

**Known, not yet fixed:** moving focus away from a column (e.g. Categories) and back (e.g. into
Channels) via LEFT/RIGHT doesn't restore the exact row you left - it lands wherever directional
search resolves to from the new focus position, not "the same one as before." Needs a real
"remember focus per column, restore on re-entry" mechanism (an `onFocusChanged` at the column
level detecting entry-from-outside vs. movement-within, redirecting carefully to avoid focus-
loop bugs) - deliberately not rushed alongside the fullscreen-specific fix that inspired this
rule, since a hasty version risks new focus bugs of its own. Backlog item for whoever builds the
next screen with more than one focusable column.

## Backlog - explicitly logged, not forgotten

- **Tooltip on long-focus for truncated category names** (user idea, 2026-09-12): categories long
  enough to always ellipsis, even after the visual pass, could show their full name after the
  D-pad rests on them briefly - not built, needs a design pass on timing/placement first.
- **TiviMate-style categorized Settings screen** (user idea, 2026-09-12): group settings under
  named sections the way TiviMate does, once Settings has more than the 3 actions it has today
  (Check for updates, Add another playlist, Reset). Premature while Settings is this small.

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
