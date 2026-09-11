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
