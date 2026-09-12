# Phase 2 — The Player

## Status board (update as you go)

| # | Task | State |
|---|---|---|
| 2.1 | `PlayerScreen`: key router, overlay state machine, Back peeling, scrim | ✅ done & build-verified |
| 2.2 | Zap: neighbour queries, UP/DOWN, zap banner, stream badges, no-black-screen | ⬜ not started |
| **A** | **Checkpoint — user tests entry, zap, OK overlay skeleton, Back** | ⬜ |
| 2.3 | OK overlay: info block, tile row, elevator to action row, pickers | ⬜ not started |
| 2.4 | LEFT channel-list overlay, RIGHT last-channel zap, long-press context menu | ⬜ not started |
| 2.5 | Recents + last-channel: table, real migration 6→7, History tile, resume setting | ⬜ not started |
| **B** | **Checkpoint — user tests the full matrix on the real list** | ⬜ |
| 2.6 | Acceptance sweep: migration from v0.20.x, zap latency, memory with overlays | ⬜ not started |

**Goal:** watching live TV feels like TiviMate. Today the fullscreen player is a bare video surface
that swallows every key except Back; to change channel you leave it. After this phase, everything
`docs/vision/PRODUCT_VISION.md` §2 calls "the industry standard" works from the remote without
leaving the video: OK → overlay, UP/DOWN → zap, LEFT → channel list, RIGHT → last channel,
long-press OK → context menu, Back peels one layer at a time.

**Why this and not VOD/Series/Guide:** `PRODUCT_VISION.md` §1 — the document that outranks every
other — opens with *"The Player IS The App."* Live TV is what the family uses daily; this is the
room they live in, and it's the largest remaining gap between RedSurf and "feels like TiviMate."
VOD and Series are new rooms. The Guide merge (`AGENTS.md`, Product decisions) needs EPG data
first (Phase 3) and needs *this* overlay model to exist to be built on.

**Definition of done:** on the Chromecast, with the user's real ~30K-channel list: enter a channel
from the browse screen (one OK, no overlay), zap UP/DOWN through 20+ channels with no black
screen between them and a banner each time, press OK and see the info block + tile row, press
DOWN and see the action row replace it, change an audio track from it, press LEFT and pick a
different channel without leaving fullscreen, press Back from each layer and land exactly one
layer down each time, update from a v0.20.x build and still have the playlist — **all observed by
the user, not inferred from a green build.** Memory with overlays open stays within 30 MB of
Phase 1.6's 113 MB.

---

## Reference material — read these, they are the spec

The user walked TiviMate on their own TV (2026-09-12) and described three levels precisely. The
screenshots in `docs/vision/references/tivimate/` anchor each one:

| Level | What the user sees | Screenshot |
|---|---|---|
| 0 — video | Second OK from the browse screen: **fullscreen, no overlay at all.** | (none needed — nothing on screen) |
| Zap banner | UP/DOWN with nothing showing: zaps, and the *same info block as Level 1* appears alone at the **bottom** of the screen, then auto-hides. | `Channelplayeroverlay.webp` — the info block only, imagine it dropped to the bottom edge |
| 1 — OK overlay | Breadcrumb `Playlist • Group` top-left, clock top-right; info block (logo, programme, times, remaining, `3 Channel 3`, `HD · 30 FPS · STEREO` badges, next programme, full-width progress bar); a **tile row** beneath: TV guide, History, then recent channels; a down-chevron. | `Channelplayeroverlay.webp`, `Recentchannelswhileplaying.png` (same screen) |
| 2 — action row | DOWN from the tile row: the tile row leaves and the **action row takes the same real estate** — "like going down a floor in an elevator." UP reverses. Info block stays. | `Fullscreenchanneloverlaymenu.jpg` (a TV photo; the icon row is legible, the rest is not - layout is *decided* below, not copied) |
| LEFT | Channel list panes slide over the video. | `GuideOverlayWhileChannelPlaying.png` — note TiviMate itself shows `No information` rows without EPG |

