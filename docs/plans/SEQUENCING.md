# Execution sequencing — post-Lattice, 2026-09-22

**⏸ RESUME HERE, next session.** User's own words, ending tonight: "let's execute tomorrow and
pick up without missing a beat." Nothing is running, nothing is uncommitted, device is on the
genuine v0.37.9 (verify via `gh release list` before trusting that number's still current - this
session already hit one version-collision surprise). **The next action is Sprint 2**, directly
below - 9 real, already-root-caused bugs (file:line and mechanism given for every one, several
just deepened by a full-codebase audit against the new `docs/vision/FOCUS_MODEL.md` rule set,
committed `ca5b7b1`), none needing a design decision, ready to fix and device-verify as one batch
the moment the TV is free - no re-diagnosis needed, just launch it. After Sprint 2: Sprint 3
(double-row EPG rows + the two confirmed icons, user's explicit high priority), Sprint 4 (public
EPG supplement, two-phase), Sprint 5 (nav-strip auto-hide), Sprint 6 (the preview→fullscreen grow
transition), plus the separate Teleport Menu debugging track - full detail in each section below.

**What this is:** every open item from `docs/plans/TESTING_OUTSTANDING.md` plus the 2026-09-21/22
feedback round, grouped into an actual build order with reasoning. Nothing here is built yet -
this is the plan the user reviews/corrects before any sprint launches. Priorities per the user,
2026-09-22: **(A) TiviMate parity, (B) sound architecture/state/caching, with performance treated
as a high concern throughout** - both named explicitly, not inferred.

## Sprint 1 — CLOSED, 2026-09-22 (second pass, real fixes + real verification)

