# Task 023 - Sports Hub S0: classify provider channel categories

**Builder:** Gemini, level 3   **Branch:** `agy/023-sports-category-matcher`   **Written by:** lead, 2026-10-31

**Goal:** Provider playlists carry tens of thousands of channels categorized by `group-title`. `SPORTS_HUB.md` §10 requires matching to search dedicated sports categories (e.g. "NFL", "NBA", "NFL Game Pass") first, then general sports, and skip non-sports categories. Classify raw category names by sports relevance and target league, and determine search priority per league. Pure, no UI.
The tests are already written: they are the spec. Your job is the implementation that makes them pass.

**Read first (only these):** `GEMINI.md`; `docs/plans/SPORTS_HUB.md` §10 and §3b; `tv-native/app/src/main/java/com/redsurf/tv/sports/SportsModels.kt` (the `League` enum); the spec test `docs/agy/tasks/spec/SportsCategoryMatcherTest.kt`.

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. Copy `docs/agy/tasks/spec/SportsCategoryMatcherTest.kt` to `tv-native/app/src/test/java/com/redsurf/tv/sports/SportsCategoryMatcherTest.kt` byte for byte (`cp`). Never edit the copy.
3. Create `tv-native/app/src/main/java/com/redsurf/tv/sports/SportsCategoryMatcher.kt`, package `com.redsurf.tv.sports`:
   - Enum: `enum class CategoryPriority { DEDICATED_LEAGUE, GENERAL_SPORTS, NON_SPORTS }`.
   - Object `SportsCategoryMatcher`:
     - `fun leagueFor(rawCategory: String): League?`:
       NFKC normalizes and lowercases the category string, replaces punctuation (`|`, `-`, `:`, `_`, `/`, `•`) with spaces.
       Checks whole words / recognized phrases:
       - `League.NFL`: `"nfl"`, `"sunday ticket"`, `"super bowl"`
       - `League.NBA`: `"nba"`, `"league pass"`
       - `League.MLB`: `"mlb"`, `"extra innings"`
       - `League.NHL`: `"nhl"`, `"center ice"`
       - `League.UFC`: `"ufc"`, `"mma"`, `"fight pass"`
       Returns the matched `League`, or `null` if none match.
     - `fun isSportsCategory(rawCategory: String): Boolean`:
       Returns `true` if `leagueFor(rawCategory) != null`, or if normalized tokens contain sports indicators:
       `"sports"`, `"sport"`, `"deportes"`, `"espn"`, `"fox sports"`, `"sky sports"`, `"dazn"`, `"bein"`, `"ppv"`, `"events"`, `"box office"`, `"match day"`, `"tnt sports"`, `"super sport"`, `"racing"`, `"football"`, `"basketball"`, `"baseball"`, `"hockey"`, `"wwe"`, `"afl"`, `"rugby"`, `"cricket"`.
       Returns `false` for non-sports groups (e.g. news, cinema, movies, kids, general entertainment).
     - `fun priority(rawCategory: String, league: League): CategoryPriority`:
       If `leagueFor(rawCategory) == league` -> `CategoryPriority.DEDICATED_LEAGUE`.
       Else if `isSportsCategory(rawCategory)` -> `CategoryPriority.GENERAL_SPORTS`.
       Else -> `CategoryPriority.NON_SPORTS`.
   - KDoc on each public declaration: what it does and why, one short paragraph.
4. Run the compile check and the unit tests (`GEMINI.md` section 3). Read real counts from the report. If a spec test fails, fix the implementation, never the test.
5. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/sports/SportsCategoryMatcher.kt`, `tv-native/app/src/test/java/com/redsurf/tv/sports/SportsCategoryMatcherTest.kt` (the copy), and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] `diff docs/agy/tasks/spec/SportsCategoryMatcherTest.kt tv-native/app/src/test/java/com/redsurf/tv/sports/SportsCategoryMatcherTest.kt` prints nothing.
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted, including `SportsCategoryMatcherTest`'s count.
- [ ] `git diff origin/main --stat` shows only the three files.

**Stop and report if:** a spec test seems to contradict a rule above, or cannot pass without changing an existing file - report the test name and why; do not edit the test or pick a rule silently.

## Builder report

- Status: done
- Branch: `agy/023-sports-category-matcher`
- Files changed:
  - `tv-native/app/src/main/java/com/redsurf/tv/sports/SportsCategoryMatcher.kt`
  - `tv-native/app/src/test/java/com/redsurf/tv/sports/SportsCategoryMatcherTest.kt`
  - `docs/agy/tasks/023-sports-category-matcher.md`
- Build / Test output:
  - `./gradlew :app:compileDebugKotlin` and `:app:testDebugUnitTest` failed:
    `ERROR: JAVA_HOME is set to an invalid directory: /Users/agy/.local/opt/jdk17/Contents/Home`
  - Skipped per user instruction ("If a JDK or gradle step fails, skip it and say so in your report - do not hunt for java").
- NOT verified: compile and unit test execution (JAVA_HOME invalid).
- Spec test copy: `diff docs/agy/tasks/spec/SportsCategoryMatcherTest.kt tv-native/app/src/test/java/com/redsurf/tv/sports/SportsCategoryMatcherTest.kt` printed nothing.