Convert the `.webp` files with Pillow before viewing (`python3 -c "from PIL import Image; Image.open('x.webp').save('/tmp/x.png')"`).

**Palette and typography:** `docs/vision/UI_SPEC.md` §1–2 and `ui/theme/` — same tokens, same
`RedSurfType` roles, same `RedSurfFocus` states. The overlay is RedSurf red/black, never
TiviMate's blue. Borrow the geometry, not the colour (`AGENTS.md`, constraint 2).

---

## Decisions already made (don't re-litigate, don't ask again)

Every judgment call is made here so execution is Sonnet-lane. Items marked **(confirm at A/B)**
are decided but no screenshot could show them - the user corrects them on the first device look
if wrong. Build to the decision as written.

1. **One composable owns fullscreen: `ui/player/PlayerScreen.kt`.** It replaces the inline
   `if (isFullscreen) Box { PlayerHost }` in `LiveTvScreen.kt`. `PlayerHost` stays as the
   ExoPlayer owner exactly as it is; `PlayerScreen` composes it and layers the overlays on top.
   `LiveTvScreen`'s browsing Row stays composed underneath, as today (state preservation).

2. **One key router, one state machine.** `PlayerScreen` holds
   `overlay: PlayerOverlay` = `None | ZapBanner | Controls(floor: Tiles | Actions) | ChannelList |
   ContextMenu | Picker(kind)`. All D-pad handling for Level 0 lives in a single `onKeyEvent` on
   the fullscreen root, dispatching on `overlay`. When an overlay with focusable content is
   showing, real Compose focus moves *into* it (first tile / current channel row) and normal
   focus traversal takes over inside it; the root only handles what the overlay doesn't consume.
   No second key handler anywhere else in the player.

3. **The control matrix at Level 0** (nothing showing), per `PRODUCT_VISION.md` §2:
   - **OK** → `Controls(Tiles)`. **Long-press OK** (≥ 500 ms, `KeyEvent.isLongPress` /
     `repeatCount`) → `ContextMenu`.
   - **UP / DOWN** → zap to previous / next channel *in the current group by `num`* and show
     `ZapBanner`. Zap on the first key-down only: ignore `nativeKeyEvent.repeatCount > 0` -
     a held button must not machine-gun through channels. **(confirm at A)**
   - **LEFT** → `ChannelList`. **RIGHT** → zap to the *previous channel watched* (last-channel
     zap). The vision makes RIGHT configurable between this and a mini-EPG; the mini-EPG needs
     EPG data and comes in Phase 3. Ship the half that's real.
   - **Back** → exit fullscreen to the browse screen (existing behaviour, existing focus
     restoration - `ChannelsColumn.returnFocusRequester`, unchanged).
   - Everything else is swallowed (`onKeyEvent { it.key != Key.Back }` stays the outer guard).

4. **Back peels exactly one layer, always.** `Picker → Controls(Actions)`,
   `Controls(Actions) → Controls(Tiles)`, `Controls(Tiles) → None`, `ChannelList → None`,
   `ContextMenu → None`, `ZapBanner → None` (dismisses early), `None → browse screen`. Never
   skips a layer, never exits fullscreen from inside an overlay. This is the user's standing
   "state and focus discipline" rule (`AGENTS.md`) applied to the player. **(confirm at A)**

5. **The elevator.** `Controls` has two floors in one slot: `Tiles` (TV guide, History, recent
   channels) and `Actions`. DOWN from any tile on the `Tiles` floor → `Actions`; UP from any
   action on `Actions` → `Tiles`. Implement as a single row slot whose content animates
   vertically (`AnimatedContent` with `slideInVertically`/`slideOutVertically`, 200 ms) - the
   info block above it does not move. Each floor remembers which item was focused so UP/DOWN and
   back preserves position (state rule). The chevron under the row points down on `Tiles`, up
   on `Actions`. **(confirm at A: direction of the slide)**

