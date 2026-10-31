# Task 028 - Polish: Make recents strip horizontally scrollable and recover focus on Clear

**Builder:** Gemini, level 2   **Branch:** `agy/028-recents-strip-scroll`   **Written by:** lead, 2026-10-31

**Goal:** On a 1080p display (960.dp viewport), 11 tiles in the player recents row (TV Guide + History + up to 8 channels + Clear) measure 1,228.dp. Because `TileRow` is currently a non-scrollable `Row`, the 9th tile is clipped to half-width and the "Clear" tile is completely invisible off-screen. Make the row horizontally scrollable, retain composed items, and ensure focus snaps to the first tile ("TV guide") when Clear is executed.

**Read first (only these):** `GEMINI.md`; `docs/vision/FOCUS_MODEL.md` (Rules 1, 4, 8); `docs/plans/TESTING_OUTSTANDING.md` §O (line 53: "BUG - recents: no Clear tile visible; the last tile is cut to half width"); `tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt` (lines 753–780 and 1009–1046); the spec test `docs/agy/drafts/022/spec/RecentsLayoutGeometryTest.kt`.

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. Copy `docs/agy/drafts/022/spec/RecentsLayoutGeometryTest.kt` to `tv-native/app/src/test/java/com/redsurf/tv/ui/player/RecentsLayoutGeometryTest.kt` byte for byte (`cp`). Never edit the copy.
3. In `tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt`:
   - Create pure helper `RecentsGeometry` (or place in file) to satisfy `RecentsLayoutGeometryTest`:
     ```kotlin
     object RecentsGeometry {
         fun calculateTotalWidthDp(tileCount: Int, tileWidthDp: Int = 100, spacingDp: Int = 8, horizontalPaddingDp: Int = 48): Int {
             if (tileCount <= 0) return 0
             return (tileCount * tileWidthDp) + ((tileCount - 1) * spacingDp) + horizontalPaddingDp
         }
         fun calculateFullyVisibleTileCount(viewportWidthDp: Int = 960, horizontalPaddingDp: Int = 48, tileWidthDp: Int = 100, spacingDp: Int = 8): Int {
             val availableWidth = viewportWidthDp - horizontalPaddingDp
             val slotWidth = tileWidthDp + spacingDp
             return (availableWidth + spacingDp) / slotWidth
         }
     }
     ```
   - In `TileRow` (lines 1009–1046):
     - Replace the outer static `Row(modifier = modifier, ...)` with a scrollable row:
       ```kotlin
       val scrollState = rememberScrollState()
       Row(
           modifier = modifier.horizontalScroll(scrollState),
           horizontalArrangement = Arrangement.spacedBy(8.dp),
       ) {
           // ... tiles
       }
       ```
     - All 11 items remain composed in the tree (`FOCUS_MODEL.md` Rule 4), avoiding lazy-composition focus loss.
   - In `Controls(Floor.Tiles)` host block (around lines 775–778):
     - When `onClearRecents` is triggered, ensure focus resets gracefully to `tilesFloorFocus` (or closes overlay to `PlayerOverlay.None` if recents are empty):
       ```kotlin
       onClearRecents = {
           scope.launch {
               repository.clearRecentChannels()
               delay(50)
               runCatching { tilesFloorFocus.requestFocus() }
           }
       }
       ```
4. Run the compile check and unit tests (`GEMINI.md` section 3).
5. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt`, `tv-native/app/src/test/java/com/redsurf/tv/ui/player/RecentsLayoutGeometryTest.kt`, and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] `diff docs/agy/drafts/022/spec/RecentsLayoutGeometryTest.kt tv-native/app/src/test/java/com/redsurf/tv/ui/player/RecentsLayoutGeometryTest.kt` prints nothing.
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted.
- [ ] `git diff origin/main --stat` shows only the three files.

**Device Acceptance (Lead check via `redsurf-tv`):**
1. `redsurf-tv begin`
2. Tune a channel with >= 8 recent channels populated.
3. `redsurf-tv key DPAD_DOWN` -> opens Controls (Floor.Tiles).
4. `redsurf-tv key DPAD_RIGHT` 9 times until the end of the row.
5. Confirm the "Clear" tile smoothly scrolls into view with full 100.dp width (not clipped to half width).
6. `redsurf-tv key DPAD_CENTER` on Clear tile -> recents are cleared; focus safely snaps to "TV guide" tile without dropping key handling.
7. `redsurf-tv end`

**Stop and report if:** horizontal scrolling interferes with spatial D-pad navigation or tests fail.

## Builder report

- **Status:** (done / stopped - why)
- **Branch:** agy/028-recents-strip-scroll
- **Files changed:**
  - tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt
  - tv-native/app/src/test/java/com/redsurf/tv/ui/player/RecentsLayoutGeometryTest.kt
  - docs/agy/drafts/022/028-recents-strip-scroll.md
- **Build output tail:**
- **Test output tail (real counts):**
- **Not verified:**
- **Notes:**
