# Task 025 - Sports Hub S0: build structured hub feed sections

**Builder:** Gemini, level 3   **Branch:** `agy/025-sports-hub-feed-builder`   **Written by:** lead, 2026-10-31

**Goal:** Transform scheduled games and their resolved candidate sources (`GameSourceResolver`) into the structured sections required by `SPORTS_HUB.md` §5 (Layout B+ Live Desk), §9.6, and §10: Live now row, followed market teams row (Philadelphia), and per-league sections, with team logo URLs and confident playability status. Pure, no UI.
The tests are already written: they are the spec. Your job is the implementation that makes them pass.

**Read first (only these):** `GEMINI.md`; `docs/plans/SPORTS_HUB.md` §5, §9.6, §10; `tv-native/app/src/main/java/com/redsurf/tv/sports/SportsModels.kt`; `tv-native/app/src/main/java/com/redsurf/tv/sports/SourceRanker.kt`; `tv-native/app/src/main/java/com/redsurf/tv/sports/TeamDirectory.kt`; the spec test `docs/agy/tasks/spec/SportsHubFeedBuilderTest.kt`.

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. Copy `docs/agy/tasks/spec/SportsHubFeedBuilderTest.kt` to `tv-native/app/src/test/java/com/redsurf/tv/sports/SportsHubFeedBuilderTest.kt` byte for byte (`cp`). Never edit the copy.
3. Create `tv-native/app/src/main/java/com/redsurf/tv/sports/SportsHubFeedBuilder.kt`, package `com.redsurf.tv.sports`:
   - Data classes:
     - `data class SportsGameCard(val game: Game, val topSource: SourceCandidate?, val sourceCount: Int, val isConfident: Boolean, val isFollowedTeam: Boolean, val homeLogoUrl: String?, val awayLogoUrl: String?)`.
     - `data class SportsFeedSection(val id: String, val title: String, val league: League? = null, val cards: List<SportsGameCard>)`.
     - `data class SportsHubFeed(val liveSection: SportsFeedSection?, val followedSection: SportsFeedSection?, val leagueSections: List<SportsFeedSection>)`:
       - `val totalCards: Int get() = (liveSection?.cards?.size ?: 0) + (followedSection?.cards?.size ?: 0) + leagueSections.sumOf { it.cards.size }`.
   - Object `SportsHubFeedBuilder`:
     - `fun buildFeed(resolvedGames: List<Pair<Game, RankedSources>>, market: String? = null, followedTeams: Set<String> = emptySet()): SportsHubFeed`:
       - Resolve market teams via `TeamDirectory.marketTeams(market)` if market is non-null. Combine team codes/names into effective followed set: `followedTeams + marketTeams.map { it.code } + marketTeams.map { it.name }`.
       - For each `(game, ranked)` in `resolvedGames`:
         - Determine if home or away is followed: `TeamDirectory.isFollowed(game.league, game.homeTeam, effectiveFollowed) || TeamDirectory.isFollowed(game.league, game.awayTeam, effectiveFollowed)`.
         - Lookup logos via `TeamDirectory.logoUrl(game.league, game.homeTeam)` and `awayTeam`.
         - Create `SportsGameCard(game = game, topSource = ranked.topSource, sourceCount = ranked.sources.size, isConfident = ranked.isConfident, isFollowedTeam = isFollowed, homeLogoUrl = homeLogo, awayLogoUrl = awayLogo)`.
       - Section sorting comparator:
         1. Games currently playing (`GameState.IN`) first.
         2. Pre-game fixtures (`GameState.PRE`) ordered by `kickoffMillis` ascending.
         3. Completed games (`GameState.POST`) ordered by `kickoffMillis` descending.
         4. Unknown games (`GameState.UNKNOWN`).
       - Construct `liveSection`:
         - Cards where `game.state == GameState.IN`, sorted by `kickoffMillis` ascending.
         - If non-empty: `SportsFeedSection(id = "live", title = "Live Now", cards = liveCards)`. If empty: `null`.
       - Construct `followedSection`:
         - Cards where `isFollowedTeam == true`, sorted using section sorting comparator.
         - Title: if `market != null` then market capitalized (e.g. "Philadelphia"), else "My Teams".
         - If non-empty: `SportsFeedSection(id = "followed", title = title, cards = followedCards)`. If empty: `null`.
       - Construct `leagueSections`:
         - Group cards by `game.league`.
         - For each league in fixed order `[League.NFL, League.NBA, League.MLB, League.NHL, League.UFC]`:
           - If games exist for that league, sort using section sorting comparator.
           - `SportsFeedSection(id = "league_${league.name.lowercase()}", title = league.name, league = league, cards = leagueCards)`.
           - Empty leagues are omitted from `leagueSections`.
       - Return `SportsHubFeed(liveSection, followedSection, leagueSections)`.
   - KDoc on each public declaration: what it does and why, one short paragraph.
4. Run the compile check and the unit tests (`GEMINI.md` section 3). Read real counts from the report. If a spec test fails, fix the implementation, never the test.
5. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/sports/SportsHubFeedBuilder.kt`, `tv-native/app/src/test/java/com/redsurf/tv/sports/SportsHubFeedBuilderTest.kt` (the copy), and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] `diff docs/agy/tasks/spec/SportsHubFeedBuilderTest.kt tv-native/app/src/test/java/com/redsurf/tv/sports/SportsHubFeedBuilderTest.kt` prints nothing.
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted, including `SportsHubFeedBuilderTest`'s count.
- [ ] `git diff origin/main --stat` shows only the three files.

**Stop and report if:** a spec test seems to contradict a rule above, or cannot pass without changing an existing file - report the test name and why; do not edit the test or pick a rule silently.

## Builder report

(empty - fill in)
