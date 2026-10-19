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
