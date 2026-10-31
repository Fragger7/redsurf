# Task 031 - Polish: Long-press OK menu gesture latch and TiviMate-style action tiles

**Builder:** Gemini, level 2   **Branch:** `agy/031-player-context-menu`   **Written by:** lead, 2026-10-31

**Goal:** The fullscreen long-press OK menu opens but immediately self-destructs because the releasing `KeyUp` / repeat `KeyDown` events bleed into the newly focused child surface and trigger its click handler. Latch the long-press gesture so the menu stays open until the user makes a subsequent choice or presses Back. Expand the menu layout to match TiviMate's OK+DOWN action tiles ("Add/Remove favourites", "Video info", "Hide channel") with room for future rows.

**Read first (only these):** `GEMINI.md`; `docs/vision/FOCUS_MODEL.md` (Rules 1, 3, 8); `docs/plans/TESTING_OUTSTANDING.md` §O (lines 54–55, 69–70: "BUG - long-press OK menu (fullscreen) opens but doesn't stay up... Long-press OK = TiviMate's OK+DOWN tiles, stays open until a pick or Back, room for more rows"); `tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt` (lines 567–621, 919–998).

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. In `tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt`:
   - In root key handler (lines 567–621):
     - When `overlay == PlayerOverlay.ContextMenu`:
       - Consume all `DirectionCenter` and `Enter` events while `okWasLongPress` is active:
         ```kotlin
         if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
             if (okWasLongPress) {
                 if (event.type == KeyEventType.KeyUp) {
                     okWasLongPress = false
                 }
                 return@onKeyEvent true // Consume both KeyDown repeats and the releasing KeyUp
             }
             return@onKeyEvent false // Allow subsequent presses through to the focused child
         }
         ```
   - In `ContextMenuPanel` (lines 919–998):
     - Redesign as a modular action tile strip styled with `RedSurfFocus` tokens:
       - Tile 1: "Add to favourites" / "Remove from favourites"
       - Tile 2: "Video info" (calls `onOpenVideoInfo()`, setting `overlay = PlayerOverlay.Picker(PickerKind.Info)`)
       - Tile 3: "Hide channel" (calls `onHidden()`)
       - Tile 4 (Future hook / disabled): "Multi-View"
     - Use a bounded retry loop for initial focus (`firstFocus.requestFocus()`, `FOCUS_MODEL.md` Rule 3).
     - When an action is chosen, or when Back is pressed, dismiss cleanly back to playback (`PlayerOverlay.None`).
3. Run compile check and unit tests (`GEMINI.md` section 3).
4. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt` and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted.
- [ ] `git diff origin/main --stat` shows only `PlayerScreen.kt` and this file.
- [ ] Releasing long-press OK does not trigger a selection or dismiss the menu.

**Device Acceptance (Lead check via `redsurf-tv`):**
1. `redsurf-tv begin`
2. Fullscreen playback active.
3. `redsurf-tv longpress DPAD_CENTER`
4. Confirm menu opens AND STAYS OPEN on the TV screen.
5. `redsurf-tv focused` shows first action tile focused.
6. `redsurf-tv key DPAD_DOWN` / `DPAD_RIGHT` navigates between "Add/Remove favourites", "Video info", and "Hide channel".
7. Focus "Video info", press `DPAD_CENTER` -> Video Info overlay opens.
8. Reopen menu, press `BACK` -> menu closes immediately to fullscreen playback.
9. `redsurf-tv end`

**Stop and report if:** compile fails or menu cannot be focused.

## Builder report

- **Status:** (done / stopped - why)
- **Branch:** agy/031-player-context-menu
- **Files changed:**
  - tv-native/app/src/main/java/com/redsurf/tv/ui/player/PlayerScreen.kt
  - docs/agy/tasks/031-player-context-menu.md
- **Build output tail:**
- **Test output tail (real counts):**
- **Not verified:**
- **Notes:**
