# Execution sequencing — post-Lattice, 2026-09-22

**What this is:** every open item from `docs/plans/TESTING_OUTSTANDING.md` plus the 2026-09-21/22
feedback round, grouped into an actual build order with reasoning. Nothing here is built yet -
this is the plan the user reviews/corrects before any sprint launches. Priorities per the user,
2026-09-22: **(A) TiviMate parity, (B) sound architecture/state/caching, with performance treated
as a high concern throughout** - both named explicitly, not inferred.

## Sprint 1 — Performance & state audit — PARTIALLY DONE, 2026-09-22

**Shipped, real fix:** the exact conditional-composition trap suspected. `AppShell.kt` used to
remove `LiveTvScreen` from composition entirely every time `destination` changed away from Live TV
and back - not just the data (`selectedGroup`/`focusedChannel`, already hoisted since 2026-09-15)
but every *derived* piece of state downstream of it: the categories Flow subscription,
`GroupsColumn`'s scroll position and one-shot focus-claim latch, and the grid's own non-paged
`channelsInGroup`/`programsForChannels` query results - all torn down and rebuilt from zero on
every round trip. Fixed by keeping `LiveTvScreen` permanently composed once a playlist is active
and adding a `visible: Boolean` param that gives it real size or `Modifier.size(0.dp)` (zero
layout cost, can't receive focus/input) instead of removing it from the tree; a new
`LaunchedEffect(destination)` in `AppShell` reclaims focus via the existing (already
re-triggerable) `liveTvClaimInitialFocusTrigger` mechanism whenever `destination` becomes LiveTv
again, now cheap since nothing has to wait on a fresh load first. Real device measurement: on
return to Live TV from Settings, zero `gridQuery`/`initialFocus`/`epgBackfill` log lines fired
(previously every one of those re-ran). **Builds clean, 23/23 tests pass, this specific result
verified on device via logcat** - visual re-confirmation of the returned screen itself was cut
short by external interference (see below) before a screenshot could be taken.

**Not fixed, real open question - escalating per the sprint's own instructions, not guessing:**
cold-launch itself is separately, seriously slow at real scale, and this is NOT explained by the
conditional-composition trap above (that only concerns *re-entry*, not first load). Measured live:
`channelsInGroup()` alone took **6893ms** for a 134-channel result on a table with ~140K total
channels across 3 playlists; one playlist's `epg_programs` table alone holds **125,175 rows**. The
`channels` table already has a matching index (`playlistId, streamType, groupName`) and the query
result set is small (134 rows), so a straightforward missing-index explanation doesn't fit cleanly
- ruled out as the likely sole cause, not confirmed as ruled out entirely. Real candidate causes,
none confirmed: (a) DB connection contention - three `EpgSyncWorker` backfill-check queries
(`COUNT(*)` against tables up to 125K rows) fire on `Dispatchers.IO` at the same moment as this
query, and Room's default journal mode wasn't verified on this specific device (checked
`ro.config.low_ram`, unset, so WAL should apply by Room's own default heuristic - but this wasn't
confirmed against the actual open database, `run-as` doesn't work on this release build); (b) the
grid's own `programsForChannels()` call (not separately timed this pass - the log line only wraps
`channelsInGroup()`) could be the larger of the two costs, not yet isolated; (c) something else
entirely not yet identified. **This needs either a proper `EXPLAIN QUERY PLAN` against the real
database or an Opus consult with this data in hand, per the sprint's own escalation instructions -
not a guessed fix.** The user's own three sluggishness reports are likely a mix of both issues
(this pass fixes the re-entry half, not the cold-load half) - test again once the cold-load
question is resolved, not before.

**Item 2 (flat-memory-at-scale re-check) and item 3 (channel-to-channel zap sluggishness):** not
reached this pass - the cold-launch finding above was significant enough to warrant stopping to
report rather than continuing to accumulate findings without resolving the first one. Real
`dumpsys meminfo` at cold launch: 117801 KB PSS (~115MB) - not yet compared against a
before/after-navigation delta or the historical ~112-145MB range from `PHASE_1.md` 1.6, since that
comparison needs the cold-load question settled first to be a fair read.

**Stopped, not finished - external interference:** `youtube.tv`'s `MainActivity` took real device
foreground mid-verification (`dumpsys activity activities` confirmed `mFocusedApp` was YouTube,
not RedSurf) - the same class of household-remote interference logged in the Lattice sprint. Key
injection stopped there rather than fighting a live person for the remote, per this project's own
established practice. Resuming needs a free TV.

**Also found, unrelated to this sprint's own scope, noted for the record:** the last several
"docs:"-only commits (`b97c643` onward) triggered real CI release cuts (v0.37.3/4/5) despite not
touching app code - whatever `[skip ci]` convention was applied to earlier docs commits didn't
apply to (or was dropped from) these. Not fixed here - out of scope for a performance audit, but
worth a look before it produces more release noise.

**Opus consult, 2026-09-22 - the coordinator's own WAL/TRUNCATE hypothesis was wrong on
mechanism, right on shape.** One live read-only check settled the journal-mode question with zero
code: `adb shell getprop ro.config.low_ram` → unset, and Room 2.6.1's actual `AUTOMATIC.resolve()`
source (read directly) only falls back to TRUNCATE when that property is set - so WAL is almost
certainly already active, and the "reads blocked behind an exclusive write lock" theory is very
likely dead. The real picture, ranked:

1. **The 6893ms measurement itself is suspect.** `LiveTvScreen.kt`'s timer wraps `LaunchedEffect`
   code running on `AndroidUiDispatcher.Main`, so the interval contains dispatch-to-IO-thread,
   queue wait, the actual query, *and* resuming back onto a main thread that's mid-cold-launch
   (first composition, `WaveSpinner`, ExoPlayer init) - not just SQL time. It also used
   `System.currentTimeMillis()` (wall-clock, can jump on an NTP resync at boot) instead of
   `SystemClock.elapsedRealtime()`.
2. **Room's default executor is a shared, fixed 4-thread pool for every read and write in the
   whole app**, never overridden (`setQueryExecutor`/`setTransactionExecutor` are never called).
   Cold launch runs 3 `EpgSyncWorker`s concurrently (WorkManager's own default executor sizes to
   3 threads on this 4-core SoC) doing ~125-200 batch-insert calls each against a 201K-row feed -
   strict FIFO, no priority. The 134-row Live TV read is very plausibly just queued behind that,
   not blocked by a lock.
3. **Raw I/O saturation** - three concurrent 67MB downloads + ~600 write transactions (one commit/
   fsync per 1000-row batch) on eMMC with 880MB free, independent of any locking model.
4. **A second, previously-unnamed real bug, on the same cold path:** `getLiveGroupCounts()` (the
   query that powers the Categories column) has **no `playlistId` constraint** and isn't covered
   by the existing index at all - a full `SCAN channels` + temp B-tree `GROUP BY` over the whole
   ~140K-row table, every cold launch, with `sqlite_stat1` never populated (nothing in this app
   ever runs `ANALYZE`). This is a real, separately-fixable finding the original sprint never
   measured.
5. Room's `InvalidationTracker` posts its own write transaction after every commit
   (`refreshVersionsAsync`), roughly doubling the ~600 commits to ~1200 - overhead, not a cascade
   (nothing observes `epg_programs` via `Flow`/`PagingSource`, so no triggers are installed on it).
6. `index_epg_programs_playlistId_channelEpgId` is a strict prefix of the table's own implicit
   primary-key autoindex `(playlistId, channelEpgId, startTime)` - redundant, pure write
   amplification on ~200K rows every sync, serves no query the autoindex can't already.
7. `MainViewModel.checkLocalCache`'s `epgRows = ...countForPlaylist(...)` is fetched only to put in
   a log string - three wasted `COUNT(*)` scans over 125K-row tables on the cold path, per launch.

**Fix set given, ranked must-do/nice-to-have/unnecessary** (full detail and exact code in the
consult transcript, this session): **must-do** - get EPG sync off the cold-launch critical path
entirely (move the sync-scheduling loop to *after* `AppState.Loaded` is set, add a real initial
delay, stagger the 3 playlists so they never download concurrently by construction); give Room
separate query/transaction executors; batch ~10 of the 1000-row inserts into one
`withTransaction` instead of committing every batch; explicitly pin `JournalMode.WRITE_AHEAD_LOGGING`
(removes a device-dependent branch even though it's probably not *the* fix - "say that plainly in
the commit message rather than letting it get credited"); drop the redundant index.
**Nice-to-have** - a real covering index for `getLiveGroupCounts()`
(`streamType, isHidden, playlistId, groupName`); one `ANALYZE` after each import, never on the
launch path. **Explicitly unnecessary, don't spend the sprint here** - `busy_timeout` tuning (not
the actual contention point under WAL), a second read-only database instance (duplicate
`InvalidationTracker`/triggers, a known footgun), lowering `BATCH_SIZE` (commit count is the cost,
not memory), raising `cache_size` (wrong trade at 449MB free).

**Verification protocol for next pass, before shipping any fix blind** (this project's own
"never claim something works because you wrote plausible code" rule, applied to a design decision
this time, not just an implementation): `adb shell dumpsys dbinfo com.redsurf.tv` works on this
release build with **zero code changes** (it's a system dump via `ActivityThread`, not `run-as`)
and prints real connection-pool size (settles WAL-vs-not definitively) plus **per-connection real
statement durations with full SQL text** - the single most direct way to see whether the channels
query itself is slow or just queued. A four-marker instrumentation split
(`toIo`/`query`/`resume`/`total`, using `SystemClock.elapsedRealtime()`) isolates dispatch
overhead from real SQL time. A concrete technique for real `EXPLAIN QUERY PLAN` against the actual
140K-row database despite the non-debuggable build: checkpoint the WAL, copy all three DB files
(`-wal`/`-shm` included) to the app's external files dir (readable via plain `adb shell`, no
`run-as` needed), `adb pull`, then real `sqlite3`/`.eqp on` on the Mac against the exact live data
- gives an uncontended baseline to compare the device's contended numbers against.

**Blocked from measuring further right now, same class as before:** a household member had
YouTube in foreground (`dumpsys activity activities` confirmed) when this consult tried its own
live check - deliberately did not steal foreground to push further. Suggested order for the next
pass: `dumpsys dbinfo` + the `getprop` check first (five minutes, free, decides whether pinning
WAL is the headline fix or a footnote) → the four-marker instrumentation → the must-do fix set as
one batch → the nice-to-haves last, behind a real `EXPLAIN QUERY PLAN`.

## Sprint 2 — Cheap, mechanical bug fixes (batch together, one pass)

Four independent, well-understood bugs, none needing a design decision - bundle per this
project's own "batch related changes" convention rather than four separate releases:

1. **Categories UP-scroll escapes to the nav-strip prematurely** (`GroupsColumn`'s UP-key
   handling, DOWN is fine) - real, reproducible, user-confirmed.
2. **Grid focus-on-entry lands on the rightmost visible cell, not the current/"now" slot** - should
   default to "now" (or the nearest available slot to it) every time a row is entered fresh.
3. **Preview silently dies instead of reconnecting** - `PREVIEW.md`'s original design deliberately
   skipped a stall watchdog for simplicity; the user has since clarified preview should run
   perpetually until the user navigates away or the app closes. Needs at least a light reconnect
   path, not the full fullscreen-grade watchdog ladder.
4. **Scrolling Categories repeatedly steals focus into the grid** (found 2026-09-22, root-caused
   by reading the code, not yet fixed or device-verified). `LiveTvScreen.kt:391` -
   `LaunchedEffect(gridChannels) { if (gridChannels.isNotEmpty()) { ...
   gridFocus.requestFocus() ... } }` fires an **unconditional** focus-claim onto the grid's first
   row every time `gridChannels` changes - which happens on every category-settle debounce while
   just browsing Categories, not only on a genuine "enter the browse screen" moment. User's report,
   verbatim: "if you stop your scrolling the focus annoyingly keeps going to the top channel in the
   channel category it stopped at. Over and over... stopping on a channel group should load the
   channel list, but the focus shouldn't automatically jump to the channel section." **This effect
   looks redundant, not just wrong:** two other effects in the same file already handle the
   legitimate "land on the grid" cases correctly - `LaunchedEffect(isFullscreen)`'s else-branch
   (returning from fullscreen) and `LaunchedEffect(claimInitialFocusTrigger)` (cold launch). Fix
   direction: gate this effect the same way `ChannelsColumn`'s old fresh-entry redirect worked
   (`state.hasFocus && !hadFocus` - only claim when transitioning *into* focus, not on every data
   refresh), or remove it outright if the other two effects already cover every real case once
   checked carefully - don't guess which without checking, per this project's own rule.

## Sprint 3 — Double-row EPG channels + the two confirmed icons (high priority, not deferred)

Corrected priority per the user, 2026-09-22 - this is a real want for the EPG sprint, not a
nice-to-have. Bundled together because double-row is *what gives the icons room to exist*
without cramping the channel name - designing them separately would mean revisiting the row
layout twice.

1. **Selectable double-height channel rows** (TiviMate feature: a focused/selected row can render
   2x tall when enabled) - the real-estate unlock for everything below.
2. **Currently-playing indicator** - confirmed via the user's own reference screenshots
   (`TiviMate_CatchupIconExample.jpg`): a small blue play triangle (▶) inline in the channel row,
   not a row-colour change.
3. **Catch-up availability icon** - confirmed via the same references: a small white
   history/rewind glyph, inline in the row right after the channel name, **after** the play
   triangle when both apply (`name → ▶ → history-icon → grid`). Per-channel, not per-cell.
   **Data-only this pass** - store `tv_archive`/`tv_archive_duration` from `get_live_streams`
   (not captured today) and render the icon when present. Actually *playing* catch-up content
   (a distinct playback URL pattern, scrubbing into past-but-archived cells) is real, separate
   scope - own brief, sequenced later, not bundled into "does the icon show up."

## Sprint 4 — Public EPG source supplement (added 2026-09-22, user request)

**Origin:** the user's own real test data keeps confirming coverage is sparse - "guide entries
are scant" (2026-09-21 test), and this session's provider-coverage check found only ~27% of one
provider's channels carry an `epg_channel_id` at all. The user's actual question is empirical:
**"I need to see if our app is able to provide so much missing programming data"** - i.e. prove
the supplement helps before investing in the full configurable UI, not build the whole thing
blind.

**Naming correction, gently:** "EPGGenius" was already researched in an earlier session
(`AGENTS.md`'s EPG data-source backlog entry, 2026-09-15) - it didn't turn up as a real, distinct
hosted service under that name; likely a misremembering of EPG.lat (cited as integrating well
with TiviMate) or one of the sources below. Worth using one of the actually-confirmed-real ones
rather than chasing a name that may not correspond to anything live.

**Real, vetted candidates from that same research** (`AGENTS.md`): **EPGSHARE01**
(epgshare01.online - free, hosted, static XMLTV by country/source, no self-hosting, "for LEGAL use
only," updated ~daily) is the closest to a drop-in URL and the obvious Phase A pick; **open-epg.com**
(free public XMLTV by country, plus a channel-ID-mismatch editor tool) is the fallback candidate;
**iptv-org/epg** is real but self-hosted (you run the scraper), more setup than this phase needs.

**Two-phase split, mirroring this project's own P0/P1 discipline elsewhere (prove it cheaply
before building the full configurable version):**

- **Phase A - proof of concept, answers the user's actual question.** Wire exactly one public
  source (EPGSHARE01) as a per-channel fallback: when a channel has no provider EPG listing,
  attempt a match against the public source's `<channel id>` via the same `tvg-id`/`epg_channel_id`
  matching already used for provider data. **A real technical angle worth trying:** the user's own
  playlists already use country-prefixed category names ("US|", "UK|", etc. - the same prefixes
  Root Category's parsing already reads) - that's a plausible signal for which of EPGSHARE01's
  per-country files to even fetch, worth testing before assuming a fixed file list. No settings
  UI beyond a single on/off - the deliverable is a real, measured answer to "how many more
  channels get real programme data with this on," not a shipped feature yet.
- **Phase B - the full locked design, only once Phase A shows it's worth it.** Already fully
  specified, nothing new to decide: `AGENTS.md`'s "EPG Sources UI - DECIDED, 2026-09-17" entry -
  per-playlist "Provider EPG"/"Fallback with public sources" toggles, a global "Supplement all
  Playlists" bulk-setter, the "Manage Sources" catalog (built-in sources + custom XMLTV URL +
  promoting a playlist's own provider endpoint into the shared pool), manual reorderable tie-break
  priority when multiple sources both have data. Also unlocks the accessibility-aware
  supplemented-data colour+border indicator on the grid (`AGENTS.md`, 2026-09-15) - moot until
  there's a second data source to actually distinguish from provider data.

## Sprint 5 — Nav-strip auto-hide (already scoped, ~1 hour)

Unchanged from the 2026-09-21 scoping in `TESTING_OUTSTANDING.md` section F - default On, one
Settings row cycling 3s/5s/10s/Off, applies everywhere, reveal-on-focus-reaching-for-it (not
any-key-press). Natural pairing with Sprint 3 since both are about EPG-screen real estate, but
independent enough to land in any order relative to Sprints 3-4.

## Sprint 6 — Preview → fullscreen grow transition (biggest single risk item, own pass)

User's steer: prioritize real TiviMate parity, reached via sound architecture. Read as: the
target is the true version (one persistent player + surface, the container animates bounds while
playback never stops), not a freeze-frame near-miss - but this is a genuine architecture reversal
of Preview-on-OK's original two-decoder design (chosen specifically to avoid a prior crash class),
so it deserves its own scoping pass rather than riding along with anything else. Candidate for an
Opus consult on the choreography itself (the same lane that produced Teleport's "Curl" and the
Lattice), since a persistent-surface handoff has real technical unknowns (surface reuse across a
Compose recomposition boundary, whether `SurfaceView`/`TextureView` survives a bounds animation
cleanly on this hardware) worth thinking through before committing to the approach, not just
building and finding out. Reverse transition (fullscreen shrinking back into the hero band on
Back) is the same mechanism run backwards, not separate scope.

## Separate track — Teleport Menu, real debugging session (not a background build)

Different in kind from everything above: last time's automated build-and-sweep produced a false
"verified" reading the user's own remote immediately contradicted, so this needs an actual
interactive session with the user driving the remote in real time, anchored against the "Faraz
Strong" playlist per the user's own worked examples (2026-09-21/22):

- **Nav-Strip** - fix the existing "jump to current destination's pill" behavior (confirmed
  broken, not a redesign) - the configurable-fixed-pill idea goes to backlog, explicitly low
  priority, sequenced whenever, not part of this pass.
- **Playlist Root** - retarget to the playlist's own accordion header row in Categories
  (`GroupsRow.Header` already exists and already does exactly this expand/collapse job - just a
  different focus target, no new UI).
- **Playlist Favorites** - build per-playlist (TiviMate-confirmed, user-agreed 2026-09-22): a
  Favorites row as the first category under each playlist, reusing existing category/channel
  machinery. Real feature, not just un-greying the Teleport row - Favorites itself doesn't exist
  yet anywhere in the app.
- **Root Category** - confirmed working logic (prefix-parse on earliest delimiter), re-verify
  against Faraz Strong's real categories (example given: "US|24/7 ACTION/ADVENTURE Raw 60fps" is
  first in the "US|" family).
- **Root Channel Group** - the original rationale already exists and is sound ("take me back to
  exactly where what I'm watching lives," distinct from Playlist Root and Root Category) - no new
  invention needed, just verify/fix the implementation.
- **Return to fullscreen**, **Exit RedSurf** (confirmed: standard `finish()`, not a process kill)
  - re-verify only.
- T.5 discoverability tips - still needs the user's own gut-check on a threshold number before
  any code; not blocking the rest of this track.

## Standing, not a sprint

- **Build Settings alongside every feature from here on** (user's own standing instruction,
  2026-09-20/21) - not a discrete task; applies to every sprint above that touches a togglable
  behavior (double-row, auto-hide, preview reconnect timing, etc. all get real Settings rows as
  part of their own sprint, not bolted on after).
- **General direction: move Settings toward full TiviMate parity sooner than later** (user,
  2026-09-21) - a standing lean when choosing what to build next once the above clears, not an
  item with its own acceptance criteria yet.

## What's deliberately not sequenced here

- Catch-up *playback* (own future brief, per Sprint 3's note).
- The configurable-default Teleport nav-pill (backlog, explicitly low priority).
- App-wide density-token rollout beyond the EPG grid, time-preserving UP/DOWN grid cursor,
  `ProgrammeInfoCard` restyle, playlist detail/edit page - all still real, all still logged in
  `AGENTS.md`'s backlog, none raised as urgent this round. (Public-source EPG supplement moved
  into Sprint 4 above, 2026-09-22 - no longer in this unsequenced list.)
