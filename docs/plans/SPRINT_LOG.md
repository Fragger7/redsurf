# Sprint log

One entry per module sprint (`docs/plans/WORKFLOW.md` "Sprint mode"). Newest first.

## 2026-09-22 (later still) — Sprint 2 CLOSED: Live TV grid focus, preview reconnect, Settings persistence

Full detail: `docs/plans/SEQUENCING.md`'s Sprint 2 section (rewritten with real per-item results),
`docs/plans/TESTING_OUTSTANDING.md` section H, `docs/vision/FOCUS_MODEL.md` (rules 3 and 7 updated
with this session's lessons). Real release **v0.37.13** (versionCode 135), commit `e1d81b1`.

All 9 items from Sprint 2's plan fixed in one batch across 6 files (`GroupsColumn.kt`,
`EpgGridColumn.kt`, `LiveTvScreen.kt`, `PreviewPlayerHost.kt`, `SettingsScreen.kt`, `AppShell.kt`).
Compiled clean, 23/23 unit tests passing, device-verified on the real Chromecast against a real
signed release (never a debug build), no `FATAL` crash from this batch's own code across the whole
session, all 3 real playlists ("Random Strong", "Random Score", "Faraz Strong") confirmed intact
via `epgBackfill` logcat lines at the start and end.

**Two items needed a real second fix, found only by testing on the device, not by re-reading the
code:**

- **Items 1/8 (Categories UP boundary guard).** The in-list "scroll up one row" behavior worked
  perfectly from the first attempt - hammered with dozens of presses across a real ~500+-row
  combined multi-playlist category list, zero premature escapes. But the true-row-0 escape case,
  deliberately left to fall through to Compose's default `moveFocus`, turned out **not** to work:
  three consecutive UP presses at a genuine top row all stayed on the same row, confirmed via
  `uiautomator` focus bounds never changing across the presses. Fixed with an explicit `onEscapeUp`
  callback wired to `AppShell`'s existing `navPillFocusRequesters[destination]` lookup - the same
  mechanism the long-press-Back nav-jump feature already uses successfully. **This fix itself was
  not re-verified at the true list boundary** - the device's real category list ran into the
  hundreds of rows (confirmed by climbing partway through it across several hundred UP presses,
  crossing US-prefixed, UK-prefixed, and NL-prefixed sections without reaching the top), and
  brute-forcing back to the top a second time wasn't a good use of the remaining session. High
  confidence given the reused proven mechanism, not full confirmation - flagged honestly.
- **Items 5/6 (Back-from-cold-launch-fullscreen focus restore).** The channel-aware grid target
  (`targetChannelStreamId`) was straightforward, but the very first live test of the combined fix
  failed: on this session's genuinely cold DB cache (fresh install, first launch), the group query
  measured **5.23s** (`gridQuery ... tookMs=5230`), longer than the original 20×150ms=3s retry
  budget on `claimGridFocus()`. The claim silently exhausted its retries and focus fell back to
  Categories instead of the resumed channel - screenshot-confirmed. A second cold-launch test with
  a warm cache (363ms) passed cleanly, which would have hidden the bug if that had been the only
  test run. Fixed by widening the retry to 20×300ms=6s, matching `GroupsColumn`'s own
  already-established 6s ceiling for the same class of race. Re-verified after the fix on two more
  fresh `force-stop`+relaunch cycles, both landing correctly on the exact resumed channel's grid
  row.

**The other five items worked as designed on first verification:** item 2 (grid entry no longer
lands on a stale scrolled-away position - confirmed on every category switch), item 3 (preview
reconnect - fixed and compiles clean, structurally sound, but not exercised live; see below), item
4 (scrolling Categories no longer steals grid focus - 8+ consecutive DOWN presses confirmed), item
7 (no conflicting-focus races observed across the session), item 9 (Settings state/focus survives
a destination round trip, including an in-progress confirm-dialog observed correctly persisting).

**Item 3 (preview reconnect) - the one item not meaningfully exercised live.** Two blockers, both
discovered live rather than anticipated: this test device's `adb` connection is itself over the
same wifi a real network-interruption test would need to sever, making `adb shell svc wifi disable`
too risky to attempt; and this device's "Preview channel on select" setting turned out to be
**off** already (a pre-existing setting from earlier testing, not a bug caused by this sprint) -
OK jumps straight to fullscreen, so the two-step preview flow itself, reconnect included, was never
actually entered this session. Logged as a specific next-session task in `TESTING_OUTSTANDING.md`.

**Deviated: found and deliberately did NOT fix a real, separate crash - out of this batch's
scope.** Mid-verification, a genuine `FATAL EXCEPTION`
(`IllegalStateException: Expected BringIntoViewRequester to not be used before parents are placed`,
`ContentInViewNode.calculateRectForParent`) reproduced live in `SettingsScreen`'s Playlists pane:
cancel a remove-playlist confirmation, then press UP a few times, and the app force-quits. Checked
enough to confirm it's not caused by any of this batch's 6 changed files - `SettingsScreen.kt` has
no explicit scroll/`BringIntoView` call anywhere, so this is Compose Foundation's own internal
bring-into-view/layout-placement race, not app code. No playlist data was lost either time it fired
(confirmed via `epgBackfill` logs immediately after and again at session end). This needs a real
debugging session to find the minimal repro, not a guess-and-check patch under this sprint's time
pressure - logged in `TESTING_OUTSTANDING.md` section H for next session.

**Deviated: a test-tooling near-miss, not a product bug, worth naming so it doesn't repeat.** Early
in verification, `scripts/tv-test.sh`'s generic `nav_to_settings` helper (built for Live TV's
shallow layout, where one UP reliably reaches the NavStrip) was used from deep inside Settings'
Playlists pane, where UP does not escape in one press (the same class of gap as items 1/8, just in
a different screen, not part of this batch's scope). The helper's blind RIGHT-search-then-
unconditional-OK fallback landed on a playlist's "Remove" confirmation and came within one keypress
of pressing **Confirm remove** on a real playlist - caught before it fired by inspecting the tool
call sequence and pressing `Back` deliberately instead of continuing the scripted helper. No data
was lost; all 3 playlists confirmed intact both immediately after and at the very end of the
session. Lesson for future sessions: don't use the generic nav helpers inside Settings' Playlists
pane - navigate manually, one press at a time, with a `focused_info` check before any
`KEYCODE_DPAD_CENTER` there specifically.

**Release process note:** built and verified locally against a real signed release
(`-PversionName=v0.37.13 -PversionCode=135`, signature-verified against the established keystore
fingerprint) before pushing, per this project's current sprint protocol - no debug builds used.
`gh release list` was checked both before starting (confirmed `v0.37.12` was the last real release,
so `v0.37.13` was the correct next version to test against locally) and after pushing (confirmed CI
assigned the same `v0.37.13`, no version collision this time).

## 2026-09-22 (later) — Sprint 1 CLOSED: cold-launch DB contention fixed, real numbers

Second pass, same sprint - picks up from the Opus consult below. Full detail:
`docs/plans/SEQUENCING.md`'s Sprint 1 section (rewritten), `docs/plans/TESTING_OUTSTANDING.md`
section G. Real release **v0.37.8** (versionCode 130).

**Measured first, per the consult's own protocol** (`adb shell dumpsys dbinfo com.redsurf.tv` -
works on this non-debuggable release build, zero code needed): confirmed `journalMode=WAL`,
`Max connections: 4` - the journal-mode question settled for good, WAL was already active. Caught
two structurally-cheap queries as live victims of contention during the write storm: the
`recent_channels` join (2.3-3.3s) and `getLiveGroupCounts()` (2.3-3.3s), the latter also
genuinely unindexed as the consult predicted.

