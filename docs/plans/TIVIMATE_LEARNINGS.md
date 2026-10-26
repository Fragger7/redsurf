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
| F8 | **Focused category stays at a fixed mid-list position** while the list scrolls under it (Leanback window alignment), observed on the TV 2026-10-19 | The model for fixing L1 (RedSurf pins the focused row to the *top*). |
| F9 | **20×DOWN then 20×UP returns to exactly the same row and screen position**, in both the guide and the category list, while each category move reloads the guide | RedSurf lost presses in the same test (ended 2 rows off in Categories, escaped to the nav strip from the grid). Input must never be dropped, however busy the frame. |
| F10 | Measured frame timing, same TV, same script - see S2 results below | RedSurf's browsing is roughly 10× worse; TiviMate's own player transitions are not jank-free either. |
| F11 | TiviMate ships **R8-optimized** (that's why its names are scrambled); RedSurf had R8 **off** in release | Turned on 2026-10-19 with `-dontobfuscate` (readable traces) - Compose is built to be R8-optimized. APK 3.6 MB after. |
| F12 | TiviMate's preview area is **one fixed size** - it never resizes while you browse (observed on the TV) | RedSurf's hero band animated between two heights on every scan start/stop, re-laying out the whole grid each frame. Fixed height since 2026-10-19 (batch 2). |
| F13 | In TiviMate's guide, UP/DOWN from the programme airing now **stays on the programme airing now** (from the user's report; not yet re-checked on TiviMate by Claude) | RedSurf anchored on the cell's visible start and could land on an ended programme, where OK only shows info (user report 2026-10-19). Now anchors on *now* from the live cell; device-checked 8 steps. |
| F14 | **The guide focuses whole channel rows, not programme cells** (uiautomator, 2026-10-20 01:45, TiviMate's guide on the TV): 96 nodes / 45 text / 16 focusable; focusables are the GridView, one FrameLayout per channel row (full width) and a horizontal RecyclerView per row. Programme cells are plain, non-focusable TextViews. RedSurf's guide: 173 nodes / 63 text / 51 focusable - every programme cell focusable. | Rebuild the row: one focusable row, a selected-programme index moved by LEFT/RIGHT, cells drawn only. Cuts focus search, bring-into-view and the accessibility diff - which runs on every change on this TV because Projectivy Launcher's accessibility service is always on (~115ms/press measured in `checkForSemanticsChanges`/`sendAccessibilitySemanticsStructureChangeEvent`). |
| F15 | **A TiviMate category move costs one heavy frame** (atrace, 2026-10-20 04:00, 12 DOWNs in its category list): ~220ms after the key, one 85-123ms frame (`RV OnLayout` rebinding the guide rows, `onBindViewHolder` ~20-29ms); no other frame over 25ms. RedSurf v0.41.13 paid three per move: ~30ms on the key (focus + restyle + scroll), 60-110ms when the group committed to AppShell, 140-170ms rebuilding the rows. | So TiviMate debounces too and its reload is not free - the gap is the extra frames. **Tried and reverted (2026-10-20):** loading the rows first and committing with them in one frame cut UI-thread work (3.4s vs 3.7s per 12 moves, frames >33ms 19 vs 29) but gfxinfo got worse in 4/4 interleaved runs (janky 145-149 vs 118-136, p90 150 vs 113-129ms) - one huge frame of new rows is harder on the RenderThread than two smaller ones. Next: make the rebuild itself cheaper / spread it, keep the key frame light. |
| F16 | **Fullscreen is an overlay on an unchanged layout** (inferred from TiviMate's 57ms preview→fullscreen p50 vs RedSurf's ~200ms first frame, then confirmed as RedSurf's cause by trace, 2026-10-20) | RedSurf's entry/exit resized the whole UI (safe-area padding dropped, nav strip removed) and re-measured everything underneath. Fixed in v0.41.27: constant layout, the player alone escapes the safe area; first frame OK→fullscreen ~200 → 82ms, Back 421 → 174ms. Rule: transitions must not change the size of anything that stays composed. |
| F17 | **Visible playback/exit strings** (`res/values/strings.xml`, 2026-10-20): a **"Buffer size"** setting - None/Small/Medium/Large/Very large, described as "duration of video that must be downloaded to start playback ... can increase the channel switching time"; separate **software audio decoder** and **software video decoder** toggles; AFR; **"Confirm exit by second press Back"** with a "Press again to exit" toast; a **"Playlist updating…"** status string; "Press again to switch the channel". | TiviMate's answer to poor providers is a user-chosen startup buffer, not network trickery - RedSurf has a fixed buffer today. Double-Back-to-exit is a TiviMate setting, which supports the user's idea (final Back reveals the nav bar, the next Back exits). A playlist-updating status line exists there too. **Built 2026-10-26 (S8, v0.44.21):** second-Back exit within 4s with a "Press BACK again to exit" toast, and one global "Updating guide" line in the nav strip. Buffer size built in S5. Software audio decoder (FFmpeg) deferred - GPLv3 licence decision. |
| F18 | **Accessibility cost is View-cheap, Compose-expensive** (2026-10-26): with the Projectivy launcher's a11y service bound, RedSurf pays ~4.5 points of category jank in semantics diffing; TiviMate's Views report a11y events lazily | Keep the semantics tree small (merge/clear row descendants). TiviMate's 7.5% was measured with the same service on. |
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

### S2 results - 2026-10-19, RedSurf v0.41.1 vs TiviMate 5.3.3

`scripts/perf-measure.sh`, `dumpsys gfxinfo`, presses ~0.2s apart from an on-device loop. Janky% =
Android's own janky-frame share; p50/p90/p99 = frame time in ms. Device context during the runs:
~251 MB RAM free, swap 491/495 MB used (heavy pressure for both apps).

| Move | RedSurf frames / janky / p50 / p90 / p99 | TiviMate frames / janky / p50 / p90 / p99 |
|---|---|---|
| Categories 20×DOWN, 20×UP | 121 / **69%** / 200 / 900 / 1550 | 601 / 7.5% / 18 / 42 / 109 |
| Guide 20×DOWN, 20×UP | 91 / **98%** / 250 / 500 / 1000 | 581 / 7.6% / 29 / 32 / 44 |
| Guide 10×RIGHT, 10×LEFT | 154 / **36%** / 32 / 200 / 300 | 384 / 1.0% / 32 / 36 / 40 |
| OK → playback (RedSurf: straight to fullscreen, preview setting off; TiviMate: preview) | 106 / 6.6% / 19 / 21 / 750 | 106 / 11% / 8 / 23 / 57 |
| Fullscreen → Back to guide | 12 / **100%** / 200 / 600 / 650 | 79 / 15% / 7 / 69 / 129 |
| (TiviMate preview → fullscreen) | - | 36 / 28% / 57 / 69 / 77 |

Method notes: `screencap` on this TV does not capture the video layer (both apps show a grey frame
during playback - not a bug). Keep labels honest - several runs landed on the wrong screen and
were relabeled `(misnav)` in the raw log rather than reported.

### Visual design notes - 2026-10-20 (design-language research, `docs/vision/design-language/RESEARCH.md`)

Measured from a TiviMate 5.3.3 device screenshot and the decoded resources: guide row pitch ~44dp
(~40dp cell + ~4dp gutter); rows and cells are separated by **background-coloured gutters, no
lines**; near-square cells; cell `#1B1E21` on a cool `#131619` background, focused row `#424547`,
selected cell desaturated red `#753A37`. Its only bundled font is a **digits-only subset of Roboto
Medium with tabular figures** - it makes numbers line up. (Poppins, our wordmark font, has no
tabular figures at all - keep it off numbers.)

### Settings parity notes - 2026-10-26 (overnight run)

- **EPG time shift:** TiviMate keeps it per playlist (playlist settings → "EPG time shift").
  RedSurf's Time offset is global for now - one family, providers in one zone. Moving it per
  playlist later needs a column on `playlists`, not a new preference.
- **Hide group:** TiviMate hides a group from its long-press menu on the group and unhides it
  under Settings → Parental control → Hidden groups. RedSurf does the same: hold OK on a
  category → "Hide category", unhide under Parental controls → Hidden categories. The hidden set
  is keyed by playlist + raw group name, so it survives a re-import.
- **Remote control:** TiviMate's is a full key-remap table (resource strings: "Long OK", "Long
  Back", "Long Up"..., "Restore to defaults", hint "Long OK: open menu"), each key bound to an
  action ("Show menu", "Add to Favorites", "Recent channels", "TV guide", "Search", "Record",
  "Catch-up"). RedSurf keeps Back fixed (Back-peel + long-press Teleport/jump are its own model)
  and offers the remap only for Long-press OK, with TiviMate's three most useful actions. A full
  remap table can come later if asked for.
