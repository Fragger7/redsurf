# Task 020 - Sports Hub S0: resolve and rank sources for a game

**Builder:** Gemini, level 3   **Branch:** `agy/020-game-source-resolver`   **Written by:** lead, 2026-10-31

**Goal:** Combine the four matching probes (user picks, event channels, network broadcasts, EPG programs), apply language and flag filters, and rank candidate channels into `RankedSources` per `SPORTS_HUB.md` §3. Pure, no UI.
The tests are already written: they are the spec. Your job is the implementation that makes them pass.

**Read first (only these):** `GEMINI.md`; `docs/plans/SPORTS_HUB.md` §3; `tv-native/app/src/main/java/com/redsurf/tv/sports/SourceRanker.kt`, `EventChannelMatch.kt`, `NetworkBroadcastMatch.kt`, `EpgTeamMatch.kt`, `ChannelLanguage.kt` and `SportsModels.kt`; the spec test `docs/agy/tasks/spec/GameSourceResolverTest.kt`.

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. Copy `docs/agy/tasks/spec/GameSourceResolverTest.kt` to `tv-native/app/src/test/java/com/redsurf/tv/sports/GameSourceResolverTest.kt` byte for byte (`cp`). Never edit the copy.
3. Create `tv-native/app/src/main/java/com/redsurf/tv/sports/GameSourceResolver.kt`, package `com.redsurf.tv.sports`:
   - Data classes:
     - `data class PlayableChannel(val playlistId: String, val streamId: Int, val name: String, val isFavorite: Boolean = false, val playlistWorking: Boolean = true, val lastSuccessMillis: Long? = null)`.
     - `data class EpgProgramEntry(val playlistId: String, val streamId: Int, val title: String, val subtitle: String? = null, val description: String? = null, val startMillis: Long, val endMillis: Long)`.
   - `object GameSourceResolver { fun resolve(game: Game, channels: List<PlayableChannel>, epgEntries: List<EpgProgramEntry> = emptyList(), market: String? = null, allowedLanguages: Set<String> = emptySet(), savedPick: Pair<String, Int>? = null): RankedSources }`.
   - For each channel:
     - Normalise with `ChannelNameNormalizer.normalize(channel.name)`.
     - Evaluate probes:
       - Probe 3d (Saved Pick): if `savedPick != null && savedPick.first == channel.playlistId && savedPick.second == channel.streamId` → record hit with `MatchTier.USER_PICK`.
       - Probe 3c (Event Channel): `EventChannelMatch.match(channel.name, game)` → if non-null, record hit.
       - Probe 3a (Network Broadcast): `NetworkBroadcastMatch.match(channel.name, game, market)` → if non-null, record hit.
       - Probe 3b (EPG Programs): for entries matching `(channel.playlistId, channel.streamId)` overlapping kickoff window (`kickoff - 2h` to `kickoff + 4h`), evaluate `EpgTeamMatch.match(title, subtitle, description, game.homeTeam, game.awayTeam) ?: EpgTeamMatch.match(title, subtitle, description, game.awayTeam, game.homeTeam)`. If non-null, record hit with that tier and `kickoffDistanceMillis = entry.startMillis - game.kickoffMillis`.
     - If no probes hit, skip channel.
     - Multi-probe agreement: combine matched probe tiers (strongest tier wins, sum probe count).
     - Construct `SourceCandidate`.
     - Language filter: if `candidate.tier != MatchTier.EVENT_CHANNEL && !ChannelLanguage.passes(channelName, allowedLanguages)`, discard candidate (event channels bypass language filter).
     - Pair kept candidate with `channelName`.
   - Pass candidates to `SourceFilter.apply(candidatesWithNames)` to drop flags and deduplicate quality variants per playlist.
   - Pass filtered candidates to `SourceRanker.rank(filtered)` to return `RankedSources`.
   KDoc on each public declaration: what it does and why, one short paragraph.
4. Run the compile check and the unit tests (`GEMINI.md` section 3). Read real counts from the report.
   If a spec test fails, fix the implementation, never the test.
5. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/sports/GameSourceResolver.kt`, `tv-native/app/src/test/java/com/redsurf/tv/sports/GameSourceResolverTest.kt` (the copy), and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] `diff docs/agy/tasks/spec/GameSourceResolverTest.kt tv-native/app/src/test/java/com/redsurf/tv/sports/GameSourceResolverTest.kt` prints nothing.
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted, including `GameSourceResolverTest`'s count.
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
