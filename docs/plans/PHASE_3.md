# Phase 3 — EPG data + the merged Live TV/Guide screen

**Origin:** the "Live TV/Guide merge" decision on record in `AGENTS.md` (user, 2026-09-11), plus
the EPG data-source/resolution-order/EPG-Sources-UI decisions locked 2026-09-15/17 - all written
down well before this sprint, never built. Slotted next per the roadmap once Teleport Menu was
paused (user, 2026-09-19) - Teleport Menu's own real-device bugs are tracked separately in
`docs/plans/TELEPORT_MENU.md` and are not part of this brief.

**This brief is a first draft, same convention as `TELEPORT_MENU.md`** - **(confirm)** flags
genuine guesses; everything else is either already locked in `AGENTS.md` or a small, low-risk
implementation detail.

## What's already there, unexpectedly

Before drafting this I checked the actual code rather than assuming Phase 1/2's "EPG never
populates, no worker scheduled" note still held. It's half true: `EpgSyncWorker.kt`,
`XmlTvParser.kt`, `EpgDao.kt`, and `EpgProgramEntity` all already exist and look production-shaped
(the parser is a real streaming `XmlPullParser` with 1000-row batch inserts, not a stub). What's
missing is entirely the wiring and the UI:

- `EpgSyncWorker` is never enqueued anywhere - no `WorkManager` call references it. Dead code.
- It takes one global `EPG_URL` input - no concept of "per playlist," which conflicts with the
  already-locked per-playlist EPG Sources design (`AGENTS.md`, 2026-09-17).
