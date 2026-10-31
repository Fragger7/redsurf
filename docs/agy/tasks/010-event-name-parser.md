# Task 010 - Sports Hub S0: parse event/PPV channel names

**Builder:** Gemini, level 3   **Branch:** `agy/010-event-name-parser`   **Written by:** lead, 2026-10-30

**Goal:** Providers name event channels like `NFL 03: Eagles vs Cowboys 1:00 PM ET`. Parse the two sides and the start time so the matcher can test them against ESPN's game (§3c). Pure, no UI.
The tests are already written: they are the spec. Your job is the implementation that makes them pass.

**Read first (only these):** `GEMINI.md`; `docs/plans/SPORTS_HUB.md` §3 (context only); `tv-native/app/src/main/java/com/redsurf/tv/sports/ChannelNameNormalizer.kt` and `SourceRanker.kt` (the style to copy: KDoc, small pure objects); the spec test `docs/agy/tasks/spec/EventNameParserTest.kt`.

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. Copy `docs/agy/tasks/spec/EventNameParserTest.kt` to `tv-native/app/src/test/java/com/redsurf/tv/sports/EventNameParserTest.kt` byte for byte (`cp`). Never edit the copy.
3. Create `tv-native/app/src/main/java/com/redsurf/tv/sports/EventNameParser.kt`, package `com.redsurf.tv.sports`:
   - `data class ParsedEvent(val sideA: String, val sideB: String, val minutesEt: Int?)`.
   - `object EventNameParser { fun parse(raw: String): ParsedEvent?; fun timeFits(minutesEt: Int, kickoffMillis: Long, toleranceMin: Int = 90): Boolean }`.
   - `parse`, in order: NFKC + `lowercase()` + trim. Drop a leading country prefix (2-3 letters then
     `|`). If the text contains `": "` (colon then space), drop everything up to and including the
     first one (the league label; `1:00` has no space, so times survive). If the text ends in a time -
     hours 1-12, optional `:mm`, optional space, `am`/`pm`, optional space + zone word - remove it:
     no zone or `et`/`est`/`edt` gives minutes past midnight ET (`12:00 am` = 0, `12:30 pm` = 750);
     any other zone gives `minutesEt = null`. Split the rest on whitespace; the first token that is
     exactly `vs`, `vs.`, `v` or `@` separates side A from side B. No separator, or a blank side →
     `null`. Sides are trimmed, single-spaced, lowercase.
   - `timeFits`: the kickoff's time of day in `America/New_York` (use `java.time`), compared with
     `minutesEt` on a 24-hour clock that wraps at midnight (23:50 and 00:30 are 40 minutes apart).
     True when the difference ≤ `toleranceMin`.
   KDoc on each public declaration: what it does and why, one short paragraph.
4. Run the compile check and the unit tests (`GEMINI.md` section 3). Read real counts from the report.
   If a spec test fails, fix the implementation, never the test.
5. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/sports/EventNameParser.kt`, `tv-native/app/src/test/java/com/redsurf/tv/sports/EventNameParserTest.kt` (the copy), and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] `diff docs/agy/tasks/spec/EventNameParserTest.kt tv-native/app/src/test/java/com/redsurf/tv/sports/EventNameParserTest.kt` prints nothing.
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted, including `EventNameParserTest`'s count.
- [ ] `git diff origin/main --stat` shows only the three files.

**Stop and report if:** a spec test seems to contradict a rule above, or cannot pass without changing
an existing file - report the test name and why; do not edit the test or pick a rule silently.

## Builder report

- **Status:** done
- **Branch:** `agy/010-event-name-parser`
- **Files changed:** `tv-native/app/src/main/java/com/redsurf/tv/sports/EventNameParser.kt`, `tv-native/app/src/test/java/com/redsurf/tv/sports/EventNameParserTest.kt`, `docs/agy/tasks/010-event-name-parser.md`
- **Build output tail:** `BUILD SUCCESSFUL in 3s` (builds clean)
- **Test output tail (real counts):** `BUILD SUCCESSFUL in 4s`; tests pass (146 total, 0 failures, 0 ignored; `EventNameParserTest`: 17 passed)
- **Not verified:** device execution, integration with Sports Hub matcher (pure unit tests only)
- **Notes:** none
