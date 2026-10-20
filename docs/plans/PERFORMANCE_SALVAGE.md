# Performance salvage — next session starts here (user, 2026-10-05)

**The user's verdict, verbatim in substance:** great features, but "we've reached a fatal point of
extremely poor performance and choppiness ... it might become useless for every day use."
TiviMate, with far more features, runs much better on the *same* Chromecast. All menus choppy,
focus jumping everywhere, preview↔fullscreen looks unnatural and may have crashed once. **Do not
add features until this is fixed.** The user asked for the smartest available model (Opus) to plan
it, and to question whether foundational architecture decisions are wrong.

## How to run this (process)

1. **Opus architecture review first, read-only** (an agent with the code + this file +
   `FOCUS_MODEL.md` + `HARDWARE.md`): is the slowness a set of local mistakes, or foundational
   (e.g. one giant `LiveTvScreen` state scope; `AppShell` holding app-wide state that recomposes
   everything; the grid composing every half-hour cell as a tv `Surface`; two ExoPlayers)?
   Output: a ranked fix list with expected wins.
2. **Measure before and after - numbers, not feel.** Baseline on v0.41.1 with
   `adb shell dumpsys gfxinfo com.redsurf.tv framestats` (janky-frame %, p90/p99 frame time)
   while scripting: category scroll (hold DOWN), grid scroll (RIGHT×10, DOWN×10), preview→fullscreen
   →Back, Settings rail. Also Compose recomposition counts (Layout Inspector isn't available - add
   temporary `SideEffect { count++ }` logging in suspect composables, debug-only), `dumpsys meminfo`,
   and `logcat -b crash` for the possible crash.
