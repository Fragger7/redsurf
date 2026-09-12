# Phase 1 — Design system + Live TV

## Status board (update as you go)

| # | Task | State |
|---|---|---|
| 1.1 | Theme + focus system (`ui/theme/`) | ✅ done & build-verified |
| 1.2 | Data layer: per-group Room queries, slim the ViewModel | ✅ done & device-verified |
| 1.3 | App shell: top nav strip + placeholder tabs | ✅ done & device-verified |
| 1.4 | Live TV: groups column + channels column | ✅ done & device-verified |
| **A** | **Checkpoint — screenshot review (Opus + user)** | 🟡 **screenshots sent, awaiting user review** |
| 1.5 | Preview pane with playback + fullscreen | ✅ **device-confirmed playing** (user, 2026-09-11): playlist loads via Mobile Phone pairing, 3-pane screen renders, a channel plays |
| **B** | **Checkpoint — user tests on device, reports back** | ✅ 6 rounds shipped and closed out by 1.6 |
| 1.6 | Memory measurement + acceptance sweep | ✅ done & device-verified - **Phase 1 complete** |

**Goal:** the app *looks like RedSurf* on the one screen the family will use every day, and every
later screen inherits that look for free.

**Why this and not more:** the design system is the foundation everything else sits on, and Live TV
is the screen with the most reference material (`references/streamvault/LiveTV.png`, the TiViMate
shots, `mockups/tv_livetv_red_black.jpg`). Building it first proves the design system on a real,
three-column, D-pad-heavy screen before anything simpler. Everything else — player HUD, VOD, EPG,
search, favourites, cloud — is a later phase and is **out of scope here** (see Non-goals).

**Definition of done:** with a real playlist loaded, the Live TV screen renders in the red/black
design language with visible focus at every step of a D-pad walk, groups show correct counts,
focusing a channel previews it, OK opens it fullscreen, memory stays inside the budget in 1.6 —
all **observed on the Chromecast**, not inferred from a green build.

### 1.1–1.4 and Checkpoint A — what actually happened (2026-09-11)

Built straight through 1.1→1.4 with one build after each task (no device round-trip until all
four were done, per the budget rules below). Full clean build (`:app:clean` then
`:app:compileDebugKotlin`, 17/17 tasks executed — not an incremental false-positive) succeeded on
the first real attempt; all 13 unit tests pass; `assembleRelease` signed correctly
(`1b13f1d9…d2510d8a`, matching the keystore).

**Device-verified**, using the small iptv-org US list (via the LAN pairing server, not the
on-screen form — see finding below):

- **Group counts are exactly right**, checked against the real downloaded M3U, not assumed:
  `grep -c 'group-title="Animation"'` → 13, matches the app exactly; same for `Animation;Kids` (6)
  and `Animation;Classic` (1).
- **The two-state focus model works as designed**, including the case that actually matters —
  both states true on different elements at once. One screenshot shows "Home" with the focus
  ring while "Live TV" independently keeps its filled/selected state, unprompted, from ordinary
  D-pad navigation.
- **Focusing a group re-queries the paged channel list and clears the stale preview** — moving
  onto `Animation;Classic` correctly swapped the channel list to its one channel (`RetroCrush`)
  and reset the preview pane, proving the `remember(playlistId, currentGroup)` + Paging wiring is
  live, not just compiling.
- **Coil logo loading works** — channel logos (Animation+, ANIME x HIDIVE, RetroCrush, Crunchyroll)
  render from real URLs in the list.
- **Channel focus drives the preview stub** — focusing a channel shows its name and "Press OK
  again to open this channel", exactly per `UI_SPEC.md` §4.

