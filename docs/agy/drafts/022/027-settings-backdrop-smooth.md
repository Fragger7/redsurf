# Task 027 - Polish: Remove grainy noise from Settings SurfBackdrop

**Builder:** Gemini, level 1   **Branch:** `agy/027-settings-backdrop-smooth`   **Written by:** lead, 2026-10-31

**Goal:** The red gradient backdrop in Settings currently generates a per-pixel random noise grain in software (`Random(7)`), which the user reported looks "grainy/unprofessional" on a large television screen. Remove the manual noise loop and rely on a smooth radial gradient with hardware dithering enabled.

**Read first (only these):** `GEMINI.md`; `docs/plans/TESTING_OUTSTANDING.md` §O (line 57: "Settings' red gradient backdrop looks grainy/unprofessional... try a smooth one or leave for the UI pass"); `tv-native/app/src/main/java/com/redsurf/tv/ui/theme/SurfBackdrop.kt` (lines 56–83).

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. In `tv-native/app/src/main/java/com/redsurf/tv/ui/theme/SurfBackdrop.kt`:
   - In `SurfBitmap.bake(w: Int, h: Int)`:
     - Set `paint.isDither = true` to prevent color banding without artificial noise.
     - Remove the pixel reading, random noise generation loop, and pixel writing (lines 68–80):
       ```kotlin
       // REMOVE THIS NOISE BLOCK:
       val px = IntArray(w * h)
       bmp.getPixels(px, 0, w, 0, 0, w, h)
       val rnd = Random(7)
       for (i in px.indices) { ... }
       bmp.setPixels(px, 0, w, 0, 0, w, h)
       ```
     - Directly return `bmp.asImageBitmap()`.
     - Remove the unused `import kotlin.random.Random` if present.
3. Run the compile check and unit tests (`GEMINI.md` section 3).
4. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/ui/theme/SurfBackdrop.kt` and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted.
- [ ] `git diff origin/main --stat` shows only `SurfBackdrop.kt` and this file.
- [ ] No `Random` loop exists in `SurfBackdrop.kt`.

**Device Acceptance (Lead check via `redsurf-tv`):**
1. `redsurf-tv begin`
2. `redsurf-tv nav "Settings"`
3. `redsurf-tv shot` -> open screenshot under `/Users/Shared/redsurf-tv/`.
4. Inspect background: deep radial crimson gradient transitions smoothly into dark surface without film grain or pixelated static.
5. `redsurf-tv end`

**Stop and report if:** compile fails or `SurfBitmap.bake` structure differs from `SurfBackdrop.kt:56-83`.

## Builder report

- **Status:** (done / stopped - why)
- **Branch:** agy/027-settings-backdrop-smooth
- **Files changed:**
  - tv-native/app/src/main/java/com/redsurf/tv/ui/theme/SurfBackdrop.kt
  - docs/agy/drafts/022/027-settings-backdrop-smooth.md
- **Build output tail:**
- **Test output tail (real counts):**
- **Not verified:**
- **Notes:**