6. **Timeouts.** `ZapBanner` auto-hides after **4 s**. `Controls` auto-hides after **8 s** with
   no key press; any key resets the timer. `ChannelList`, `ContextMenu`, `Picker` never auto-hide
   (the user is mid-decision). Both numbers are `const val`s in `PlayerScreen.kt` today and
   become Settings entries ("OSD timeout", `PRODUCT_VISION.md` §5) when Settings gets its shell -
   not in this phase. **(confirm at A)**

7. **Info block content and geometry** (Level 1 and the zap banner - the *same* composable,
   `PlayerInfoBlock`, positioned differently). On the 960×540 dp canvas, measured off
   `Channelplayeroverlay.webp` scaled to it:
   - Logo tile 64 dp square, `SurfaceRaised`, 10 dp radius, `ChannelLogo` fallback initial.
   - Line 1: programme title, `RedSurfType.heroTitle`. Until EPG exists this is the literal string
     **"No schedule information"** in `TextSecondary` - honest, same as the browse screen.
   - Line 2: `start – end` · thin progress · `N min` (all EPG - omit the whole line until Phase 3)
     then `num  name` in `rowTitle`, then badges.
   - Badges: `SurfaceRaised` chips, `RedSurfType.badge`, from ExoPlayer at
     `onVideoSizeChanged`/`videoFormat` and `audioFormat`: resolution class (`SD` < 720p, `HD`
     ≥ 720, `FHD` ≥ 1080, `4K` ≥ 2160), `${frameRate.roundToInt()} FPS` when known, audio
     (`STEREO` for 2 ch, `5.1` for 6, else the channel count) and codec short name from
     `sampleMimeType` (`AAC`, `AC3`, `EAC3`, `MP3`). Unknown → badge omitted, never a placeholder.
   - Line 3: next programme (EPG - omit until Phase 3).
   - Progress bar: full width, 3 dp, `Accent` on `SurfaceRaised` - EPG-driven; **omitted until
     Phase 3**, not shown empty.
   - Breadcrumb top-left: `playlistName • groupName`, `rowSecondary`, `TextSecondary`. Clock
     top-right, same style, `EEE, MMM d, h:mm a`, ticking each minute.
   - Scrim: a vertical gradient from transparent at 55% height to `Background` at 85% alpha at
     the bottom, drawn *behind* the info block and rows, never a full-screen dim.

8. **Tile row (Level 1).** Tiles are 100 × 72 dp, 8 dp apart, `RedSurfFocus.shape(10.dp)`,
   `rowColors(resting = SurfaceRaised.copy(alpha = 0.85f))`, focus ring/glow/scale as everywhere.
   Order: **TV guide** (icon `Icons.Filled.List` + label) → **History** (`Icons.Filled.History`) →
   recent channels (up to 8, most recent first, excluding the one playing: logo tile 28 dp +
   name, `rowTitle`, one line, ellipsis). Opening `Controls` focuses the **first recent channel**
   if any, else TV guide. **(confirm at A)**
   - OK on a channel tile → tune to it (stays fullscreen, shows `ZapBanner`).
   - OK on **TV guide** → exit fullscreen to the browse screen (the merged guide is Phase 3).
   - OK on **History** → `Picker(History)`: a vertical list of the last 30 watched, same rows.

9. **Action row (Level 2).** Only actions that *work* - no greyed tiles for Multiview, PiP,
   Recordings, Search. Those are separate features (`UI_SPEC.md` §7 for Multiview; the hardware
   ceiling is 2 tiles) and a disabled tile is hollow UI. Order, each an icon + label tile of the
   same size as Level 1's:
   1. **Channels** → `ChannelList` (same as LEFT).
   2. **Audio** → `Picker(Audio)`: `TrackManager.getAudioTracks`, one row per track (language
      name or `Track N`, channel count), current one selected. Selecting calls
      `selectAudioTrack`.
   3. **Subtitles** → `Picker(Subtitles)`: `Off` + `getSubtitleTracks`.
   4. **Aspect** → cycles `RESIZE_MODE_FIT → FILL → ZOOM` on the `PlayerView` (three-way toggle,
      label shows the current one, no picker) and remembers per session.
   5. **Video info** → `Picker(Info)`: read-only rows - resolution, frame rate, video codec,
      audio codec/channels, bitrate if known. Closes on Back.
   `TrackManager` is reused as-is. `ui/player/PlayerOsd.kt` is **deleted** - it's dead (no
   callers), built on the pre-Phase-1 `data.Channel` model, hardcodes colours, and duplicates
   what `PlayerScreen` becomes. `MultiViewEngine.kt` stays untouched (its own feature, its own
   phase). **(confirm at A: the five-action set)**