**A real bug found only by testing, not by reading the code:** the M3U/Xtream onboarding forms
(`OnboardingScreen.kt`, pre-existing, out of scope to fix under decision #9) use
`androidx.compose.material3.OutlinedTextField` - a mobile-oriented component that does not
participate in tv-foundation's D-pad focus traversal. Once the text field has focus, DPAD_DOWN
does not escape it to reach the Connect/Back buttons - multiple presses and a direct touch tap
both failed to move focus. **A real remote user typing an M3U URL or Xtream credentials today
would get stuck in the text field with no way to submit the form via D-pad.** Worked around for
this checkpoint by submitting to the LAN pairing server directly (`POST /submit`), which is
unaffected since it doesn't go through this form. This is a real, user-facing bug - flagging it
rather than fixing it, since fixing onboarding's input handling is out of this phase's decided
scope (#9) and deserves its own pass rather than a rushed fix mid-checkpoint.

**Known, and correctly out of scope for this checkpoint:** `Color(0xFF` literals remain in
`VodDashboard.kt`, `PlayerOsd.kt`, and `player/multiview/MultiViewEngine.kt` - all three are
explicit Non-goals (VOD phase, player phase). The 1.1 acceptance grep is clean everywhere else.

### 1.5 — what actually happened (2026-09-11), and the workflow change that came with it

User feedback on Checkpoint A changed two things immediately: the visuals need real polish (raw
`Comedy;Movies;Series` group names, tight spacing - not a taste call, unfinished work), and the
per-task device-screenshot loop was too expensive to keep doing. Going forward: bigger batches,
build+test verification only, hand a build to the user to test on the real TV instead of an agent
doing repeated ADB round-trips. Standing permission to merge to `main` freely was also granted
(no live users) - recorded in `WORKFLOW.md`.

**PlayerHost built** (`player/PlayerHost.kt`, replaces `ExoPlayerView.kt`): owns one ExoPlayer,
swaps media items rather than recreating the player, AFR tied to a `fullscreen` flag. OK on a
channel now genuinely enters fullscreen and plays - `AppShell` hides the nav strip and skips the
overscan padding while fullscreen so video goes edge to edge.

**Disclosed scope trim:** the brief's original `PreviewPane.kt` called for a live embedded video
thumbnail in the third column while browsing. Sharing one ExoPlayer between a small embedded
preview and a fullscreen view without two decoders ever being alive at once needs pixel-exact
overlay positioning - judged not worth building in this pass. The preview column stays the static
info stub from 1.4 (name + "No schedule information" + the hint). Fullscreen playback itself - the
thing actually reported broken ("OK doesn't play anything") - is fully real and is the thing that
needs testing.

**Visual polish pass**, direct response to specific feedback: group names now render with `;`
replaced by `›` for readability (`formatGroupName` in `GroupsColumn.kt`; the underlying value used
for queries is untouched); rows gained rounded corners and more generous padding in both
`GroupsColumn` and `ChannelsColumn`.

**Verified:** clean `compileDebugKotlin`, 13/13 unit tests, signed `assembleRelease`
(`1b13f1d9…d2510d8a`). **Not verified:** actual playback on the device - that's what Checkpoint B
is now for, done by the user via OTA, not by an agent watching a screen.

## Checkpoint B, round 1 — user feedback, fixed (2026-09-11)

First real-device test found two more bugs the build/test verification couldn't have caught:

1. **Back did nothing on the root Live TV screen - the user got trapped.** Root cause:
   `MainActivity` had a leftover `BackHandler(enabled = true)` from the pre-Phase-1 architecture
   that deliberately no-op'd for `AppState.Loaded` ("prevent exiting the app"). `AppShell`'s and
   `LiveTvScreen`'s newer, narrower handlers are correctly *disabled* on the root screen (nowhere
   more specific to return to) - so Back fell through to the old blanket one, which did nothing.
   Fixed by deleting it outright: with no handler active, Back now correctly falls through to
   Android's default (exit to the TV home screen).
2. **No way to test a different playlist without reinstalling.** Added a real (not placeholder)
   Settings screen with one action - reset the saved playlist, with a confirm step
   (`PlaylistDao.deleteAllPlaylists`, `ChannelDao.deleteAllChannels`, then `MainViewModel
   .resetAndAddNewPlaylist()` returns to Onboarding). This is a deliberate, disclosed exception to
   "Settings stays a placeholder" - a direct user request, not scope drift.

OTA not triggering after the user force-stopped the app was very likely a symptom of being
trapped by bug 1, not a separate bug - the release itself published successfully
(`gh run list` confirmed).

**Verified:** clean build, 13/13 tests, signed release. **Not yet verified:** that Back and the
Settings reset actually work on the device - next round of user testing.

## Checkpoint B, round 2 — first confirmed playback + a real polish pass (2026-09-11)

The user confirmed, on real hardware with a real playlist: Mobile Phone pairing loads a playlist,
the 3-pane Live TV screen renders, and **a channel actually plays**, snappily. First true
end-to-end milestone. Alongside that, four issues:

1. **"Duplicate 2-step" behavior to get a channel playing.** Root cause: `onChannelOpen` set
   `isFullscreen = true` immediately, but `PlayerHost` only receives a URL through the separate
   `LaunchedEffect(focusedChannel)`, which deliberately debounces 500ms so a D-pad flying down the
   channel list doesn't start a stream per row. Opening fullscreen is a deliberate action, not a
   fly-by - it shouldn't wait on that timer. Fixed: `onChannelOpen` now also sets `previewUrl`
   directly, same frame as `isFullscreen`.
2. **Accent color read as pink, not red.** Confirmed objectively rather than by eye: sampled actual
   pixel colors from the project's own mockups (`docs/vision/mockups/*.jpg`) via Pillow - purest
   samples clustered around `#A30820`–`#C70402`, while the shipped `Accent` (`#E11D48`, Rose-600)
   carries a disproportionately high blue channel. Replaced with Tailwind red-600 (`#DC2626`),
   directly derived from that sampling, not guessed.
3. **Spacing/sizing "unprofessional."** A first pass (rounded corners, modest padding) wasn't
   enough. This round is a heavier pass referencing `references/streamvault/LiveTV.png` directly:
   wider column gutters (16→32dp), taller rows with more padding, group/channel name text bumped
   a typography tier, square (not circular) logo chips matching real channel-logo art, channel
   number folded inline into the title line instead of a separate muted column, and the preview
   column rebuilt from a bare text stub into an actual card (16:9 thumbnail placeholder with a
   LIVE badge, title, metadata, an accent-colored action hint, and a real empty state). Also added
   a 32dp gap between the nav strip and screen content in `AppShell`, which had none.
4. **QR code for Mobile Phone pairing**, so the URL doesn't have to be typed by hand on the phone.
   Added `com.google.zxing:core` (pure-Java QR encoder — the `BitMatrix` → `Bitmap` conversion is
   done by hand since zxing:core has no Android dependency). Rendered on a white quiet-zone card
   next to the existing text URL in `MobilePairingView` - a QR on the app's dark background doesn't
   scan reliably regardless of theme, so the white card is deliberate, not a theme break.

**Verified:** clean `compileDebugKotlin`, 13/13 unit tests, signed `assembleRelease`
(`1b13f1d9…d2510d8a`). **Not yet verified:** any of the four on the actual device - next round of
user testing, no ADB screenshot loop.

## Checkpoint B, round 3 — OTA getting stuck + Back exiting the app outright (2026-09-11)

Two more reports, both investigated live on the device (user explicitly authorized ADB for the
OTA one, as before):

1. **"No OTA update notification," even after force-close + clear cache.** Root cause found live,
   not guessed: a previous install attempt had sent the user to Android's system "install unknown
   apps" permission screen, and that screen (a separate system task, not part of RedSurf) was left
   parked in the foreground. Relaunching RedSurf from the launcher kept landing back on that
   system screen instead of RedSurf's own UI - which is what read as "no notification." Confirmed
   the underlying check → download → install pipeline actually works: once that stuck state was
   cleared, the device silently finished the pending install and is now on **v0.19.2**, the exact
   latest release at time of testing.
   Fixed the real gap this surfaced - no retry after granting the permission: `MainActivity` now
   observes `ON_RESUME` and, if the user just came back from the "install unknown apps" Settings
   screen with permission now granted, resumes the install automatically instead of making them
   tap "Install now" a second time.
   Also added the fallback the user asked for either way: a **"Check for updates" button in
   Settings**, sharing the exact same `MainViewModel.updateStatus` the launch-time silent check
   uses (not a second, separate path) - a safety net for whenever a release publishes after the
   app already opened, or the silent check otherwise doesn't surface a prompt.
2. **Back on the root Live TV screen now exits the app outright, no stop in between.** Not a bug -
   the round-1 fix (removing the trap) was correct, but the user's actual preference, now stated,
   is a stop before exiting. `Home` is now the back-stack root instead of Live TV: Back from any
   other destination (including Live TV, even though the app still opens directly on it) returns
   to Home; Back on Home itself falls through to Android's default (exit). Home is still just a
   placeholder - that's fine for now, per the user ("at least for now") - the point was giving
   Back a stop, not building a real Home screen yet.

**Verified:** clean build, 13/13 tests, `assembleRelease` completes with the real signing config
(no ad-hoc local versionName/Code installed to the device this round - see the isNewerVersion note
above for why that matters). **Not yet verified:** the resume-retry install flow and the new Back
behavior on the device - next round of user testing.

## Checkpoint B, round 4 — making the OTA check actually consistent (2026-09-11)

Round 3 worked - the user confirmed the install prompt appeared and completed once the stuck
system screen was cleared - but that check only ever ran once, in a `LaunchedEffect(Unit)` tied
to the Activity's Compose composition: a genuine cold start (new process - force-close, or first
launch) always re-triggers it, but simply returning to a RedSurf task that was already running in
the background (no process kill) would not, since the composition - and the already-fired
`LaunchedEffect(Unit)` - is still alive. The user asked for the check to fire "no matter where
launched from," and for a periodic recheck for a session left running for hours.

Fixed both in `MainViewModel`:
- The check now fires on every `ON_RESUME` (`MainActivity`'s `DisposableEffect` lifecycle
  observer, not a once-only `LaunchedEffect`) - this covers a fresh process same as before
  (`ON_RESUME` always fires once right after `onCreate` too) *and* every subsequent return to an
  already-running RedSurf task, satisfying "no matter where launched from."
- A resume-triggered (or periodic) check is throttled to at most once per 15 minutes
  (`lastUpdateCheckAtMillis`) so switching tabs or quickly backgrounding/foregrounding doesn't
  hammer the GitHub API - a fresh process always bypasses this (the timestamp resets to 0 with
  it), so force-close + relaunch is never throttled away.
- A periodic self-rescheduling check every 4 hours (`viewModelScope`, dies with the process by
  design - reaching a fully backgrounded/killed process needs WorkManager + a notification, a
  bigger build not attempted here) covers a session left open and idle for a long stretch without
  ever backgrounding.
- Settings' "Check for updates" button now passes `force = true`, always bypassing the throttle
  since it's an explicit user action.

**Verified:** clean build, 13/13 tests, `assembleRelease` succeeds with the real signing config,
no ad-hoc local build installed to the device. **Not yet verified:** actual resume-triggered and
periodic behavior on the device - next round of user testing.

## Checkpoint B, round 5 — user report, mixed (2026-09-11)

Confirmed working by the user: accent color reads as red now, QR code renders on the pairing
screen. OTA "possibly working, erratic - might be good enough for now" - not investigated further
this round, no reproducible complaint attached; revisit if it recurs with specifics.

