# Task 030 - Polish: Consolidate Video Info overlay and remove Stats for Nerds

**Builder:** Gemini, level 2   **Branch:** `agy/030-video-info-overlay`   **Written by:** lead, 2026-10-31

**Goal:** The user found "Stats for nerds" redundant with the "Video info" overlay, and noted that pressing Back dropped out of fullscreen into Live TV. Remove the independent StatsForNerds overlay and its tile in ActionRow. Make "Video info" the single consolidated overlay containing all technical metrics, and ensure pressing OK or Back cleanly dismisses it back to playback (`PlayerOverlay.None`).

**Read first (only these):** `GEMINI.md`; `docs/vision/FOCUS_MODEL.md` (Rules 1, 8); `docs/plans/TESTING_OUTSTANDING.md` §O (lines 41–43, 70–72); `tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt` (lines 425–450, 784–800, 1073–1105, 1452–1572).

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. In `tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt`:
   - Remove `StatsForNerds` composable (lines 1538–1572) and `var showStats` boolean state.
   - In `ActionRow` (lines 1073–1105):
     - Remove `statsOn: Boolean` and `onToggleStats: () -> Unit` parameters.
     - Remove the `Tile(..., label = if (statsOn) "Stats: on" else "Stats: off", ...)` tile.
     - Update call-site in `Controls(Floor.Actions)` (lines 784–800) accordingly.
   - In `VideoInfoOverlay` (lines 1452–1524):
     - Ensure metrics from StatsForNerds are presented:
       - Video: Codec, Resolution (raw/class), Frame rate (FPS), Bitrate.
       - Audio: Codec, Channels, Bitrate.
       - Network & Decoder: Live network speed, Buffer ahead (seconds + percentage), Decoder name + HW/SW status (`decoderLabel`), Dropped video frames.
     - Add `modifier.focusable()` to the outer container and handle click to dismiss:
       ```kotlin
       Box(
           modifier = modifier
               .background(Color.Black.copy(alpha = 0.72f))
               .padding(48.dp)
               .focusable()
               .clickable { onDismiss() },
           // ...
       )
       ```
   - In key routing and `peelBack`:
     - When `overlay == PlayerOverlay.Picker(PickerKind.Info)`:
       - Pressing `Key.DirectionCenter`, `Key.Enter`, or `Key.Back` transitions directly to `PlayerOverlay.None` (return cleanly to video playback).
       - Never exit fullscreen or drop to Live TV.
3. Run compile check and unit tests (`GEMINI.md` section 3).
4. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt` and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted.
- [ ] `git diff origin/main --stat` shows only `PlayerScreen.kt` and this file.
- [ ] `StatsForNerds` is removed; "Stats: on/off" tile is gone from `ActionRow`.

**Device Acceptance (Lead check via `redsurf-tv`):**
1. `redsurf-tv begin`
2. Fullscreen playback active.
3. `redsurf-tv key DPAD_DOWN` -> Floor.Tiles -> `redsurf-tv key DPAD_DOWN` -> Floor.Actions.
4. Verify "Stats: on/off" tile is gone.
5. Focus "Video info", press `DPAD_CENTER` -> Video Info overlay opens over the video.
6. Verify live network speed, buffer, decoder (HW/SW), dropped frames are visible.
7. Press `DPAD_CENTER` -> overlay closes immediately back to fullscreen video.
8. Reopen Video Info, press `BACK` -> overlay closes immediately back to fullscreen video (does NOT drop to Live TV browse view).
9. `redsurf-tv end`

**Stop and report if:** compile fails or key routing creates a focus trap.

## Builder report

- **Status:** (done / stopped - why)
- **Branch:** agy/030-video-info-overlay
- **Files changed:**
  - tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt
  - docs/agy/tasks/030-video-info-overlay.md
- **Build output tail:**
- **Test output tail (real counts):**
- **Not verified:**
- **Notes:**
