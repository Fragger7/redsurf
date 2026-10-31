# Task 019 - Sports Hub S0: match linear network broadcasts to a game

**Builder:** Gemini, level 3   **Branch:** `agy/019-network-broadcast-match`   **Written by:** lead, 2026-10-31

**Goal:** Linear channels carry games through national networks (ESPN, TNT), regional sports networks (NBC Sports Philadelphia), or local broadcast affiliates (WTXF FOX 29) (§3a, §9.7). Match a linear channel name against a `Game`'s broadcast networks and the user's home market to award `MatchTier.NATIONAL`, `MatchTier.IN_MARKET`, or `MatchTier.MAYBE`. Pure, no UI.
The tests are already written: they are the spec. Your job is the implementation that makes them pass.

**Read first (only these):** `GEMINI.md`; `docs/plans/SPORTS_HUB.md` §3a, §9.7; `tv-native/app/src/main/java/com/redsurf/tv/sports/ChannelNameNormalizer.kt`, `SourceRanker.kt` and `SportsModels.kt`; the spec test `docs/agy/tasks/spec/NetworkBroadcastMatchTest.kt`.

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. Copy `docs/agy/tasks/spec/NetworkBroadcastMatchTest.kt` to `tv-native/app/src/test/java/com/redsurf/tv/sports/NetworkBroadcastMatchTest.kt` byte for byte (`cp`). Never edit the copy.
3. Create `tv-native/app/src/main/java/com/redsurf/tv/sports/NetworkBroadcastMatch.kt`, package `com.redsurf.tv.sports`:
   - `object NetworkBroadcastMatch { fun match(rawChannelName: String, game: Game, market: String?): MatchTier? }`.
   - Normalise `rawChannelName` with `ChannelNameNormalizer.normalize(rawChannelName)`. If `channelName.flags` is not empty, return `null` (TEST, BACKUP, 24/7 channels are dropped).
   - If `game.networks` is empty, return `null`.
   - Compute game canonical networks: map `game.networks` through `NationalNetworks.canonicalFor`.
   - Market matching (when `market != null`):
     - Check local RSN: if any raw entry in `game.networks` resolves to an RSN via `MarketNetworks.channelFor(it, market)`, and `MarketNetworks.channelFor(rawChannelName, market)` equals that same channel, return `MatchTier.IN_MARKET`.
     - Check local broadcast affiliate: if `MarketNetworks.channelFor(rawChannelName, market)` resolves to an affiliate (`MarketKind.AFFILIATE`):
       - If its `.network` is in the game's canonicals, return `MatchTier.IN_MARKET`.
       - If the game has a streaming-exclusive canonical (`"prime video"` or `"peacock"`), return `MatchTier.IN_MARKET` (local broadcast simulcast rule, §9.7).
   - National matching:
     - Look up `NationalNetworks.canonicalFor(rawChannelName)`. If it matches any of the game's canonicals:
       - Broadcast networks (`"fox"`, `"cbs"`, `"nbc"`, `"abc"`) carried on non-market or out-of-market feeds return `MatchTier.MAYBE`.
       - Dedicated national cable/satellite sports networks (`"espn"`, `"espn2"`, `"tnt"`, `"tbs"`, `"nfl network"`, `"nhl network"`, `"mlb network"`, etc.) return `MatchTier.NATIONAL`.
   - Otherwise, return `null`.
   KDoc on each public declaration: what it does and why, one short paragraph.
4. Run the compile check and the unit tests (`GEMINI.md` section 3). Read real counts from the report.
   If a spec test fails, fix the implementation, never the test.
5. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/sports/NetworkBroadcastMatch.kt`, `tv-native/app/src/test/java/com/redsurf/tv/sports/NetworkBroadcastMatchTest.kt` (the copy), and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] `diff docs/agy/tasks/spec/NetworkBroadcastMatchTest.kt tv-native/app/src/test/java/com/redsurf/tv/sports/NetworkBroadcastMatchTest.kt` prints nothing.
- [ ] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted, including `NetworkBroadcastMatchTest`'s count.
- [ ] `git diff origin/main --stat` shows only the three files.

**Stop and report if:** a spec test seems to contradict a rule above, or cannot pass without changing
an existing file - report the test name and why; do not edit the test or pick a rule silently.

## Builder report

- **Status:** done
- **Branch:** agy/019-network-broadcast-match
- **Files changed:** `tv-native/app/src/main/java/com/redsurf/tv/sports/NetworkBroadcastMatch.kt`, `tv-native/app/src/test/java/com/redsurf/tv/sports/NetworkBroadcastMatchTest.kt`, `docs/agy/tasks/019-network-broadcast-match.md`
- **Build output tail:** BUILD SUCCESSFUL in 1m (18 actionable tasks: 18 executed)
- **Test output tail (real counts):** BUILD SUCCESSFUL in 8s (28 actionable tasks: 10 executed, 18 up-to-date) - 189 tests, 0 failures across suite (NetworkBroadcastMatchTest: 12 tests, 0 failures)
- **Not verified:** device runtime verification (pure unit test task, no TV run)
- **Notes:** none