- `EpgProgramEntity`'s primary key is `channelId-startTime` with no `playlistId` - the exact same
  cross-playlist collision class already fixed once for `ChannelEntity` (`BACKLOG_SWEEP.md` #13)
  and again for `RecentChannelEntity` (`PHASE_2.md` decision 14) - `epgChannelId` values are only
  unique within one provider's own feed, so two playlists can collide on the same id.
- No Guide screen exists. `NavDestination.Guide` is a nav-strip pill wired to nothing real; inside
  the player, "Guide" (`PlayerScreen.kt`'s `onOpenGuide`) currently just exits fullscreen back to
  the plain Live TV browse column. There's no timeline grid anywhere in the codebase.

So this phase is real ground-up UI work plus fixing/wiring already-written data-layer code, not
starting from nothing.

## P0 vs. P1 - what ships this sprint, what's deferred

Mirroring `PLAYER_ENGINEERING_BRIEF.md`'s own P0/P1 split (engine foundation first, polish after).
**P0 is "real EPG data flows in, and the merged screen shows it" - enough to actually watch TV
with a working guide.** P1 is the EPG Sources *settings UI* and the presentation refinements that
don't block a first working version:

**P0 (this sprint):**
1. Fix the data model (`playlistId` scoping) and the sync worker (per-playlist URL, real
   scheduling) - decisions 1-2 below.
2. The merged Live TV/Guide screen itself: categories left (reusing `GroupsColumn` as-is), a real
   EPG timeline grid on the right replacing today's plain channel-list column - decisions 3-5.
3. Wire existing nav entry points (`NavStrip`'s Guide pill, the player's "Guide" button) to the
   real screen instead of today's placeholder behavior.

**P1 (explicitly deferred, already fully designed in `AGENTS.md` - not re-litigated here):**
- The EPG Sources Settings UI (per-playlist "Provider EPG"/"Fallback with public sources"
  toggles, the global "Manage Sources" catalog, custom XMLTV URL entry, promoting a playlist's
  provider endpoint into the shared pool, drag-to-reorder tie-break priority).
- Public-source fallback sync itself - P0 only pulls each playlist's own provider EPG endpoint.
  A channel with no provider listing just shows an empty guide slot in P0, honestly, rather than
  silently guessing at a source. This is real functionality lost vs. the target design, not a
  shortcut being hidden - flagging it plainly.
- The supplemented-data colour-legend indicator (`AGENTS.md`'s accessibility-aware colour+border
  spec) - moot until P1 adds a second data source to distinguish from provider data.
- The top nav's auto-hide-on-idle behavior (`AGENTS.md`'s "TiviMate's left rail" reference) -
  real, but a separate, independent polish item from getting the grid itself working.
- Programme reminders/DVR-style actions on a future timeslot - never decided or asked for; not
  in scope at all, P1 or otherwise, unless it comes up as a real request later.

## Decisions

1. **`EpgProgramEntity` gets a `playlistId` column, matching every other cross-playlist entity's
   own fix for the same bug class.** New composite primary key `(playlistId, channelEpgId,
   startTime)`, indexed on `(playlistId, channelEpgId)` for the grid's own per-channel query.
   Destructive migration - no live users, sprints already wipe the device routinely (matches this
   project's own established practice for exactly this situation).

2. **Per-playlist EPG sync, real scheduling.** For each Xtream playlist, the URL is
   `{serverUrl}/xmltv.php?username={username}&password={password}` (Xtream's standard XMLTV
   export endpoint - the same shape `parseXtreamCredentials` in `PlayerScreen.kt` already parses
   for the Connections badge, reused here rather than re-parsing credentials a second way).
   **(confirm: M3U-only and Stalker playlists have no equivalent - P0 simply has no EPG data for
   them, same as a channel-level gap; M3U's own inline `tvg-id`/EPG-URL-in-playlist convention,
   if this provider's M3U even has one, is P1 territory, not guessed at here.)**
   `WorkManager.enqueueUniquePeriodicWork` per playlist, unique work name keyed on `playlistId` (so
   multiple playlists' syncs never collide or cancel each other) - daily cadence, matching the
   public-source refresh cadence already noted in `AGENTS.md`. Also fire one **immediate one-off
   sync** the moment a playlist is added, so the guide isn't empty for up to a day after setup.

3. **Guide replaces the "Guide" nav-strip destination's placeholder; Live TV's own browse column
   gains a way to reach the same grid.** **(confirm - this is the one real IA question in this
   brief, worth a second look before building):** `AGENTS.md`'s 2026-09-11 decision literally says
   "redesign the Live TV screen itself" - i.e. Live TV *becomes* the merged grid, not "Live TV and
   Guide stay two separate destinations that happen to look similar." Proposed: `NavDestination`
   keeps both pills for now (removing one is a bigger, more visible change than this brief should
   make unilaterally), but both route to the exact same screen - Live TV opens it with the
   category/channel that was last focused (existing restore behavior, untouched); Guide opens it
   with focus already inside the timeline grid instead of the channel list. One screen, two
   entry points, no duplicate implementation. Flag if you'd rather collapse to a single pill now
   instead.

4. **Grid layout and scope, sized for `HARDWARE.md`'s ~449MB ceiling.** Rows = channels in the
   currently-selected category (same selection state `GroupsColumn` already drives - no new
   selection concept). Columns = a **rolling 6-hour window** from "now" (not a full day) - bounded
   query size, bounded render cost, matches how much a 449MB device can hold in scroll-back
   comfortably; TiviMate itself defaults to a similar few-hour window rather than a full-day grid.
   Each row is a `LazyRow` of programme cells sized proportionally to real duration (a `Modifier`
   `weight`/fixed-px-per-minute scale, not a fixed-width-per-cell layout that misrepresents a
   90-minute movie the same width as a 30-minute show); a static "now" vertical line drawn once,
   not animated. Rows themselves in a `LazyColumn` so scrolling to channel 200 of a category
   doesn't measure the other 199. **(confirm the 6-hour window - TiviMate-style, not a number the
   user gave directly; easy to widen later once real performance is seen on-device.)**

5. **Grid key handling.** UP/DOWN move between channel rows. LEFT/RIGHT move between programme
   cells within a row, bounded to however much real data that channel actually has (no scrolling
   into empty time on a channel with only 2 hours of listings). OK on the **currently-airing**
   cell tunes that channel, exactly like today's channel-list OK. OK on a **future** cell shows a
   lightweight, dismissible info card (title/time/description) - no reminder/action, matching the
   P1 carve-out above. Back leaves the grid the same way it leaves the channel-list column today
   (existing Live TV Back behavior, untouched).

## Status board

| # | Task | State |
|---|---|---|
| P0.1 | `EpgProgramEntity`/`EpgDao` fix - `playlistId` scoping, migration | ✅ built, compiles, real `MIGRATION_8_9` (see below) |
| P0.2 | Per-playlist EPG sync: URL resolution, `WorkManager` scheduling (periodic + on-add) | ✅ built, compiles - not yet observed actually syncing real data on-device (blocked, see below) |
| P0.3 | Guide screen: grid layout, channel rows, programme cells, "now" line | ✅ built, compiles - visual polish pass added (see below); not yet seen rendering real data on-device |
| P0.4 | Grid key handling (decision 5), OK-on-current tunes, OK-on-future info card | ✅ built, compiles - not yet exercised on-device |
| P0.5 | Wire `NavStrip` Guide pill + player's "Guide" button to the real screen | ✅ built, compiles - not yet exercised on-device |
| **A** | **Checkpoint - user tests the guide on the real playlist** | ❌ blocked - device went unreachable mid-sweep, see "Sweep status" below |

## Sweep status (2026-09-19) - code complete, device verification blocked

**Everything above is "builds clean" and "tests pass" (15/15, confirmed via result XML) - nothing
above is yet "verified on device" per this project's own one rule.** The sweep was under way (one
real release build, v0.34.0/versionCode 119, installed successfully and confirmed running) when
the Chromecast dropped off wireless ADB entirely - `adb devices` shows the last known transport as
`offline`, and `adb mdns services` finds nothing to reconnect to. That combination (not just
asleep - `WORKFLOW.md` already established this device stays reachable while asleep) means the
wireless-debugging session itself ended, which needs a fresh pairing code read off the TV's own
screen to recover - not something fixable by retrying `adb connect`. Tried: `kill-server`/
`start-server`, `disconnect`+`connect` on the last known port, repeated `mdns services` discovery
spaced over several minutes. Per the sprint skill's own "Blocked" section, not burning further
window retrying past that.

**A real, important bug was found and fixed during the part of the sweep that did run**, before
the disconnect: the original v9 migration used `fallbackToDestructiveMigration()`, reasoning that
`epg_programs` (the only table whose schema changed) had never held real data. True for that one
table, but destructive migration drops and recreates the **entire** database, not just the changed
table - it silently wiped the device's real playlist the moment the build installed. This is
exactly the "playlist vanished after an update" bug class `RedSurfDatabase.kt`'s own v8 migration
comment already says the user reported once before. Caught via the backfill scheduling logging
zero playlists right after install, root-caused, and fixed with a real `MIGRATION_8_9` that only
touches `epg_programs` (full account in `RedSurfDatabase.kt`'s own updated doc comment). The
device's playlist is gone as a result - a re-seed is needed before the sweep can resume, in
addition to reconnecting.

**Coordinator guidance incorporated (2026-09-19, mid-sweep):** pushed harder on the Guide grid's
visual quality than decision 4's plain-functional spec, per explicit direction ("jaw-dropping,
lightning-fast, most usable UX," don't ship bare-functional if a cheap on-brand treatment exists).
Added, all reusing established precedent rather than inventing a new style:
- **A "LIVE" glyph** on each row's currently-airing cell - the exact same crescent arc/gradient
  brush as `WaveSpinner`, held static (no rotation - "nothing animates at rest," the same rule
  `TELEPORT_MENU.md` already established), replacing a plain "NOW" text badge.
- **A brand-anchored header** - `ic_mark` at 24dp beside the "Guide" label, the same static-mark
  pattern Teleport Menu's own header already established, instead of bare text.
- **A cheap one-shot entrance** - fade + 24dp rise, ~220ms, one `Animatable` driving one
  `graphicsLayer`, fired once per Guide-mode entry (not per category switch, not per row - no
  staggering, no infinite transition). Same cost bracket as every other motion in this app.
Decision 3 (Live TV/Guide as one consolidated screen) was reconfirmed by the user as settled - no
change made, just noted as no longer an open question.
**Explicitly not touched, per direct instruction:** no embedded-video channel preview - today's
single-OK-jumps-to-fullscreen behavior in `openChannel` (`LiveTvScreen.kt`) is unchanged. That's
real, separate engineering (a second decoder or surface-swapping, new OK semantics) already once
deliberately descoped (see this file's own class doc comment history) - it's a dedicated follow-up
sprint once P0 actually lands, not part of this one.

**What's needed to finish:** (1) the device reconnected - needs a human to read a fresh pairing
code off the TV screen, (2) the test playlist re-seeded (`scripts/tv-test.sh`'s `seed_playlist`),
(3) the actual machine-verifiable sweep run against the still-installed-but-unverified v0.34.0
build (or a fresh one if more changes land first). Not pushed to `main` / no CI release cut yet -
that's tied to a completed, verified sweep per this project's own workflow, and this one isn't
that yet. All code is committed locally so nothing here is at risk of being lost.

## Acceptance - machine-verifiable

1. Adding/already having a real Xtream playlist results in `epg_programs` rows for that
   `playlistId` within one sync cycle (confirmed via a DB query or logcat, not just "no crash").
2. Two playlists with overlapping `epgChannelId` values do not collide or overwrite each other's
   programme data.
3. The grid renders real programme titles/times for at least one channel confirmed to have
   provider EPG data; a channel with none shows an honest empty slot, not a crash or a fake entry.
4. OK on the current-airing cell tunes exactly that channel; Back leaves the grid cleanly with
   focus/state preserved per this project's standing state-discipline rule.

## Acceptance - feel/vision (user)

1. Does the grid feel usable on the real remote - readable at 10 feet, scrollable without lag on
   this hardware?
2. Is the 6-hour window the right amount to see at once, or does it need to be wider/narrower?
3. Does routing both "Live TV" and "Guide" to the same screen (decision 3) feel right, or should
   they diverge/collapse to one pill?