**All must-do and nice-to-have items from the Opus consult shipped and verified on device,
real release v0.37.8 (versionCode 130).** Built: (M1) EPG sync scheduling moved to *after*
`AppState.Loaded` instead of before, plus a real `setInitialDelay` (45s) and a per-playlist
stagger (`staggerIndex * 5min`) so multiple playlists' downloads can never start concurrently by
construction - the manual "Update EPG now" button deliberately stays unstaggered (a user pressing
it wants it now); (M2) Room's `RedSurfDatabase.getDatabase()` now sets a dedicated 4-thread
`queryExecutor` and a single-thread `transactionExecutor`, instead of sharing Room's own default
`ArchTaskExecutor` pool with every other read/write in the app; (M3) `XmlTvParser`/`EpgSyncWorker`
now group 10 of the existing 1000-row batches into one `db.withTransaction` instead of committing
every batch (~20 commits per sync instead of ~200); (M4) `.setJournalMode(WRITE_AHEAD_LOGGING)`
pinned explicitly - confirmed via live `dumpsys dbinfo` to already be the effective mode, so this
removes a device-dependent branch rather than fixing the measured slowness itself, stated plainly
rather than credited; (M5) the redundant `epg_programs` index dropped (real `MIGRATION_9_10`, a
strict prefix of the table's own implicit PK autoindex, zero read benefit, real write cost);
(N1) a real covering index added for `getLiveGroupCounts()` (`streamType, isHidden, playlistId,
groupName`) - the previously-unindexed full-scan bug the Opus consult found; (N2) one
`ANALYZE` after each successful sync, never on the launch path.

**Verified on device, real before/after numbers, not "should be faster":**
- `dumpsys dbinfo` confirmed `journalMode=WAL`, `Max connections: 4` - settles the
  journal-mode question for good; WAL was already active before this pass, as the Opus consult
  predicted.
- **The exact statement the Opus consult flagged as a live victim of contention** -
  `getLiveGroupCounts()` - measured **3286ms** pre-fix (via `dumpsys dbinfo`'s real per-statement
  duration) and **254ms** post-fix on a clean second cold launch (the first post-install launch
  includes one-time migration/schema-validation overhead and isn't a fair comparison - excluded).
  **13x faster**, and the only statement over 50ms anywhere in a 20-second post-launch window
  (was: multiple 2-3s statements).
- **The app-level `gridQuery` line** (the original 6893ms measurement from the first pass) now
  reads **361ms** on the same clean second cold launch - **19x faster**.
- `epgBackfill` correctly reports `stale=false` for all 3 real playlists (no unnecessary re-sync);
  **zero `epgSync -> start` lines fired in the 20-second cold-launch window**, confirming EPG sync
  is genuinely off the critical path now. Honest gap: all 3 playlists happened to be fresh this
  pass, so the per-playlist *stagger* specifically (as opposed to the deferral) was never observed
  preventing a real concurrent-download collision live - the code path is verified by reading and
  by the deferral's own confirmed effect, not by watching staggered downloads actually happen.
- **Flat-memory-at-scale re-check (item 2): holds.** `dumpsys meminfo` at the real current scale
  (3 playlists, ~140K channels, roughly 2-5x `PHASE_1.md` 1.6's original 28-60K test) measured
  **~131-136MB PSS** - still inside that same original 112.8-145.7MB range. Paging genuinely still
  bounds memory at this larger scale, not just the smaller one it was last proven on.
- **Channel-zap sluggishness (item 3): inconclusive, not verified.** Four `KEYCODE_DPAD_UP`
  presses while RedSurf was confirmed foreground and genuinely playing (real audio focus,
  `positionWatchdog` ticking normally) produced zero `zap dir=` log lines - the keys didn't
  appear to reach `PlayerScreen`'s zap handler at all, for a reason not root-caused this pass
  (a plausible but unconfirmed theory: repeated cold-launches earlier in this same session for
  the dbinfo measurements may have triggered "auto-play last channel on launch," landing the app
  directly in fullscreen with `PlayerOverlay.Controls` or a picker open rather than `None`, before
  the zap attempt began, changing what UP actually does). Reported honestly as unverified rather
  than guessed - worth a clean, isolated re-check next time, starting from a known browse-view
  state rather than stacked on top of several prior cold launches.
- Install-in-place (no uninstall) confirmed the v9→v10 migration ran cleanly against the real
  device database with its real ~140K rows - no crash, no fallback to destructive migration, no
  data loss. Builds clean, 23/23 unit tests pass.

**Resolved, 2026-09-23 (no code change needed):** the "docs-only commits triggering real CI
releases" mystery flagged twice as open - checked the actual commit messages (`5b9e1ae`,
`ca5b7b1`, `acff831`, `8fc80de`) directly; none of them contained the literal string `[skip ci]`
at all. Not a CI/tooling bug - just inconsistent habit across commits within the same session.
v0.37.10/v0.37.11 both confirmed as real (harmless, no-op) releases from exactly this. Nothing to
fix; just remember the marker consistently going forward.

## Sprint 2 — Cheap, mechanical bug fixes (batch together, one pass)

Nine focus/state findings (five original bugs plus four from a systematic pattern-level audit),
none needing a design decision - bundle per this
project's own "batch related changes" convention rather than nine separate releases:

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
5. **Back from a cold-launch-autoplay fullscreen doesn't land on the resumed channel - sometimes
   doesn't even land in the grid at all** (found 2026-09-22, root-caused, not yet fixed). User's
   report: pressing Back after the app launches straight into fullscreen (auto-play last channel)
   lands focus on Categories, not the channel that was playing. Two stacked bugs in the same spot,
   `LiveTvScreen.kt:399-412`'s `LaunchedEffect(isFullscreen)`:
   - **Reliability bug, likely the dominant one:** entering fullscreen retries focus-claim up to 20
     times (3s) before giving up; *exiting* only tries **once**, 100ms after `isFullscreen` flips,
     with no retry, and the failure is silently swallowed (`runCatching`). On a fast cold-launch
     followed immediately by Back, the grid for the resumed category has very plausibly not
     finished composing yet, the single attempt fails, nothing claims focus afterward, and Compose's
     default spatial search picks whatever's nearest - plausibly Categories. Fix: give the exit
     branch the same bounded-retry shape as the entry branch already has.
   - **Already-known, deliberately-logged scope trim, compounding even when the above works:**
     `gridFocus` is a single shared FocusRequester on the grid's row 0, not channel-aware (see the
     doc comment directly above it in the same file, and `LIVE_TV_GUIDE_MERGE.md`'s own "known,
     wider-reaching scope trim" note) - so even a successful claim lands one row off from the exact
     channel, not on it. Real fix needs the grid to find and focus the row matching
     `focusedChannel`'s `streamId`, the same pattern `ChannelsColumn` used to have before the merge
     removed it.
   Fix both together, same file, same pass - fixing only the retry without the channel-aware match
   would still leave "the wrong row" as a visible miss.

**Items 6-9 added 2026-09-22 - a systematic codebase-wide focus audit**, done in response to the
user asking for pattern-level analysis rather than one-bug-at-a-time reports. Full rule set this
audit was checked against: `docs/vision/FOCUS_MODEL.md` (new canonical reference, read it before
touching any focus-handling code - these findings are the audit's output, that file is the rules
it was measured against). All four are read-and-reasoned findings, not yet device-verified.

6. **The original 2026-09-13 "Categories ↔ Channels via LEFT/RIGHT doesn't restore the exact row"
   known instance is still open** (`AGENTS.md`) - it never got fixed, it just changed shape. Back
   when it was written, "Channels" meant `ChannelsColumn`, which has since been fully fixed
   (channel-aware `returnFocusRequester`, confirmed correctly built by this audit - it's just no
   longer used in the browse view, relocated intact to the player's own LEFT-edge overlay,
   `ChannelListOverlay.kt`). The Guide merge (2026-09-20) replaced it in the browse view with
   `EpgGridColumn`, which was built with only the coarse row-0-only `gridFocus` (item 5's second
   bug) - the channel-aware version was explicitly logged as "worth a real follow-up... not
   attempted in this already-large pass" at the time and never came back. High confidence - this
   is the same unresolved bug, not a new one, just easy to lose track of since it moved files.
7. **Three independent, uncoordinated effects all target the single shared `gridFocus`
   `FocusRequester`** (`LiveTvScreen.kt` - `LaunchedEffect(gridChannels)` at ~391, the
   `LaunchedEffect(isFullscreen)` else-branch at ~409-412, and
   `LaunchedEffect(claimInitialFocusTrigger)` at ~417-430), keyed on three unrelated triggers, with
   no coordination between them (`FOCUS_MODEL.md` rule 6). Structural risk, not directly observed
   in isolation this pass: whichever effect's retry timing happens to land first wins, which can
   plausibly vary run to run. **Named as a real candidate explanation for why Teleport Menu's
   `jumpToGroup`/`returnToChannelGroup` (both route through `claimInitialFocusTrigger`, which
   targets this same contested `gridFocus`) tested as "verified" in the automated build sweep but
   as broken on the user's real remote** (`TELEPORT_MENU.md`'s own already-logged discrepancy) -
   plausible, not proven; worth checking directly (does disabling/delaying the other two effects
   make Teleport's jumps reliable?) before assuming this is *the* explanation rather than *a*
   contributing one. Medium confidence.
8. **`GroupsColumn` (Categories) has no boundary guard on UP at all** - no `onPreviewKeyEvent`
   anywhere in the file or in `LiveTvScreen.kt` around it, unlike `SettingsScreen.kt`'s rail/pane
   router or `PlayerScreen.kt`'s own key router (both of which adopted explicit interception after
   finding default behavior/`focusProperties.exit` unreliable - `FOCUS_MODEL.md` rule 7). Relies
   entirely on Compose's default directional search stopping at the list's true top - which
   item 1's already-reported "UP escapes to the nav-strip prematurely" bug demonstrates it doesn't
   reliably do, plausibly worsened by the mixed header-row/item-row content type multi-playlist
   accordion mode introduces. This *is* item 1, not a new item - listed here because the audit
   found the actual mechanism (no guard exists at all, not a misbehaving one) which changes the
   fix shape: item 1 needs a real `onPreviewKeyEvent` boundary guard added, not a bug fixed in an
   existing one. High confidence (absence of any guard is a direct code fact, not inference).
9. **`SettingsScreen` is still conditionally composed/torn down on every destination switch away
   from Settings** - the exact same architectural pattern Sprint 1 just fixed for `LiveTvScreen`
   (`FOCUS_MODEL.md` rule 4), confirmed by reading `AppShell.kt`'s routing `when` block directly
   (`destination == NavDestination.Settings -> { ... SettingsScreen(...) }`, still a plain
   conditional branch, no `visible` param). **Medium-confidence assessment that this is currently
   low-severity, not zero-severity:** `selectedSettingsCategory` is hoisted to `AppShell` and the
   rail's own re-entry redirect correctly re-derives its target from that persisted value on every
   remount (confirmed by reading `SettingsScreen.kt:135-138`), and Settings has no expensive async
   Paging/query state analogous to Live TV's grid to lose - so a full remount likely looks
   behaviorally close to correct today, unlike Live TV's case. **But that same rail re-entry claim
   is itself a single-shot `runCatching { railFocus.requestFocus() }` with a bare 50ms delay and no
   retry loop** (`FOCUS_MODEL.md` rule 3) - structurally the same reliability gap as item 5's
   fullscreen-exit bug, just not yet reported as a live failure, plausibly because Settings'
   category rail is short and composes fast rather than because the code is actually safe. Worth
   the same `visible`-param treatment preemptively, before a future Settings category with real
   async state (e.g. a live-data-backed one) turns this into a repeat of Live TV's bug rather than
   waiting for that report.

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
