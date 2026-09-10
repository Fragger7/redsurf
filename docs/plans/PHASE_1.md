# Phase 1 — Design system + Live TV

## Status board (update as you go)

| # | Task | State |
|---|---|---|
| 1.1 | Theme + focus system (`ui/theme/`) | ⬜ not started |
| 1.2 | Data layer: per-group Room queries, slim the ViewModel | ⬜ not started |
| 1.3 | App shell: top nav strip + placeholder tabs | ⬜ not started |
| 1.4 | Live TV: groups column + channels column | ⬜ not started |
| **A** | **Checkpoint — screenshot review (Opus + user)** | ⬜ |
| 1.5 | Preview pane with playback + fullscreen | ⬜ not started |
| **B** | **Checkpoint — screenshot + logcat review (Opus + user)** | ⬜ |
| 1.6 | Memory measurement + acceptance sweep | ⬜ not started |

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

---

## Decisions already made (don't re-litigate, don't ask again)

These are the Opus-lane calls for this phase. Sonnet executes against them.

1. **Cloud pairing is being revived** (user decision, 2026-09-10). The "Cloud Login" onboarding
   card stays. Nothing in Phase 1 touches pairing, the web app, or Firestore — the TV-side listener
   is the cloud phase's job. Just don't delete anything cloud-related.
2. **No Paging 3 yet.** Channels are queried **per group** from Room as `Flow<List<ChannelEntity>>`.
   The critical fix is that the app must **never load every channel across all groups into memory**
   — which is exactly what `MainViewModel.checkLocalCache` and `performSearch` do today
   (`getAllChannels()` then filter in Kotlin). A single group is typically hundreds to a few
   thousand rows; that fits. If 1.6's memory measurement says otherwise on a real playlist,
   Paging 3 (`androidx.room:room-paging`) is the escalation — as its own change, later.
3. **Coil 2.6.0 for channel logos** (`io.coil-kt:coil-compose:2.6.0`). It's the one new
   dependency this phase adds. It targets Kotlin 1.9 / Compose 1.5, which is what we're pinned to.
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

**`db/RedSurfDatabase.kt`** — add to `ChannelDao`:

```kotlin
data class GroupCount(val groupName: String, val count: Int)

@Query("SELECT groupName, COUNT(*) AS count FROM channels " +
       "WHERE playlistId = :playlistId AND isHidden = 0 " +
       "GROUP BY groupName ORDER BY groupName")
fun getGroupCounts(playlistId: String): Flow<List<GroupCount>>

@Query("SELECT * FROM channels WHERE playlistId = :playlistId AND groupName = :groupName " +
       "AND isHidden = 0 ORDER BY num, name")
fun getChannelsInGroup(playlistId: String, groupName: String): Flow<List<ChannelEntity>>
```

Room returns the aggregate into the POJO directly. Bump the database `version` only if the schema
changes — it doesn't here (queries only), so don't.

**`data/ChannelRepository.kt`** — thin wrapper exposing exactly those two flows plus
`getPlaylists()`. Constructed as `ChannelRepository(db.channelDao(), db.playlistDao())`.

**`MainViewModel.kt`** — slim it:
- `AppState.Loaded` no longer carries `groups`. It carries `playlists` and `activePlaylistId` only.
  The Live TV screen collects its own group/channel flows from the repository.
- Delete the in-memory `groupBy` in `checkLocalCache` and the in-memory filter in `performSearch`
  (search is a later phase; remove the method rather than leave a memory bomb behind).
- Expose `val repository: ChannelRepository` (set in `setDatabase`).
- In `loadPlaylist`, set `num = index` when mapping parsed channels to entities (it's `0` for every
  row today, so the channel-number column would be meaningless). `mapIndexed`.

**Acceptance:** builds; existing 11 unit tests pass; `MainViewModel` contains no call to
`getAllChannels()`.

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

**`ChannelsColumn.kt`** — header: group name + "N channels"; `TvLazyColumn` of channel rows from
`getChannelsInGroup(activePlaylistId, selectedGroup)`. Each row: **logo chip** (Coil
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

With the **full** iptv-org index loaded (10k+ channels — the stress test) and preview playing:

```bash
adb shell dumpsys meminfo com.redsurf.tv | grep -E "TOTAL PSS|Java Heap|Native Heap"
```

Record it in this file. **Target ≤ 150 MB PSS, hard ceiling 200 MB.** Baseline before Phase 1 was
41 MB at onboarding with no player. If it's over 200, the first suspect is 1.2's per-group query
having been bypassed somewhere; the second is Coil's cache cap not being applied; the third is
where Paging 3 becomes the answer. Report the number either way.

Then the sweep — every line below must be true, and each is either grep-checkable or has a
screenshot on record:

1. `grep -rn "Color(0xFF" --include="*.kt" tv-native/app/src/main | grep -v ui/theme/` → empty.
2. `grep -rn "getAllChannels" --include="*.kt" tv-native/app/src/main` → only the DAO definition.
3. `ExoPlayerView.kt` and `TiViMateLayout.kt` no longer exist.
4. Checkpoint A and B screenshots are on record and reviewed.
5. Memory number recorded, under the ceiling.
6. `./gradlew :app:testDebugUnitTest` green; `assembleRelease` green and signed
   (`apksigner --print-certs` → `1b13f1d9…d2510d8a`).
7. Status board above fully updated. `AGENTS.md` "Verified state" updated to match.

---

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

Load a playlist via the TV's LAN pairing form (`http://<tv-ip>:8080`) or direct M3U entry:

- **Smoke test:** `https://iptv-org.github.io/iptv/countries/us.m3u` (~1–2k channels, dozens of
  groups). Use this for Checkpoints A and B.
- **Stress test:** `https://iptv-org.github.io/iptv/index.m3u` (10k+ channels). Use this for 1.6
  only.

These are public, legal channel lists. The user may separately verify with their real provider
credentials — entered by them on the TV, never shared in chat.

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

- **Never mark an item done without running it.** A compile is not a screenshot; a screenshot on
  the wrong build is nothing (Phase 0 §0.7 caught exactly that — check `versionName` first).
- The decisions section is settled. If something in it turns out to be wrong in practice, stop and
  say so with evidence — don't quietly do something else.
- Anything not covered here that requires a judgment call → stop and hand back to Opus, per
  `WORKFLOW.md`. Anything covered here → just do it.
- Batch before you spend a device round-trip. Two checkpoints for the whole phase is the budget.
- No Hilt. No dependency upgrades. No new screens beyond what's listed.
