# Task 009 - Sports Hub S0: match a game to EPG programmes by team names

**Builder:** Gemini, level 3   **Branch:** `agy/009-epg-team-match`   **Written by:** lead, 2026-10-30

**Goal:** Decide whether an EPG programme is about a given game by finding *both* teams in its text (§3b). Pure functions, no database, no UI.
The tests are already written: they are the spec. Your job is the implementation that makes them pass.

**Read first (only these):** `GEMINI.md`; `docs/plans/SPORTS_HUB.md` §3 (context only); `tv-native/app/src/main/java/com/redsurf/tv/sports/ChannelNameNormalizer.kt` and `SourceRanker.kt` (the style to copy: KDoc, small pure objects); the spec test `docs/agy/tasks/spec/EpgTeamMatchTest.kt`.

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. Copy `docs/agy/tasks/spec/EpgTeamMatchTest.kt` to `tv-native/app/src/test/java/com/redsurf/tv/sports/EpgTeamMatchTest.kt` byte for byte (`cp`). Never edit the copy.
3. Create `tv-native/app/src/main/java/com/redsurf/tv/sports/EpgTeamMatch.kt`, package `com.redsurf.tv.sports`:
   - `object TeamTokens { fun of(team: String, opponent: String): Set<String> }`. Fold the text
     (NFKC, `lowercase()`, every run of non-letter/non-digit characters becomes one space, trim) and
     split it into words. Keep words of at least 4 characters. Drop the stopwords `city`, `united`,
     `club`, `team`, `state`, and drop any word that also appears in the folded opponent (e.g.
     "angeles" for two Los Angeles teams).
   - `object EpgTeamMatch { fun match(title: String, subtitle: String?, description: String?, home: String, away: String): MatchTier? }`.
     A team is present in a text when any of its tokens is a whole folded word of that text (no
     substrings). Both teams are present in title + subtitle → `MatchTier.EPG_TITLE`. Otherwise both
     are present once the description is added → `MatchTier.EPG_DESCRIPTION`. Otherwise, or if either
     team has no tokens, → `null`. The time window (kickoff -2h/+4h) is not part of this task.
   KDoc on each public declaration: what it does and why, one short paragraph.
4. Run the compile check and the unit tests (`GEMINI.md` section 3). Read real counts from the report.
   If a spec test fails, fix the implementation, never the test.
5. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/sports/EpgTeamMatch.kt`, `tv-native/app/src/test/java/com/redsurf/tv/sports/EpgTeamMatchTest.kt` (the copy), and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] `diff docs/agy/tasks/spec/EpgTeamMatchTest.kt tv-native/app/src/test/java/com/redsurf/tv/sports/EpgTeamMatchTest.kt` prints nothing.
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted, including `EpgTeamMatchTest`'s count.
- [ ] `git diff origin/main --stat` shows only the three files.

**Stop and report if:** a spec test seems to contradict a rule above, or cannot pass without changing
an existing file - report the test name and why; do not edit the test or pick a rule silently.

## Builder report

- **Status:** done
- **Branch:** agy/009-epg-team-match
- **Files changed:**
  - `tv-native/app/src/main/java/com/redsurf/tv/sports/EpgTeamMatch.kt`
  - `tv-native/app/src/test/java/com/redsurf/tv/sports/EpgTeamMatchTest.kt`
  - `docs/agy/tasks/009-epg-team-match.md`
- **Build output tail:**
  `> Task :app:compileDebugKotlin`
  `BUILD SUCCESSFUL in 5s`
- **Test output tail (real counts):**
  `> Task :app:testDebugUnitTest`
  `BUILD SUCCESSFUL in 6s`
  Report: 142 tests, 0 failures, 0 ignored (`EpgTeamMatchTest`: 13 tests, 0 failures, 0 ignored). Tests pass.
- **Not verified:** Device runtime / UI integration (pure logic task).
- **Notes:** None.