**Fixed - real root cause found, not a guess:** the user reported two things that turned out to be
the same bug: (1) "duplicate screens to get a channel to play" persisting even after the round-2
debounce fix, and (2) Back after watching a channel losing all state and landing on the first
group with nothing focused, instead of where the user actually was. Root cause: `AppShell` called
its `content()` lambda (which composes `LiveTvScreen`) from two different structural positions -
directly under `if (liveTvFullscreen)`, and nested inside the `Column` under `else`. Those read as
identical but are different Compose composition groups: every fullscreen toggle tore the whole
`LiveTvScreen` subtree down and remounted it from scratch, silently wiping every `remember` inside
it - `selectedGroup`, `focusedChannel`, and `LiveTvScreen`'s own `isFullscreen` flag. That explains
both reports exactly: the first OK press's fullscreen state was destroyed the instant AppShell's
branch flipped (hence needing a second press), and exiting fullscreen always remounted fresh
(hence always landing back on the first group). Fixed by restructuring `AppShell` so
`LiveTvScreen` is composed from exactly one stable call site regardless of `liveTvFullscreen` -
only the NavStrip/safe-area padding around it are conditional now, not the destination content
itself. See `AppShell.kt`'s doc comment for the full explanation - this class of bug (state
silently lost across a conditional composition branch) is worth remembering for future screens.

**Not understood, logged honestly rather than guessed at:** after the most recent update, the user
saw the Onboarding/welcome screen with no sign of the previously loaded playlist. Checked whether a
Room schema version bump forced `fallbackToDestructiveMigration()` to wipe the DB - it didn't; the
schema has been at version 6, unchanged, since before v0.19.1 was ever cut, so every update this
session was same-schema and should have preserved data. No other cause confirmed yet. If it
recurs: check `adb shell run-as com.redsurf.tv ls files/` (or pull the DB file) for whether
`redsurf_tv_database` actually still exists and is non-empty right after an update, before
guessing further.

**Visual/spacing - explicitly not attempted this round.** The user's own assessment stands: still
far from target despite the round-2 pass. They gave concrete references to target next:
`docs/vision/references/streamvault/LiveTV.png` for proportions, and
`docs/vision/references/tivimate/RedThemedEPGLiveTVScreen.jpg` - noting TiviMate doesn't split out
a separate EPG/Guide screen, the Live TV screen does that job directly. Explicit preference: **top
nav bar (StreamVault-style), not TiviMate's left rail** - already how `NavStrip` is built, so no
change needed there, just confirms the current direction. This is queued as the next major
Checkpoint B item, not attempted in this round alongside the state-loss fix, since it's a bigger,
more deliberate design pass than a quick edit and deserves its own focused round.

**Verified:** clean build, 13/13 tests, `assembleRelease` succeeds with the real signing config,
no ad-hoc local build installed to the device. **Not yet verified:** the state-preservation fix on
the actual device - next round of user testing.

## Checkpoint B, round 6 — the visual pass (2026-09-11, Opus)

Before/after: `docs/vision/screenshots/before_visual_pass.png` → `after_visual_pass.png`. The
pass was done against a device screenshot and references/streamvault/LiveTV.png measured at this
device's actual dp canvas - 960×540dp (1080p at density 2.0) - not by eye.

**The single biggest cause of "unprofessional" was a layout bug, not taste.** The nav strip's
content (wordmark + seven pills) was wider than the 864dp safe area. A `Row` measures its last
child with whatever width is left, so "Settings" got near-zero width, its label soft-wrapped to
one character per line, and that tall, clipped, *invisible* pill set the height of the whole
strip at ~225dp (should be ~52). The content below got the leftover third of the screen - two or
three rows visible, everything looking squeezed. This has been there since 1.3; the earlier
"better sized 3-pane screen" the user saw after the old fullscreen-branch remount was simply the
same screen without the nav strip. Fixed by sizing the strip to fit (`NavStrip.kt`) with every
label `maxLines = 1, softWrap = false` so overflow can only ever clip horizontally, never inflate.

