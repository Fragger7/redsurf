# Sprint log

One entry per module sprint (`docs/plans/WORKFLOW.md` "Sprint mode"). Newest first.

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
