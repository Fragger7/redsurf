# Task 012 - Sports Hub S0: measure how many televised games get a source

**Builder:** Gemini, level 3   **Branch:** `agy/012-match-rate`   **Written by:** lead, 2026-10-30

**Goal:** S0's exit criterion is: at least 80% of today's nationally televised games get a source. Build the pure measurement, so a later on-device run can print it and list the alias gaps.
The tests are already written: they are the spec. Your job is the implementation that makes them pass.

**Read first (only these):** `GEMINI.md`; `docs/plans/SPORTS_HUB.md` §3 (context only); `tv-native/app/src/main/java/com/redsurf/tv/sports/ChannelNameNormalizer.kt` and `SourceRanker.kt` (the style to copy: KDoc, small pure objects); the spec test `docs/agy/tasks/spec/MatchRateTest.kt`.

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. Copy `docs/agy/tasks/spec/MatchRateTest.kt` to `tv-native/app/src/test/java/com/redsurf/tv/sports/MatchRateTest.kt` byte for byte (`cp`). Never edit the copy.
3. Create `tv-native/app/src/main/java/com/redsurf/tv/sports/MatchRate.kt`, package `com.redsurf.tv.sports`:
   - `data class MatchReport(val nationalGames: Int, val nationalGamesWithSource: Int, val unmatchedNetworks: Map<String, Int>)`
     with `val rate: Double` = withSource / national, `0.0` when there are no national games.
   - `object MatchRate { fun measure(games: List<Game>, channelNames: List<String>, market: String): MatchReport }`.
   - A channel is usable when `ChannelNameNormalizer.normalize(name).flags` is empty.
   - For each game, map its `networks` through `NationalNetworks.canonicalFor`. Every network that maps
     to `null` adds 1 to `unmatchedNetworks[thatRawNetworkName]` (counted for every game, national or
     not). A game with at least one canonical is national.
   - A national game has a source when some usable channel has `NationalNetworks.canonicalFor(channel)`
     equal to one of its canonicals, or `MarketNetworks.channelFor(channel, market)?.network` equal to
     one of them (e.g. `FOX 29 Philadelphia` serves `fox` in the Philadelphia market).
   - Use only these existing APIs; do not change them.
   KDoc on each public declaration: what it does and why, one short paragraph.
4. Run the compile check and the unit tests (`GEMINI.md` section 3). Read real counts from the report.
   If a spec test fails, fix the implementation, never the test.
5. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/sports/MatchRate.kt`, `tv-native/app/src/test/java/com/redsurf/tv/sports/MatchRateTest.kt` (the copy), and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] `diff docs/agy/tasks/spec/MatchRateTest.kt tv-native/app/src/test/java/com/redsurf/tv/sports/MatchRateTest.kt` prints nothing.
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted, including `MatchRateTest`'s count.
- [ ] `git diff origin/main --stat` shows only the three files.

**Stop and report if:** a spec test seems to contradict a rule above, or cannot pass without changing
an existing file - report the test name and why; do not edit the test or pick a rule silently.

## Builder report

- **Status:** done
- **Branch:** `agy/012-match-rate`
- **Files changed:**
  - `tv-native/app/src/main/java/com/redsurf/tv/sports/MatchRate.kt`
  - `tv-native/app/src/test/java/com/redsurf/tv/sports/MatchRateTest.kt`
  - `docs/agy/tasks/012-match-rate.md`
- **Build output tail:**
  `> Task :app:compileDebugKotlin`
  `BUILD SUCCESSFUL in 5s`
- **Test output tail (real counts):**
  `BUILD SUCCESSFUL in 6s`
  Report: 155 tests, 0 failures, 0 ignored (MatchRateTest: 9 tests, 0 failures)
- **Not verified:** on-device execution, TV integration
- **Notes:** byte-for-byte spec test copy verified with diff; builds clean and all tests pass.