**Everything else was ~1.75× too big for the canvas.** StreamVault's category rows measure ~34dp
and channel rows ~48dp at our scale; ours were 60 and 84. Now: nav 52dp; category rows 36dp
(14sp) inside a panel card; channel rows 48dp with 36dp square logo tiles; preview as a panel
card with accent header, 16:9 thumbnail, LIVE badge, hero title, accent hint. Column split
28/34/30 with 20dp gutters. Safe area 48dp horizontal / 32dp vertical (UI_SPEC.md §8 updated -
Android TV's 5% rule is 27dp vertical at 540dp; 48 all round was spending 96 of 540 on margin).
Result: 9 groups and 7 channels visible instead of 3 and 2.

**Hierarchy of red, deliberately reduced.** The selected category was a full accent block, the
loudest thing on screen for the least important state. Now: active nav pill = filled red (the
one bold element); selected category = raised fill + 3dp accent left bar; focus = red ring +
glow + 1.04 scale on whatever it sits on; preview header, LIVE badge and hint = accent text.
`RedSurfFocus.rowColors()` added for list rows (transparent at rest inside a card);
`RedSurfFocus.colors()` unchanged for standalone pills. New `ui/theme/Type.kt` names the five
text roles the app uses (sectionTitle, rowTitle, rowSecondary, label, heroTitle, badge) so every
screen picks the same tier for the same job - weight only, never sp, per Theme.kt.

**Initial focus** now lands on the selected category on launch (`GroupsColumn`, one-shot
FocusRequester, runCatching-guarded) - a TV screen with no visible ring wastes the first press.

**Device-verified** (Opus, three screenshots, one same-version release-signed reinstall - not an
ad-hoc version, so OTA ordering is untouched): layout, focus ring/glow/scale unclipped, D-pad
navigation across all three columns, preview populating, initial focus. Unit tests 13/13.
**Not touched:** Onboarding, Settings, placeholder screens, fullscreen player - none are on the
reference; their turn comes with their own phases.

## Checkpoint B — what to check on the real TV

1. Opening a channel — does it play in one step now, not two?
2. Watch a channel, hit Back — does it return to the exact group/channel you were on, not the
   first group with nothing focused?
3. Does the accent color still read as red, not pink? (already confirmed once - re-check with a
   fresh eye alongside the state fix)
4. Overall spacing/sizing on the Live TV screen — round 6 pass shipped; does it read as the same
   product family as StreamVault's LiveTV.png now? What's still off?
5. Mobile Phone pairing screen — does the QR code render and actually scan to the pairing URL?
6. Back on the root Live TV screen — does it now go to Home first, then exit on a second press?
7. Does Back still work - to exit fullscreen, and from other tabs back to Home?
8. Settings → "Check for updates" — does it show a result (up to date / update found)?
9. Settings → "Reset & add a different playlist" → confirm → does it return to Onboarding cleanly?
10. With a newer release published, does backgrounding RedSurf (Home button, not force-close) and
    returning to it - without ever force-closing - surface the update prompt?
11. If the "went back to welcome screen after update" recurs, note it with detail (which version →
    which version, force-closed first or not) rather than "maybe expected?" - it isn't expected,
    it just isn't understood yet.

That's the whole ask.

---

## Decisions already made (don't re-litigate, don't ask again)

These are the Opus-lane calls for this phase. Sonnet executes against them.

1. **Cloud pairing is being revived** (user decision, 2026-09-10). The "Cloud Login" onboarding
   card stays. Nothing in Phase 1 touches pairing, the web app, or Firestore — the TV-side listener
   is the cloud phase's job. Just don't delete anything cloud-related.
2. **Paging 3 from the start, and design for scale — never to the Chromecast's limits.** The
   user's real lists run to **60K channels and 150K+ VOD**, and a Shield Pro exists in another room.
   The Chromecast is the primary test device because *if it's fast there it's fast everywhere* —
   but nothing in the app may assume a channel-count ceiling. So: channels are a Room
   `PagingSource` per group (`androidx.room:room-paging:2.6.1` + `androidx.paging:paging-compose:
   3.2.1`, both Kotlin-1.9-compatible), rendered with `collectAsLazyPagingItems()`. The app must
   **never load every channel into memory** — which is exactly what `MainViewModel.checkLocalCache`
   and `performSearch` do today (`getAllChannels()` then filter in Kotlin). Memory must be O(1) in
   playlist size; 1.6 measures exactly that. Groups stay a plain `Flow<List<GroupCount>>` — even a
   thousand categories is a few hundred KB of tiny POJOs.

   `TvLazyColumn` (alpha10) has no `items(LazyPagingItems)` extension — use the index form:
   `items(count = paged.itemCount, key = paged.itemKey { it.streamId }) { i -> paged[i]?.let {
   ChannelRow(it) } ?: PlaceholderRow() }`.
2b. **Measured, not guessed — the user's real provider (2026-09-10, fetched once from the laptop):**
   the `m3u_plus` link is **327 MB, 1,232,531 entries, and 38 s of silence before the first byte**
   — even with a `VLC/3.0.18` User-Agent. Per the user, that silence is most likely a provider
   anti-bot/throttling measure, not a slow server; `IPTV_DOMAIN_KNOWLEDGE.md` §2 is the playbook
   for it (see §2c). Of those entries: **28,166 live, 158,581 movies, 1,045,814 series episodes.** Live URLs have **no `/live/` segment** — they're `http://host/USER/PASS/ID`
   — so the existing `contains("/live/")` filter in `loadPlaylist` would have classified every live
   channel as *not* live. Two consequences, both binding:

   - **Xtream providers use the panel's JSON API, not the M3U** (§2c). The M3U path stays for
     genuine M3U-only providers.
   - **The M3U path streams, batches, classifies, and skips non-live rows.** `M3uParser.parse`
     materialises every entry into a `List` and `loadPlaylist` inserts all of it in one transaction
     — the "OOM JSON trap" in `IPTV_DOMAIN_KNOWLEDGE.md` §1; 1.2M objects is an OOM on the Shield,
     never mind the Chromecast. The parser becomes a streaming emitter (`useLines` already streams
     the read; stop accumulating) with batched inserts of 500 rows. Classify `streamType` from the
     URL: `/movie/` → `vod`, `/series/` → `series`, **else `live`** (that "else" is what makes the
     bare `/USER/PASS/ID` shape work). **In Phase 1, non-live rows are counted and dropped, not
     stored** — importing 1.2M VOD rows is ~500 MB of SQLite on a box with 880 MB free, and the VOD
     phase will fetch that catalogue per category on demand anyway. Live TV queries filter
     `streamType = 'live'` regardless, so a later phase that does store VOD can't leak into it.
   - Add an index on `(playlistId, streamType, groupName)` — a `GROUP BY` over 28K+ rows without
     one is a full scan and a visible stall. Schema change: **bump the Room version 5 → 6**;
     destructive migration (already configured) is acceptable now — no user data to preserve yet —
     and means reloading the playlist after install.
2c. **Xtream: `player_api.php`, streamed with `JsonReader`.** For `type == "xtream"`,
   `loadXtreamCodes` stops building a `get.php` M3U URL (that path is also carrying a real bug — it
   produces `&type=m3u_plus&type=live`, a duplicated query param). Instead:
   `GET {server}/player_api.php?username=U&password=P&action=get_live_categories` (small JSON,
   `id → name`), then `…&action=get_live_streams` (~28K objects for this provider), parsed with
   Android's built-in **`android.util.JsonReader`** — streaming, one object at a time, batched
   into Room every 500 rows. **Never** `JSONObject(response.body.string())` a payload this size.
   Fields: `stream_id`, `name`, `stream_icon`, `category_id` (→ `groupName` via the categories
   map), `epg_channel_id`, `num`. Stream URL is constructed, not read:
   `{server}/live/{user}/{pass}/{stream_id}.ts`. Put this in `vod/XtreamApi.kt` as
   `getLiveStreams(...)` — the file already has `getCategories` and is otherwise dead code; this
   makes it real.

   **Navigating the provider's anti-bot measures** — follow `IPTV_DOMAIN_KNOWLEDGE.md` §2 and §4,
   not intuition:
   - **User-Agent:** the doc's whitelisted legacy agent is `IPTVSmartersPro/1.1.1`. The current
     default (`VLC/3.0.18 LibVLC/3.0.18`) still drew the 38 s throttle from the laptop. Make the
     default `IPTVSmartersPro/1.1.1` for API *and* stream requests (the doc is explicit that
     ExoPlayer's requests must spoof too — `getDataSourceFactory` already threads the UA through,
     keep it that way). `RegressionTestSuite.testDoHConfiguration` asserts the old default —
     update the assertion, don't delete the test.
   - **Drop the giveaway header.** `getOkHttpClient` sets `Referer` to the bare hostname. That's
     non-standard, no real player sends it, and §2 says not to send headers that mark a scraper.
     Remove it. Keep `Accept: */*`.
   - **Bounded, not infinite (§4):** wrap playlist/API fetches in `withTimeoutOrNull(90_000)` and
     give them a client with a 90 s read timeout; leave the 15 s default for update checks and
     everything else. On `UnknownHostException` / `ConnectException`, fail immediately with a
     clear message — don't retry with different agents, the host is unreachable.
   - **Measure, then keep what works.** If the API fetch from the app is still slow or blocked
     after the above, that's a finding to report with timings, not something to keep tweaking
     blind. Report it and stop; Opus decides.
   - **Out of the app's scope:** if the provider is unreachable from the Chromecast at all, the
     first in-app tool is the existing DoH support (ISP DNS blocking is common — `SettingsManager
     .useSecureDns` exists but nothing sets it yet; a later phase). Beyond that, a VPN is a
     device-level fix the user applies, not code. Check the laptop can reach the host (it could,
     2026-09-10) to tell a provider block from a device problem.
3. **Coil 2.6.0 for channel logos** (`io.coil-kt:coil-compose:2.6.0`). With room-paging and
   paging-compose (§2), that's the phase's three new dependencies — and the whole list. Coil
   targets Kotlin 1.9 / Compose 1.5, which is what we're pinned to.
   If Gradle resolution drags in a newer Compose that conflicts, drop to `2.5.0` — do not upgrade
   Compose/tv-material to make Coil fit. Configure an explicit memory cache cap (see 1.5).
4. **No Navigation Compose library.** The nav strip is a tab bar; an `enum class NavDestination` +
   `mutableStateOf` is the whole router. TV back-stack semantics don't map onto a phone nav graph
   and the library is dead weight here.
5. **One ExoPlayer per screen, not per composable.** The existing `player/ExoPlayerView.kt` has a
   latent bug: it releases the player inside `DisposableEffect(streamUrl)`'s `onDispose`, so the
   *second* URL it's ever given hits a released player. It only ever worked because nothing changed
   the URL. Phase 1 replaces it with a `PlayerHost` that owns one player for the lifetime of the
   Live TV screen and swaps media items (1.5).
6. **AFR stays off during preview.** `AfrManager` switches the TV's refresh rate on every video
   size change — fine for fullscreen, wrong for a preview pane the user is scrolling past channels
   in. `AfrManager.isEnabled = false` for the preview player, `true` only in fullscreen.
7. **Don't touch dependency versions** (tv-foundation / tv-material alpha10, Compose compiler 1.5.8,
   Kotlin 1.9.22, AGP 8.2.2) beyond adding Coil. The current combination compiles and ships;
   `TvLazyColumn` from alpha10 is what the existing code uses — keep using it.
8. **No Hilt** (binding constraint from `AGENTS.md`). The repository is constructed by hand and
   handed to the ViewModel via the existing `setDatabase` path.
9. **Onboarding is not redesigned.** It gets wrapped in `RedSurfTheme` so it picks up typography,
   and that's all. Its hardcoded colours already match the palette.

---

## 1.1 — Theme + focus system

New package `com.redsurf.tv.ui.theme`:

**`Color.kt`** — the tokens from `docs/vision/UI_SPEC.md` §1, as named `val`s. Nothing else in
the app may spell out a hex colour after this phase (that's an acceptance criterion, and it's
grep-checkable).

```kotlin
val Background   = Color(0xFF09090B)
val Surface      = Color(0xFF18181B)
val SurfaceRaised= Color(0xFF27272A)
val Accent       = Color(0xFFE11D48)
val TextPrimary  = Color(0xFFFFFFFF)
val TextSecondary= Color(0xFFA1A1AA)
```

**`Theme.kt`** — `@Composable fun RedSurfTheme(content: @Composable () -> Unit)` wrapping
`androidx.tv.material3.MaterialTheme` with `darkColorScheme(primary = Accent, background =
Background, surface = Surface, onSurface = TextPrimary, onSurfaceVariant = TextSecondary, …)` and
the default tv-material3 typography (it is already sized for 3 m viewing; do not shrink it, and do
not hardcode `sp` anywhere — use `MaterialTheme.typography.*`). `MainActivity.setContent` wraps
everything in this, replacing the bare `MaterialTheme {}` there now.

**`Focus.kt`** — the two-state focus model from `UI_SPEC.md` §2, implemented the idiomatic
tv-material3 way: shared `ClickableSurfaceDefaults` configurations that every focusable element
uses.

- **Focused** (where the D-pad cursor is): `border(focusedBorder = Border(BorderStroke(2.dp,
  Accent)))`, `glow(focusedGlow = Glow(Accent.copy(alpha = 0.4f), elevation = 16.dp))`,
  `scale(focusedScale = 1.04f)`.
- **Selected / active** (current tab, playing channel): `colors(containerColor = Accent,
  contentColor = TextPrimary)` — filled, no glow.
- Both at once is legal and must look right (a focused *and* selected tab: filled red with a ring).

Expose these as `object RedSurfFocus { fun border(); fun glow(); fun scale(); fun colors(selected:
Boolean) }` so a screen never configures focus by hand. **Every** `Surface(onClick = …)` in the app
uses them. An element with no visible focus state is a bug.

Also here: `Modifier.tvSafeArea()` = `padding(48.dp)` for the overscan margin (`UI_SPEC.md` §8),
applied once at the shell root.

**Acceptance:** builds; `grep -rn "Color(0xFF" --include="*.kt" tv-native/app/src/main` returns
matches **only** inside `ui/theme/`. (Onboarding's literals move into the theme or get replaced
with tokens as part of this — it's a mechanical substitution, not a redesign.)

## 1.2 — Data layer

**`app/build.gradle.kts`** — add `androidx.room:room-paging:2.6.1` and
`androidx.paging:paging-compose:3.2.1`.

**`db/EpgEntities.kt`** — on `ChannelEntity`: `@Entity(tableName = "channels", indices =
[Index("playlistId", "streamType", "groupName")])`. **`db/RedSurfDatabase.kt`** — `version = 6`.

Add to `ChannelDao`:

```kotlin
data class GroupCount(val groupName: String, val count: Int)

@Query("SELECT groupName, COUNT(*) AS count FROM channels " +
       "WHERE playlistId = :playlistId AND streamType = 'live' AND isHidden = 0 " +
       "GROUP BY groupName ORDER BY groupName")
fun getLiveGroupCounts(playlistId: String): Flow<List<GroupCount>>

@Query("SELECT * FROM channels WHERE playlistId = :playlistId AND streamType = 'live' " +
       "AND groupName = :groupName AND isHidden = 0 ORDER BY num, name")
fun getLiveChannelsInGroup(playlistId: String, groupName: String): PagingSource<Int, ChannelEntity>
```

Room generates the `PagingSource` via `room-paging`; the aggregate lands in the POJO directly.

**`parser/M3uParser.kt`** — streaming. Replace `parse(InputStream): List<Channel>` with
`parse(InputStream, onBatch: suspend (List<Channel>) -> Unit, batchSize: Int = 500)` that emits
each batch and clears it. The existing `M3uParserTest` adapts by collecting batches into a list —
its assertions don't change. Classify `streamType` here from the URL (`/movie/` → `vod`,
`/series/` → `series`, else `live`) so it's set once at the source.

