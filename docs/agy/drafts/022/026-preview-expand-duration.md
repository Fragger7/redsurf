# Task 026 - Polish: Preview ↔ Fullscreen animation duration (~30% slower)

**Builder:** Gemini, level 1   **Branch:** `agy/026-preview-expand-duration`   **Written by:** lead, 2026-10-31

**Goal:** The preview ↔ fullscreen layer animation works smoothly but was judged "too abrupt" by the user on the device. Slow it down by ~30% (from 220 ms to 290 ms) to give a calmer transition while remaining responsive. Pure parameter adjustment, no layout or recomposition changes.

**Read first (only these):** `GEMINI.md`; `docs/plans/TESTING_OUTSTANDING.md` §O (line 52 & line 73: "Preview <-> fullscreen animation works but is too abrupt - slow it by a hair... Preview <-> fullscreen ~30% slower, user judges"); `tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/LiveTvScreen.kt` (lines 950–965).

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. In `tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/LiveTvScreen.kt`:
   - Declare a private constant near the animation setup:
     ```kotlin
     private const val PREVIEW_EXPAND_DURATION_MS = 290
     ```
   - Update `LaunchedEffect(isFullscreen)` around line 960 to use `PREVIEW_EXPAND_DURATION_MS` instead of literal `220`:
     ```kotlin
     expand.animateTo(
         if (isFullscreen) 1f else 0f,
         androidx.compose.animation.core.tween(
             PREVIEW_EXPAND_DURATION_MS,
             easing = androidx.compose.animation.core.FastOutSlowInEasing,
         ),
     )
     ```
3. Run the compile check and unit tests (`GEMINI.md` section 3).
4. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/LiveTvScreen.kt` and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted.
- [ ] `git diff origin/main --stat` shows only `LiveTvScreen.kt` and this file.
- [ ] Constant `PREVIEW_EXPAND_DURATION_MS = 290` is used in `expand.animateTo`.

**Device Acceptance (Lead check via `redsurf-tv`):**
1. `redsurf-tv begin`
2. `redsurf-tv nav "Live TV"`
3. Confirm preview video plays in hero box.
4. `redsurf-tv key DPAD_CENTER` -> video expands to fullscreen; animation takes ~290ms without stutter or sudden snap.
5. `redsurf-tv key BACK` -> video shrinks back to preview box; animation takes ~290ms.
6. `redsurf-tv end`

**Stop and report if:** compile fails or the animation timing constant cannot be located at `LiveTvScreen.kt:950-965`.

## Builder report

- **Status:** (done / stopped - why)
- **Branch:** agy/026-preview-expand-duration
- **Files changed:**
  - tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/LiveTvScreen.kt
  - docs/agy/tasks/026-preview-expand-duration.md
- **Build output tail:**
- **Test output tail (real counts):**
- **Not verified:**
- **Notes:**
