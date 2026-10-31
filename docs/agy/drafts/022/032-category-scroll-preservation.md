# Task 032 - Polish: Preserve category on-screen position on Back from guide

**Builder:** Gemini, level 2   **Branch:** `agy/032-category-scroll-preservation`   **Written by:** lead, 2026-10-31

**Goal:** Pressing Back from the guide currently focuses the playing channel's category but calls `listState.scrollToItem(index)`, throwing it to the top row of the viewport even if it was comfortably visible mid-screen. The user noted this is disorienting and asked: "category off screen (Teleport/recents/search/resume) -> centre it; on screen -> don't scroll". Preserve on-screen place if already visible; centre with `holdMidList` only when off-screen.

**Read first (only these):** `GEMINI.md`; `docs/vision/FOCUS_MODEL.md` (Rules 1, 2); `docs/plans/TESTING_OUTSTANDING.md` §O (lines 36–38, 68–69); `tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/GroupsColumn.kt` (lines 246–284, 446–453); the spec test `docs/agy/drafts/022/spec/CategoryVisibilityTest.kt`.

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. Copy `docs/agy/drafts/022/spec/CategoryVisibilityTest.kt` to `tv-native/app/src/test/java/com/redsurf/tv/ui/livetv/CategoryVisibilityTest.kt` byte for byte (`cp`). Never edit the copy.
3. Create `tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/CategoryVisibility.kt`, package `com.redsurf.tv.ui.livetv`:
   ```kotlin
   package com.redsurf.tv.ui.livetv

   object CategoryVisibility {
       fun shouldScroll(targetIndex: Int, visibleIndices: List<Int>): Boolean {
           if (visibleIndices.isEmpty()) return true
           return targetIndex !in visibleIndices
       }

       fun calculateMidListFirstIndex(targetIndex: Int, visibleItemCount: Int): Int {
           val rowsAbove = (visibleItemCount - 1) / 2
           return (targetIndex - rowsAbove).coerceAtLeast(0)
       }
   }
   ```
4. In `tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/GroupsColumn.kt`:
   - In `LaunchedEffect(focusRequest)` (lines 246–284):
     - Replace unconditional `runCatching { listState.scrollToItem(index) }`:
       ```kotlin
       val visibleIndices = listState.layoutInfo.visibleItemsInfo.map { it.index }
       if (CategoryVisibility.shouldScroll(index, visibleIndices)) {
           runCatching { listState.holdMidList(index) }
       }
       // If already visible on screen, DO NOT scroll! Keep current scroll offset intact.
       targetRowKey = rowKey(rows[index])
       ```
5. Run compile check and unit tests (`GEMINI.md` section 3).
6. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/CategoryVisibility.kt`, `tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/GroupsColumn.kt`, `tv-native/app/src/test/java/com/redsurf/tv/ui/livetv/CategoryVisibilityTest.kt`, and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] `diff docs/agy/drafts/022/spec/CategoryVisibilityTest.kt tv-native/app/src/test/java/com/redsurf/tv/ui/livetv/CategoryVisibilityTest.kt` prints nothing.
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted.
- [ ] `git diff origin/main --stat` shows only the four files.

**Device Acceptance (Lead check via `redsurf-tv`):**
1. `redsurf-tv begin`
2. `redsurf-tv nav "Live TV"`
3. Navigate to a category mid-way down the column (e.g. index 5).
4. `redsurf-tv key DPAD_RIGHT` into the Guide.
5. `redsurf-tv key BACK` to return to Categories column.
6. Observe category column: the focused category stays at its exact vertical screen position and does NOT jump to the top of the column!
7. `redsurf-tv end`

**Stop and report if:** compile fails or tests fail after one honest fix attempt.

## Builder report

- **Status:** (done / stopped - why)
- **Branch:** agy/032-category-scroll-preservation
- **Files changed:**
  - tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/CategoryVisibility.kt
  - tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/GroupsColumn.kt
  - tv-native/app/src/test/java/com/redsurf/tv/ui/livetv/CategoryVisibilityTest.kt
  - docs/agy/tasks/032-category-scroll-preservation.md
- **Build output tail:**
- **Test output tail (real counts):**
- **Not verified:**
- **Notes:**
