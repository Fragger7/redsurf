# Task 024 - Sports Hub S0: team directory and logo resolution

**Builder:** Gemini, level 3   **Branch:** `agy/024-sports-team-directory`   **Written by:** lead, 2026-10-31

**Goal:** Resolve team names across the covered leagues (NFL, NBA, MLB, NHL) to canonical metadata, abbreviations, ESPN CDN logo URLs (`SPORTS_HUB.md` §2, §10), and Philadelphia market teams (§9.1). Pure, no UI.
The tests are already written: they are the spec. Your job is the implementation that makes them pass.

**Read first (only these):** `GEMINI.md`; `docs/plans/SPORTS_HUB.md` §2, §5 C, §8 Q1, §9.1, §10; `tv-native/app/src/main/java/com/redsurf/tv/sports/SportsModels.kt` (the `League` enum); the spec test `docs/agy/tasks/spec/TeamDirectoryTest.kt`.

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. Copy `docs/agy/tasks/spec/TeamDirectoryTest.kt` to `tv-native/app/src/test/java/com/redsurf/tv/sports/TeamDirectoryTest.kt` byte for byte (`cp`). Never edit the copy.
3. Create `tv-native/app/src/main/java/com/redsurf/tv/sports/TeamDirectory.kt`, package `com.redsurf.tv.sports`:
   - Data class:
     `data class Team(val code: String, val league: League, val city: String, val name: String, val fullName: String)`.
   - Object `TeamDirectory`:
     - Holds canonical team rosters for covered leagues (`League.NFL`, `League.NBA`, `League.MLB`, `League.NHL`) and Philadelphia market seed:
       - NFL: Philadelphia Eagles (`phi`), Kansas City Chiefs (`kc`), Dallas Cowboys (`dal`), San Francisco 49ers (`sf`), Buffalo Bills (`buf`), Green Bay Packers (`gb`), etc.
       - NBA: Philadelphia 76ers (`phi`), Boston Celtics (`bos`), Los Angeles Lakers (`lal`), Golden State Warriors (`gsw`), Milwaukee Bucks (`mil`), etc.
       - MLB: Philadelphia Phillies (`phi`), New York Yankees (`nyy`), Los Angeles Dodgers (`lad`), Boston Red Sox (`bos`), Atlanta Braves (`atl`), etc.
       - NHL: Philadelphia Flyers (`phi`), Edmonton Oilers (`edm`), Boston Bruins (`bos`), New York Rangers (`nyr`), Toronto Maple Leafs (`tor`), etc.
     - `fun find(league: League, rawName: String): Team?`:
       NFKC normalizes and lowercases `rawName`, matches by `code`, `name` (e.g. "Eagles", "76ers", "Sixers"), or `fullName` (e.g. "Philadelphia Eagles"). Returns `null` if unrecognized or for UFC (individual fighters).
     - `fun logoUrl(league: League, rawName: String): String?`:
       If `find(league, rawName)` returns a team, constructs:
       `"https://a.espncdn.com/i/teamlogos/${league.name.lowercase()}/500/${team.code}.png"`.
       Returns `null` if team cannot be resolved.
     - `fun marketTeams(market: String): List<Team>`:
       Matches market name case-insensitively. For `"philadelphia"` (per `SPORTS_HUB.md` §9.1), returns the 4 Philadelphia market teams:
       `[Eagles (NFL), 76ers (NBA), Phillies (MLB), Flyers (NHL)]`.
       Returns empty list for unknown markets.
     - `fun isFollowed(league: League, rawName: String, followed: Set<String>): Boolean`:
       Returns `true` if `find(league, rawName)` matches any code, name, or fullName in `followed` (case-insensitive).
   - KDoc on each public declaration: what it does and why, one short paragraph.
4. Run the compile check and the unit tests (`GEMINI.md` section 3). Read real counts from the report. If a spec test fails, fix the implementation, never the test.
5. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/sports/TeamDirectory.kt`, `tv-native/app/src/test/java/com/redsurf/tv/sports/TeamDirectoryTest.kt` (the copy), and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] `diff docs/agy/tasks/spec/TeamDirectoryTest.kt tv-native/app/src/test/java/com/redsurf/tv/sports/TeamDirectoryTest.kt` prints nothing.
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted, including `TeamDirectoryTest`'s count.
- [ ] `git diff origin/main --stat` shows only the three files.

**Stop and report if:** a spec test seems to contradict a rule above, or cannot pass without changing an existing file - report the test name and why; do not edit the test or pick a rule silently.

## Builder report

(empty - fill in)
