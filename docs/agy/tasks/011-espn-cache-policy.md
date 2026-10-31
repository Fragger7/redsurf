# Task 011 - Sports Hub S0: how long a cached ESPN day stays fresh

**Builder:** Gemini, level 3   **Branch:** `agy/011-espn-cache-policy`   **Written by:** lead, 2026-10-30

**Goal:** Decide how long one day's ESPN scoreboard may be served from cache before refetching. Pure, no storage, no network.
The tests are already written: they are the spec. Your job is the implementation that makes them pass.

**Read first (only these):** `GEMINI.md`; `docs/plans/SPORTS_HUB.md` §3 (context only); `tv-native/app/src/main/java/com/redsurf/tv/sports/ChannelNameNormalizer.kt` and `SourceRanker.kt` (the style to copy: KDoc, small pure objects); the spec test `docs/agy/tasks/spec/EspnCachePolicyTest.kt`.

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. Copy `docs/agy/tasks/spec/EspnCachePolicyTest.kt` to `tv-native/app/src/test/java/com/redsurf/tv/sports/EspnCachePolicyTest.kt` byte for byte (`cp`). Never edit the copy.
3. Create `tv-native/app/src/main/java/com/redsurf/tv/sports/EspnCachePolicy.kt`, package `com.redsurf.tv.sports`:
   - `object EspnCachePolicy { fun ttlMillis(day: LocalDate, nowMillis: Long): Long; fun isStale(fetchedAtMillis: Long, day: LocalDate, nowMillis: Long): Boolean }`
     (`java.time.LocalDate`).
   - "Today" is `nowMillis`'s date in `America/New_York`, not UTC. Days from today to `day`:
     0 → 30 minutes; 1 → 4 hours; 2 or 3 → 8 hours; 4 or more → 24 hours; past days → 24 hours.
   - `isStale`: true when `nowMillis - fetchedAtMillis >= ttlMillis(day, nowMillis)`, and also true
     when `fetchedAtMillis > nowMillis` (the clock went backwards; refetch).
   KDoc on each public declaration: what it does and why, one short paragraph.
4. Run the compile check and the unit tests (`GEMINI.md` section 3). Read real counts from the report.
   If a spec test fails, fix the implementation, never the test.
5. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/sports/EspnCachePolicy.kt`, `tv-native/app/src/test/java/com/redsurf/tv/sports/EspnCachePolicyTest.kt` (the copy), and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] `diff docs/agy/tasks/spec/EspnCachePolicyTest.kt tv-native/app/src/test/java/com/redsurf/tv/sports/EspnCachePolicyTest.kt` prints nothing.
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted, including `EspnCachePolicyTest`'s count.
- [ ] `git diff origin/main --stat` shows only the three files.

**Stop and report if:** a spec test seems to contradict a rule above, or cannot pass without changing
an existing file - report the test name and why; do not edit the test or pick a rule silently.

## Builder report

- **Status:**
- **Branch:**
- **Files changed:**
- **Build output tail:**
- **Test output tail (real counts):**
- **Not verified:**
- **Notes:**