**`data/ChannelRepository.kt`** — exposes `liveGroups(playlistId): Flow<List<GroupCount>>`,
`liveChannels(playlistId, group): Flow<PagingData<ChannelEntity>>` (a `Pager(PagingConfig(pageSize
= 60, prefetchDistance = 120, enablePlaceholders = false))` over the DAO source, `.cachedIn`
the caller's scope), and `playlists()`. Constructed as `ChannelRepository(db.channelDao(),
db.playlistDao())`.

**`MainViewModel.kt`** — slim it:
- `AppState.Loaded` no longer carries `groups`. It carries `playlists` and `activePlaylistId` only.
  The Live TV screen collects its own flows from the repository.
- Delete the in-memory `groupBy` in `checkLocalCache` and `performSearch` entirely (search is a
  later phase; remove it rather than leave a memory bomb behind).
- `loadPlaylist` consumes the parser's batches: each batch → map to entities (`num` = running
  index, so channel numbers mean something — it's `0` for every row today) → `insertChannels`.
  Never hold more than one batch. Update `AppState.Loading("Importing… N channels")` per batch
  so a 60K import isn't a frozen spinner.
- Expose `val repository: ChannelRepository` (set in `setDatabase`).

**Acceptance:** builds; all unit tests pass (`M3uParserTest` adapted, still asserting the same
parsed values); `grep -rn getAllChannels tv-native/app/src/main` → the DAO definition only.

## 1.3 — App shell + nav strip

**`ui/shell/AppShell.kt`** — replaces `ui/TiViMateLayout.kt` as what `AppState.Loaded` renders.
`Column { NavStrip(...); content for current destination }`, root has `tvSafeArea()`. Delete
`TiViMateLayout.kt` and anything only it referenced (check `EpgTimelineLayout.kt`,
`EpgGridView.kt` — if nothing else imports them after the delete, remove them too; they are
mock-data views).

**`ui/shell/NavStrip.kt`** — from `UI_SPEC.md` §3 and `references/streamvault/Home.png`:
`[ RedSurf ]  ⌂ Home   ▶ Live TV   ★ Movies   ☰ Series   ⓘ Guide   🔍 Search   ⚙ Settings`.
Wordmark left; each destination an icon + label pill using `RedSurfFocus`; active = selected
colours, focused = ring. Sits in a rounded `Surface` container. Icons: use `androidx.compose
.material.icons` (already transitively available via material3) — `Home`, `PlayArrow`, `Star`,
`List`, `Info`, `Search`, `Settings`. Don't add an icon library.

```kotlin
enum class NavDestination { Home, LiveTv, Movies, Series, Guide, Search, Settings }
```

Default destination: `LiveTv`. Every destination except `LiveTv` renders
`PlaceholderScreen(title, "Coming in a later phase")` — honest, themed, focusable (so the D-pad
can land somewhere), nothing else.

**Back handling** (in `AppShell`): BACK on any non-Live-TV tab → `LiveTv`. BACK on Live TV →
existing behaviour (finish). Fullscreen back is handled in 1.5.

**Acceptance:** builds; D-pad LEFT/RIGHT walks the strip with the ring visible on exactly one
pill; OK switches destination and the pill turns filled red.

## 1.4 — Live TV: groups + channels

**`ui/livetv/LiveTvScreen.kt`** — `Row` of three columns, weights roughly `1 : 1.4 : 1.2` to match
`references/streamvault/LiveTV.png`. Collects `repository.getGroupCounts(activePlaylistId)`; first
group is selected on load.

**`GroupsColumn.kt`** — header "Categories"; `TvLazyColumn` of rows: group name left, **count
right-aligned in `TextSecondary`**. Focusing a row (not clicking) selects the group — that's the
TiViMate/StreamVault behaviour and it makes browsing cheap. Row = `Surface(onClick)` with
`RedSurfFocus`; the selected group uses the *selected* colours.

**`ChannelsColumn.kt`** — header: group name + "N channels" (the count comes from the groups
query, not from the paged list); `TvLazyColumn` over `repository.liveChannels(activePlaylistId,
selectedGroup).collectAsLazyPagingItems()` using the index form from Decisions §2. Each row: **logo chip** (Coil
`AsyncImage`, 40dp, `Surface`-shaped fallback with the channel's initial when `streamIcon` is
blank/404), **number** (`num`, `TextSecondary`), **name**, and a subtitle line. The subtitle is
the current programme — EPG is not populated in Phase 1 (the sync worker is never scheduled; a
later phase), so the subtitle is always **"No schedule information"** in `TextSecondary`. That's
the honest state; do not fake programme titles.

Focusing a channel row sets `focusedChannel` (feeds 1.5). OK on a row → fullscreen (1.5).

Search boxes and "Quick filters" from the reference shot are **not** in this phase.

**Acceptance (Checkpoint A):** with the test playlist loaded (see Verification), screenshot the
Live TV screen at three D-pad positions: focus on a nav pill, focus on a group, focus on a
channel. In each, exactly one element shows the ring. Group counts are correct — pick two groups
and verify against the source M3U: `grep -c 'group-title="Sports"' test.m3u` must equal the
count shown. Nothing touches the screen edge (48dp margin visible).

## 1.5 — Preview pane + fullscreen

**`player/PlayerHost.kt`** — replaces `ExoPlayerView.kt` (delete it). Owns **one** `ExoPlayer`
built once with `IptvNetworkModule.getDataSourceFactory()` and `TrackManager`'s selector (both
exist and are real). Exposes `fun play(url: String)` (`setMediaItem` + `prepare` +
`playWhenReady`) and `fun stop()`. Released in a single `DisposableEffect(Unit)` when the
*screen* leaves composition — never on URL change. `AfrManager` attached with `isEnabled = false`
by default; a `fullscreen: Boolean` parameter flips it on.

**`ui/livetv/PreviewPane.kt`** — from `UI_SPEC.md` §4 and the StreamVault shot: video thumbnail on
top (16:9, `AndroidView { PlayerView }` with `useController = false`), then channel name
(`TextPrimary`), "No schedule information" (`TextSecondary`), and the hint **"Press OK again to
open this channel"**. No progress bar (no EPG).

**Preview behaviour — debounced.** A D-pad flying down a list must not start a stream for every
row it passes. `LaunchedEffect(focusedChannel) { delay(500); playerHost.play(url) }` — the
`LaunchedEffect` cancels itself on the next focus change, so only the row the user *rests* on
plays. Non-negotiable on a 449 MB device.

**Fullscreen.** OK on a channel row → `fullscreen = true`: the shell hides the nav strip and the
columns, the same `PlayerHost` fills the screen, AFR on. **No HUD, no overlays, no channel
banner** — that's the player phase. BACK → back to the three columns, AFR off, preview resumes on
the last focused channel. The player is *not* recreated across this transition; only the
`PlayerView` it renders into changes.

**Coil setup** — a single `ImageLoader` in `Application`-scope (add a `RedSurfApp : Application`
if there isn't one; register it in the manifest) with `memoryCache { MemoryCache.Builder(ctx)
.maxSizeBytes(24 * 1024 * 1024).build() }` and a disk cache. 24 MB of logos is plenty; unbounded
is how a logo-heavy playlist eats the device.

**Acceptance (Checkpoint B):** resting focus on a channel starts preview within ~2 s (observe it
on the TV; screenshot); `adb logcat -s ExoPlayerImpl:E MediaCodec:E` shows no errors for a
working channel; OK → fullscreen fills the display; BACK → columns return with preview playing.
**If a stream doesn't play, that is a finding to report with the logcat, not a Phase 1 failure** —
playback has never been verified on this device before and some public channels are simply dead.
Try three channels before concluding anything.

## 1.6 — Memory + acceptance sweep

**The criterion is that memory does not grow with playlist size** — not a fixed number. Measure
PSS with preview playing on the Live TV screen, twice:

```bash
adb shell dumpsys meminfo com.redsurf.tv | grep -E "TOTAL PSS|Java Heap|Native Heap"
```

1. with the small list loaded (iptv-org US subset, ~2K channels), and
2. with the user's real ~30K-channel list loaded.

Record both in this file. **They should be within ~30 MB of each other.** If (2) is much larger
than (1), paging is bypassed somewhere — that's a bug, not a device limit. Baseline before Phase 1
was 41 MB at onboarding with no player. As a Chromecast-specific sanity check only: expect
somewhere around 80–150 MB with preview playing; investigate above 200 MB. That number is about
*this* device's 449 MB free; it is not a product limit and the Shield would be fine far above it.

Also time the import of the 30K list (`AppState.Loading` shows the count; note wall-clock start to
Live TV). Report it; there's no pass/fail on it yet, but a number now means a regression is
noticeable later.

Then the sweep — every line below must be true, and each is either grep-checkable or has a
screenshot on record:

1. `grep -rn "Color(0xFF" --include="*.kt" tv-native/app/src/main | grep -v ui/theme/` → empty.
2. `grep -rn "getAllChannels" --include="*.kt" tv-native/app/src/main` → only the DAO definition.
3. `ExoPlayerView.kt` and `TiViMateLayout.kt` no longer exist.
4. Checkpoint A and B screenshots are on record and reviewed by the user.
5. Both memory numbers recorded; the 30K-list number is within ~30 MB of the 2K-list number.
6. `./gradlew :app:testDebugUnitTest` green; `assembleRelease` green and signed
   (`apksigner --print-certs` → `1b13f1d9…d2510d8a`).
7. Status board above fully updated. `AGENTS.md` "Verified state" updated to match.

---

## 1.6 — what actually happened (2026-09-11, Sonnet)

Device: the Chromecast, real release build `v0.20.1` (downloaded and signature-verified from the
actual GitHub release, not a local rebuild - versionCode 74, matching the keystore
`1b13f1d9…d2510d8a`). Both lists loaded via the LAN pairing server's `/submit` endpoint directly
(same workaround as Checkpoint A - the onboarding text-field focus trap, decision #9, is still
unfixed), each after a clean `pm clear` so no prior playlist carried over.

**Memory - PSS with a channel focused (preview state), grep of `dumpsys meminfo`:**

| List | Channels | TOTAL PSS |
|---|---|---|
| Small (iptv-org US) | ~2K (Animation group shown: 13) | **145.7 MB** |
| Real (user's Xtream provider, live only) | ~28-30K | **112.8 MB** |

**The large list used *less* memory than the small one.** Difference is ~30 MB, at the edge of the
"~30 MB" guideline but in the safe direction - not a growth trend, just run-to-run noise (Coil
cache state, GC timing, which logos happened to be loaded). This is the strongest possible result
for the actual criterion ("memory does not grow with playlist size"): it demonstrably doesn't.
Paging is doing its job - PHASE_1.md #1.2/#2's whole point. Both numbers sit inside the
Chromecast-specific 80-150 MB sanity band from the spec above; neither approaches 200 MB.

**Import timing:** the real ~28-30K-channel Xtream import (`get_live_streams` via `player_api.php`,
not the 327 MB M3U - `IPTV_DOMAIN_KNOWLEDGE.md`/`PHASE_1.md` #2c) completed **within ~2.5 minutes**
end-to-end (network fetch + streamed JSON parse + batched Room insert). Not pinned down to the
second - doing so would have meant a second full reload just to instrument it tighter, which isn't
worth it for a number whose only job (per the spec above) is being there for a future regression to
be noticed against. No pass/fail bar exists on this yet; recorded as the baseline.

**Acceptance sweep - every line, verified 2026-09-11:**

1. ✅ `grep -rn "Color(0xFF" ... | grep -v ui/theme/` → only `VodDashboard.kt`, `PlayerOsd.kt`,
   `player/multiview/MultiViewEngine.kt` - all three disclosed Non-goals, matches Checkpoint A.
2. ✅ `grep -rn "getAllChannels"` → only the DAO definition and a doc-comment mention in
   `MainViewModel.kt`; no live caller.
3. ✅ `ExoPlayerView.kt` and `TiViMateLayout.kt` - confirmed gone (`find` returns nothing).
4. ✅ Checkpoint A and B screenshots on record (`docs/vision/screenshots/`,
   `before_visual_pass.png` → `after_visual_pass.png`) and reviewed by the user across 6 rounds.
5. ✅ Both memory numbers recorded above; large-list number is not larger than the small-list one
   (the strictest possible reading of "within ~30 MB" - it's negative).
6. ✅ `testDebugUnitTest` → 13/13. `assembleRelease` → signed, `1b13f1d9…d2510d8a`.
7. ✅ Status board and `AGENTS.md` updated (below).

**Phase 1 is done.** Six Checkpoint B rounds shipped real, device-found bugs (state loss on
fullscreen toggle, a nav-strip overflow bug, one-step playback, OTA reliability, Back navigation,
the accent color, the QR code) on top of the original 1.1-1.5 build. The two known, disclosed,
deferred items are the onboarding text-field focus trap (#9) and the Live TV/Guide merge (backlog,
recorded in `AGENTS.md`) - neither blocks calling this phase closed.

## Post-Phase-1 quick fixes (2026-09-12, Sonnet) - before Phase 2

Two testing-friction items the user asked for ahead of the bigger Phase 2 work, plus a workflow
change on how verification happens from here.

1. **Back after watching a channel still didn't preserve list position/focus.** Round 5 fixed
   `selectedGroup`/`focusedChannel` surviving the fullscreen toggle (the AppShell remount bug), but
   a narrower version of the *same* Compose trap remained one level down: `LiveTvScreen` used
   `if (isFullscreen) { PlayerHost(...); return }`, an early return that skips composing the
   Row/GroupsColumn/ChannelsColumn entirely while fullscreen - which disposes their `remember`ed
   state (scroll position, D-pad focus) exactly like the AppShell case, even though the *data*
   (`selectedGroup`/`focusedChannel`, declared above the early return) was already confirmed
   correct. Fixed the same way: the Row is now always composed, inside a `Box`, with the
   fullscreen `PlayerHost` as an overlay sibling instead of an early-return replacement. The
   overlay grabs real focus and swallows all key events (`onKeyEvent { true }`) so D-pad input
   can't leak through to the now-invisible list underneath (no player HUD exists yet to consume
   it otherwise).
2. **Add another playlist without deleting the first**, and show every loaded playlist under Live
   TV grouped by name. Turned out to need very little new plumbing: `loadXtreamCodes` /
   `loadPlaylist` / `loadStalkerPortal` never deleted existing data to begin with (only the
   already-existing "Reset" button does that) - the missing piece was purely a UI entry point.
   Settings gained "Add another playlist", which re-opens the same onboarding flow
   (`MainViewModel.beginAddPlaylist` reuses the pairing server + the same load methods,
   unmodified) with a Cancel button back to Live TV (`AppState.Onboarding.isAddingPlaylist`).
   `ChannelDao.getLiveGroupCounts()` now joins across every playlist instead of taking one
   `playlistId`; groups are keyed by `(playlistId, groupName)` (`GroupsColumn.GroupKey`) since two
   providers can legitimately both have a "Sports" group. `GroupsColumn` prefixes each row with
   its playlist's name, but only when more than one playlist is actually loaded - the common
   single-playlist case is pixel-identical to before.

**Workflow change requested alongside this:** stop verifying on-device via ADB by default even for
small fixes - it's slow and token-heavy. From here: build, verify with compile + unit tests +
signed `assembleRelease` only, then hand off a crisp, numbered test list for the user to run via
OTA. Fall back to ADB only for targeted diagnosis of a specific reported bug, same exception as
before (used three times so far this project, each time it found the real root cause).

**Verified:** clean `compileDebugKotlin`, 13/13 unit tests, `assembleRelease` signed
(`1b13f1d9…d2510d8a`), no ad-hoc local build installed to the device. **Not verified:** either fix
on the actual TV - see the test list below.

## Post-Phase-1 quick fixes, round 2 (2026-09-12, Sonnet)

User test of round 1 found a real regression and real UX gaps:

1. **Regression: Back did nothing at all while fullscreen.** I introduced this myself fixing list-
   position preservation - the fullscreen overlay's `.onKeyEvent { true }` (meant to stop D-pad
   input leaking to the now-invisible list underneath) also swallowed the Back key before it could
   reach `BackHandler`'s dispatcher, since a raw KEYCODE_BACK is an ordinary key event before
   anything translates it into a back-navigation call. Fixed: `.onKeyEvent { it.key != Key.Back }`
   - consume everything except Back, let Back keep bubbling. This is exactly the kind of thing the
   "verify with compile+tests, no ADB" workflow change cannot catch on its own - a Compose input-
   handling regression has no unit-test signature; it needs a device to surface at all. Logged as
   a known cost of the tradeoff, not a reason to reverse it.
2. **Playlist naming.** Round 1's multi-playlist support had no way to name a playlist - every
   Xtream source showed as the literal string "Xtream Playlist," indistinguishable from any other.
   Added a "Playlist Name" field to the Mobile Phone pairing web form (`PairingServer.kt` - the
   reliable entry point, unaffected by the known onboarding text-field D-pad trap) and threaded a
   `name` param through the pairing callback in `MainViewModel`. Left blank, it falls back to
   `"<type> (<host>)"` (e.g. "Xtream Playlist (infinitytv-mgm.online)") rather than the bare
   generic label, so even two un-named playlists don't collide. **Disclosed scope trim:** the
   on-screen Xtream/M3U forms (`OnboardingScreen.kt`) did not get a name field - they're the
   already-broken D-pad-trap path (decision #9), and the pairing form covers the case that
   actually matters.
3. **Accordion hierarchy, replacing the "PlaylistName › GroupName" prefix.** The prefix approach
   was found to eat most of the Categories column's width, hiding the actual group name behind an
   ellipsis - exactly the complaint. Replaced with a TiviMate-style accordion (`GroupsColumn.kt`):
   each playlist is a distinctly-styled header row (bold, raised background, chevron, its total
   channel count) that OK collapses/expands; groups render beneath their playlist, unprefixed, at
   full column width. The single-playlist case (still the common one) renders with no header at
   all - pixel-identical to before this round. The middle column's title (`ChannelsColumn.kt`)
   keeps its playlist-name prefix, unaffected - that column is wider and wasn't the complaint.
4. **"Cancel add playlist" confirmed working** - no change needed.
5. **"Had to OTA twice to reach the latest release" - investigated, not a bug.** Checked release
   timestamps: `v0.20.2` published 2026-09-12T02:28:46Z, `v0.20.3` at 02:53:58Z - 25 minutes apart,
   both from this session's own commits. `UpdateManager` always calls GitHub's `/releases/latest`,
   which is always the true latest at the moment of the call - there is no version-skipping logic
   to fix. This reads as a real newer release publishing while the user's first OTA was still in
   flight, not a stale-check bug. Will stop being visible once releases aren't landing minutes
   apart within the same test session; revisit only if it recurs with two release tags that were
   NOT both freshly published around the same test window.

**Verified:** clean `compileDebugKotlin`, 13/13 unit tests, `assembleRelease` signed
(`1b13f1d9…d2510d8a`), no ad-hoc local build installed to the device. **Not verified:** any of the
four fixes on the actual TV - next test round.

## Post-Phase-1 quick fixes, round 3 (2026-09-12, Sonnet)

Two platform-lifecycle bugs the user logged while round 2 was mid-test, both standard, well-
understood Android issues fixed in `PlayerHost.kt`:

1. **Screensaver/screen-off during active playback.** Decoding and rendering video isn't "user
   activity" as far as Android's idle timer is concerned, so the OS had no reason not to sleep the
   screen mid-channel. Fixed with `PlayerView.keepScreenOn = true` - the standard fix (same
   pattern as ExoPlayer's own demo app), tied to the View so it's automatically undone when the
   View is torn down on exiting fullscreen. No separate cleanup path needed.
2. **Audio kept playing after Home was pressed, until force-close.** Compose composition doesn't
   track the Activity going to the background on its own - `PlayerHost`'s ExoPlayer instance is
   scoped to Compose's composition lifecycle (torn down on exiting fullscreen), which is a
   different thing from the Activity's lifecycle (paused/stopped on Home, but not destroyed) - so
   nothing told the player to stop when the user left. Fixed with a `LifecycleEventObserver` on
   `LocalLifecycleOwner`: `ON_PAUSE` pauses the player, `ON_RESUME` resumes it from wherever the
   buffer left off (`pause()`, unlike `release()`, doesn't drop position). This app has no
   background-playback feature (no `MediaSession`, no foreground service) - pausing on backgrounding
   is the correct, conservative behavior here, not a workaround for a missing feature.

**Verified:** clean `compileDebugKotlin`, 13/13 unit tests, `assembleRelease` signed
(`1b13f1d9…d2510d8a`), no ad-hoc local build installed to the device. **Not verified:** either fix
on the actual TV - next test round, bundled with round 2's four items above.

## Non-goals — do not drift into these

- EPG data of any kind. The worker is unscheduled; "No schedule information" is correct.
- Search, favourites, hide/rename groups, multi-playlist switching. The DAO methods exist; wiring
  them is later.
- Player HUD, channel banner, zap overlay, audio/subtitle pickers — the player phase.
- VOD, Series, Guide, Settings screens — placeholders only. `VodDashboard.kt` stays as-is (dead,
  mock data) until the VOD phase replaces it.
- Cloud, pairing, web app, Firestore — the cloud phase (decision: revive; recorded in
  `AGENTS.md`).
- Onboarding redesign, QR code, the pairing server.
- R8/minify, dependency upgrades, Paging 3 (unless 1.6 forces it — then as its own change).

## Verification — test data

Two lists, two jobs:

- **Small (checkpoints A and B):** `https://iptv-org.github.io/iptv/countries/us.m3u` — public,
  legal, ~1–2K channels, dozens of groups. Fast to load, good enough to judge the layout.
