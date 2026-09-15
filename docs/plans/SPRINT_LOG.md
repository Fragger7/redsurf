# Sprint log

One entry per module sprint (`docs/plans/WORKFLOW.md` "Sprint mode"). Newest first.

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
