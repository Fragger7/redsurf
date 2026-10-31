# Task 029 - Polish: 0.5s network speed polling and EMA smoothing (pure logic)

**Builder:** Gemini, level 2   **Branch:** `agy/029-network-speed-filter`   **Written by:** lead, 2026-10-31

**Goal:** Network speed polling in `PlayerController.kt` currently runs every 2.0s with slow smoothing, which user testing found laggy. Update the poll interval to 500 ms with an exponential moving average (EMA, alpha = 0.4) that reacts within 1 second while absorbing burstiness, resetting to null only after 3.0 seconds (6 consecutive quiet windows). Pure logic object + controller integration.

**Read first (only these):** `GEMINI.md`; `docs/plans/TESTING_OUTSTANDING.md` §O (line 71: "network speed must update continuously (today it's laggy)"); `tv-native/app/src/main/java/com/redsurf/tv/player/PlayerController.kt` (lines 360–396); the spec test `docs/agy/drafts/022/spec/NetworkSpeedFilterTest.kt`.

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. Copy `docs/agy/drafts/022/spec/NetworkSpeedFilterTest.kt` to `tv-native/app/src/test/java/com/redsurf/tv/player/NetworkSpeedFilterTest.kt` byte for byte (`cp`). Never edit the copy.
3. Create `tv-native/app/src/main/java/com/redsurf/tv/player/NetworkSpeedFilter.kt`, package `com.redsurf.tv.player`:
   ```kotlin
   package com.redsurf.tv.player

   /**
    * Filters byte counts into smoothed bps estimates over short sampling windows.
    * An EMA absorbs streaming burstiness; sustained silence (6 windows = 3.0s) clears the readout.
    */
   class NetworkSpeedFilter(
       val sampleIntervalMs: Long = 500L,
       val alpha: Float = 0.4f,
   ) {
       var smoothedBps: Long? = null
           private set
       var consecutiveEmptyWindows: Int = 0
           private set

       fun onSample(bytesTransferred: Long): Long? {
           val multiplier = 1000L / sampleIntervalMs
           val instantBps = bytesTransferred * 8L * multiplier

           if (instantBps > 0) {
               consecutiveEmptyWindows = 0
               smoothedBps = smoothedBps?.let { (it * (1f - alpha) + instantBps * alpha).toLong() } ?: instantBps
           } else {
               consecutiveEmptyWindows++
               if (consecutiveEmptyWindows >= 6) {
                   smoothedBps = null
               }
           }
           return smoothedBps
       }
   }
   ```
4. In `tv-native/app/src/main/java/com/redsurf/tv/player/PlayerController.kt`:
   - Replace private vars `smoothedBitrateEstimateBps` and `consecutiveEmptyWindows` with an instance of `NetworkSpeedFilter(sampleIntervalMs = 500L, alpha = 0.4f)`.
   - In `startNetworkStatsPoll()` (lines 369–396):
     - Change `delay(2_000)` to `delay(500)`.
     - Delegate bps calculation to `filter.onSample(bytes)`.
5. Run compile check and unit tests (`GEMINI.md` section 3).
6. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/player/NetworkSpeedFilter.kt`, `tv-native/app/src/main/java/com/redsurf/tv/player/PlayerController.kt`, `tv-native/app/src/test/java/com/redsurf/tv/player/NetworkSpeedFilterTest.kt`, and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] `diff docs/agy/drafts/022/spec/NetworkSpeedFilterTest.kt tv-native/app/src/test/java/com/redsurf/tv/player/NetworkSpeedFilterTest.kt` prints nothing.
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted.
- [ ] `git diff origin/main --stat` shows only the four files.

**Stop and report if:** compile fails or a test fails after one honest fix attempt.

## Builder report

- **Status:** (done / stopped - why)
- **Branch:** agy/029-network-speed-filter
- **Files changed:**
  - tv-native/app/src/main/java/com/redsurf/tv/player/NetworkSpeedFilter.kt
  - tv-native/app/src/main/java/com/redsurf/tv/player/PlayerController.kt
  - tv-native/app/src/test/java/com/redsurf/tv/player/NetworkSpeedFilterTest.kt
  - docs/agy/tasks/029-network-speed-filter.md
- **Build output tail:**
- **Test output tail (real counts):**
- **Not verified:**
- **Notes:**