- **Real (1.6, and anything playback-related):** the user's own provider — ~30K channels plus a
  large VOD catalogue. This is the list that actually matters; the small one is just cheaper to
  iterate against.

**How the real credentials get in.** The user has offered them for testing. They must not be
pasted into chat — transcripts persist. Either:
1. **The user enters them on the TV** via the LAN pairing form (`http://<tv-ip>:8080`) or the
   Xtream card. Nothing in the session ever sees them. Preferred.
2. **For unattended reloads** (e.g. after the v6 schema wipe, while the user is away):
   `~/.redsurf/test-playlist.url` on the laptop — same directory as the keystore, outside the
   repo, mode 600. **It already exists** (created 2026-09-10) and holds this provider's
   `get.php` URL. That one URL contains everything the Xtream path needs: host → `server`,
   `username=` → user, `password=` → pass. Parse those three out of it and submit them to the
   TV's pairing form (`POST http://<tv-ip>:8080/submit`, fields `type=xtream`, `server`, `user`,
   `pass`, `contentType=live`) with `curl`, exactly as a phone would. **Do not fetch the M3U URL
   itself** — that's the 327 MB / 38 s path §2b exists to avoid, and it's a heavy pull on a
   throttled provider. Never print the file's contents.

   **This provider allows one concurrent connection** (`Max Conns: 1`). If the user is watching it
   on another device, a Chromecast test stream will fail or kick them — check before playback
   tests, and don't expect multiview to work against it in a later phase. It expires 2026-11-08.