10. **Pickers** are a narrow panel anchored bottom-right, `Surface` colour, 16 dp radius, rows
    36 dp like `GroupsColumn`'s, focus lands on the current selection, OK selects and closes,
    Back closes without change.

11. **LEFT overlay = the browse columns over the video.** Categories + channels
    (`GroupsColumn` + `ChannelsColumn` composed as *new instances* inside the overlay, seeded
    with the current channel's group and `streamId`), in a panel covering the left **62%** of the
    screen with a scrim, the video still visible on the right. Focus lands on the current channel
    row (its `returnFocusRequester`). OK tunes and closes the overlay; Back closes it. TiviMate's
    second pane (the per-channel schedule) is EPG and comes in Phase 3. Two paged lists alive at
    once (this overlay's and the browse screen's underneath) is fine - Paging holds pages, not
    playlists; 2.6 measures it. *Recorded alternative for Phase 3:* make the browse Row itself the
    overlay (it's already composed underneath) - one instance, no seeding, and it's the natural
    shape of the Guide merge. Not this phase: it changes what "exit fullscreen" means.

12. **Context menu (long-press OK).** A small centred panel: **Add to favourites / Remove from
    favourites** (`ChannelDao.updateFavorite` - exists, dead until now) and **Hide channel**
    (`updateHidden` - exists; hidden rows are already excluded by every live query, so this works
    end-to-end today). "Programme description" and "Lock" wait for EPG and parental controls.
    Two items. **(confirm at B)**

13. **Zap mechanics.** Neighbour lookup is two O(1) queries, never a list:
    `ChannelDao.nextInGroup(playlistId, groupName, num)` = first row with `num > :num` ordered by
    `num, name`; `prevInGroup` mirrored. Wrap around at the ends. Zap = `exoPlayer.setMediaItem`
    + `prepare()` on the *same* player (`PlayerHost` already does this) - never `stop()`, never
    release. `PlayerView.setKeepContentOnPlayerReset(true)` and `setShutterBackgroundColor(BLACK)`
    so the last frame holds until the next stream's first frame (`PRODUCT_VISION.md` §3 "black
    screen minimizer"). Crossfade is *not* in scope - holding the frame is the 90% of it.
    `LoadControl`: `DefaultLoadControl.Builder().setBufferDurationsMs(minBufferMs = 2_500,
    maxBufferMs = 15_000, bufferForPlaybackMs = 500, bufferForPlaybackAfterRebufferMs = 1_500)`
    (§4 "Fast Zap"). These are a starting point; 2.6 measures key-press-to-first-frame and
    records it, and they're tuned only from that number.

14. **Recents and last channel: one table, one real migration.** New entity
    `recent_channels(streamId PK, playlistId, watchedAt: Long)`; upsert on every tune (from
    anywhere: browse OK, zap, tile, LEFT overlay), keep the newest 50. History tile and the
    recent-channel tiles read from it; RIGHT reads the second-newest row. **Room version 6 → 7
    with a written `Migration(6, 7)` (`CREATE TABLE …`) - `fallbackToDestructiveMigration()` must
    not be the path taken.** Phase 1 accepted destructive migrations because no user data existed;
    it does now - the user's playlists - and "the playlist vanished after an update" is a bug they
    have already reported once. 2.6's first line is a real update from a v0.20.x build with the
    playlist surviving.

15. **Resume last channel on launch: built, default OFF.** `PRODUCT_VISION.md` §1 wants to boot
    into playback; the user chose StreamVault's browse shell as home (Product decisions,
    `AGENTS.md`). Both are honoured: a setting `Resume last channel on launch` (the first real
    preference in Settings - a single toggle row added to the existing flat screen, *not* the
    categorized shell, which is its own backlog item) defaulting to **off**. When on, the app
    opens straight into `PlayerScreen` on the newest `recent_channels` row, and Back goes to the
    browse screen as usual. Persist with `SharedPreferences` - no DataStore dependency for one
    boolean. **(confirm at B: the default)**

16. **Focus and state - the standing rule, applied here explicitly.** Opening any overlay
    focuses a specific stated element (decisions 8, 10, 11). Closing any overlay returns real
    focus to the fullscreen root (`fullscreenFocus.requestFocus()`), never to whatever Compose
    picks. Each elevator floor remembers its focused index. Exiting fullscreen restores the
    browse screen's channel row (already built). Every one of these is a `FocusRequester`
    actually wired, not assumed - see the "when the user leaves and comes back" question in
    `AGENTS.md`.

17. **Screen-on, lifecycle pause, opaque black, key swallowing** - all already in
    `PlayerHost`/`LiveTvScreen` from the Phase 1 quick-fix rounds. Keep them; `PlayerScreen`
    wraps them, it doesn't re-implement them.

18. **Don't touch:** dependency versions, `AppShell`'s nav model, the browse screen's layout,
    `MultiViewEngine`, anything EPG (`sync/EpgSyncWorker.kt`, `EpgProgramEntity` - Phase 3).

---

## 2.1 — what actually happened so far (2.1a, 2026-09-12, Sonnet)

Split into two sessions at the user's request (rate-limit-conscious, wanted small verifiable
steps): **2.1a - pure extraction, done.** `ui/player/PlayerScreen.kt` created, owning exactly what
the inline `Box` in `LiveTvScreen.kt` used to (opaque black background, focus capture, Back-key
passthrough, `PlayerHost`) - zero behavior change, confirmed by inspection (same modifiers, same
order, same comments carried over explaining *why* each one exists). `PlayerOverlay` (decision 2's
sealed class) and `PickerKind` are declared in the new file but not wired to anything yet - next
session (2.1b) is the actual key router, Back-peeling, timeouts, scrim, breadcrumb/clock.

