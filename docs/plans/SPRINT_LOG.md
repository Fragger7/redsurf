# Sprint log

One entry per module sprint (`docs/plans/WORKFLOW.md` "Sprint mode"). Newest first.

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

**Release:** `v0.26.2` (pending this handoff's push).