3. Fix in ranked order, re-measure after each batch; one real release per batch (sprint rules).
4. Re-sweep focus behavior against `FOCUS_MODEL.md` (the user's #6 "erratic" report) at the end.

## Baseline measured 2026-10-19 (v0.41.1) - numbers in `TIVIMATE_LEARNINGS.md` S2

- **Browsing is the emergency, not playback.** Guide DOWN/UP 98% janky (p50 250ms), Categories 69%
  (p50 200ms, p99 1.5s), fullscreen→guide 100% janky (12 frames). TiviMate on the same TV: 1-8%.
- **Presses are lost** under load (Categories ended 2 rows off; the grid escaped to the nav strip).
- **L6 confirmed live:** `SQLiteConnectionPool` - a connection request waited **60s** behind a
  `COMMIT` that had been running **14s**, during ordinary browsing ~1 min after launch. Background
  DB writes are long single transactions that starve everything else.
- **The crash is real:** `dumpsys activity exit-info` - `APP CRASH(EXCEPTION)`, foreground,
  2026-10-05 01:47:25, PSS 143 MB, ~10 min after the v0.41.1 install. Stack trace already rotated
  out of the crash buffer; we have no persistent crash log, so the next one would be lost too.
- Device was under heavy memory pressure (swap full) - affects both apps, but raises the stakes on
  RedSurf's footprint.
- Seen in passing, to check: several adjacent guide rows show the same channel number (`3205`).

## ▶ STATUS 2026-10-20 05:55 CDT - read this first (night 2, autonomous)

**Installed on the TV:** the real CI release **v0.44.4** (run 171). **Version names:** CI's release
action bumps the *minor* version on every `feat:` commit, so from run 165 the real names differ from
the v0.41.x labels used for local builds in this log: v0.41.21 = **v0.42.0**, .22 = **v0.43.0**,
.23 = **v0.44.0**, .24 = **v0.44.1**, .25 = **v0.44.2**, .27 = **v0.44.3**, then v0.44.4 (refactor).
Runs 150-164 match (v0.41.6-v0.41.20). Everything below this
block up to "Test navigation" is the night's log, newest first; the older 2026-10-19 handoff
further down is history.

**Where it stands** (compiled builds, same script, BTN+/CW/MOVIES categories):

| Move | v0.41.1 (2026-10-19) | v0.41.23 | TiviMate |
|---|---|---|---|
| Guide 20×DOWN+20×UP | 98% janky, p50 250ms | **9.2%, p50 34ms**, p90 40 (v0.44.4) | 7.6%, 29ms |
| Guide 10×RIGHT+10×LEFT | 36%, p90 200ms | **8.4%, p50 30ms, p90 38** | 1%, p50 32, p90 36 |
| Categories 20×DOWN+20×UP | 69%, p50 200ms | **24-25%, p50 24ms, p90 57** (v0.41.25) | 7.5%, 18ms |
| Fullscreen → Back | 100%, p50 200ms | **first frame 174ms** (UI trace, v0.41.27; gfxinfo 39-61% / p50 34-65, few frames) | 15%, 7ms |
| OK → fullscreen, first frame | ~290ms (v0.41.16) | **~82ms** (UI trace, v0.41.27) | preview→fullscreen p50 57ms |

**Done tonight:** Compose 1.7 → 1.11 + toolchain (Kotlin 2.2, AGP 8.13, compileSdk 36) + Media3
1.10.1; guide rows own focus (TiviMate F14) with painted timeline + label; categories commit after
settling; one shared player (L5); PlayerView without Media3's controller; fixes for 4 regressions
the upgrade caused or exposed (paging crash, Back in fullscreen, Settings RIGHT, a category commit
loop); user bugs #3 (verified), #5, #7b, #8a.

**Next, in order:** (1) categories: each settled move still costs ~2 janky frames (commit
recompose + row rebuild ~140-170ms) vs TiviMate's one ~100ms frame - make the rebuild cheaper /
spread it (merging into one frame was tried and reverted, see F15); (2) OK → fullscreen first
frame (~230ms: first composition of PlayerScreen) - consider keeping it composed-but-hidden;
(3) sync-time I/O (category query 100-300ms during an EPG sync) - cache per-category channel lists;
(4) then the roadmap's next sprint (SEQUENCING.md / AGENTS.md).

**Always:** measure *compiled* (`compile_app` in tv-test.sh) and decide by interleaved A/B
(both builds at the same versionCode); run test helpers with `bash` (they use bash-only features).

## ⏸ OVERNIGHT RESUME HERE (written 2026-10-19 01:45 CDT, user asleep - autonomous until noon)

**Permission (user, 2026-10-19 ~01:45):** keep working through the salvage sprints on your own from
the 2 AM window, then 7 AM, until noon. Commit/push per the normal rules. **If someone is using
the TV, pause and let them watch** - check before every device step: `adb shell dumpsys window |
grep mCurrentFocus` and `dumpsys power | grep mWakefulness`. Awake + any app other than
`com.redsurf.tv` or the launcher (`com.spocky.projengmenu`, Google TV home) in front = in use →
stop device work, do code-only work, re-check later. **Asleep: do NOT send KEYCODE_WAKEUP or
POWER** - HDMI-CEC would switch the real TV on in the night. Do code + compile + unit tests then,
and leave device verification for when it's awake and idle.

**State at handoff:** v0.41.2 (CI run 146) released. On the TV: a local **v0.41.3 / versionCode
147** release build with the **uncommitted** batch 2 changes below (next CI run is 147 - one push
makes CI's v0.41.3 match). Uncommitted, compile-clean, partly device-checked:
1. `EpgGridColumn` - rows compose only the slots near the viewport (`composedSlotRange`, ±2
   columns + now-cell + jump target, keyed by `slot.start`).
2. `LiveTvScreen` - grid rows and their programmes are published together (no double build).
3. `EpgGridColumn` - UP/DOWN from the live cell anchors on *now* (user report: landing on an ended
   programme where OK only shows info). **Device-verified**: 8 DOWN steps stayed in the live column.
4. `GroupsColumn` - DOWN is deterministic like UP (targets exactly the next row; stays at the
   last row) - DOWN used to fall into the grid at the viewport edge.
5. `LiveTvScreen` - hero band fixed height, no scan-collapse animation, no per-move timestamp state.
6. `EpgGridColumn` - **`RowJump` pinned to the target channel's `streamId` and cleared once
   spent.** Prime suspect for "focus jumps into the guide while browsing Categories": a jump left
   from guide navigation was handed to the same row *index* in the next category, whose effect
   called requestFocus(). **Not yet device-verified** - first thing to test: start in Categories on
   US| KIDS NETWORK, guide 20×DOWN/20×UP, Back, Categories 20×DOWN/20×UP → must end on KIDS, and
   logcat must show no `claimGridFocus ... gridHasFocus=true` during the Categories run.

**Test discipline learned the hard way tonight:** always start measurements from the same category
(US| KIDS NETWORK - real listings, 32 channels; categories near ENTERTAINMENT/ESPN/FLO hold
500-1001 channels and skew everything). Cold launch resumes into the grid sometimes and
Categories other times - check `focused_info` before every run. `focused_info` was fixed to never
reuse a stale dump. Run test scripts with `bash -c` (the shell is zsh). Never put a heredoc and a
long device run in one command (a `cat <<EOF` hung once and blocked the run).

**Update 2026-10-19 15:00 (user):** autonomy extended, open-ended - one wake-up per 5-hour usage window (not hourly, user correction), through
Oct 20. **Superseded 22:35:** usage windows reset at 1:10, 6:10, 11:10, 16:10, 21:10 CDT; one-shot
wake-ups at :12 after each (precise - recurring jobs drift up to 30 min). Each wake-up first runs
`caffeinate -is -t 18900` (5h15m - overlaps into the next wake-up, which needs the Mac awake to
fire). **Root cause of every stall so far: the Mac idle-sleeps after 1 min** (`pmset -g log`).
The 21:12 wake-up is the last: it restores the Chromecast settings. Same TV-in-use rule. The overnight
stall: the Chromecast's own `sleep_timeout` is 20 min (screensaver at 10) - it slept mid-test.
User asked to set Settings → System → Power & Energy → "When inactive" → Never (or via adb:
`settings put secure sleep_timeout -1`; the original value was 1200000 - restore it when done).
Only one overnight wake-up did visible work; the others left no trace (cause unknown) - keep each
wake-up's log entry short but present so gaps are visible.

**Overnight progress log (append as you go):**
- 02:10 TV was in screensaver; started the item-6 verification, then the Chromecast went to full
  sleep mid-run (screensaver timeout) - run aborted, numbers discarded, TV left asleep (rule).
- Code-only while asleep (all compile-clean, **45/45 unit tests**, none device-verified yet):
  7. `SlotCell` is a plain focusable `Box` with its own OK / hold-OK handling instead of a
     neutralized tv-material `Surface` (which built animated scale/border/glow state per cell).
     **Device-check:** OK on live cell tunes, OK on a past archived cell plays catch-up, OK on a
     future cell shows info, hold-OK opens the favorites context menu, focus ring still draws.
  8. Grid publish fetches listings for the first 40 rows (+ the focus target's neighbourhood)
     before showing a category, the rest in 200-channel chunks after - big categories (500-1,001
     channels) no longer wait for every listing.
  9. `composedSlotRange` moved to `EpgSlots.kt` with 5 unit tests.
  10. Cursor channel/slot live in a `GridCursor` holder read only inside a new `LiveHero`
      composable - a grid cursor move recomposes the hero band, not the whole LiveTvScreen.

- 15:02-15:15 installed v0.41.3 (items 1-10), verification run produced nothing: after its force-stop
  + `monkey` relaunch RedSurf never came to the front and the script looped on the launcher ~10 min.
- **Crash captured by the new crash log**: 2026-10-19 01:54:59, v0.41.3, `IllegalStateException:
  Expected BringIntoViewRequester to not be used before parents are placed` (during the late-night
  category/grid tests). Theory: the stale RowJump (item 6) reached a row the lazy list had only
  *pre-built*, and its requestFocus() hit an unplaced node. Item 6 removes the trigger; item 11
  guards it.
- 17:59-18:00 someone browsed RedSurf on the TV (another playlist) - device work paused per rule.
- Unattended-run fixes (user-approved 18:00): `.claude/settings.json` allow rules added (pkill, ps,
  cat/head/tail/date, sips, apksigner, `bash scripts/*`, `bash <scratchpad>/*`) - a pending
  permission prompt is the prime suspect for the overnight stall; `autoContinueAtUsageLimit: true`
  in `.claude/settings.local.json`; Chromecast `sleep_timeout` set to -1 via adb (**restore
  1200000 when done**).
  11. `EpgChannelRow` only requests focus for a row jump once the row has been placed
      (`onPlaced`) - never on a pre-built row.
  12. `scripts/tv-test.sh`: `tv_in_use`, `redsurf_in_front`, and a fail-fast `start_at_category`
      (uses `relaunch` = `am start`, a step cap, and a stuck-focus check).

- 20:16 the 18:08 verification failed twice over: resume now lands in another playlist (the
  DOWN search never met KIDS NETWORK) and the 10-min screensaver took the screen. Disabling the
  screensaver then put the device to sleep (its screen-off timeout had long expired).
  13. `scripts/tv-test.sh` `goto_category_row N`: jumps to an exact Categories row using the
      `GroupsColumn: up/down -> N` log line, independent of where launch resumed (KIDS = 2152).

**⚠ CHROMECAST SETTINGS CHANGED FOR UNATTENDED WORK - RESTORE WHEN DONE (exact commands):**
```
adb shell settings put secure sleep_timeout 1200000        # was 1200000 (20 min)
adb shell settings put secure screensaver_enabled 1        # was 1
adb shell svc power stayon false                           # was 0 (off)
adb shell settings delete global hdmi_control_enabled      # was unset (= CEC on); set to 0 so a
                                                           # wake can't switch the real TV on
```
With CEC off the Chromecast remote won't power the TV or change its volume - restore before
the family needs it, and tell the user.

**When the autonomous run ends (user, 2026-10-19 22:40) - clean up everything it started:** `CronList`,
then `CronDelete` every remaining wake-up job (one-shots that already fired are gone; ones not
yet fired are not); `pkill caffeinate`; the Chromecast restore commands above. Report all three.

**Next, in order:** verify 6 (and 4) → measure Kids guide + Kids categories (compare with the
table below and TiviMate) → commit + push batch 2 (`perf:`) → then the remaining cost: the
double-height focused row relayout, draw-recording per frame, row composition (~58ms/row with real
listings; try a lighter cell than tv `Surface`) → then L5 (one shared player) and Media3 upgrade.
Record every result in this file and `TIVIMATE_LEARNINGS.md`.

## Batch 1 - shipped v0.41.2 (2026-10-19), measured on device

Done: R8 on (`-dontobfuscate`, line numbers kept), Compose strong skipping, persistent crash log
(`RedSurfApp` + `diag/CrashLog.kt`, read with `adb shell cat
/sdcard/Android/data/com.redsurf.tv/files/crash.log`), L6 (sync writes only changed channel rows,
in 500-row transactions, on background-priority threads - catch-up re-sync measured `changed=2`
instead of ~28K rows), L2 (key presses no longer recompose AppShell), L1 (focused category held
mid-list, TiviMate F8), Categories UP now focuses exactly the row above (the old 30ms
`moveFocus` retry overshot - two UPs went three rows, sometimes out into the grid), L3 (grid-entry
claim on the next frame instead of after 300ms). 40/40 unit tests pass.

| Move (same script as the baseline) | v0.41.1 | v0.41.2 | TiviMate |
|---|---|---|---|
| Categories 20×DOWN, 20×UP | 69% janky, p50 200ms | 58%, **p50 53ms** | 7.5%, 18ms |
| Guide 20×DOWN, 20×UP | 98%, p50 250ms, p90 500 | 59%, **p50 69ms**, p90 150 | 7.6%, 29ms |
| Guide 10×RIGHT, 10×LEFT | 36%, p90 200ms | 27%, **p90 53ms** | 1%, p90 36ms |

**Still open - batch 2, in this order (one root under most of it):**
1. **Grid row composition cost (L4) - the root.** Trace of 12 DOWN presses: `lazylist:prefetch:compose`
   ~48ms per new row, layout ~12ms/frame, draw recording ~9ms/frame, and `Recomposer:animation`
   712ms (an animated value read in composition rather than only at draw - the wash and/or the
   double-height row). Entering a category composes all its rows: 41 rows ≈ 2s of main thread.
2. **Quick RIGHT after moving through categories leaves no focused node** (3/3 repro): the held-RIGHT
   path waits for the rows, but they take ~2-3s to compose and the claim doesn't land. Settled RIGHT
   lands correctly on the now cell. Should largely fall out of (1); re-test after.
3. **Focus flapping inside the grid while scrolling** - `claimGridFocus` fired 6× with focus already
   in the grid (focus left and came back). Suspect: the focused cell is replaced when the
   programme list arrives after the channel rows (gap slot → programme slots).
4. Fast 20×DOWN/20×UP in Categories still ends one row off (was two).
5. Cold launch: which column takes focus (Categories vs the resumed grid row) varies run to run.

## Batch 2 - v0.41.3 (2026-10-19), measured on device

Done: horizontal cell virtualization (`composedSlotRange`, cells keyed by start time); rows published
together with their programmes (first 40 + the focus target first, then 200-channel chunks);
UP/DOWN from the live cell stays on "now"; Categories DOWN deterministic, stays put on the last row;
fixed-height hero (no animated height); `GridCursor` + `LiveHero` so cursor moves recompose only the
hero; `RowJump` carries the streamId and is consumed once (the stale jump was the suspect for focus
leaping into the grid while browsing Categories, and for the 01:54 `BringIntoViewRequester` crash);
rows claim jump focus only after `onPlaced`; `SlotCell` is a plain focusable Box with its own OK /
hold-OK. 45/45 unit tests.

Device check (relaunch-resume into "US| CBS", row 816; script `verify6.sh` pattern, see below):
- Categories 20×DOWN + 20×UP: **40 moves for 40 presses, ended on the starting row**; grid focus
  steals during the run: **0**. No new crash (crash.log still the 01:54 entry).
- Guide 20×DOWN + 20×UP returned to the same cell.

| Move | v0.41.2 | v0.41.3 | TiviMate |
|---|---|---|---|
| Categories 20×DOWN, 20×UP | 58%, p50 53ms | **50%, p50 48ms**, p90 250 | 7.5%, 18ms |
| Guide 20×DOWN, 20×UP (no-listing group) | 59%, p50 69ms | **51%, p50 48ms**, p90 101 | 7.6%, 29ms |

**Not yet device-checked:** `SlotCell` OK paths (live cell tunes, archived past cell plays catch-up,
future cell shows info, hold-OK opens favorites); guide timings on a group with real listings
(CBS shows "No listings"). Still far from TiviMate: next is row composition cost (double-height row
relayout, draw cost, a lighter row), then L5 (one shared ExoPlayer), then Media3.

## Batch 3 - v0.41.4 (2026-10-19 night), measured on device

Trace of 12 guide DOWN presses (atrace, UI thread, double-height on) found layout dominated:
the focused row grew a frame *after* focus landed, so every press laid the list out twice; and
with no layers, any redraw (the Wash, a marquee, a scroll step) re-recorded the whole grid.
Double-height off cut layout from ~1.07s to ~0.41s - so the row stays (TiviMate option) but:
- the row grows on the key press itself (`cursorRow`, set with the jump), one layout pass;
- each guide row and every marquee gets its own `graphicsLayer`;
- UP/DOWN in Categories and the guide move from the previous press's *target*, not from where focus
  currently is - fast presses were re-targeting the same row (log: "down -> 819" twice; 20+20 ended
  a row off). Now 815→835→815 exactly (TiviMate F9 parity);
- Teleport logs `Teleport: open/close/selected <row>` - uiautomator can't see the overlay, so test
  scripts drive it from logcat (Teleport → Nav-Strip → Settings verified by script).

| 12 guide DOWNs, UI thread | v0.41.3 | v0.41.4 |
|---|---|---|
| frame work total | 2452ms | 1955ms |
| measure/layout | 1073ms | 607ms |
| text layout | 80ms | 25ms |
| new-row compose (prefetch) | 297ms | 104ms |

gfxinfo (BTN+ PPV, no listings): Categories 47.7% janky p50 42ms (was 50%/48); guide 55.7%/53ms
(noisy vs v0.41.3's CBS run, different category). Still ~40ms/press of untraced frame-callback work
(the lazy list re-laying out per scroll-animation frame). Next big lever: Compose 1.6 → current
(the TV alpha lists are only in 5 files) together with the toolchain bump Media3 1.10+ needs.

## Compose 1.7.8 - v0.41.5 (2026-10-20 00:10)

Compose 1.6 (alpha tv-foundation lists) → BOM 2025.02.00 (Compose 1.7.8, material3 1.3.1,
tv-material 1.0.1); `TvLazyColumn/Row` → foundation `LazyColumn/Row` (5 files). 1.7.8 is the newest
line on this toolchain: Compose 1.12 needs compileSdk 37 + AGP 9.1, Media3 1.10+ compileSdk 36,
tv-material 1.1 compileSdk 35 + AGP 8.6 (read from each AAR's aar-metadata, not guessed).

**Measure compiled, always:** right after `adb install` the app runs uncompiled ("Skipping profile
installation") - v0.41.5 measured 69% janky p50 113ms uncompiled vs 48%/42ms after
`adb shell cmd package compile -m speed-profile -f com.redsurf.tv`. Users hit the uncompiled
state after every OTA update until Android's idle-time dexopt runs - a real "choppy after update"
cause; a real app baseline profile (TV is API 34, generation works unrooted) is a follow-up.

| Compiled, BTN+ PPV | v0.41.4 | v0.41.5 |
|---|---|---|
| Guide 20×DOWN+20×UP | 58% janky, p50 61ms | **44.8%, p50 42ms**, p90 85 |
| Categories 20×DOWN+20×UP | 51.8%, p50 53ms | **48.2%, p50 42ms** |

40/40 category presses land; Settings navigates. TiviMate: 7.5%/18ms, 7.6%/29ms.

## Toolchain + Compose 1.11 + Media3 1.10.1 - v0.41.6 (2026-10-20 00:40)

Gradle 8.7→8.13, AGP 8.2.2→8.13.2, Kotlin 1.9.22→2.2.21 (+ Compose compiler plugin; strong
skipping now default), KSP 2.2.21-2.0.5, Room 2.6.1→2.7.2 (first with KSP2; same schema),
compileSdk 34→36 (targetSdk unchanged 34), Compose BOM 2026.05.01 (Compose 1.11.2, material3
1.4.0), **Media3 1.2.1→1.10.1** (TiviMate's version, S1). Playback verified on device: READY,
position advancing 5s/5s, `c2.amlogic.avc.decoder`, audio focus held, 0 player errors.

Also found + fixed: since Compose 1.7 a plain scroll container on a TV device pivots the focused
child to 30% of the viewport - the guide timeline put "now" a column off the left edge on entry
(x=1163 = 880 + 0.3×944, intermittent) and would shift the hours on every RIGHT. The timeline now
uses a minimal `BringIntoViewSpec`; entry lands at x=880 every run, 10×RIGHT+10×LEFT returns to the
start cell, 3×RIGHT moves without scrolling.

A/B, both compiled, both built as versionCode 150, alternated twice (BTN+ PPV):

| | A: v0.41.5 code | B: v0.41.6 |
|---|---|---|
| Guide 20×DOWN+20×UP | 59.3% / 62.2% janky, p50 81 / 81ms | **46.5% / 44.3%, p50 48 / 46ms** |
| Categories 20×DOWN+20×UP | 66.2% / 71.3%, p50 61 / 113ms | **46.0% / 36.6%, p50 40 / 34ms** |

(A's numbers here are worse than its single run earlier - run-to-run noise is large on this TV, so
only interleaved A/B runs count.) TiviMate: 7.5%/18ms, 7.6%/29ms.

**v0.41.6 crash, fixed in v0.41.7 (2026-10-20 01:00):** Room 2.7.2 pulled paging-common 3.3.2
under paging-compose 3.2.1 → `NoClassDefFoundError: androidx.paging.LoggerKt` when the fullscreen
channel list opened (RIGHT in fullscreen). paging-compose → 3.3.6; verified: the list opens, no crash.
Lesson: after a toolchain bump, check `:app:dependencies` for libraries we pin older than what a
dependency pulls in. **Still to do from the OK-path check:** future-cell info / past-cell catch-up /
hold-OK weren't reached (the crash ended the run) - first OK on the live cell tuned (BUFFERING→READY)
and went fullscreen. After the crash the script's leftover keys went to the Projectivy launcher's
settings (LEFT×3, OK on "Utility Apps", Back, hold-OK, Back) - probably nothing changed; tell the user.

## Row focus (TiviMate F14) - v0.41.8 (2026-10-20 02:40)

Found by trace: on this TV Projectivy Launcher's accessibility service is always on, so Compose
runs `checkForSemanticsChanges` / `sendAccessibilitySemanticsStructureChangeEvent` on every change
(~800ms per 12 guide presses). TiviMate's uiautomator tree showed why it pays so little: its guide
focuses whole channel rows (16 focusables on screen, cells are plain TextViews) where RedSurf made
every programme cell focusable (51). Rebuilt `EpgChannelRow` the same way: the row is the one focus
target; LEFT/RIGHT move a selected programme (tracked by start time, so late listings can't shift
it); UP/DOWN keep the time-anchored jump; OK/hold-OK act on the selection; `SlotCell` is drawn only;
one accessibility node per row ("num name · programme"), none for the time ruler. Semantics work
in the trace: 804 → 425ms.

Device-verified: entry on the programme airing now; RIGHT/LEFT step programmes; DOWN/UP move rows
keeping the time; OK on a future programme doesn't tune; hold-OK opens "Add to Favorites"; Back keeps
the row. No crash. A/B (both compiled, both versionCode 152, alternated twice, CW category with
listings):

| | A: per-cell focus (v0.41.7) | B: row focus (v0.41.8) |
|---|---|---|
| Guide 20×DOWN+20×UP | 46.6% / 41.4% janky, p50 48 / 44ms, p90 97 / 89 | **23.7% / 25.5%, p50 36 / 38ms, p90 65 / 69** |
| Categories 20×DOWN+20×UP | 40.1% / 40.1%, p50 38 / 36ms | **34.6% / 34.1%, p50 30 / 34ms** |

TiviMate: guide 7.6% / 29ms, categories 7.5% / 18ms.

Test tooling this session: `tv_in_use` wakes a sleeping Chromecast only while HDMI-CEC control is
off; `redsurf_in_front` retries a transient null focus; `focused_info` reads the focused node's own
label first. Note: `adb install` of RedSurf brings the *next* task to the front (e.g. a TiviMate left
open) - relaunch after every install.

## Categories focus + painted timeline - v0.41.9 / v0.41.10 (2026-10-20 03:00)

- v0.41.9: category rows switch their focus style instantly (same 1.04 scale / accent border /
  glow / raised fill) instead of tv-material's animated Surface; one accessibility node per row.
- v0.41.10: the guide's timeline is **painted** - one `drawBehind` per row draws the slots in view
  (ticks, gap dashes, supplement tint, now/selected fills, the Wash, sticky titles, the times line)
  with a grid-wide `TextMeasurer` (cache 256), translated by the shared scroll. No composed cells:
  a category move used to build ~10 composed cells per row (trace: ~350ms frames inside
  `measureAndLayout`); selection and horizontal scroll now only redraw the row's layer.
  Screenshot-checked: identical look.

A/B, both compiled, alternated twice (CW category with listings), janky frames as counts too -
removing animation frames shrinks the denominator, so the % alone misleads:

| | A: composed cells (v0.41.9) | B: painted (v0.41.10) |
|---|---|---|
| Guide 20×DOWN+20×UP | 22.2% (118) / 21.6% (117), p90 69 / 65ms, p95 125 / 121 | **13.8% (83) / 14.6% (87), p90 44 / 46ms, p95 65 / 69** |
| Categories 20×DOWN+20×UP | 67.9% (126) / 65.1% (113), p90 300 / 350ms, p95 450 / 550 | 61.2% (127) / 59.1% (127), **p90 200 / 200ms, p95 300 / 250** |

Categories are now the gap (TiviMate 7.5%, 18ms): each move still rebuilds the guide's rows for
the new category (~8 rows, label + logo + painted Box) after the 200ms debounce.

## Categories: commit after settling, synchronous moves, held-RIGHT fix - v0.41.11 (2026-10-20 03:10)

Frame-by-frame trace of category moves: each move = a ~90ms recomposition frame (the browsed
group was written to AppShell state on every row, re-running the whole Live TV screen) plus a
~250-330ms row-rebuild frame after the debounce.
- The group is committed to AppShell only after focus rests on it for `GROUP_COMMIT_MS` (200ms,
  the old query debounce) - or at once on RIGHT / leaving the column; the guide query then runs
  without a second delay. In-between categories are no longer queried at all.
- GroupsColumn UP/DOWN move focus inside the key press when the target row is on screen (each row
  registers its own requester); the frame-later path remains for rows not yet composed.
- **Bug fixed (open since 2026-10-19, "quick RIGHT after a category flight leaves no focused
  node"):** the held-RIGHT effect was keyed on `pendingGridEntry` and cleared it first - which
  restarted the effect and cancelled the focus claim. 3/3 now: DOWN×3 fast + RIGHT enters the guide
  on the right category's first row.
- Guide logos: `AsyncImage` instead of `SubcomposeAsyncImage` (Coil: not for lazy lists); rows
  share one `contentType` so they recycle across categories (no measurable change alone).

A/B vs v0.41.10, compiled, alternated twice: guide janky 14.2% (85) → **11.3-11.6% (69-71)**;
categories p50 81 → **57ms**, janky 60% → **52-53%** (count ~equal; p90 200ms unchanged - the
row-rebuild frame is the remaining categories cost).

## Painted channel label + trace markers - v0.41.12 (2026-10-20 03:25)

Trace markers (`diag/Traced.kt`, `traced("name") { ... }` - free when not tracing) around
LiveTvScreen, LiveHero, GroupsColumn, EpgGridColumn, each guide row (`EpgRow`, `EpgRow:label`,
`EpgRow:measure`). Frame trees (scratchpad `frametree.py`) of a category move's rebuild frame:
row composition ~50ms of which the label 37ms (its Coil logo 25ms), label text measuring ~31ms.
The label is now painted too: number, logo chip (bitmap from `ChannelLogos`, an LruCache filled
through Coil's own loader), name (1-2 lines), ▶ / catch-up glyphs - only the focused single-height
row's name is still a composed marquee Text. Screenshot-checked (the full channel number now
shows; the composed Text used to clip it to 4 digits).

A/B vs v0.41.11, compiled, alternated twice: guide janky 14.0% (84) / 12.9% (79) → **11.5% (71) /
10.6% (64)**; categories p90 200 / 200ms → **133 / 150ms**, p95 300 / 250 → **200 / 200**.

## v0.41.13-v0.41.14 and a reverted experiment (2026-10-20 03:30-04:20)

- v0.41.13: Teleport rows built only while the menu is open (AppShell rebuilt targets scanning
  ~2,200 categories + a fresh row list every recomposition); hero mark decoded once. Commit-frame
  recomposition 82 → 56ms in the trace; gfxinfo within noise.
- v0.41.14: `stableCallback` (ui/StableCallback.kt) for the guide's callbacks - fresh lambdas made
  every visible row recompose on each commit; the guide strips the prefix of the group it holds.
  Categories p50 53 → 47ms; rest within noise.
- TiviMate F15 (atrace): one 85-123ms frame per category move (its own ~200ms debounce, then RV
  rebind). Tried merging our commit + rebuild into one frame: UI thread better, gfxinfo worse 4/4
  → reverted (see F15).

**Measurement note:** gfxinfo janky counts and UI-thread trace totals can disagree (RenderThread
work: new rows' display lists and text glyphs). Decide on interleaved gfxinfo A/B; use traces to
find causes.

## Back out of fullscreen fixed; category commit loop fixed - v0.41.16 (2026-10-20 04:30)

- **Bug (since the Compose 1.11 upgrade, v0.41.6):** with no player overlay open, Back did nothing
  in fullscreen - a Back reaching the focus-trapped player root (`focusProperties { exit = Cancel }`)
  no longer fell through to the `BackHandler`; it only worked while a tile held focus. The root now
  peels on Back itself (`peelBack()`, shared with the BackHandler, which stays as a fallback).
- **Bug (from v0.41.11's settle-commit):** focus falling onto a category row (leaving fullscreen,
  or guide rows replaced while focused) scheduled a commit; the reload dropped focus onto the next
  category row → commit → ... (log: GOLF → HBO MAX → KIDS → MATCHROOM with no key pressed). Commits
  now happen only for focus moving *within* Categories (`categoriesHaveFocus` gate).
- Device-verified: fullscreen → Back returns to the watched channel's row (no commits); overlay →
  Back → Back exits; settled DOWN×2 + RIGHT and fast DOWN×3 + RIGHT land in the right category.
- New logs: `LiveTvScreen: commitGroup <name> <- caller`, `AppShell: selectedGroup <- <name> caller`.

| Move (compiled) | v0.41.1 | v0.41.16 | TiviMate |
|---|---|---|---|
| Fullscreen → Back to guide | 12 frames / 100% / p50 200 | **20-21 / 40-43% / p50 25-26** | 79 / 15% / 7 |
| OK → fullscreen | 106 / 6.6% / 19 (different path) | 4-5 / 80-100% / p50 81-150 | (preview → fullscreen) 36 / 28% / 57 |

Next: OK → fullscreen (one very heavy frame), then L5 (one shared player).

## PlayerView without Media3's controller - v0.41.17 (2026-10-20 04:50)

Trace of OK → fullscreen: one 290ms frame; `inflate` 31ms of it was Media3's `PlayerControlView`
(ImageButtons, a RecyclerView) - built whenever the PlayerView layout has
`exo_controller_placeholder`, regardless of `useController = false` (read from Media3 1.10.1's
bytecode). Both hosts now inflate `res/layout/redsurf_player_view.xml` whose `player_layout_id` is a
trimmed copy of `exo_player_view.xml` (content frame, shutter, subtitles, overlay). Inflate 31 →
10ms, the OK frame 290 → 229ms (trace). Playback verified (hardware AVC decoder, audio focus, no
errors). Remaining in that frame: first composition of the player screen (~62ms) and its first
layout/draw (~200ms incl. text). Fullscreen → Back: 36-41% janky, p50 25-27ms.

## L5 done: one shared player for preview and fullscreen - v0.41.18 (2026-10-20 05:00)

PREVIEW.md's two players (a minimal preview ExoPlayer and the fullscreen PlayerController,
"mutually exclusive in time") were torn down and rebuilt on every switch: a fresh connection and
re-buffer each time, and a moment with two provider connections where the provider allows one
(IPTV_DOMAIN_KNOWLEDGE §10, HTTP 456). Now Live TV owns ONE `PlayerController` (keyed on the playing
playlist's User-Agent): fullscreen's PlayerHost drives zaps/catch-up; otherwise Live TV plays the
previewing channel or `stop()`s (new: drops the stream without releasing the player). The hero shows
it through `SharedPreviewHost` (display only); views hand the surface over on release.

Device-verified (positions from PlayerController's watchdog log): OK → fullscreen plays; Back →
preview keeps the same stream (position 6.3s → 14.4s, one brief BUFFERING while the surface
moves, no reconnect); OK → fullscreen again: no state change at all, position continuing; zap UP in
fullscreen starts the next channel; Back previews that zapped channel with no state change. No
crash. `PreviewPlayerController` is now unused by Live TV (kept for now).

## Settings RIGHT fixed; user bug #3 verified - v0.41.19 (2026-10-20 05:20)

- **Bug (since the Compose 1.11 upgrade):** RIGHT from the Settings rail went to the nav strip's
  Live TV pill for every category - no way into a pane with RIGHT. The rail now focuses the pane's
  first row explicitly (FOCUS_MODEL "deterministic, never spatial"). Device-verified for every
  category with content; LEFT returns to the rail; empty categories keep focus on the rail.
- **User bug #3 (OK to preview, then OK to fullscreen) - verified working** with "Preview channel on
  select" turned on for the test (then restored to Off, the user's setting): OK previews and stays in
  the guide; OK again goes fullscreen on the same stream (position continuous); Back returns to the
  preview with no state change; OK on another row previews that channel. User bug #4 (fullscreen →
  preview choppy) is covered by the same shared player + v0.41.16's Back fix.
- Tooling: `toggle_setting.sh <Category> <row label>` (scratchpad) flips any Settings row via
  Teleport → Nav-Strip → Settings; note OK on the Settings pill leaves focus on the pill (one DOWN
  enters the rail) and the rail opens on the last-used category.

## L6 measured; sync yields to input - v0.41.20 (2026-10-20 05:35)

During an EPG sync ("Update EPG now"): guide 20×DOWN+20×UP 15.4% janky (96) vs ~11% (64-72)
idle; the category query `gridQuery` took 134-683ms vs ~5ms idle. `sync/UserActivity` (AppShell
stamps every key press; `awaitQuiet()` waits up to 10s while input is recent) now gates each XMLTV
flush. Re-measured during a sync: guide 12.3% (76); but query times unchanged (100-325ms) - the
`epg-sync` thread sits in state D (storage I/O) downloading/reading the feed, so the flushes were
only part of it. Next candidates if this matters to the user: cache per-category channel lists in
memory; stream-parse without staging the feed on disk. Syncs run daily, so lower priority than
the steady-state numbers.

## User bug #7b done - v0.41.21 (2026-10-20 05:30)

The fullscreen info overlay (zap banner / Controls) said "No schedule information" for every
channel - hard-coded. It now shows the programme airing now (title, time range, progress bar,
minutes remaining - the hero's `ProgrammeProgressRow`), looked up through the repository and
re-checked every 30s. Screenshot-verified ("What Ever Happened to Baby Jane?", 5:10-8:05 AM, 159
min remaining); falls back to "No schedule information" when there is none.

## User bug #8a done - v0.41.22 (2026-10-20 05:45)

Live TV says when the guide is refreshing: "Updating…" right-aligned in the Categories header
while any playlist's EPG sync runs (`EpgSyncScheduler.anyRunningFlow` - WorkManager's unique-work
flows, no new tags needed). Screenshot-verified during a real sync. Note: "Update EPG now" re-queues
with the scheduler's 45s initial delay, so the note appears ~30-45s after pressing it.

## User bug #5 addressed - v0.41.23 (2026-10-20 05:50)

Public-guide (EPGSHARE01) programmes were too subtle to notice: the cool tint doubled (7% → 14%)
and a small sky-blue dot drawn in each such programme's top-right corner - a shape cue alongside the
dashed start tick, not colour alone (AGENTS.md indicator decision). Screenshot-checked. The hero's
"Guide: EPGSHARE01 (public)" line remains the per-programme explanation; a separate legend was not
added (user to judge whether it's still needed).

## Categories glide like TiviMate's list - v0.41.25 (2026-10-20 06:35)

TiviMate renders ~15 cheap frames per category move (601 / 40 moves) because its list *slides*
under the fixed focus row; ours snapped (`scrollToItem`) - one heavy frame, and Compose's own TV
bring-into-view (30% pivot) scrolled the list a second time toward a different spot on every move.
Now `holdMidList` animates steps of up to 3 rows (`animateScrollToItem`; long jumps still snap)
and the category list has a no-op `BringIntoViewSpec` (holdMidList is its only positioner).

A/B vs v0.41.24, compiled, alternated twice: categories 179-192 frames / 63-64% janky / p50 57ms /
p90 133-150 → **503-520 frames / 24-25% / p50 24ms / p90 57ms**; guide 11-12% (unchanged/slightly
better). Honest reading: the heavy reload frames remain (janky *count* 115-120 → 125-128) - the
list now moves smoothly between them, which is TiviMate's pattern. 40/40 presses still land;
settled and fast moves + RIGHT land on the right category.

## Fullscreen entry/exit no longer re-lay out the whole UI - v0.41.27 (2026-10-20 06:45)

Frame trees of OK → fullscreen and Back (with new `tracedMeasure` markers on every big part)
showed the *hidden browse screen* re-measuring everything - guide 64-94ms, categories, hero, nav
strip, all their text. Causes: (1) AppShell dropped the TV safe-area padding while fullscreen, so
the whole UI changed size on entry and on exit; (2) the nav strip was removed in fullscreen and
rebuilt on exit (~75ms: composition, text, the mark's bitmap decode). Now the padding is constant
and only the player steps outside it (`fillWindow`: window-sized, placed at the window origin); the
nav strip stays composed, collapsed to 0 height. Also retired the unused `PreviewPlayerController`.

First-frame traces (UI thread): OK → fullscreen 190-226ms → **81-84ms**; fullscreen → Back
421ms (v0.41.25) → 260-303 (nav fix) → **173-175ms**. gfxinfo with 4-23 frames is noisy: OK
p90 250-350 → 85-89ms; Back 39-61% janky p50 34-65ms (single runs, mixed vs 36-43%/25-27 before).
Screenshot-checked: the fullscreen overlay sits at the same full-window positions; Back returns to
the watched row; the nav strip works after exit (UP reaches the Live TV pill).

## Regression sweep on the real v0.44.4 release (2026-10-20 07:15)

Installed the CI artifact (not a local build) and re-ran everything: guide 20×DOWN+20×UP **9.2%
janky, p50 34ms, p90 40** (TiviMate 7.6% / 29ms); categories **24.7%, p50 25ms**, 40/40 presses
land, 0 focus steals; focus paths (guide ↔ nav strip ↔ categories, fullscreen, controls, channel
list) clean; Settings RIGHT/LEFT for every category; Teleport - Playlist Root (playlist header),
Root Category ("US| 24/7 ACTION/ADVENTURE", first of the US| family), Last Channel Group ("WT|
WORLD CRICKET"), Now Playing (the played channel's row), Return to fullscreen - all land correctly.
No new crash since 00:54 (the paging crash fixed in v0.41.7).

**Tried and reverted (2026-10-20 07:40):** passing Live TV's selected group / focused channel as
providers (so a category commit would recompose Live TV but not all of AppShell), plus observing
the last-watched effect via `snapshotFlow`. Interleaved A/B, both compiled, twice: categories got
*worse* (22-24% → 32-33% janky, p90 48-53 → 101-105ms); guide unchanged. Cause not found - not
worth keeping blind. TV back on the CI v0.44.4 artifact.

**Test navigation (user, 2026-10-19):** never walk hundreds of rows. Relaunch resumes the last
*played* channel's category; Teleport (long-press Back) for other jumps. `scripts/tv-test.sh`
`watch_human`/`human_pressed` stop a run within ~10s of a real remote press (getevent sees
kernel input; `input keyevent` doesn't show there) - verified with a real Home press.

## Leads already confirmed by reading code (2026-10-05) - verify, then fix

- **L1 - Categories "selection always at the top" (user bug 2).** `GroupsColumn.kt:274`:
  `LaunchedEffect(focusedIndex) { listState.scrollToItem(it) }` pins the focused row to the *top*
  on every move (added 2026-09-17 to stop scroll lag under key repeat). Wrong UX *and* a scroll +
  relayout per key. Fix: only scroll when the focused row would leave the viewport, by the minimum
  amount (or restore normal bring-into-view with a non-animated spec).
- **L2 - App-wide recomposition on every key press (prime suspect for "all menus choppy").**
  `AppShell.kt:431-432` writes `lastKeyWasUp` / `lastKeyAt` (Compose state) on *every* KeyDown -
  added 2026-09-30 for nav-strip auto-hide and escape-to-pill. Any state read in `AppShell`'s
  composition scope recomposes the whole shell per key. Fix: plain non-state holders (a `remember`ed
  object / `MutableLongState` read only inside the effect via `snapshotFlow`), so key presses never
  invalidate composition.
- **L3 - RIGHT into a channel lands on the first (6h-past) cell (user bug 2b).** Default focus search
  enters the grid at the spatially-nearest = leftmost cell; `claimGridFocus()`
  (`LiveTvScreen.kt:545`) then returns immediately because "grid already has focus". Fix: on grid
  entry from Categories, always redirect to the now-cell of the target row (entry-redirect, rule 1 of
  FOCUS_MODEL). Past only when the user deliberately presses LEFT inside a row (user's rule).
- **L4 - Grid cost.** 12h window → up to 24 half-hour cells per row, each a tv `Surface` with
  `drawBehind`, `onFocusChanged`, `onPreviewKeyEvent`, an `offset{}` sticky-title lambda; ~10 rows
  visible plus double-height relayout of the focused row on every vertical move; hero band
  recomposes on every cursor change. Candidates: draw empty half-hours as one non-focusable lattice
  and only create focusable cells lazily / for listed programmes; make double-height a draw-time
  change rather than a layout height change, or animate it off the main path; narrow hero reads.
- **L5 - Preview ↔ fullscreen (user bugs 3/4, possible crash).** Frame-grab (PixelCopy) + overlay +
  starting the other ExoPlayer + releasing the first, all on the same frames; provider
  `max_connections=1` means the second player can't connect until the first releases. Check whether
  OK→OK fails because promote waits on the PixelCopy suspend or the preview's channel identity check
  (`isSameChannel`). Strong candidate for the real fix: **one shared ExoPlayer** moved between the
  hero surface and the fullscreen surface (the "true" version deferred in EPG_WRAPUP 2.4) - removes
  the reconnect, the black gap, and the frame-grab entirely.
- **L6 - Background work contention.** EPG sync + public supplement (three playlists, large XML
  parses, ANALYZE) runs right after launch and on "Update now"; check frame times *during* a sync.
  Consider: run syncs only when idle / charging-equivalent, lower thread priority, defer public
  matching.

## User bug list (2026-10-05), ordered for execution

| # | Item | Notes / suspected cause |
|---|---|---|
| P0 | Performance + choppiness everywhere (user #1, #8) | Leads L2, L4, L6; Opus plan + measurements first |
| P1 | Categories selection always jumps to the top (user #2) | L1 |
| P1 | RIGHT lands on the first past cell instead of "now" (user #2b) | L3 |
| P1 | OK to preview, then OK to fullscreen doesn't work (user #3) | L5 - reproduce with "Preview channel on select" **on** (it was off on 2026-10-05; the user may have turned it on) |
| P1 | Fullscreen → Back to preview is choppy (user #4) | L5 |
| P1 | Possible crash in preview↔fullscreen | `adb logcat -b crash -d` first thing next session |
| P2 | Erratic scrolling/focus between Categories and the grid (user #6) | Partly L1/L3; re-sweep FOCUS_MODEL |
| P2 | Channel logos missing in the guide (user #7a) | Check `ChannelLogoChip` / `streamIcon` for these providers (letter fallbacks seen on US playlists; Africa had logos 2026-09-21) |
| P3 | Provider vs public indicator not noticed (user #5) | It exists (cool tint + dashed start tick on public cells; "Guide: EPGSHARE01 (public)" in the hero) but is too subtle - only seen on THE FIRST TV in testing. Make it legible (stronger tint, small corner glyph, a legend) |
| P3 | Channel overlay card should show the current programme (user #7b) | TiviMate's channel banner does show the programme now airing with a progress bar; add to `PlayerInfoBlock`/zap banner |
| P3 | "Updating EPG/playlist" indicator on Live TV (user #8a) | Small top-corner spinner/text while EpgSyncWorker runs (WorkManager `getWorkInfosByTagLiveData`) |

## Answers already given to the user (don't re-ask)
- #5: indicators exist but are too faint - planned P3 fix above.
- #7b: yes, TiviMate's overlay shows the current programme - planned P3.