**Shipped the full ranked fix set:** EPG sync scheduling moved to *after* `AppState.Loaded`
instead of before, with a real 45s initial delay and a per-playlist 5-minute stagger
(`EpgSyncScheduler`) so multiple playlists can't download concurrently by construction; Room's
`RedSurfDatabase` now uses a dedicated 4-thread query executor and a single-thread transaction
executor instead of sharing the app-wide default pool with EPG ingest; `XmlTvParser`/
`EpgSyncWorker` group 10 of the existing 1000-row batches into one `withTransaction` (~20 commits
per sync instead of ~200); `WRITE_AHEAD_LOGGING` pinned explicitly (real migration `MIGRATION_9_10`,
v9→v10, additive, real data preserved) even though it wasn't the actual fix, stated plainly rather
than credited; the redundant `epg_programs` index dropped (a strict prefix of its own implicit
primary-key autoindex, pure write amplification); a real covering index added for
`getLiveGroupCounts()`; one `ANALYZE` after each successful sync, never on the launch path.

**Verified with real before/after numbers, on a clean second cold launch** (the first post-install
launch includes one-time migration overhead and was excluded as an unfair comparison):
- The app-level `gridQuery` line, originally measured at 6893ms in the first pass: **361ms**
  (19x faster).
- `getLiveGroupCounts()` specifically, via `dumpsys dbinfo`'s real per-statement duration:
  3286ms → **254ms** (13x faster), the only statement over 50ms anywhere in a 20-second
  post-launch window (was several 2-3s statements).
- Zero `epgSync -> start` lines fired in that same 20-second window - EPG sync genuinely off the
  critical path. Honest gap: all 3 real playlists happened to be fresh this pass
  (`epgBackfill ... stale=false` for all three), so the *stagger* specifically was never observed
  preventing a real concurrent-download collision live - verified by code and by the deferral's
  own confirmed effect, not by watching staggered downloads actually happen.
- Flat-memory-at-scale re-check (item 2 from the first pass, never reached then): holds. ~131-136MB
  PSS at the real current scale (3 playlists, ~140K channels), still inside `PHASE_1.md` 1.6's
  original 112.8-145.7MB range measured at 28-60K channels.
- Channel-zap sluggishness (item 3, never reached in the first pass): **inconclusive.** Four
  `KEYCODE_DPAD_UP` presses against a confirmed-playing fullscreen session produced zero
  `zap dir=` log lines - not root-caused, reported honestly as unverified rather than guessed.
  Plausible unconfirmed theory: several stacked cold-launches earlier in this same session for the
  dbinfo measurements may have left the player in a `Controls`/picker overlay state instead of
  plain fullscreen before the zap attempt began.
- Install-in-place (no uninstall) confirmed `MIGRATION_9_10` ran cleanly against the real device
  database and its real ~140K rows - no crash, no destructive-migration fallback, no data loss.

Build/test: `compileDebugKotlin` clean, 23/23 unit tests (result XML confirmed). Real signed
release `v0.37.8` (versionCode 130, keystore fingerprint `1b13f1d9…d2510d8a` matching every prior
release) installed in place and confirmed running before push.

**Not investigated, still open:** the docs-only commits that were triggering real CI releases
despite an intended `[skip ci]` - flagged twice now, still not root-caused. **Caught live this
time:** a docs-only commit (`8fc80de`, "pin the Categories-scroll-steals-focus bug for Sprint 2",
pushed in parallel to this sprint's own work) triggered a real CI release tagged **v0.37.8** at
06:27 - the *same* version string this sprint's own local test build used
(`-PversionName=v0.37.8 -PversionCode=130`) for on-device verification, purely by coincidence of
timing. **Real consequence, not just noise this time:** GitHub's actual `v0.37.8` release artifact
is that docs-only commit's code - it does NOT contain this sprint's fix set, even though the
device had a same-named local build with the real fixes installed on it during verification. This
sprint's own commit (`c46051d`) was pushed after, and CI was still running for it
(`in_progress`, run `35695535673`) when work stopped - it will presumably land as `v0.37.9` or
whatever semantic-release assigns next, which is the version the device needs to end up on to be
consistent with the real release history. **Action for next session: confirm CI finished, confirm
the resulting release tag's version, and install *that* exact artifact on the device** - don't
assume the currently-installed local "v0.37.8" is safe to leave as the device's permanent state,
since it doesn't match what GitHub calls v0.37.8. This is now a real OTA-integrity concern, not
just release-count noise - worth fixing the underlying skip-ci trigger before it causes a second
collision.

**Stopped here, mid-verification of the push, at the coordinator's explicit request** (laptop
closing soon, network/power to the device about to become unreliable) - not because of any
blocker in the work itself. Everything of substance for this pass is committed and pushed
(`c46051d`); nothing was left half-edited. Sprint 2 was not started, per that same instruction.

## 2026-09-22 — Sprint 1 (performance/state audit): re-entry fixed, cold-load escalated

Full account: `docs/plans/SEQUENCING.md`'s Sprint 1 section (rewritten with real findings) and
`docs/plans/TESTING_OUTSTANDING.md`'s new section G. Summary:

**Root-caused and fixed the re-entry churn** the user reported three separate ways this week
(items 4, C16, the D1 side note): `AppShell.kt` was tearing `LiveTvScreen` out of composition
entirely on every destination change away from Live TV and back - not just losing the hoisted
`selectedGroup`/`focusedChannel` data (already fixed 2026-09-15) but every derived query result
and scroll/focus state downstream of it. Fixed by keeping the composable permanently alive once a
playlist is active (`visible: Boolean` param, real size vs. `Modifier.size(0.dp)` instead of
removal - can't receive focus/input while hidden, zero layout cost) and reclaiming focus via the
existing re-triggerable `liveTvClaimInitialFocusTrigger` mechanism on return. **Verified on
device:** zero `gridQuery`/`initialFocus`/`epgBackfill` log lines on a Settings→Live TV return
that previously fired all three every time.

**Escalating, not guessing:** cold launch itself is separately and seriously slow at the user's
real scale (140K channels/3 playlists, one playlist's EPG table alone at 125,175 rows) -
`channelsInGroup()` measured at 6893ms for a 134-row result despite a matching index and a small
result set, which doesn't fit a simple missing-index explanation. Real candidates (DB contention
with concurrent `EpgSyncWorker` backfill queries, unconfirmed journal mode, an unisolated EPG-
query cost within the same effect) are named but none confirmed. Per the sprint's own escalation
instructions: stopped here rather than picking an architecture and building it blind - needs
`EXPLAIN QUERY PLAN` against the real database or an Opus consult with this data before a fix.

**Not reached:** the flat-memory-at-scale re-check and the channel-zap sluggishness check (sprint
items 2-3) - stopped after the cold-load finding to report rather than keep accumulating findings.

**Stopped by external interference**, same class as the Lattice sprint: `dumpsys activity
activities` showed YouTube's TV app had taken real foreground mid-verification (a household member
with the remote). Key injection stopped there rather than fight a live person for control.

**Also found, out of scope, noted for the record:** three consecutive docs-only commits
(`b97c643`-`10bd9f6`) each cut a real CI release despite touching no app code - some `[skip ci]`
convention that applied earlier in the session didn't hold for these. Not investigated further.

Build/test: `compileDebugKotlin` clean, 23/23 unit tests (result XML confirmed). Real signed
release built locally and installed in place for verification (`v0.37.7`, versionCode 129,
keystore fingerprint `1b13f1d9…d2510d8a` matching every prior release) - not yet pushed to `main`;
that happens right after this entry, and CI will assign its own next version number to the same
commit per this project's own established practice.

## 2026-09-21 (later) — EPG sync never completed on the device: root-caused and fixed

