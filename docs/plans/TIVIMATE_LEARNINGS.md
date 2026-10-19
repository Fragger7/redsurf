# TiviMate learnings — what its shipped APK tells us (2026-10-18)

Source: TiviMate **5.3.3** (`ar.tvplayer.tv`, versionCode 5332), pulled from the user's own TV and
studied with REA (`docs` at github.com/morluto/rea) + JADX. **Everything here is observation and
technique, written in our own words. No TiviMate code, layouts, strings or assets are copied into
this repo, and none should be** - RedSurf borrows techniques, never transplants UI (`AGENTS.md`).
The raw material lives outside the repo, in `~/Development/rea-lab` (APK, `out/src` decompile,
scripts); REA is registered as an MCP server for that folder only (`cd ~/Development/rea-lab &&
claude`).

## Standing rule (user, 2026-10-18): consult TiviMate by default, for everything

Not limited to the scenarios above or the S-items below. For the rest of RedSurf's development,
**before designing or deciding anything non-trivial** (a screen, a list, player/network/error
handling, data model, background work, settings structure, a performance fix), check what
TiviMate does and record what was learned here, the same way "builds clean" is checked. Scope is
anything that makes RedSurf better: pace, performance, architecture, efficiency, logic, and
second-guessing our own design decisions against a mature, proven app.

How, cheapest first:
1. **This file** - is it already answered?
2. **The decompile** (`~/Development/rea-lab/out/src`) - `grep` locally for framework/library
   names, constants, layouts, dimens, anims, strings; read only the few matching lines, never dump
   whole files into context (the user has tight usage limits). REA's MCP tools (session started in
   `rea-lab`) for reference tracing.
3. **TiviMate running on the TV** over adb - for behavior the hidden code won't show (timing,
   focus, failure handling, frame stats).

Then add a numbered finding to the table below, with what it means for RedSurf. Limits stay:
technique only, written in our own words; no copied code/assets; no decryption. If a newer
TiviMate is on the TV, re-pull and re-decompile (steps in the memory note / `rea-lab/scripts`).

## What is and isn't visible

- **Hidden:** TiviMate's own app code - `MainActivity`, the guide, playlist/EPG handling, settings
  screens, its buffering and error logic, its own strings (`player_api.php`, `xmltv` never appear).
  It ships **DexProtector** (`libdexprotector.so` + an encrypted `assets/assets.dat`). We do not
  decrypt it (decided 2026-10-18; circumventing a protection measure isn't covered by personal or
  non-commercial use). Learn its *behavior* by observing it on the TV instead.
- **Visible:** all bundled libraries (with versions), the manifest, and decoded resources -
  184 layouts, ~935 strings incl. error messages, `anim`/`animator`/`transition`/`interpolator`,
  colors, dimens, drawables. Class and member names are scrambled (R8 + repackaging into one
  package), but framework/library names and constants survive.

## Findings

| # | Finding | What it means for RedSurf |
|---|---|---|
| F1 | **Media3 1.10.1** (RedSurf: **1.2.1**, late 2023) | ~2 years of ExoPlayer/HLS fixes and performance work we don't have. Upgrade is item S1 below. Likely needs a compileSdk/AGP bump - verify first. |
| F2 | Lists are **Leanback `VerticalGridView`** (View/RecyclerView-based), not Compose | Explains part of the smoothness gap on this Chromecast, but see "Compose vs Leanback" below - not a reason to rewrite. |
| F3 | Ships Media3's **FFmpeg extension** (`libffmpegJNI`, `libavutil`) | Software audio decoding for formats the device can't decode (AC3/E-AC3 etc.) - a robustness technique for IPTV streams. Candidate for later. |
| F4 | Bundles its **own SQLite build** (`libsqliteJni`), plus WorkManager, DataStore, OkHttp, Glide, coroutines, kotlinx.serialization | Same family of choices RedSurf already made (Room/WorkManager/OkHttp); nothing to switch. |
| F5 | Buffering setup visible in the APK is **Media3's own defaults** (start after ~1s, rebuffer ~2s, 50s max) | TiviMate's own tuning, if any, is in the hidden code. RedSurf already tunes its own (`PlayerController`, `PreviewPlayerHost`). |
| F6 | Startup profile (`assets/dexopt/baseline.prof`) ~10 KB, library-only | RedSurf ships the same kind (~11 KB). Not the source of the gap. |
| F7 | Screen map from the manifest: settings edits are **separate small full-screen steps** (playlist name, delete playlist, guide URL, guide source name, logos folder, recordings folder, reset positions, reset watch time, create channel group, copy channels, a playlist wizard) | Structural reference for the per-playlist detail page and EPG Sources work (`AGENTS.md` backlog). The settings *contents* are built in hidden code. |

## Compose vs Leanback - decided 2026-10-18: stay on Compose

The confirmed leads in `PERFORMANCE_SALVAGE.md` (L2 state writes on every key press, L4 a focusable
`Surface` per half-hour cell, L5 two ExoPlayers) are about how RedSurf *uses* Compose; the same
patterns would be slow in Leanback too. A switch is a rewrite of every screen onto a toolkit Google
treats as legacy. **Escape hatch, only if measurement demands it:** after L2/L4 are fixed and
re-measured, if the guide grid still misses frame budget, host *that one grid* as a View-based list
inside Compose (`AndroidView`). One screen, not a rewrite.

## TiviMate parity / learning sprint - runs inside the performance salvage

No new features until `PERFORMANCE_SALVAGE.md` is done, so this is folded into it, not after it.

| # | Item | How |
|---|---|---|
| S1 | **Upgrade Media3 1.2.1 → 1.10.x** | Check prerequisites (compileSdk/AGP), upgrade, re-run the player paths (tune, zap, preview↔fullscreen, error panel, stall watchdog) on device. |
| S2 | **Measure TiviMate next to RedSurf on the same TV** | `dumpsys gfxinfo <pkg> framestats` while scripting the same moves in both apps (category scroll, grid scroll, open/close player, settings). Gives the salvage a numeric target instead of "feels choppier". |
| S3 | **Reference notes from visible resources** | Row heights/spacing from layouts + dimens, focus/transition timings from `anim`/`interpolator`, error-message *coverage* (which failure cases TiviMate names). Written up here as numbers and categories, implemented our own way. |
| S4 | **Behavior capture for hidden logic** | Drive TiviMate over adb for the things the code won't show: zap timing, what happens on a dead stream, guide scroll behavior, settings depth. Replaces the user checking TiviMate by hand. |
| S5 | FFmpeg audio extension (F3) | After the salvage; only if real streams hit unsupported audio. |