`LiveTvScreen.kt` lost four now-unused imports (`focusable`, `Color`, `Key`/`key`/`onKeyEvent`) and
the direct `PlayerHost` import, gained `PlayerScreen`. Everything else in that file - the browse
Row, `channelReturnFocus`, the fullscreen `BackHandler`, the debounce effects - untouched.

**Verified:** clean `compileDebugKotlin`, 13/13 unit tests, `assembleRelease` signed
(`1b13f1d9…d2510d8a`), no ad-hoc local build installed to the device. **Not verified:** on the
actual TV - this step has no new behavior to check, so it's being trusted to the build/test
verification alone rather than spending a device round-trip on a no-op change.

**2.1b - state machine, key router, Back-peeling, timeouts. Done; scrim/breadcrumb/clock still
pending.** Same session, one more small slice (still rate-limit-conscious - deliberately stopped
short of the full #2.1 task). `PlayerScreen` now owns:
- The overlay state machine and Level-0 key router (decisions 2-3): OK short-press → `Controls
  (Tiles)`, long-press (`nativeKeyEvent.isLongPress`, tracked across the held-key stream since
  it's only known true on a later repeat event, not the initial down) → `ContextMenu`, UP/DOWN →
  `ZapBanner` (no real zap yet - #2.2), LEFT → `ChannelList`, RIGHT → `ZapBanner` (real
  last-channel zap is #2.4). All act on first-down only (`repeatCount == 0`), not every repeat
  tick.
- The elevator (decision 5): DOWN/UP inside `Controls` swap `Tiles`/`Actions` in place rather than
  opening a new overlay.
- Back-peeling (decision 4) - moved out of `LiveTvScreen` entirely into a `BackHandler` inside
  `PlayerScreen`, since peeling needs to inspect `overlay` state that now lives here.
  `onExitFullscreen` is the one case (`overlay == None`) where it still delegates up.
- Timeouts (decision 6): 4s/8s, `LaunchedEffect(overlay, activityTick)` - `activityTick` exists
  because reassigning the same `PlayerOverlay` value (e.g. zapping again while the banner is
  already up) is a no-op to Compose and wouldn't otherwise restart the delay, which decision 6's
  "any key resets the timer" requires.
- `Log.d("PlayerScreen", "overlay -> $overlay")` on every transition (readable in `logcat`).

**Not done, still #2.1's remaining slice:** the scrim and breadcrumb/clock (decision 7 UI only -
the info block content itself is #2.3). No visual change exists yet; `overlay` state is only
observable in `logcat`, matching Checkpoint A's own emphasis that this much is checkable without
a screen recording.

**Verified:** clean `compileDebugKotlin`, 13/13 unit tests, `assembleRelease` signed
(`1b13f1d9…d2510d8a`), no ad-hoc local build installed to the device. **Not verified:** on the
actual TV - real behavior exists now (state transitions, Back-peeling, timeouts), so this is a
good point for a `logcat`-only device check if there's time; otherwise it waits for the scrim/
breadcrumb slice to bundle into one real Checkpoint A round.

## 2.1 — closed out: scrim, breadcrumb, clock (2026-09-12, Sonnet)

Third small slice this session. `PlayerScreen` gained a `breadcrumb: String` param (computed by
`LiveTvScreen`, which already has the group/playlist context, as `"PlaylistName › GroupName"` for
whatever channel is playing - found by matching `focusedChannel`'s `playlistId`/`groupName`
against `groups`). Inside `PlayerScreen`: the scrim (`Brush.verticalGradient`, transparent to 0.55,
fading to `Background` at 85% alpha at the bottom, decision 7's exact stops), the breadcrumb
top-left, and a clock top-right (`EEE, MMM d, h:mm a`, ticking every 60s via a `LaunchedEffect`
loop) - all three shown only when `overlay != PlayerOverlay.None`, matching the reference
screenshots: Level 0 (nothing pressed) stays pure video, no transient chrome on entry.

This closes **all** of #2.1: `PlayerScreen` exists, owns the fullscreen surface, the overlay state
machine, the key router, Back-peeling, timeouts, and now the scrim/breadcrumb/clock. The info
block's actual content (logo, programme text, badges) and the tile/action rows remain #2.3 - this
task's chrome is deliberately just the frame around where those will sit.

**Verified:** clean `compileDebugKotlin`, 13/13 unit tests, `assembleRelease` signed
(`1b13f1d9…d2510d8a`), no ad-hoc local build installed to the device. **Not verified:** on the
actual TV - this is real visible UI now (the scrim + breadcrumb + clock actually render), so it's
a good candidate for the user's next real screenshot, even ahead of Checkpoint A's full list.

## 2.2 — what actually happened so far (backend slice, 2026-09-12, Sonnet)

First of two slices (same bite-sizing as #2.1). **Done, backend only, nothing wired to the UI or
key router yet:**
- `ChannelDao.nextInGroup`/`prevInGroup`/`firstInGroup`/`lastInGroup` (decision 13) - two O(1)
  indexed neighbour lookups plus the two wrap-around fallbacks, each intentionally a separate
  simple query. `ChannelRepository.nextChannel`/`prevChannel` apply the wrap-around (fall back to
  first/last when a neighbour query returns null at either end of the group).
- `PlayerHost` buffer tuning: `DefaultLoadControl` with decision 13's exact numbers
  (min 2.5s / max 15s / 500ms to start / 1.5s after a rebuffer), `setKeepContentOnPlayerReset(true)`
  + `setShutterBackgroundColor(BLACK)` so a channel switch holds the last frame instead of
  flashing to a blank surface.
- `StreamInfo` (resolution class, frame rate, audio channels, audio + video codec) computed from
  `exoPlayer.videoFormat`/`audioFormat` on a `Player.Listener`, delivered via a new
  `PlayerHost(onStreamInfo: (StreamInfo) -> Unit = {})` callback param. **Deviation from the
  brief's literal wording** (which suggested exposing a `StateFlow<StreamInfo>`): a callback
  matches how every other composable in this codebase already reports upward
  (`onFullscreenChanged`, `onChannelFocused`, …), so it was used instead of introducing a second
  pattern - noted per the brief's own instruction to say so when a decision needs adjusting in
  practice. Nothing calls `onStreamInfo` yet since `PlayerScreen` doesn't pass it - next slice.

**Second slice - the real thing, done.** `PlayerScreen` now:
- Calls `repository.prevChannel`/`nextChannel` from the UP/DOWN branch of the key router (a
  `rememberCoroutineScope` launch, since the key handler itself isn't suspend), and on a result
  calls the new `onChannelChanged` callback - the bridge to `LiveTvScreen`'s `focusedChannel`/
  `previewUrl` (state/focus discipline: zapping has to update the same state fullscreen-exit
  already restores focus to, or Back-after-zapping would land you back on the channel you
  *opened*, not the one you're *watching*). `onChannelChanged` sets `previewUrl` immediately,
  bypassing the browse-debounce, same reasoning as `onChannelOpen`.
- Passes `onStreamInfo = { streamInfo = it }` to `PlayerHost`, so the listener built in the first
  slice now actually reaches something.
- Renders the real `PlayerInfoBlock` (decision 7's content: 64dp logo, channel num+name, the
  badges - each individually omitted when unknown, never a placeholder) at the bottom whenever
  `ZapBanner` or `Controls` is showing. Both states use the same position for now, since the
  tile/action row that would differentiate them doesn't exist until #2.3.

**Not done:** LEFT's real channel-list overlay and RIGHT's real last-channel zap (both still just
flip `overlay` state, no actual channel change) - #2.4. The tile/action row content - #2.3.

**Verified:** clean `compileDebugKotlin`, 13/13 unit tests, `assembleRelease` signed
(`1b13f1d9…d2510d8a`), no ad-hoc local build installed to the device. **Not verified:** on the
actual TV - this is the first genuinely user-visible slice since the scrim/breadcrumb/clock (real
zapping, real badges) - worth an actual test pass rather than trusting build/tests alone.

**Acceptance:** 20 consecutive zaps on the real list with no black frame between channels
(video holds until the next first frame); banner shows and hides; badges match what the stream
actually is (check one known 1080p channel and one SD). Holding DOWN zaps once.

## Checkpoint A — user, on the device

1. OK from browse → fullscreen, nothing on screen. Back → browse, focus on the channel row.
2. UP/DOWN → zaps, banner at the bottom, hides by itself. Holding a key doesn't machine-gun.
3. OK → overlay appears (info block + placeholder rows). DOWN → row swaps floors. UP → back.
   Back → peels one layer each press. Confirm the slide direction feels like "an elevator."
4. Timeouts feel right (4 s banner, 8 s overlay)?
5. LEFT / RIGHT / long-press do what decision 3 says (LEFT may still be a placeholder panel).

## 2.3 — OK overlay: info block, tiles, elevator, actions, pickers

Decisions 7–10. `PlayerInfoBlock` real; tile row with TV guide / History / recents (recents from
2.5's table - stub with the last 8 from an in-memory list until 2.5 lands, then swap); the
elevator; the five actions; the pickers; `TrackManager` reuse; delete `PlayerOsd.kt`.

**Acceptance:** OK on a recent-channel tile tunes. Audio picker lists the real tracks of a
multi-language channel and switching is audible. Subtitles Off/On works on a channel that has
them. Aspect cycles visibly on a 4:3 channel. Video info shows the same numbers as the badges.

## 2.4 — LEFT overlay, RIGHT last-channel, context menu

Decisions 11, 3 (RIGHT), 12.

**Acceptance:** LEFT opens the panel with focus on the current channel; OK on another tunes and
closes; Back closes. RIGHT after zapping A→B→C lands on B, again on C. Long-press → favourite
toggles (visible in the browse screen? not yet - favourites have no UI; verify via
`adb shell` sqlite or by hiding a channel and seeing it gone from the list).

## 2.5 — Recents, migration, History, resume

Decision 14 and 15. `RecentChannelEntity`, DAO, `Migration(6, 7)`, upsert on every tune, History
picker, resume-on-launch setting (default off) as one toggle row in `SettingsScreen`.

**Acceptance:** install this build over v0.20.x **with a playlist loaded** → playlist still there,
recents empty, no crash. Watch three channels → History shows all three, newest first; the tile
row shows them. Toggle resume on, relaunch → opens in the player on the last one; Back → browse.

## Checkpoint B — user, on the device, real list

1. The whole matrix, decision 3, from memory - does it match TiviMate muscle memory?
2. The five-action set - anything missing that you'd reach for, anything that shouldn't be there?
3. Context menu: is favourite/hide the right two?
4. Resume-on-launch: should the default be on?
5. Anything that lost your place. (The standing rule.)

## 2.6 — Acceptance sweep

1. **Migration:** `adb install -r` of this release over a v0.20.x install that has the user's
   playlist → `SELECT COUNT(*) FROM channels` unchanged, `recent_channels` exists. Recorded.
2. **Zap latency:** time from key-down to `onRenderedFirstFrame` for 10 zaps on the real list,
   logged from `PlayerHost`; record median and worst. No pass/fail yet - a baseline, same as
   1.6's import time. If median > 1.5 s, tune decision 13's `LoadControl` numbers *from this
   measurement* and re-record.
3. **Memory:** `dumpsys meminfo` PSS with `Controls` open, then with `ChannelList` open, on the
   real list. Both within 30 MB of 1.6's 113 MB.
4. `grep -rn "PlayerOsd" tv-native/app/src` → empty. `grep -rn "Color(0xFF" --include="*.kt"
   tv-native/app/src/main | grep -v ui/theme/` → only `VodDashboard.kt` and `MultiViewEngine.kt`
   (both still Non-goal files).
5. Every `FocusRequester` in `PlayerScreen` has a comment naming the transition it serves.
6. 13/13 tests; signed release; status board and `AGENTS.md` updated.

---

## Non-goals — do not drift into these

- EPG: data, sync worker, programme lines, progress bar, mini-EPG, the schedule pane, the merged
  Guide screen. All Phase 3. The layout has the slots; they stay empty and honest.
- Multiview, Picture-in-picture, Recordings, Search — separate features. No tiles for them.
- Number-key direct channel entry. (Small, real, later.)
- Boot-into-player as the *default* (decision 15 builds it, off).
- The categorized Settings shell (`AGENTS.md` backlog) - one toggle row is added to the flat screen.
- Crossfade between channels (holding the last frame is the deliverable).
- Any change to the browse screen, `AppShell`, or the nav strip.

## Verification — test data

The user's real Xtream list (~28–30K live channels, `~/.redsurf/test-playlist.url` / the pairing
form). It has multi-language channels (audio picker), 4:3 channels (aspect), SD and HD (badges).
The small iptv-org list is fine for 2.1's state-machine work only.

## Order of work, and who

1. **2.1 → 2.2 → Checkpoint A** — Sonnet. Build + tests + signed release; one build handed to the
   user with the checkpoint list. No ADB loops (the standing workflow rule), *except* 2.2's zap
   verification and 2.6's measurements, which are diagnostic and explicitly allowed.
2. **2.3 → 2.4 → 2.5 → Checkpoint B** — Sonnet, one or two builds.
3. **2.6** — Sonnet.
4. **Opus** only if Checkpoint A or B raises a design question this brief didn't decide - the
   overlay's look against the reference is the likeliest one.

## Rules for whoever executes this

- `AGENTS.md` first. Then this file's decisions. If a decision here turns out to be wrong in
  practice, say so in the "what actually happened" note and fix it - don't silently do
  something else.
- The state-and-focus rule is a gate, not a nice-to-have: for every overlay, name where focus
  lands on open and on close, in code comments, before calling it done.
- "Verified" means observed on the Chromecast or in logcat from it. Everything else is "builds
  clean" or "tests pass" - say which.
- Log what actually happened under each task, as Phase 1 did. Future-you reads it.