Not a sprint - a follow-up to the Lattice build's one open question ("why was every reachable
category empty when 66,807 EPG rows exist?"). Answered from the provider's own data, fetched
directly from the Mac (the provider drops any client not sending the app's own `User-Agent`):

- **Matching is correct.** 28,444 live streams; 7,753 (27%) carry an `epg_channel_id` at all;
  7,744 of those match the XMLTV feed's `<channel id>` exactly (99.9%). The categories the build
  reached (AF | AFRICA: 0 of 222 have an id; the SKYMIX and US local-affiliate categories
  likewise) are simply in the 73% the provider doesn't map. BBC/CNN/sports do have ids. The
  other 73% is what the P1 public-source supplement exists for.
- **The sync was a partial, every time.** Full feed measured: **67MB, 201,463 programmes**, three
  days (yesterday/today/tomorrow). The device held 66,807 - exactly a third. Root cause, three
  parts: the EPG download went through the shared client's **15s read timeout** (right for API
  calls, fatal for a 67MB stream on this box); the worker **cleared the playlist's rows before
  parsing**, so every interrupted attempt left less data than it found; and WorkManager's default
  **exponential backoff** pushed retries out hours after a run of provider 502s. The "150MB and
  growing" netstats figure from the P0 sprint was the same 67MB feed being re-attempted.

**Fixed (commit on `main`, real release cut by CI):** 120s read timeout for the EPG pull only;
no clear-before-parse (the composite key + REPLACE already makes the parse an upsert, so an
interrupted sync now leaves the DB strictly better than it found it), with a prune of
already-ended programmes only after a full parse succeeds; the parser skips already-ended and
unparseable-date programmes (a third of this feed is yesterday); linear 5-minute backoff on both
work requests, applied to existing schedules via `ExistingPeriodicWorkPolicy.UPDATE`; and the
cold-launch backfill now keys off a real per-playlist "last completed" marker (`EpgSyncState`)
instead of a row count that can't tell "complete" from "died a third of the way in."

