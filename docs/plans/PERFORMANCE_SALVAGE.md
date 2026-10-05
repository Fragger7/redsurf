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
