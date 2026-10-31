# Task 033 - Polish: Playlist import progress model and determinate progress bar

**Builder:** Gemini, level 2   **Branch:** `agy/033-playlist-import-progress`   **Written by:** lead, 2026-10-31

**Goal:** The user liked the import progress text but requested a visual progress bar with "N of total" (channels now, VOD later). Add a structured `ImportProgress` model and render a horizontal progress bar beneath the WaveSpinner in `MainActivity.kt` during playlist imports.

**Read first (only these):** `GEMINI.md`; `docs/plans/TESTING_OUTSTANDING.md` §O (lines 61–62: "import progress liked; wants a bar with 'N of total' (channels now, VOD later)"); `tv-native/app/src/main/java/com/redsurf/tv/MainViewModel.kt` (lines 64, 582–631, 710–750); `tv-native/app/src/main/java/com/redsurf/tv/MainActivity.kt` (lines 133–146); the spec test `docs/agy/drafts/022/spec/ImportProgressFormatterTest.kt`.

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. Copy `docs/agy/drafts/022/spec/ImportProgressFormatterTest.kt` to `tv-native/app/src/test/java/com/redsurf/tv/model/ImportProgressFormatterTest.kt` byte for byte (`cp`). Never edit the copy.
3. Create `tv-native/app/src/main/java/com/redsurf/tv/model/ImportProgress.kt`, package `com.redsurf.tv.model`:
   ```kotlin
   package com.redsurf.tv.model

   data class ImportProgress(
       val current: Int,
       val total: Int? = null,
       val percent: Int? = null,
       val stage: String = "Importing channels",
   ) {
       val fraction: Float?
           get() = percent?.let { it / 100f } ?: total?.takeIf { it > 0 }?.let { current.toFloat() / it }?.coerceIn(0f, 1f)

       val formattedStatus: String
           get() = when {
               percent != null -> "Importing %,d channels (%d%% read)".format(current, percent)
               total != null && total > 0 -> "%,d of %,d channels (%d%%)".format(current, total, ((current.toLong() * 100) / total).coerceIn(0, 100))
               else -> "Importing %,d channels...".format(current)
           }
   }
   ```
4. In `tv-native/app/src/main/java/com/redsurf/tv/MainViewModel.kt`:
   - Update `AppState.Loading`:
     ```kotlin
     data class Loading(
         val message: String = "Loading...",
         val progress: com.redsurf.tv.model.ImportProgress? = null,
     ) : AppState()
     ```
   - In `loadXtreamCodes` (lines 615–617) and M3U parser (lines 745–748):
     - Emit `AppState.Loading(message = progress.formattedStatus, progress = progress)`.
5. In `tv-native/app/src/main/java/com/redsurf/tv/MainActivity.kt`:
   - In `AppState.Loading` block (lines 133–146):
     - Beneath `WaveSpinner` and `Text(s.message)`, when `s.progress != null`:
       ```kotlin
       val frac = s.progress.fraction
       if (frac != null) {
           Spacer(modifier = Modifier.height(16.dp))
           Box(
               modifier = Modifier
                   .width(320.dp)
                   .height(6.dp)
                   .clip(RoundedCornerShape(3.dp))
                   .background(SurfaceRaised),
           ) {
               Box(
                   modifier = Modifier
                       .fillMaxHeight()
                       .fillMaxWidth(frac)
                       .background(Accent),
               )
           }
       }
       ```
6. Run compile check and unit tests (`GEMINI.md` section 3).
7. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/model/ImportProgress.kt`, `tv-native/app/src/main/java/com/redsurf/tv/MainViewModel.kt`, `tv-native/app/src/main/java/com/redsurf/tv/MainActivity.kt`, `tv-native/app/src/test/java/com/redsurf/tv/model/ImportProgressFormatterTest.kt`, and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] `diff docs/agy/drafts/022/spec/ImportProgressFormatterTest.kt tv-native/app/src/test/java/com/redsurf/tv/model/ImportProgressFormatterTest.kt` prints nothing.
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted.
- [ ] `git diff origin/main --stat` shows only the specified files.

**Device Acceptance (Lead check via `redsurf-tv`):**
1. `redsurf-tv begin`
2. Trigger channel refresh from Settings -> "Update channels".
3. Verify loading screen displays `WaveSpinner` with a sleek 320.dp red fill progress bar and formatted count text (e.g. `1,420 of 8,500 channels (16%)`).
4. `redsurf-tv end`

**Stop and report if:** compile fails or tests fail after one honest fix attempt.

## Builder report

- **Status:** (done / stopped - why)
- **Branch:** agy/033-playlist-import-progress
- **Files changed:**
  - tv-native/app/src/main/java/com/redsurf/tv/model/ImportProgress.kt
  - tv-native/app/src/main/java/com/redsurf/tv/MainViewModel.kt
  - tv-native/app/src/main/java/com/redsurf/tv/MainActivity.kt
  - tv-native/app/src/test/java/com/redsurf/tv/model/ImportProgressFormatterTest.kt
  - docs/agy/tasks/033-playlist-import-progress.md
- **Build output tail:**
- **Test output tail (real counts):**
- **Not verified:**
- **Notes:**