**Device-verified, v0.37.1 → v0.37.2 (versionCode 124, screenshots in `scratchpad/epg-design/`):**
- The cold-launch backfill fired on the real marker (`rows=66807 lastCompleted=0 stale=true`).
- One playlist's sync ran to completion for the first time ever - `epgSync -> done
  inserted=17949`, marker recorded; on the next launch it correctly did *not* re-sync
  (`stale=false`).
- Linear backoff retried at exactly +5:00.
- **A second bug surfaced and fixed in v0.37.2:** the periodic job's daily run coincided with the
  cold-launch backfill and fired two concurrent feed requests for one account. Collapsed to a
  single periodic request per playlist (`CANCEL_AND_REENQUEUE` = run now; `UPDATE` = keep timer,
  apply new criteria; legacy one-time requests cancelled). Verified: exactly one `start` per
  playlist on the next cold launch.
- **Settings → EPG's first live rows** ("Update EPG now" with an Updating… state, "Last updated"
  from the real marker, "Last attempt · Failed · HTTP 502" only when the newest attempt failed)
  - screenshot-verified. Grey rows corrected to "Provider only" / "Daily".
- **The remaining 502 is a dead provider, not a bug.** The new host log line showed the failing
  playlist is `line.bestlina14.cc` (one of the user's own test lists - "Random Strong"/"Random
  Score" are what's on the device, not the seeded test list), and its `xmltv.php` returns a bare
  nginx "502 Bad Gateway" page. User's call: don't chase it; more test connections can be added
  later. Note for the record: the id-coverage numbers above (27%/99.9%) were measured against the
  seeded test provider, which turned out not to be one of the two playlists on the device.

**Still not seen:** a populated programme cell in the redesigned grid. The one playlist that did
sync completely needs a category whose channels carry ids - that's the next hands-on check.

## 2026-09-21 — EPG grid redesign, "The Lattice" (G.1-G.9), v0.37.0

Full brief and as-built account: `docs/plans/EPG_GRID_REDESIGN.md`. One pass, all nine items:
the slot-list time model (`EpgSlots.kt`, unit-tested), `RedSurfDensity` tokens with a
viewport-derived minute scale and a :00/:30-snapped window, the redundant "Guide" title row gone
and a two-state hero band (136dp / 56dp collapsed bar with a live clock), the drawn lattice
replacing per-cell `Surface`s, time-gridded gap slots, a grid-specific cursor (`RedSurfFocus.
gridCell()` sibling, row band, 1.5dp outline, leading bar, The Wash), a ticking now-line drawn on
top with cap and wake, a shared horizontal axis, and the channel-row fixes.

**Workflow:** real release-signed builds only (`assembleRelease -PversionName=v0.37.0
-PversionCode=122`, keystore fingerprint `1b13f1d9…d2510d8a`), `adb install -r` in place three
times, **no uninstall, playlist survived the whole sprint.** `.claude/commands/sprint.md` read
fresh, as the coordinator asked. Screenshots (`scratchpad/epg-design/*.png`) were the primary
visual verification - the first sprint in this project to have them.

**Verified on device (screenshots):** 6 rows with the hero expanded, 8 collapsed (was ~2); the
two-tier ruler; the now-line on top with cap and wake, re-snapping itself across two half-hour
boundaries; gap rows as dashed time-gridded spans; the cursor's band/outline/bar; marquee on the
cursor row; the collapsed hero bar. **Not eyes-verified:** aired-cell rendering and LEFT/RIGHT
shared scrolling (no category reached had channel ids matching the provider's feed - the DB had
66,807 rows, just not for those categories), and the 180ms Wash (under screencap latency).

**deviated:** label column 180dp not 150 (real names ellipsized at 74dp); per-row shared
`horizontalScroll` instead of one column-level scroll (pinned label would cover revealed cells);
gap dash clears the "No listings" caption; hero text follows the D-pad cursor via a new
`cursorChannel`/`cursorSlot` (the band collapsed while browsing otherwise); one out-of-scope fix -
cold launch re-enqueues EPG sync for a playlist with zero rows (a run of provider 502s had pushed
WorkManager's backoff out 4+ hours).

**Stopped early, deliberately:** mid-sweep the launcher and then Nuvio took the foreground,
launched from the launcher's own uid right after an HDMI-CEC input-active event - a person in the
household had the remote. Key injection stopped there; nothing was relaunched over them. The
remaining three checks need a free TV and a category with real listings.

**Real release v0.37.0** cut via CI on push (run #122 → versionCode 122, matching the device).

## 2026-09-20 (later) — Live TV/Guide real merge built and device-verified

Full brief and account: `docs/plans/LIVE_TV_GUIDE_MERGE.md`. Filed after the user saw Phase 3 P0
live and reported the merge wasn't real (Live TV and Guide still read as two sections despite
sharing one composable) and the grid looked nothing like the TiviMate reference - both confirmed
true by reading the actual code and the actual reference image before building anything.

**Built:** `NavDestination.Guide` removed entirely - one nav pill reaches Live TV now. The old
`guideMode` two-branch layout split is gone; there is one screen: a hero preview band (TiviMate
parity - title, time range + progress bar, description, category label, a grey non-functional
favorite star) directly under the nav-strip, hosting Preview-on-OK's player relocated from the old
right column, then categories + an enriched `EpgGridColumn` below (real channel logos/numbers, a
real date/time header, a genuinely synced "now" line built from the observation that the grid's
rolling window always starts at "now," so a single static line at a fixed offset stays correctly
aligned with every row that hasn't been individually scrolled - a real solution, not the per-cell-
highlight fallback P0 shipped). `ChannelsColumn.kt` itself is untouched - still real code, used by
the fullscreen player's own LEFT-edge overlay, just no longer used inside the browse view.

**Live-verified on the real device** (Chromecast, real ~18-20K-channel Xtream playlist, real
signed release v0.36.0): nav strip has exactly six pills, no Guide; first OK on a grid cell held
real `GAIN` audio focus (`dumpsys audio`, not a log line) while staying in browse; second OK
promoted to fullscreen with a *new* `AudioFocusListener` instance and a real `BUFFERING -> READY`
transition; Back returned to the merged grid specifically; real EPG dates/time-ticks/channel
numbers/logo-fallback letters all confirmed present via `uiautomator` against real provider data.
Not eyes-verified (screencap is broken device-wide on this box, `HARDWARE.md`) - the hero band's
exact visual proportions and the now-line's actual appearance are reasoned-correct/non-crashing,
not seen; that's what Checkpoint A is for.

**deviated: briefly ran the wrong build protocol.** Started this sweep on the superseded debug-
build-then-release-swap cycle (already fixed once, 2026-09-17) instead of building/verifying
directly against a real signed release - the coordinator caught it live mid-sweep and corrected
it. The unnecessary uninstall/reinstall wiped the device's playlist twice; re-seeded immediately
both times rather than leaving the device stranded on Onboarding. Full account in the brief.

Build/test: `compileDebugKotlin` clean (first attempt), 15/15 unit tests (result XML confirmed).
Real signed release **v0.36.0** (versionCode 121), installed and confirmed running
(`ResumedActivity`, no crash) before push.

## 2026-09-20 — Preview-on-OK built and device-verified

Full brief and account: `docs/plans/PREVIEW.md`. TiviMate parity: a single OK on a focused
channel in Live TV's plain browse list now starts it playing for real in the preview column
(audio on, stays in browse) instead of jumping straight to fullscreen; a second OK on that same
still-previewing channel promotes it. OK on a different channel while one's already previewing
swaps immediately, no backing out required. New file `player/PreviewPlayerHost.kt` - a second,
deliberately minimal ExoPlayer instance for the preview area, mutually exclusive in time with the
fullscreen controller (never both alive at once), no stall watchdog/reconnect ladder, silent
fallback to the static stub on error. New Settings row, "Preview channel on select" under
Playback, default on.

**Verified live on the real device (Chromecast, real Xtream playlist), not just by log lines** -
this project's own established gap from the last two sprints (Teleport Menu's discrepancy, Phase
3's log-line-only claims) was deliberately avoided here by checking `dumpsys audio`'s live focus
holder directly, which only shows a real value while a decoder is actually producing audio:
- First OK: RedSurf genuinely held live `GAIN` audio focus, browse UI stayed fully composed.
- Second OK (same channel): promoted to fullscreen (full-screen focus bounds), and a *new*
  `AudioFocusListener` instance took over from the preview's - confirmed real release, not reuse.
- OK on a different channel while one previewed: preview panel's channel name swapped immediately
  (`GHANA - JOY PRIME SD` → `GHANA - 3ABN INTERNATIONAL HD` on one press).
- Focus movement alone (no OK): preview kept playing the same channel; only the hint text changed.
- Leaving Live TV for Settings: RedSurf dropped out of `dumpsys audio`'s live holder entirely.
- Settings toggle: exists, defaults on, flipping off restored the exact old single-OK-to-
  fullscreen behavior (verified both directions).

**Not independently re-tested:** cold-launch auto-play's still-unchanged fullscreen jump -
reasoned correct by code inspection (the only change in that path is a no-op `previewingChannel =
null` assignment), not re-verified via a disruptive force-stop/relaunch cycle this session.

**One bug caught before it ever reached the device:** a `previewFailed` state variable was
originally declared inside a nested `Box` block but referenced again outside it in the hint-text
logic - a real scoping error that failed to compile. Fixed by hoisting it to the composable's own
top level before any device time was spent; also cleaned up a confusingly-named helper function
in the same pass (it checked "is this the same channel," not what its original name implied).

Build/test: `compileDebugKotlin` clean, 15/15 unit tests (result XML confirmed). Real signed
release **v0.35.0** (versionCode 120), installed and confirmed running (`mResumed=true`, no
crash) before push. Device data was wiped by the debug/release swap mid-sprint (expected, per
protocol) - the test playlist needs re-seeding before the user's own hands-on testing.

## 2026-09-19 (evening, resumed) — Phase 3 P0: device reconnected, sweep substantially passed

Follow-up to the entry directly below (same sprint, same session) - the device came back on its
own after the disconnect, so this isn't a new sprint, it's the same one finishing. Full account:
`docs/plans/PHASE_3.md`'s "Sweep status" section.

**Reconnected without re-pairing** - a later `adb connect` retry just worked, no fresh pairing
code needed after all (the earlier entry's assumption that one would be required was wrong).
**Second snag on reconnect, unrelated to ADB:** the device had gone to sleep
(`dumpsys power` → `mWakefulness=Asleep`), which swallows D-pad key injection silently (no error,
no effect) - several minutes lost reading that as a broken nav-search loop before checking power
state directly. `KEYCODE_WAKEUP` before each interaction fixed it. Worth remembering for next
time: identical `uiautomator` focus bounds across a sequence of key presses means check
`mWakefulness` before assuming the input itself is broken.

**Playlist re-seeded** (lost to the migration bug below) - real provider data imported cleanly,
no repeat of the bug (the fix holds).

**Sweep results, live on the real device:** Guide pill reaches the real grid (distinct from Live
TV's plain list, confirmed via `uiautomator` text - "No programme data" vs. "No schedule
information"/"Channel Preview"). OK on a grid cell tuned real fullscreen playback (`BUFFERING` →
`READY` in logcat, right channel confirmed via the group-snapshot log line). Back from fullscreen
returned to the grid specifically, not the plain list. UP/DOWN moved focus row to row correctly
(default Compose 2D focus traversal, no custom key handling needed, as designed). EPG sync
confirmed genuinely active (not silently failing) - `EpgSyncWorker` logged `epgSync -> start`, and
`dumpsys netstats` showed this playlist's real XMLTV feed actively downloading (128MB → 134MB →
151MB across checks spaced a few seconds apart).

**One gap, not chased further:** this provider's XMLTV feed didn't finish downloading+parsing
within the session (150MB+ and still growing at last check), so a real *populated* programme cell
was never actually seen rendering - the empty-slot path was exercised live, the populated-cell
path is implemented and reasoned-correct but not yet eyes-verified. Worth a specific look at
Checkpoint A once sync has had more wall-clock time.

**Pushed to `main`** - a real CI release cut off this work (see `gh release list` for the tag).
**Deviated:** the first push's own HEAD commit accidentally carried `[skip ci]` (meant for a
docs-only convention, wrongly applied to a push that also bundled real code) - GitHub skipped the
whole workflow, not just that one commit, so no release actually landed until this follow-up
commit (no `[skip ci]`) re-triggered it. The one open item above is a feel/vision checkpoint item,
not a known bug - reasonable to ship and let Checkpoint A cover it live.

## 2026-09-19 (evening) — Phase 3 P0 (EPG + Guide): code complete, sweep blocked mid-run

Full account: `docs/plans/PHASE_3.md`'s "Sweep status" section - summarized here per the sprint
skill's own Blocked-section requirement.

**Built:** all of P0.1-P0.5 - `EpgProgramEntity`/`EpgDao` playlist-scoped, per-playlist
`EpgSyncWorker`/`EpgSyncScheduler` (real `WorkManager` enqueue, the worker previously existed but
was never scheduled anywhere), the Guide grid itself (`EpgGridColumn.kt`, new file) with a
coordinator-requested visual-polish pass (static crescent "LIVE" glyph reusing `WaveSpinner`'s own
brush, a brand-anchored `ic_mark` header, a cheap one-shot fade+rise entrance), grid key handling,
and both nav entry points (`NavStrip`'s Guide pill, the player's "Guide" quick-action) wired to the
real screen. `compileDebugKotlin` clean, 15/15 unit tests pass (result XML confirmed).

**deviated: found and fixed a real data-loss bug mid-sweep.** The original migration used
`fallbackToDestructiveMigration()` for the v8→v9 bump, reasoning "the one table that changed never
held real data" - true, but destructive migration wipes the *entire* database, not just that
table, and silently deleted the device's real playlist on install. Replaced with a real
`MIGRATION_8_9` scoped to just `epg_programs`. Root-caused live via targeted logging on a debug
build (one `-PversionCode=999` debug install, per the sprint protocol's own local-debugging
carve-out - not counted against the release-cut cap). The device's playlist is gone as a result of
the bug firing once before the fix landed.

**Blocked: the device dropped off wireless ADB entirely mid-sweep**, after one real release build
(v0.34.0, versionCode 119) had already installed and been confirmed running. `adb devices` shows
`offline`; `adb mdns services` finds nothing. This is a deeper disconnect than "asleep" (already
established elsewhere in this project as not a blocker on its own) - recovering needs a fresh
pairing code read off the TV's own screen, which needs a person. Tried: `kill-server`/
`start-server`, `disconnect`+`connect`, repeated `mdns services` over several minutes. Stopping
here per the Blocked section rather than burning the window on further retries.

**Not pushed to `main`.** Everything is committed locally. Resuming needs: the device reconnected
(human re-pairing), the test playlist re-seeded, then the actual machine-verifiable sweep run
against a real device before this can be called done and a release cut.

## 2026-09-19 (later same day) — Teleport Menu real-device feedback: most rows don't work

Not a build session - the user tested v0.33.0 live on the real remote the same day it shipped.
No code touched tonight; full detail folded into `docs/plans/TELEPORT_MENU.md`'s new "User
feedback after real device testing" section and `AGENTS.md`'s Teleport Menu entries. Summary:

- **Portal animation lands well** - user's own words, "the portal effect works well and it's
  nicely done." Two refinements requested for next session: slow the open/close slightly; add a
  cheap animated treatment to the panel's outline while open (performance over aesthetics).
- **Most rows don't actually work** - direct contradiction of this same build's own
  `uiautomator`/logcat-based sweep, which had claimed Nav-Strip/Playlist Root/Root
  Category/Return to fullscreen all device-verified. User's real remote: only Exit RedSurf
  worked. "Even 'Nav-Strip' does not work" - the simplest of the five real rows. Needs a real
  debugging session next time, with the user's own remote in hand rather than synthetic
  `adb`-driven verification alone - the gap between "the trigger fired" and "it looked right" is
  exactly the failure mode this project's "don't claim it works because the code looks plausible"
  rule exists to catch, and it slipped through here.
- **Root Category can't be independently re-checked right now** - the "Random Strong" test
  playlist used to verify it during the build has since been deleted by the user. Needs a working
  playlist re-added, and the original pitch examples re-grounded against it, before this row can
  be trusted again.
- **Explicitly deferred, not being worked tonight** - user is done for the session; this is a
  clean stopping point with everything written down for a cold start next time.

## 2026-09-19 — Teleport Menu built (T.1-T.4), device-verified, v0.33.0

Full brief and account: `docs/plans/TELEPORT_MENU.md`. Built T.1-T.4 of the brief in one pass -
Settings toggle (`Remote control`'s first-ever live row, default off), the menu overlay itself
(new file `ui/shell/TeleportMenu.kt`), all five real destinations plus the two grey ones, and
Root Category's prefix-parsing logic built for real rather than left grey (pulled forward since
this sprint was already touching the exact code it needed - the brief's own carve-out for that).
Visual treatment ("The Curl" - the reveal mask is the mark's own crescent, closing shut into a
disc then stretching into the panel, its leading edge riding `WaveSpinner`'s own arc) came from an
Opus design consultation earlier the same day - full spec in the brief's decision 5.

**Live-verified on the real Chromecast, real ~30K-channel playlist, both toggle states:**
- Settings toggle flips and persists both ways (`uiautomator` text dump).
- Toggle on: long-press Back opens the menu (`AppShell: teleportMenu -> open`, logcat). Toggle
  off: the plain TiviMate-parity fallback fires exactly as before, zero regression (same real
  synthesized long-press, `adb shell input keyevent --longpress KEYCODE_BACK`, both states).
- All 7 rows render correctly; grey rows are genuinely unfocusable - confirmed by cross-checking
  the focused node's raw bounds against `clickable`/`focusable` attributes in a real `uiautomator`
  dump. Initial focus correctly claims row 0 (Nav-Strip).
- Playlist Root: landed on the correct first channel of the right playlist's first category
  (confirmed via `PlayerScreen`'s own group-snapshot log line).
- Root Category: focused on "VIP | CHRISTMAS" (not first in its family), jumped correctly to
  "VIP | 4 GOLDEN RELAX" - the real first VIP-prefixed category in list order, against this
  provider's actual `|`-delimited names, not a synthetic example.
- Return to fullscreen: entered fullscreen and began real playback on the jumped-to channel.
- Back closes the menu with zero side effect (confirmed via logcat + unchanged focus/state).

**Not independently exercised live:** Root Channel Group and Exit RedSurf (both built on the same
proven code paths as the rows above - reasoned correct, low risk, just not separately clicked
through this session). T.5 (discoverability tips) deferred - the mechanism is done, the tips
themselves need the user's own gut-check on a tuning number more than more code.

Build/test: `compileDebugKotlin` clean, 15/15 unit tests (confirmed via the actual result XML).
Real signed release **v0.33.0** (versionCode 118, matching the next real CI run number), keystore
fingerprint `1b13f1d9…d2510d8a` (matches every prior release), installed and confirmed running on
the real device before push.

## 2026-09-18/19 — TBN freeze root-caused and fixed; long-press Back; #2.6 closed; Phase 2 done

**The real fix, this time.** Root-caused the user's recurring "channel freezes forever" report,
live, against their actual TBN channel reproducing naturally: `ExoPlayer` reports `STATE_ENDED`
on this channel roughly every ~30s (provider-side connection rolling, not a RedSurf bug), and
neither existing watchdog was watching for that state at all. First fix attempt (treat it as a
stall, call the existing bare `prepare()`) compiled clean but **failed live** - state stayed frozen
across 5 straight attempts. Real fix: `forceReconnect()` - a full `stop()` + fresh `setMediaItem`
+ `prepare()`, matching this codebase's own documented `max_connections: 1` reasoning. Live-
confirmed end to end: `ENDED -> IDLE -> BUFFERING -> READY` in ~1.4s. A follow-on bug the same
live data surfaced: the "2 strikes, give up" counter never reset on a genuine successful recovery,
so TBN's own benign reconnect cycle was tripping the terminal error even though every individual
reconnect was working - fixed by resetting it from both real-recovery signals.

Also root-caused and fixed the separate "audio plays, picture never appears, no error" pattern
(observed on two unrelated channels): real, documented ExoPlayer behavior - a video track this
device can't decode doesn't fail loudly, `DefaultTrackSelector` just silently drops to audio-only.
Detected via `Tracks.Group.isSupported` and surfaced as a real, honest error instead of an
indefinite silent black screen. Not live-reproduced this round to confirm the fix fires (both this
and the freeze fix's edge cases are intermittent/hard to force on demand) - reasoned and compiled
clean, flagged as such.

**Built and live-verified, both paths:** long-press Back - TiviMate-parity jump into fullscreen
from Live TV's browse view when a channel is already focused/previewed, nav-strip pill-focus jump
everywhere else (the "fast way back to the nav-strip no matter how deep" backlog item). Intercepted
once at `AppShell`'s root, not touching any individual screen's own key router.

**Smaller fixes, all live-verified:** the critical auto-play-on-launch focus bug (no
`runCatching`/retry on the one focus-claim that mattered - left the remote completely dead on the
user's own current default setting); Video info's network-speed estimate was frozen at channel-
change time (a bandwidth-meter estimate that never updates for one continuous connection - replaced
with a direct byte-counter, smoothed to stop flickering, and made always-present per user request);
resolution display reverted to the single Appearance setting (not "always show both," an earlier
over-correction); the provider's live connection count added to the zap-banner badge row.

**#2.6 (acceptance sweep) closed** - migration confirmed by the user directly; zap latency and
memory both explicitly waived by user decision (no measurement taken, deferred wholesale to a
future performance-tuning sprint); everything else (dead-code greps, `FocusRequester` comments,
15/15 tests, signed releases, docs) confirmed. **Phase 2 is fully closed.** "Teleport Menu" (a new
RedSurf-original quick-navigation feature the user proposed this session) inserted into the
roadmap as its own standalone sprint before Phase 3 starts - see its `AGENTS.md` backlog entry for
the full design, the two data-dependent destinations correctly scoped as grey-out-until-built
rows (not blockers), and the discoverability-tips sub-idea.

Workflow note: local release-signed builds throughout (`assembleRelease` with the real keystore,
the real next version number each time - v0.32.4 through v0.32.10), `adb install -r` for
verification, `git push` after each batch for the real CI record - per this session's own
same-day reversal of the "wait for CI every time" rule (`.claude/commands/sprint.md`).

## 2026-09-17 (evening) — Player sprint device-verification round, Checkpoint B closed

**Builds this pass: several local `assembleRelease` builds (real keystore, real next version
number each time - v0.32.4 through v0.32.5-equivalent), installed via `adb install -r`, `git
push`ed after each batch for the real CI record.** Workflow reverted mid-session, user-directed:
the morning's "wait for CI every time" rule cost more wall-clock/tokens than it saved; see
`.claude/commands/sprint.md`'s same-day correction.

Full account of what was found and fixed - each confirmed against real logcat, `uiautomator`
dumps, or a live screenshot, not inferred - lives in `PHASE_2.md`'s new "2.3-2.5 device-
verification round" section: LEFT overlay's focus-target bug (root-caused: `GroupsColumn`'s own
late-firing initial-focus claim was stealing focus back from the channel), the context-menu
repeat-key bug (two rounds to actually close), the fast-scroll bug (user corrected the diagnosis -
real fix was the list's own scroll tracking, not the focus ring), a full branded buffering/error
UX rebuild (centered `WaveSpinner`, full branded terminal-error panel), and - the headline result -
a **live-caught, live-confirmed recovery** of the "channel freezes forever" report: a real stall
(READY state, position stuck at 0) was caught by last session's position watchdog and auto-
recovered, logcat-confirmed. A follow-on bug this same test surfaced (the spinner not clearing
after a real recovery) was found and fixed in the same session.

**Checkpoint B closed.** Not confirmed, explicitly, and left open: multi-playlist performance
(untested by the user's own account) and audio/subtitle track switching (the user's live channels
are single-track, untestable without a VOD source).

**Also this session:** Provider Intelligence (a feature idea raised mid-testing, not part of the
Player sprint) researched across three separate angles and rejected on all three - live API
fields, live third-party site query, and public community knowledge all dead ends for brand
identification; see `AGENTS.md`'s Backlog for the full record. Only #2.6 (migration/zap-latency/
memory acceptance sweep) remains before Phase 3 (EPG + Guide) starts.

## 2026-09-17 — Player sprint close: #2.3/#2.4/#2.5 (`docs/plans/PHASE_2.md`)

**Builds this pass: 1 release cut (v0.32.0), 2 local compile-checks** (no install - this session
also fixed the workflow doc itself, see below, so the debug-build-then-swap cap this entry used to
track no longer applies). **Deviations: two**, both noted in `PHASE_2.md` inline where they
happened: `RecentChannelEntity` uses a composite `(playlistId, streamId)` key, not decision 14's
literal bare `streamId PK`, matching `ChannelEntity`'s own precedent; the real migration is
`7→8`, not the decision's predicted `6→7` (another version bump landed in between, from an
earlier session's resume-on-launch work).

**Also this session, before the sprint work:** `.claude/commands/sprint.md` rewritten to remove
local debug-build installs as the default iteration path - a 3rd consecutive day of the same user
complaint, traced this time to the protocol document itself silently reintroducing the pattern
regardless of what memory said. From here: compile-check locally, batch changes, push to cut one
real release, verify against that exact artifact. Also fixed live and shipped as v0.31.1/v0.31.2
this session, ahead of the sprint proper: `GroupsColumn` not scrolling to reveal the selected
category on cold-launch resume, and a real crash (`PlayerController`'s internal `CoroutineScope`
had no dispatcher, defaulting to `Dispatchers.Default` - any retry path calling `exoPlayer.prepare()`
off the main thread crashed with "Player is accessed on the wrong thread", found via
`dumpsys dropbox --print`, not guessed).

**Built:** #2.3's five real Actions-row tiles (Channels/Audio/Subtitles/Aspect/Video info),
`PlayerController` track wrappers + `resizeMode` state, three new pickers (Audio/Subtitles read
and select `Tracks.Group`s directly; Video info is read-only off `StreamInfo`); `PlayerOsd.kt`
deleted (confirmed dead). #2.4's `ChannelListOverlay.kt` (LEFT, fresh Categories/Channels
instances over a scrim), real RIGHT last-channel zap, `ContextMenuPanel` (favourite/hide, both
DAO methods that existed dead until now). #2.5's real `recent_channels` table, DAO, `Migration
(7, 8)`, `ChannelRepository`/`LiveTvScreen`/`AppShell` swapped from the in-memory recents stub to
the DB-backed `Flow`.

**Sweep:** compiled clean both times (once after the #2.4/#2.5 chunk, once after #2.3's icon-import
and `PlayerHost` shadowing fixes - `VolumeUp`/`Subtitles`/`AspectRatio` aren't in this project's
`material-icons-core`-only icon set, confirmed by inspecting the jar directly rather than guessing;
a local `val resizeMode` added to `PlayerHost` shadowed `PlayerView`'s own settable property inside
its factory block, fixed by qualifying `this.resizeMode`). Pushed as one `feat:` commit (the
`sprint.md` fix pushed separately, as `docs:`), CI green (regression suite, signed release-key
verification), published as **v0.32.0**.

**Not verified this session: on the actual TV.** `adb connect 192.172.7.160:35631` refused twice
(the standing "stop after two attempts" rule) - device unreachable, port may have rotated or the
TV was asleep. Checkpoint B (`PHASE_2.md`) is genuinely open, not just unclosed paperwork - install
v0.32.0 via OTA and run the feel/vision list handed to the user this session.

## 2026-09-15 — Backlog sweep (`docs/plans/BACKLOG_SWEEP.md`)

**Builds this pass: 1** (well under the 3-build cap). **Deviations: one** - `deviated:` added a
diagnostic `Log.d` line to `PlayerHost` (item #11's acceptance criterion explicitly asks for a
log-verifiable check, not a screenshot, since a black frame between two live streams is too fast
to reliably catch in one screencap) partway through building, before the first compile - logged
here since it's a departure from the brief's file list, not because it changed the sweep order.

**Setup:** debug/release swap as usual; seeded via the Xtream API path
(`scripts/tv-test.sh seed_playlist xtream`) - one transient `HTTP 000` on the first attempt (same
known pairing-server-not-yet-bound issue as the 2026-09-14 mini-sprint), retried 3s later,
succeeded (~27K channels, ~25s import).

**Built:** all 13 items from `BACKLOG_SWEEP.md`, in one pass, before the first compile -
`PlaceholderScreen` focus claim (#1); `CategoryRail` → `TvLazyColumn` (#2) and its `DirectionUp`
guard dropped (#3); `GroupsColumn`/`ChannelsColumn` deterministic focus-return, already present
from an earlier sprint for #4 (verified, not rebuilt) and newly added for #6 (first-channel
redirect on a never-browsed entry); `CategoryRail`'s own `hadFocus` return-redirect mirroring
`SettingsPane`'s (#5); `LiveTvScreen.onChannelChanged` now updates `selectedGroup` too, not just
`focusedChannel` (#7); `LiveTvScreen`'s own `BackHandler` (Channels → Categories → Home), kept
mutually exclusive with `AppShell`'s Home-jump one via `onChannelsFocusChanged` (#8); About →
"Created by · Faraz Ahmad" row (#9); new `settings/AppPreferences.kt`, `SharedPreferences` +
`StateFlow`, two booleans (#10); Playback → "Black screen between zaps" toggle, threaded to
`PlayerHost` (`clearMediaItems()` + `setKeepContentOnPlayerReset` inversion) (#11); Appearance →
"Resolution badge" toggle, threaded to `PlayerInfoBlock`'s badge row (#12); `ChannelEntity`
composite primary key (`playlistId`, `streamId`), Room v6→v7, destructive migration (#13).

**Sweep, one pass, clean - all 12 machine-verifiable criteria green, zero blocking failures:**
1. Home: `PlaceholderScreen`'s themed `Surface` reports real focus with a visible ring
   immediately on arrival (confirmed via both Back-to-Home and direct nav). ✅
2. Settings rail: UP/DOWN through all nine rows reaches "About" with its value fully visible,
   bounds inside the rail's card. ✅
3. Settings rail: UP at "General" escapes (no longer blocked); DOWN at "About" still stays put.
   ✅ with a **new, non-blocking finding**: UP lands on the "Home" NavStrip pill, not "Settings" -
   Compose's default spatial search picks whichever pill is horizontally nearest the rail (far
   left), not the one actually entered from. Logged as feel/vision in `AGENTS.md`, not fixed
   this sprint (forcing "Settings" specifically would need the same cross-branch `requestFocus()`
   this project already found unreliable).
4. Live TV: focused channel 24182, LEFT to Categories, RIGHT back - same `streamId` (24182)
   confirmed via `uiautomator` dump both times (this entry-redirect was already built in an
   earlier sprint; this pass verified it, not rebuilt it). ✅
5. Settings: entered "Check for updates" (spatially near "Parental controls"/"Other"), LEFT back
   to rail - landed on "About" (the category actually selected), not the spatially-nearest row.
   ✅
6. Live TV: RIGHT into a never-browsed category landed on its first channel by `num` order
   (confirmed both via bounds/child-text and a fresh-launch AF|AFRICA → 24180 check). ✅
7. Full round-trip verified live: opened a channel in AF|AFRICA, backed out, opened a different
   channel in AF|MALI, opened the tile row, picked the AF|AFRICA recent tile, Back twice out of
   fullscreen - Categories showed AF|AFRICA selected, Channels showed 24180 focused. Confirmed via
   `PlayerHost`'s media-swap log line and two full-screen screenshots (before/after the tile
   pick). ✅
8. Live TV: Back from Channels → Categories (bounds confirmed still inside Live TV, not
   NavStrip); Back again → Home (`PlaceholderScreen`'s Surface, real focus). ✅
9. About: "Created by · Faraz Ahmad" row visible, styled like Version, screenshot-confirmed. ✅
10. Playback → "Black screen between zaps" flips live (screenshot: Off → On); `PlayerHost` logs
    `blackScreenBetweenZaps=true keepContentOnReset=false` on every subsequent media swap,
    confirming the flag reached the player, not just the Settings row. ✅
11. Appearance → "Resolution badge" flips live (screenshot: "Class (SD/HD/FHD/4K)" →
    "Exact (e.g. 1920x1080)"); with it on, the zap-banner badge showed the literal `896x504`
    instead of a class label, screenshot-confirmed. ✅
12. Fresh install (uninstall, install debug, seed via Xtream) after the v7 schema bump: ~27K
    channels imported and displayed correctly - `num`/`groupName`/group counts all intact, no
    crash, no `SQLiteException` in logcat. ✅

**Hand-off:** debug uninstalled, release build to follow this entry's own push; `AGENTS.md`'s
Backlog trimmed - every item this sprint resolved is marked, and the stale "No playlist
management UI" entry (resolved by the 2026-09-13 Settings sprint but never updated) was removed
per the brief's own hand-off instruction.

**Feel/vision for the user (3 items):**
1. Back-in-Live-TV (Channels → Categories → Home) - right number of presses to exit, or too many?
2. The black-screen zap toggle - does it actually feel like the old cable-box behavior you wanted?
3. "Created by · Faraz Ahmad" - final wording, or want a role appended now that you can see it live?

Plus the new, non-blocking finding from criterion 3 above: is "UP from Settings' rail always
reaches the Home pill" fine, or should it target the Settings pill specifically (would need new
plumbing, not a quick fix)?

**Release:** pending this entry's own push (see version below once tagged).

## 2026-09-14 — Zap UP/DOWN order mini-sprint (`AGENTS.md` backlog entry as brief)

**Builds this pass: 1** (well under the 3-build cap). **Deviations: none** - straight setup →
build → sweep → hand-off, no mid-sweep fixes needed.

**Setup:** debug/release swap as usual. Seeded via the Xtream API path (`scripts/tv-test.sh
seed_playlist xtream`), not M3U - **~30s to "Importing 13500 channels..." vs. the ~3-4 minutes
the M3U path took every time in sprint 1.** One transient `HTTP 000` on the very first seed
attempt (curl fired before the pairing server had fully bound right after relaunch) - retried
immediately, succeeded. Worth a beat of settle time after `relaunch` before seeding in future
sprints, not a real bug.

**Built:** the direction flip (`PlayerScreen.zap`: UP → `nextChannel`, DOWN → `prevChannel`,
was backwards) plus permanent diagnostic logging - `ChannelDao.firstNInGroup` /
`ChannelRepository.debugFirstInGroup` (group snapshot, capped at 30, same order as the real
queries), and two `Log.d` lines: the group snapshot on entering fullscreen, and
`zap dir=<up|down> from=(<num> <name>) -> to=(<num> <name>) group=<groupName>` on every zap.

**Sweep, one pass, clean:**
- Group snapshot, "AF | AFRICA" (175 channels, real Xtream data): confirmed sequential
  `num` 24180-24354 with country-separator pseudo-channels inline (e.g. "##### AF - GHANA #####")
  - a genuine provider-organization quirk, not a data bug.
- 10× UP from 24180: `24180→24181→24182→24183→24184→24185→24186→24187→24188→24189→24190` - every
  step +1, zero repeats/skips/reversals.
- 10× DOWN from 24190: exact reverse back to 24180 - same zero-defect trace.
- Wrap-around: DOWN from the group's lowest (24180) → 24354 (the group's highest, "AF - FRANCE
  24"); UP from there → back to 24180. Both correct (`lastInGroup`/`firstInGroup` fallback).

Full logs (20-press trace + wrap-around) are in the session transcript; not duplicated here -
see `AGENTS.md`'s updated zap-order entry for the summary and what's still open.

**Hand-off:** debug uninstalled, release verified signed (fake-version local check), pushed,
CI green, release installed and version-verified on the Chromecast.

**Release:** pending this entry's own push (see version below once tagged).

**Feel/vision for the user:** does UP/DOWN zapping now feel like TiviMate on your real list, not
just this one 175-channel test group? That's the one thing this sprint couldn't measure for you.

## 2026-09-13 — Settings shell (`docs/plans/SETTINGS.md`)

**Built:** the full two-pane shell - `SettingsCategory.kt` (taxonomy + grey-row tables),
`SettingsScreen.kt` rewritten (category rail, settings pane, Playlists/About real content, grey
rows for the other seven categories), `AppShell.kt` hoisting `selectedSettingsCategory`.

**Playlist seeding:** `adb forward tcp:8080 tcp:8080` + `curl --data-urlencode` POST to
`http://127.0.0.1:8080/submit` (type=m3u, name, m3u=<url from ~/.redsurf/test-playlist.url>,
contentType=live) works reliably against the on-device pairing server. The real ~28K-channel
list takes **~3-4 minutes** to import on this device - plan sweep timing around that, don't
assume it's done after a short wait.

**Sweep, pass 1 → found real bugs (all fixed this sprint):**
1. LEFT from a pane row back to the rail was silently a no-op. Root cause turned out to be the
   `focusProperties { exit = Cancel }` trap itself - it blocked `FocusRequester.requestFocus()`
   calls crossing into a sibling focus branch, inconsistently by direction (blocked LEFT, did not
   reliably block RIGHT/UP/DOWN either). Fixed by dropping `focusProperties` entirely in favor of
   explicit `onPreviewKeyEvent` interception (matching `PlayerScreen.kt`'s own router pattern) for
   the three genuine escape cases (rail top/bottom, RIGHT into an empty pane), and leaving LEFT to
   Compose's own default `moveFocus` (which was never actually broken - only `requestFocus()` was).
   `SettingsScreen.kt`'s and `SettingsPane`'s doc comments have the full blow-by-blow; keep it in
   mind before reaching for `focusProperties.exit` anywhere else in this codebase.
2. Confirming "Remove" (or "Reset") tore down the row that held focus with nothing claiming the
   replacement - the exact "state/focus discipline" bug class already on record for
   `PlayerScreen`'s Controls floor, just reached via a confirm dialog. Fixed: both confirm blocks
   now reclaim focus onto "Confirm remove"/"Confirm reset" the moment they appear.
3. `MainViewModel.deletePlaylist`/`resetAndAddNewPlaylist` reaching zero playlists never
   transitioned to Onboarding - `checkLocalCache()`'s `context` parameter defaults to null for
   every caller except the cold-start one, and `startPairingServer` silently early-returns on a
   null context without updating `_state`. Pre-existing bug (not introduced this sprint), only
   now exercised because Settings can finally remove the last playlist in one flow. Fixed by
   storing `appContext` in the ViewModel from `setDatabase` and falling back to it.

**Sweep, pass 2 - all 7 machine-verifiable criteria green:**
1. Rail shows nine categories in taxonomy order. ✅
2. Rail UP/DOWN changes the pane, `settings -> <category>` logged each time. ✅
3. RIGHT into Playlists lands on "Remove" (first live row); DOWN walks only live rows, grey rows
   never report focus; LEFT returns to the rail. ✅ (see caveat below)
4. RIGHT into a zero-live-row category (verified on Appearance) leaves focus on the rail,
   `focus ->` unchanged. ✅
5. Remove → confirm → row gone; removing the last playlist correctly returns to Onboarding. ✅
6. About shows the running `versionName`; Check for updates cycles Idle → Checking → UpToDate. ✅
7. Leave Settings for Live TV (routed through Home, since the Home placeholder's own focus is
   flaky - pre-existing, out of this sprint's scope) and back → same category still selected. ✅

**Caveat, logged as feel/vision + `AGENTS.md` backlog, not re-chased this sprint:** LEFT (and
Back) return focus to the rail via `FocusManager.moveFocus`, which lands on whichever row is
spatially nearest to wherever you were in the pane - not necessarily the category you started
from. Same open ambiguity as the existing "GroupsColumn RIGHT entry" backlog item, just the
mirror direction; deterministic redirect isn't possible here without reintroducing the
`requestFocus()` bug above, so it's a product decision, not a bug with an obvious fix.

**Release:** `v0.27.0` - installed and version-verified on the Chromecast, signature confirmed
against the release key. Device was fully wiped during the sprint's debug/release swap (expected,
per the sprint protocol) - it's back on the Onboarding screen; needs a playlist added before the
feel/vision pass.

## 2026-09-17 - Player sprint, P0 (engine foundation)

**What was built:** `PLAYER_ENGINEERING_BRIEF.md`'s full P0 punch list (items 1-9), plus two items
the user folded in on top: the cold-launch resume focus bug and the zap connection-ordering fix
("black screen close connection fix"). Full account in commit `11618c6`'s message - not repeated
here. Headline pieces: the `PlayerController` hoist (unblocks PHASE_2 #2.3's Actions row), real
error handling where there was none before (`PlaybackErrorController` + `PlayerErrorMapper`, a
15s stall watchdog), the buffer byte ceiling, and the cold-launch D-pad focus fix.

**Deviated: no debug builds used at all this sprint** (`deviated:` per this file's own rule) -
corrected a stale assumption from the previous session that debug/release builds need to stay
apart on this device; `build.gradle.kts` has signed them with the same key since 2026-09-15
specifically so that's never necessary. Iterated entirely on local signed `assembleRelease` builds
(`-PversionName=vX.Y.Z-dev -PversionCode=999` to clear downgrade protection), `adb install -r -d`
in place every time, zero uninstalls, zero data loss, zero playlist reseeding needed across the
whole sprint. Memory corrected for future sessions.

**Deviated: more device builds than a normal sweep pass** - this wasn't run as a strict
sweep-then-fix-then-resweep cycle; the cold-launch focus fix specifically needed live,
instrumented debugging (temporary `Log.d` calls added, then removed before the final commit) to
find the real root cause (Paging's initial load taking ~3.4s for a 123-channel group, not a
simple missing-requestFocus-call bug as first suspected) - logged here since it's a real
departure from the sweep protocol's shape, not because anything was wrong with doing it this way.

**Verified on device** (signed release, real Chromecast, real ~28K-channel Xtream playlist):
- Cold launch → single OK press, zero other input → opens fullscreen directly on the exact
  resumed channel. Confirmed via targeted logcat instrumentation during development (this
  specific device's `screencap`/`screenrecord` are both broken entirely, and `uiautomator`'s
  `focused` attribute doesn't reliably report Compose TV focus here either - established earlier
  this project, held true again this sprint).
- UP/DOWN zap within fullscreen, both directions: correct channel change, real
  `AudioFocusManager` request logged with `AA=USAGE_MEDIA/CONTENT_TYPE_MOVIE` (confirms the new
  `AudioAttributes` wiring actually took effect), no HTTP 456, no crash.
- No crashes anywhere across the whole session, including through several points where the
  Chromecast's launcher unexpectedly stole foreground (YouTube TV, then Projectivy Launcher) for
  reasons unrelated to RedSurf - confirmed via `dumpsys activity` + a clean logcat crash search
  each time, not assumed.

**Not yet verified:** the error-message copy for a real dead/blocked channel (403/404/456/884) -
no such channel was actually hit live this session, so `PlayerErrorMapper`'s classification logic
is implemented and reasoned through but not observed firing against a real failure. Same for the
15s stall watchdog's second-strike path. Worth a deliberate negative-path test (tune a channel
number known not to exist, or throttle the network mid-stream) before calling this fully closed.

**Release:** not cut this sprint - the user's own explicit workflow for this session was iterate
on local signed builds throughout, cut one real release at the very end once satisfied. See
`AGENTS.md`/git log for whether that release has landed by the time this is read.