The device sleeps quickly. `adb shell input keyevent KEYCODE_WAKEUP` before every test, and
check `dumpsys package com.redsurf.tv | grep versionName` before trusting any result — both
lessons from Phase 0's §0.7.

**A Shield Pro exists** (another room). It is not the primary test device — the Chromecast is,
because it's the weakest hardware the app has to be good on. But if something is suspected to be
a Chromecast limit rather than an app bug, the Shield is how to tell the difference. See
`HARDWARE.md`.

The device sleeps quickly. `adb shell input keyevent KEYCODE_WAKEUP` before every test, and
check `dumpsys package com.redsurf.tv | grep versionName` before trusting any result — both
lessons from Phase 0's §0.7.

## Order of work, and where each lane sits

1. **1.1 → 1.2 → 1.3 → 1.4** — Sonnet, against this brief. Build after each. One device
   round-trip at the end of 1.4, not one per task.
2. **Checkpoint A** — Sonnet posts the three screenshots + the group-count check. **Opus reviews
   against the mockups; the user says whether it looks right.** Their eyes are the judge of taste;
   the brief can't encode that. Expect one round of adjustments.
3. **1.5** — Sonnet. One device round-trip.
4. **Checkpoint B** — same review. If playback fails, Opus decides what to do with the finding.
5. **1.6** — Sonnet. Report the number honestly.

This is the largest phase so far and will likely span more than one session. The checkpoints are
the natural stopping points — a session can end after A and a fresh one resume at 1.5 with
nothing lost, because everything it needs is in this file and the status board.

## Rules for whoever executes this

**Verification is proportional to risk. This is a budget rule, not a suggestion.**

- **Compile + unit tests is the default bar** for every task in 1.1–1.4. Build after each task;
  do not screenshot after each task. The device round-trip happens once, at Checkpoint A, when
  there's a whole screen to look at.
- **Trivial changes get trivial verification.** A renamed val, a moved import, a comment, a colour
  token swap — build it and move on. Don't install it, don't screenshot it, don't write a test for
  it. Phase 0 spent real budget verifying things that couldn't have been wrong; don't repeat that.
- **Device round-trips: two for the whole phase** (A, B) plus the single 1.6 measurement. Within
  a checkpoint, take the screenshots once, send them, stop. Don't iterate on the TV alone.
- **`[skip ci]` on every commit that doesn't change the APK** — docs, comments, this file.
  Releases are for things the user can install.

**Checkpoints are a hard stop, and the user reviews — not the session.**

- At Checkpoint A and B: take the screenshots, **send them to the user with `SendUserFile`** so
  they land in front of them on whatever device they're on, state plainly what to look at and
  what's known-incomplete, and **stop**. Do not proceed to the next task until the user has
  responded. Their eyes are the acceptance test for "does it look like RedSurf"; a session cannot
  pass that test on its own.
- Ask the user for the real-credentials file (`~/.redsurf/test-playlist.url`) the first time 1.6
  or a playback problem needs the real list — not before, and never for the contents.

**And the standing rules:**

- **Never mark an item done without running it.** A compile is not a screenshot; a screenshot on
  the wrong build is nothing (Phase 0 §0.7 caught exactly that — check `versionName` first).
- The decisions section is settled. If something in it turns out to be wrong in practice, stop and
  say so with evidence — don't quietly do something else.
- Anything not covered here that requires a judgment call → stop and hand back to Opus, per
  `WORKFLOW.md`. Anything covered here → just do it.
- No Hilt. No dependency upgrades beyond the three named (Coil, room-paging, paging-compose). No
  new screens beyond what's listed. No channel-count assumptions anywhere.
