# Section O Polish Items — Engineering Brief & Code Audit

**Builder:** Gemini  
**Branch:** `agy/015-draft-polish-o`  
**Date:** 2026-10-31  
**Source:** `docs/plans/TESTING_OUTSTANDING.md` Section O (User feel check received 2026-10-31 on v0.65.9)  
**Task reference:** `docs/agy/tasks/015-draft-polish-o.md`

---

## 1. Recents Strip: Missing Clear Tile & Half-Width Last Tile

### User Quote
> "BUG - recents: no Clear tile visible; the last tile is cut to half width."  
> *(Section O line 53)*

### Code Location & Audit
- **Files & Functions:**
  - `tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt`
    - `TileRow` ([lines 1009–1046](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt#L1009-L1046))
    - `Tile` ([lines 1110–1131](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt#L1110-L1131))
    - `ChannelTile` ([lines 1134–1160](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt#L1134-L1160))
    - Controls Floor hosting `TileRow` ([lines 753–780](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt#L753-L780))

### Root Cause Analysis
- `TileWidth = 100.dp` ([line 1107](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt#L1107)).
- `TileRow` arranges items with `Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp))`.
- Padding on the row is `Modifier.padding(horizontal = 24.dp)` ([line 779](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt#L779)).
- On a 1080p display at density 2.0, the total Compose viewport width is **960.dp**.
- When recents has up to 8 channels, the row contains:
  - 1 "TV guide" tile (`100.dp`)
  - 1 "History" tile (`100.dp`)
  - Up to 8 recent channel tiles (`8 × 100.dp = 800.dp`)
  - 1 "Clear" tile (`100.dp`)
  - Total = 11 tiles = `1,100.dp` + 10 gaps (`80.dp`) + horizontal padding (`48.dp`) = **`1,228.dp`**.
- Content width available = `960.dp - 48.dp = 912.dp`. Each item slot takes `108.dp`. `912 / 108 = 8.44` tiles.
- Tile 0 through 7 fit on screen. Tile 8 is clipped to `~0.44` width (half width), and Tile 9 & 10 ("Clear") are pushed entirely outside the window bounds. Because `TileRow` is a non-scrollable `Row`, D-pad RIGHT cannot bring them into view.

### Proposed Change
1. Change `TileRow` container from a static `Row` to a horizontally scrollable container using `Row(modifier = modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp))`.
2. All 11 items remain composed (no uncomposed recycling issues under `FOCUS_MODEL.md` Rule 4), and Compose's built-in `BringIntoViewRequester` automatically smoothly scrolls the focused tile into view as the user navigates RIGHT to the Clear tile.
3. When "Clear" is clicked, recents is cleared, and focus safely reclaims to "TV guide" or exits controls.

### Risks & Mitigations
- **Focus / Scroll conflict:** If `horizontalScroll` uses default TV `BringIntoViewSpec`, moving focus may cause rapid jumpy shifts. Mitigation: Provide `LocalBringIntoViewSpec` with a smooth 0-offset or standard scroll alignment per `FOCUS_MODEL.md` Rule 10.
- **Performance:** Negligible. At most 11 lightweight static surface tiles; keeping all 11 composed avoids lazy-composition frame drops.

### Complexity & Size: **S** (Small, ~15–25 lines)

---

## 2. One "Video Info" Overlay & 0.5 s Network Speed Refresh

### User Quote
> "Stats for nerds: found it; redundant with the Video Info tile, and the only way out is Back, which drops to Live TV (wrong). User's call: remove the tile/feature, keep Video Info, likely put Video Info in the long-press OK menu; any info overlay closes on OK or one Back."  
> "Decisions, same day (user answers): Stats for nerds removed; Video Info is the one overlay - graceful exit (OK/one Back), add more useful metrics; network speed must update continuously (today it's laggy)."  
> *(Section O lines 41–43, 70–72)*

### Code Location & Audit
- **Files & Functions:**
  - `tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt`
    - `VideoInfoOverlay` ([lines 1452–1524](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt#L1452-L1524))
    - `StatsForNerds` ([lines 1538–1572](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt#L1538-L1572))
    - `ActionRow` ([lines 1073–1105](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt#L1073-L1105))
    - `peelBack` ([lines 425–450](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt#L425-L450))
    - Key dispatch in `PlayerScreen` ([lines 544–546](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt#L544-L546))
  - `tv-native/app/src/main/java/com/redsurf/tv/player/PlayerController.kt`
    - `startNetworkStatsPoll` ([lines 369–396](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/player/PlayerController.kt#L369-L396))
    - `transferListener` ([lines 202–210](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/player/PlayerController.kt#L202-L210))
    - `NetworkStats` data class ([lines 89–98](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/player/PlayerController.kt#L89-L98))

### Root Cause Analysis
1. **Redundancy & Trap:** `StatsForNerds` was an independent boolean overlay (`showStats`) placed in the top corner with no focusable node. When active, hitting Back peeled the player overlay stack down to `None` and then called `onExitFullscreen()`, dropping straight to Live TV.
2. **VideoInfo Dismissal:** `VideoInfoOverlay` is wrapped in `PlayerOverlay.Picker(PickerKind.Info)`. Because it contains no focusable child buttons, key events were unhandled. Pressing OK did nothing, and Back peeled to `Controls(Floor.Actions)`.
3. **Laggy Network Speed:** `startNetworkStatsPoll` in `PlayerController.kt` executed `delay(2_000)` with calculation `val instantBps = bytes * 8 / 2`. Because it only updated every 2.0 seconds with an EMA coefficient of `(prev * 0.7 + instant * 0.3)`, it took 4–6 seconds to react to bandwidth changes, giving the user a "laggy / frozen" impression.

### Proposed Change
1. **Remove Stats for Nerds:** Remove `StatsForNerds`, `showStats`, and the "Stats: on/off" tile from `ActionRow`.
2. **Consolidate Metrics in `VideoInfoOverlay`:** Ensure all metrics from both overlays are displayed:
   - Video: Resolution (raw / class), Frame rate (FPS), Video codec, Video bitrate.
   - Audio: Audio codec, Audio channels, Audio bitrate.
   - Network & Buffer: Dynamic network speed (kbps / Mbps), Buffer ahead (seconds & percentage), Dropped video frames.
   - Hardware: Active video decoder name + HW/SW tag (`decoderLabel`).
   - Provider: Server hostname, Active/Max connections (for Xtream accounts).
3. **Graceful Dismissal:**
   - In `PlayerScreen.peelBack`: Peeling back from `PickerKind.Info` transitions directly to `PlayerOverlay.None` (return to playback).
   - In `PlayerScreen` key event handler: Intercept OK (`Key.DirectionCenter`, `Key.Enter`) when `overlay == PlayerOverlay.Picker(PickerKind.Info)` and set `overlay = PlayerOverlay.None`.
   - Add a focusable dummy surface or `focusable` modifier on the overlay box so spatial search doesn't get lost.
4. **0.5 s Network Speed Refresh:**
   - Change `delay(2_000)` in `startNetworkStatsPoll` to `delay(500)`.
   - Compute instantaneous bps as: `val instantBps = bytes * 8 * 2` (bytes transferred in 500ms × 16 = bits/sec).
   - Adjust EMA: `val smoothed = (prev * 0.6 + instantBps * 0.4).toLong()`.
   - Threshold for clearing sustained silence: 6 consecutive empty windows (`6 × 500ms = 3.0s`).

### Risks & Mitigations
- **Recomposition Rate:** Updating `_networkStats` every 500ms will recompose subscribers.
  - *Mitigation:* Only `VideoInfoOverlay` collects `networkStats` when open; `PlayerScreen` root must not recompose entirely. Isolate `networkStats` collection inside `VideoInfoOverlay`.

### Complexity & Size: **M** (Medium, ~60–80 lines across 2 files)

---

## 3. Long-Press OK Menu in the Player (Stay Open & Room for Rows)

### User Quote
> "BUG - long-press OK menu (fullscreen) opens but doesn't stay up. Keep the feature; future rows: Add to favourites, start a Multi-View group, Video Info."  
> "Decisions, same day (user answers): Long-press OK = TiviMate's OK+DOWN tiles, stays open until a pick or Back, room for more rows."  
> *(Section O lines 54–55, 69–70)*

### Code Location & Audit
- **Files & Functions:**
  - `tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt`
    - Fullscreen root key dispatcher ([lines 567–621](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt#L567-L621))
    - `ContextMenuPanel` ([lines 919–998](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt#L919-L998))
    - Context menu display host ([lines 868–877](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt#L868-L877))

### Root Cause Analysis
- `PlayerScreen.kt` uses `Modifier.onKeyEvent { ... }` on the outer Box ([line 530](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt#L530)). In Compose, `onKeyEvent` runs during the **bubbling** phase (children process the event first).
- When long-press OK triggers:
  1. `isDown && event.nativeKeyEvent.isLongPress` fires, setting `okWasLongPress = true` and `overlay = PlayerOverlay.ContextMenu`.
  2. `ContextMenuPanel` renders and its `firstFocus.requestFocus()` claims focus immediately (50ms delay).
  3. While the user is physically releasing the remote button, Android delivers repeat `ACTION_DOWN` or `ACTION_UP` events directly to the newly-focused child `TvSurface` in `ContextMenuPanel`.
  4. The child's `onClick` handler executes immediately, toggling favorite and calling `onDismiss()` ([line 963](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt#L963)).
  5. Result: The menu opens and instantly self-destructs during the same physical gesture.

### Proposed Change
1. **Latching Opening Gesture:** Add an explicit gesture latch (`isHandlingLongPressLaunch`) or use `onPreviewKeyEvent` (tunneling phase) on `PlayerScreen` root to consume all OK events until the button is fully released (`KeyEventType.KeyUp`), preventing the opening press from bleeding into `ContextMenuPanel`.
2. **Prevent Auto-Dismiss:** Remove premature dismissal. Ensure `ContextMenuPanel` only closes on:
   - An explicit subsequent selection (`onClick`), or
   - Pressing `Back`.
3. **Redesign Layout to TiviMate OK+DOWN Tiles:**
   - Transform `ContextMenuPanel` from a narrow 2-item vertical popup into a modular Action Tile Strip / Floor (similar to TiviMate's bottom quick-action panel).
   - Rows / Tiles to include:
     - "Add/Remove favourites"
     - "Video info" (opens the unified `VideoInfoOverlay`)
     - "Hide channel"
     - "Multi-View" (placeholder / future hook per Section O)
   - Layout: horizontal or vertical card list styled with `RedSurfFocus` tokens and `CrestButton` / `TvSurface`.

### Risks & Mitigations
- **Focus Trap:** When the menu opens, focus must land reliably on the first tile without dropping input.
  - *Mitigation:* Follow `FOCUS_MODEL.md` Rule 3 (bounded retry loop for `firstFocus.requestFocus()`).
- **Peel Back Behavior:** Back must close the context menu and return to clean fullscreen playback (`PlayerOverlay.None`).

### Complexity & Size: **M** (Medium, ~50–70 lines)

---

## 4. Categories: Centre Selected Category Only When Off-Screen

### User Quote
> "BUG - category column jumps: Back from the guide focuses the playing channel's category (correct) but scrolls it to the top of the column instead of keeping its on-screen place. Disorienting; scroll must not jump."  
> "Decisions, same day (user answers): category off screen (Teleport/recents/search/resume into another category) -> centre it; on screen -> don't scroll."  
> *(Section O lines 36–38, 68–69)*

### Code Location & Audit
- **Files & Functions:**
  - `tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/GroupsColumn.kt`
    - `focusRequest` handler ([lines 246–284](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/GroupsColumn.kt#L246-L284))
    - `focusedIndex` effect ([lines 298–300](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/GroupsColumn.kt#L298-L300))
    - `LazyListState.holdMidList` ([lines 446–453](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/GroupsColumn.kt#L446-L453))
  - `tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/LiveTvScreen.kt`
    - `goToCategories` ([lines 697–699](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/LiveTvScreen.kt#L697-L699))

### Root Cause Analysis
- When the user presses Back from the guide, `LiveTvScreen.kt` calls `goToCategories()`:
  `selectedGroup?.let { localGroupsFocusRequest = GroupsFocusRequest.Group(it, System.nanoTime()) }`.
- In `GroupsColumn.kt` lines 264–267:
  ```kotlin
  targetRowKey = rowKey(rows[index])
  runCatching { listState.scrollToItem(index) }
  ```
- In Compose, `listState.scrollToItem(index)` immediately positions `index` as `firstVisibleItemIndex` at pixel offset 0 (the very top of the viewport)!
- Even though the category was already comfortably visible on screen, this unconditionally threw it to the top row of the screen.

### Proposed Change
1. Introduce a helper pure function:
   ```kotlin
   fun isIndexVisible(layoutInfo: LazyListLayoutInfo, index: Int): Boolean =
       layoutInfo.visibleItemsInfo.any { it.index == index }
   ```
2. In `GroupsColumn.kt`'s `LaunchedEffect(focusRequest)`:
   - Check if `isIndexVisible(listState.layoutInfo, index)`:
     - **If visible (on-screen):** **DO NOT SCROLL AT ALL**. Simply assign `targetRowKey = rowKey(rows[index])` and call `targetFocus.requestFocus()`. Its on-screen place is 100% preserved.
     - **If not visible (off-screen):** Call `listState.holdMidList(index)` to centre it on screen, then claim focus.
3. In `LaunchedEffect(focusedIndex)`:
   - Ensure `holdMidList` only glides when moving focus via manual D-pad navigation, not when landing from an on-screen focus restore.

### Risks & Mitigations
- **Composition Timing:** If `visibleItemsInfo` is checked before the first layout pass completes, it might appear empty.
  - *Mitigation:* If `visibleItemsInfo.isEmpty()`, check if `firstVisibleItemIndex <= index && index <= firstVisibleItemIndex + estimatedVisibleCount` or fallback to `holdMidList(index)`.

### Complexity & Size: **S** (Small, ~20–30 lines)

---

## 5. Playlist Import: "N of Total" Progress Bar

### User Quote
> "Progress: import progress liked; wants a bar with "N of total" (channels now, VOD later). EPG update indicator at the top is hard to see and shows no amount done."  
> *(Section O lines 61–62)*

### Code Location & Audit
- **Files & Functions:**
  - `tv-native/app/src/main/java/com/redsurf/tv/MainViewModel.kt`
    - `AppState.Loading` definition ([lines 64](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/MainViewModel.kt#L64))
    - `loadXtreamCodes` ([lines 582–631](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/MainViewModel.kt#L582-L631))
    - `loadPlaylist` / M3U import ([lines 710–750](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/MainViewModel.kt#L710-L750))
    - `reimportPlaylistInPlace` ([lines 424–470](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/MainViewModel.kt#L424-L470))
  - `tv-native/app/src/main/java/com/redsurf/tv/MainActivity.kt`
    - `AppState.Loading` UI ([lines 133–146](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/MainActivity.kt#L133-L146))
  - `tv-native/app/src/main/java/com/redsurf/tv/vod/XtreamApi.kt`
    - `getLiveStreams` ([lines 215–250](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/vod/XtreamApi.kt#L215-L250))

### Root Cause Analysis
- `AppState.Loading` only has a string: `data class Loading(val message: String = "Loading...") : AppState()`.
- `MainActivity.kt` renders only: `WaveSpinner(size = 96.dp)` + `Image(R.drawable.ic_mark)` + `Text(s.message)`.
- No progress bar or structured `(current, total)` counts exist in `AppState`.
- For Xtream imports: `getLiveStreams` streams entities via `JsonReader` without counting the total entries up-front.
- For M3U imports: `CountingInputStream` computes `counting.count * 100 / total` (byte percentage) but formats it as a trailing string: `"Importing $imported channels... · 45% read"`.

### Proposed Change
1. **Model Enhancement:**
   ```kotlin
   data class ImportProgress(
       val current: Int,
       val total: Int? = null,
       val percent: Int? = null,
       val stage: String = "Importing channels",
   )
   
   sealed class AppState {
       data class Loading(
           val message: String = "Loading...",
           val progress: ImportProgress? = null,
       ) : AppState()
       // ...
   }
   ```
2. **UI Enhancement (`MainActivity.kt`):**
   - Beneath the `WaveSpinner` and `Text(s.message)`, when `s.progress != null`:
     - Render a sleek horizontal progress bar:
       - Width: `320.dp`, Height: `6.dp`, Shape: `RoundedCornerShape(3.dp)`.
       - Track color: `SurfaceRaised`, Fill color: `Accent` (RedSurf red).
       - Fraction: `progress.percent?.let { it / 100f } ?: (progress.current.toFloat() / progress.total!!)`.
     - Render a subtitle: `"1,420 of 8,500 channels"` (or `"1,420 channels · 45% read"`).
3. **Total Acquisition:**
   - **For Xtream:** Before streaming streams, call `getCategories` or inspect the HTTP response / stream list header. If unavailable, use a determinate byte-stream estimate or indeterminate progress bar with dynamic count `"1,420 channels imported"`.
   - **For M3U:** Use byte ratio from `CountingInputStream` to drive the determinate fraction, displaying `"Importing channels (X% read)"`.

### Risks & Mitigations
- **Memory Pressure:** Do not buffer all JSON items in memory to get the total count. Keep using `JsonReader` streaming. If total count is unknown, use an indeterminate animated pulse on the progress bar while showing the live `current` count.

### Complexity & Size: **M** (Medium, ~60–80 lines across 3 files)

---

## 6. Preview ↔ Fullscreen Animation: ~30% Slower

### User Quote
> "Preview <-> fullscreen animation works but is too abrupt - slow it "by a hair"."  
> "Decisions, same day (user answers): Preview <-> fullscreen ~30% slower, user judges."  
> *(Section O lines 52, 73–74)*

### Code Location & Audit
- **Files & Functions:**
  - `tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/LiveTvScreen.kt`
    - `expand` animatable ([lines 956–962](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/LiveTvScreen.kt#L956-L962))
    - Duration constant: `tween(220, easing = FastOutSlowInEasing)` ([line 960](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/LiveTvScreen.kt#L960))

### Proposed Change
1. Calculate 30% slower duration:
   - Current: `220 ms`.
   - Increase by ~30%: `220 × 1.30 = 286 ms`, rounded to **`290 ms`** (or `300 ms`).
2. Extract constant at top of `LiveTvScreen.kt`:
   ```kotlin
   private const val PREVIEW_EXPAND_DURATION_MS = 290
   ```
3. Update `expand.animateTo`:
   ```kotlin
   expand.animateTo(
       if (isFullscreen) 1f else 0f,
       androidx.compose.animation.core.tween(
           PREVIEW_EXPAND_DURATION_MS,
           easing = androidx.compose.animation.core.FastOutSlowInEasing,
       ),
   )
   ```

### Risks & Mitigations
- **Frame Pacing:** Lengthening the animation increases the number of frames rendered during the transform from ~13 frames to ~17 frames at 60Hz.
  - *Mitigation:* Perf Max step 5 already runs this purely in the `graphicsLayer` lambda without recomposition or layout changes; RenderThread handles scale and translation smoothly.

### Complexity & Size: **S** (Small, ~5 lines)

---

## 7. EPG "Public Guide" Indicator & Sync Indicator Visibility

### User Quote
> "EPG update indicator at the top is hard to see and shows no amount done."  
> *(Section O line 62)*  
> "Public-guide programmes - a stronger cool tint and a small dot in the corner. Enough, or do you still want a legend?"  
> *(Section M line 128)*

### Code Location & Audit
- **Files & Functions:**
  - `tv-native/app/src/main/java/com/redsurf/tv/ui/shell/NavStrip.kt`
    - `NavStrip` sync indicator ([lines 98–108](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/shell/NavStrip.kt#L98-L108))
  - `tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/EpgGridColumn.kt`
    - `GridTimeHeader` public guide legend ([lines 483–500](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/EpgGridColumn.kt#L483-L500))
    - `SupplementKeyWidth = 84.dp`, `SupplementTick = Color(0xFF7DD3FC).copy(alpha = 0.8f)` ([lines 1161–1162](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/EpgGridColumn.kt#L1161-L1162))
    - Cell drawing in `drawSlot` ([lines 1007, 1014](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/EpgGridColumn.kt#L1007-L1014))

### Root Cause Analysis
1. **NavStrip Sync Indicator:**
   - In `NavStrip.kt:98-108`: When `guideSyncing` is true, it displays a tiny `6.dp` red square + `"Updating guide"`.
   - **Flaw 1:** When the nav strip auto-hides after 3 seconds, the sync indicator slides off-screen and becomes completely invisible to the user!
   - **Flaw 2:** It provides zero indication of progress or completion percentage.
2. **Public Guide Grid Legend:**
   - In `EpgGridColumn.kt:483-500`: The "Public guide" key is cramped into an 84.dp width with `TextOverflow.Clip`. On standard TV viewing distances, the 6.dp dot and faint `TextSecondary` text are barely noticeable.

### Proposed Change
1. **EPG Sync Progress Indicator:**
   - Expose sync stage or progress through `EpgSyncState` (e.g. `Syncing(channelCount, totalChannels)`).
   - Position the sync indicator in the **persistent guide header** (`EpgHeaderBand` or adjacent to the time ruler / hero band) so it remains visible even when `NavStrip` auto-hides.
   - Include a small animated wave spinner or progress percentage.
2. **Public Guide Legend & Cell Cue Visibility:**
   - Increase `SupplementKeyWidth` from `84.dp` to `100.dp`.
   - Increase the legend dot size from `6.dp` to `8.dp` with an explicit border or badge.
   - Slightly increase `SupplementTick` opacity from `0.8f` to `1.0f` (`Color(0xFF38BDF8)`) for better contrast on dark programme tiles.

### Risks & Mitigations
- **Header Real Estate:** Expanding the legend width must not encroach on the timeline ruler. The header layout already offsets the time ruler dynamically by `SupplementKeyWidth`.

### Complexity & Size: **M** (Medium, ~40–50 lines)

---

## 8. Playlist Page & Manage Groups Redesign (TiviMate Architecture Pass)

### User Quote
> "Playlist page: "horrible" - text boxes inline, the on-screen keyboard pops for every field passed while scrolling to Remove. Wants TiviMate's pattern (row -> own screen with the field). Missing: Expiration date, Enable/Disable, EPG sources screen, Logos priority, Catch-up settings, User agent, "Xtream parameters" group (server, username, password, output format, Live TV toggle, VOD toggle, expiry, max connections), Manage Groups (reorder/hide Live TV categories per playlist - wanted in Cloud too). "Update channels" vs "Update EPG" unclear. Study TiviMate's playlist management code/UX first."  
> *(Section O lines 44–50)*

### Code Location & Audit
- **Files & Functions:**
  - `tv-native/app/src/main/java/com/redsurf/tv/ui/shell/SettingsScreen.kt`
    - `PlaylistDetail` ([lines 1099–1213](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/shell/SettingsScreen.kt#L1099-L1213))
    - `ConnectionEditor` ([lines 999–1039](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/shell/SettingsScreen.kt#L999-L1039))
    - `RemovePlaylistRow` ([lines 1224–1290](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/shell/SettingsScreen.kt#L1224-L1290))
  - TiviMate Strings Resource:
    - `/Users/Shared/tivimate-res/strings.xml`

### TiviMate Structural Analysis
From reading TiviMate's strings and resource architecture:
1. **Zero Inline Text Boxes:** TiviMate **never** embeds editable text boxes inside a scrollable settings list. Every field is a clean row displaying `Title` and current `Value` (e.g. `Playlist name: "Family IPTV"`).
2. **Drill-Down / Dialog Pattern:** Clicking a row opens a dedicated full-screen input step or popup dialog with the text field and OK/Cancel buttons. Scrolling down to "Delete playlist" NEVER opens the on-screen keyboard.
3. **Structured Grouping:**
   - **TiviMate Playlist Settings Screen:**
     - `Enable playlist` (Toggle switch)
     - `Playlist name` (Row -> text input dialog)
     - `Playlist type` (M3U / Xtream Codes / Stalker)
     - `Xtream Codes parameters` (Row -> dedicated sub-screen)
     - `Manage groups` (Row -> dedicated sub-screen)
     - `EPG sources` (Row -> sub-screen: source URL, assignment, priority, time shift)
     - `Catch-up` (Row -> sub-screen: catch-up type, days, rewind behavior)
     - `Logos priority` (Row -> picker: Prefer playlist / Prefer EPG / Prefer folder)
     - `User-Agent` (Row -> text input dialog)
     - `Update playlist` ("Update channels") & `Update on playlist change`
     - `Delete playlist` (Row with confirmation dialog)
   - **Xtream Parameters Sub-Screen (`Xtream Codes parameters`):**
     - `Server address`
     - `Username`
     - `Password`
     - `Output format` (ts / m3u8 picker)
     - `Max connections` (read-only info)
     - `Expiration date` (read-only date)
     - Content inclusion toggles: `Live streams`, `VOD movies`, `TV series`
   - **Manage Groups Sub-Screen (`Manage groups`):**
     - List of all playlist categories/groups.
     - Each group row has a visibility toggle (Visible / Hidden).
     - Group sorting / Reorder groups (Move Up / Move Down / Drag).
     - Action buttons: "Hide all groups", "Show all groups", "Restore order".

### Proposed Redesign Plan for RedSurf
1. **Replace Inline Fields with Drill-Down Rows:**
   - Convert `ConnectionEditor` and `PlaylistDetail`'s `TvTextField`s into standard clickable `LiveRow(label = "Server", value = server, onClick = { openServerDialog() })`.
   - Provide a shared `SettingsInputDialog(title, initialValue, onSave, onDismiss)` composable that handles virtual keyboard focus cleanly.
2. **Sub-Screen Navigation within Settings:**
   - Extend `SettingsPane` state to handle nested sub-screens:
     - `PlaylistDetail(playlistId)`
     - `XtreamParameters(playlistId)`
     - `ManageGroups(playlistId)`
     - `EpgSources(playlistId)`
3. **Clarify Channel vs EPG Updates:**
   - Label: `"Update channel list"` (downloads M3U / `get_live_streams` to refresh streams).
   - Label: `"Update EPG guide"` (downloads XMLTV / `get_simple_data_table` to refresh schedule).

### Risks & Mitigations
- **State & Focus Discipline (`FOCUS_MODEL.md` Rule 2 & 9):** Returning from a sub-screen or input dialog must restore focus to the exact originating row in the parent list.
  - *Mitigation:* Pass originating `FocusRequester` or row index to restore focus on dismiss.

### Complexity & Size: **L** (Large, full UI screen restructure, ~250–350 lines across 3 files)

---

## 9. Additional Section O Items

### 9.1 Settings Red Gradient Backdrop
- **User Quote:** `"Settings' red gradient backdrop looks grainy/unprofessional... try a smooth one or leave for the UI pass."`
- **Location:** `tv-native/app/src/main/java/com/redsurf/tv/ui/theme/SurfBackdrop.kt` ([lines 56–80](file:///Users/agy/RedSurf-agy2/tv-native/app/src/main/java/com/redsurf/tv/ui/theme/SurfBackdrop.kt#L56-L80)).
- **Audit:** `SurfBitmap.bake` explicitly adds random noise: `val noise = Random.nextInt(-grain, grain)`. On a large 4K/1080p display, the scaled grain appears as dirty artifacts.
- **Proposed Fix:** Remove the manual noise loop in `SurfBitmap.bake` and rely on a smooth radial gradient shader with hardware dithering, or bypass backdrop rendering until the full branding pass.
- **Size:** **S** (~10 lines).

### 9.2 Buffering Spinner Over Channel List
- **User Quote:** `"Buffering spinner over the channel list: user hasn't noticed it either way."`
- **Location:** `ChannelListOverlay.kt`.
- **Verdict:** Functioning unobtrusively; no immediate changes required.

### 9.3 Search Presentation & Branded Mic Icon
- **User Quote:** `"Search: keyword matching fine; results presentation unpolished and inconsistent. Mic button should be a branded mic icon, not a text label. Replicate TiviMate's search presentation, including one-OK preview in a translucent panel while staying on results (may need a Settings row)."`
- **Location:** `tv-native/app/src/main/java/com/redsurf/tv/ui/search/SearchScreen.kt`.
- **Verdict:** Fully detailed in `docs/plans/TIVIMATE_SEARCH_STUDY.md` and deferred to the Search Polish Sprint.

---

## 10. Proposed Unit Tests (Pure Logic)

Below are production-ready JUnit test cases covering the pure mathematical and algorithmic logic identified in the audit.

```kotlin
package com.redsurf.tv.polish

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolishLogicTest {

    // =========================================================================
    // Item 1: Recents Strip Layout & Geometry Calculations
    // =========================================================================

    /**
     * Calculates whether all tiles in the recents row fit within the visible viewport width.
     * @param totalTiles Total tiles (Guide + History + Recents + Clear)
     * @param tileWidthDp Width of each tile (100.dp)
     * @param spacingDp Gap between tiles (8.dp)
     * @param paddingDp Total horizontal padding (48.dp)
     * @param viewportWidthDp TV viewport width (960.dp on 1080p density 2.0)
     */
    private fun recentsRowExceedsViewport(
        totalTiles: Int,
        tileWidthDp: Int = 100,
        spacingDp: Int = 8,
        paddingDp: Int = 48,
        viewportWidthDp: Int = 960,
    ): Boolean {
        if (totalTiles <= 0) return false
        val totalWidth = (totalTiles * tileWidthDp) + ((totalTiles - 1) * spacingDp) + paddingDp
        return totalWidth > viewportWidthDp
    }

    @Test
    fun recentsRowOverlfowOccursWithEightRecents() {
        // 1 Guide + 1 History + 8 Channels + 1 Clear = 11 tiles
        assertTrue("11 tiles must overflow 960dp viewport", recentsRowExceedsViewport(totalTiles = 11))
        // Total width = 11*100 + 10*8 + 48 = 1228dp > 960dp
    }

    @Test
    fun recentsRowFitsWhenFewRecentsPresent() {
        // 1 Guide + 1 History + 2 Channels + 1 Clear = 5 tiles (5*100 + 4*8 + 48 = 580dp <= 960dp)
        assertFalse("5 tiles should fit within 960dp viewport", recentsRowExceedsViewport(totalTiles = 5))
    }

    // =========================================================================
    // Item 2: 0.5s Network Speed Poll & Exponential Moving Average (EMA)
    // =========================================================================

    private class NetworkSpeedFilter(
        private val sampleIntervalMs: Long = 500L,
        private val alpha: Float = 0.4f,
    ) {
        var smoothedBps: Long? = null
            private set
        var consecutiveEmptyWindows: Int = 0
            private set

        fun onSample(bytesTransferred: Long): Long? {
            // instantBps = bytes * 8 * (1000 / sampleIntervalMs)
            val multiplier = 1000L / sampleIntervalMs
            val instantBps = bytesTransferred * 8L * multiplier

            if (instantBps > 0) {
                consecutiveEmptyWindows = 0
                smoothedBps = smoothedBps?.let { (it * (1f - alpha) + instantBps * alpha).toLong() } ?: instantBps
            } else {
                consecutiveEmptyWindows++
                // After 3.0 seconds (6 consecutive 500ms windows), clear the estimate
                if (consecutiveEmptyWindows >= 6) {
                    smoothedBps = null
                }
            }
            return smoothedBps
        }
    }

    @Test
    fun networkSpeedFilterCalculatesAndSmoothsCorrectlyAtHalfSecond() {
        val filter = NetworkSpeedFilter(sampleIntervalMs = 500L, alpha = 0.4f)

        // 125,000 bytes in 500ms = 250,000 bytes/sec = 2,000,000 bps (2.0 Mbps)
        val initial = filter.onSample(125_000L)
        assertEquals(2_000_000L, initial)

        // Next sample: 250,000 bytes in 500ms = 4,000,000 bps
        // EMA: 2,000,000 * 0.6 + 4,000,000 * 0.4 = 1,200,000 + 1,600,000 = 2,800,000 bps
        val smoothed = filter.onSample(250_000L)
        assertEquals(2_800_000L, smoothed)
    }

    @Test
    fun networkSpeedFilterAbsorbsBurstinessAndClearsAfterSixEmptyWindows() {
        val filter = NetworkSpeedFilter(sampleIntervalMs = 500L)
        filter.onSample(125_000L)

        // 5 consecutive empty windows (2.5s) - must retain smoothed value
        repeat(5) {
            filter.onSample(0L)
            assertTrue("Should retain smoothed value during short idle", filter.smoothedBps != null)
        }
        assertEquals(5, filter.consecutiveEmptyWindows)

        // 6th empty window (3.0s total) - must reset to null
        filter.onSample(0L)
        assertEquals(6, filter.consecutiveEmptyWindows)
        assertEquals(null, filter.smoothedBps)
    }

    // =========================================================================
    // Item 4: Category Scroll Behavior (On-Screen vs Off-Screen)
    // =========================================================================

    enum class CategoryScrollAction { KEEP_POSITION, CENTER_ON_SCREEN }

    private fun determineCategoryScrollAction(
        targetIndex: Int,
        visibleItemIndices: List<Int>,
    ): CategoryScrollAction {
        return if (targetIndex in visibleItemIndices) {
            CategoryScrollAction.KEEP_POSITION
        } else {
            CategoryScrollAction.CENTER_ON_SCREEN
        }
    }

    @Test
    fun categoryAlreadyVisibleRetainsOnScreenPositionWithoutScrolling() {
        val visibleRows = listOf(2, 3, 4, 5, 6, 7)
        val action = determineCategoryScrollAction(targetIndex = 4, visibleItemIndices = visibleRows)
        assertEquals(CategoryScrollAction.KEEP_POSITION, action)
    }

    @Test
    fun categoryOffScreenTriggersCentering() {
        val visibleRows = listOf(0, 1, 2, 3, 4, 5)
        val action = determineCategoryScrollAction(targetIndex = 14, visibleItemIndices = visibleRows)
        assertEquals(CategoryScrollAction.CENTER_ON_SCREEN, action)
    }

    // =========================================================================
    // Item 5: Import Progress Calculation & Formatting
    // =========================================================================

    private fun formatImportProgress(imported: Int, total: Int?): String {
        return if (total != null && total > 0) {
            val percent = (imported * 100 / total).coerceIn(0, 100)
            "Importing $imported of $total channels ($percent%)"
        } else {
            "Importing $imported channels..."
        }
    }

    @Test
    fun importProgressFormattingOutputsAccurateStrings() {
        assertEquals(
            "Importing 1500 of 3000 channels (50%)",
            formatImportProgress(imported = 1500, total = 3000),
        )
        assertEquals(
            "Importing 500 channels...",
            formatImportProgress(imported = 500, total = null),
        )
    }

    // =========================================================================
    // Item 6: Expand/Shrink Duration Scaling
    // =========================================================================

    private fun calculateAdjustedDuration(baseDurationMs: Int, percentIncrease: Int): Int {
        return kotlin.math.round(baseDurationMs * (1f + percentIncrease / 100f)).toInt()
    }

    @Test
    fun expandDurationScalesByThirtyPercent() {
        val scaled = calculateAdjustedDuration(baseDurationMs = 220, percentIncrease = 30)
        assertEquals(286, scaled)
    }
}
```

---

## 11. Numbered Open Questions for the User

1. **Video Info vs Context Menu:** When consolidating Video Info into a single overlay, should "Video Info" also appear as a quick-action tile in the long-press OK menu, in the Actions floor, or both?
2. **Clear Recents Focus Target:** When the trailing "Clear" tile is pressed in the recents row and all recents are wiped, should focus snap to the first tile ("TV guide") or dismiss the controls floor back to clean fullscreen video?
3. **Long-Press OK Visual Style:** For the redesigned long-press OK menu (TiviMate OK+DOWN tiles), do you prefer a bottom tile strip (matching the Controls floor styling) or a centered semi-transparent dialog overlay?
4. **Off-Screen Category Navigation:** When a category is off-screen (e.g. returning via Teleport or search) and must be centred, should it animate smoothly or snap immediately with `scrollToItem`?
5. **Settings Input Dialog UX:** In the redesigned per-playlist settings page, when editing strings like Server URL or Playlist Name, do you prefer a modal TV dialog overlay or a dedicated full-screen editing step?
6. **EPG Sync Status Location:** When the NavStrip auto-hides after 3 seconds, should the "Updating guide..." progress indicator remain persistently visible as a subtle pill in the grid header until sync completes?
