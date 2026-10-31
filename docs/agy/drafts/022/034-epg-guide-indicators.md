# Task 034 - Polish: EPG Public Guide legend visibility and persistent sync indicator

**Builder:** Gemini, level 2   **Branch:** `agy/034-epg-guide-indicators`   **Written by:** lead, 2026-10-31

**Goal:** The user noted: "EPG update indicator at the top is hard to see and shows no amount done... Public-guide programmes: stronger cool tint and small dot in the corner." When `NavStrip` auto-hides after 3 seconds, the "Updating guide" indicator slides off-screen and becomes completely invisible. Make the Public Guide legend more prominent (100.dp width, 8.dp dot, solid tint) and place a persistent sync indicator in the Guide header.

**Read first (only these):** `GEMINI.md`; `docs/plans/TESTING_OUTSTANDING.md` §O (line 62: "EPG update indicator at the top is hard to see and shows no amount done") and §M (line 128: "Public-guide programmes - a stronger cool tint and a small dot in the corner"); `tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/EpgGridColumn.kt` (lines 483–500, 1160–1162); `tv-native/app/src/main/java/com/redsurf/tv/ui/shell/NavStrip.kt` (lines 98–108).

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. In `tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/EpgGridColumn.kt`:
   - Update constants around line 1160:
     ```kotlin
     private val SupplementTick = Color(0xFF38BDF8) // Solid, vivid sky blue
     private val SupplementKeyWidth = 100.dp // Expanded from 84.dp for full text visibility
     ```
   - In `GridTimeHeader` (lines 483–500):
     - Increase the circle canvas size from `6.dp` to `8.dp`:
       ```kotlin
       Canvas(modifier = Modifier.size(8.dp)) { drawCircle(SupplementTick) }
       ```
     - Ensure `Text("Public guide", style = RedSurfType.gridHeaderHalf, color = TextPrimary, ...)` has sufficient contrast and spacing (`padding(start = 6.dp)`).
   - In the persistent guide header band (adjacent to the time ruler / hero area):
     - When `guideSyncing` is true, render a subtle syncing chip:
       ```kotlin
       Row(verticalAlignment = Alignment.CenterVertically) {
           Box(modifier = Modifier.size(6.dp).background(Accent))
           Spacer(modifier = Modifier.width(6.dp))
           Text("Updating guide…", style = RedSurfType.gridMeta, color = TextSecondary)
       }
       ```
       so it remains visible even when `NavStrip` is auto-hidden.
3. Run compile check and unit tests (`GEMINI.md` section 3).
4. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/EpgGridColumn.kt` and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted.
- [ ] `git diff origin/main --stat` shows only `EpgGridColumn.kt` and this file.
- [ ] `SupplementKeyWidth = 100.dp` and `Canvas(modifier = Modifier.size(8.dp))` are used.

**Device Acceptance (Lead check via `redsurf-tv`):**
1. `redsurf-tv begin`
2. `redsurf-tv nav "Live TV"`
3. Wait 4 seconds for `NavStrip` to auto-hide.
4. Verify Guide header remains on screen.
5. Inspect the "● Public guide" legend in the time ruler: 8.dp sky-blue circle is clearly visible with unabbreviated text.
6. When EPG sync runs, confirm the sync indicator remains visible in the header area while browsing channels with `NavStrip` hidden.
7. `redsurf-tv end`

**Stop and report if:** header layout overlaps or time ruler labels clip.

## Builder report

- **Status:** (done / stopped - why)
- **Branch:** agy/034-epg-guide-indicators
- **Files changed:**
  - tv-native/app/src/main/java/com/redsurf/tv/ui/livetv/EpgGridColumn.kt
  - docs/agy/tasks/034-epg-guide-indicators.md
- **Build output tail:**
- **Test output tail (real counts):**
- **Not verified:**
- **Notes:**
