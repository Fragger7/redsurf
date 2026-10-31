# Sports Hub S0 Next Tasks - Architecture & Rationale

**Drafted for:** Sports Hub S0 (`docs/plans/SPORTS_HUB.md` §3 and §6)  
**Author:** Gemini (Builder), Task 016  
**Date:** 2026-10-31  

---

## 1. What Is Already Built

Tasks 007–013 built the foundational components for Sports Hub S0 in `tv-native/app/src/main/java/com/redsurf/tv/sports/`:

1. **Channel Normalisation & Network Aliases (`ChannelNameNormalizer.kt`):**
   - `ChannelNameNormalizer.normalize(raw)` extracts canonical keys, country prefixes, quality tokens (`SD`, `HD`, `FHD`, `UHD`), and flags (`BACKUP`, `TEST`, `TWENTY_FOUR_SEVEN`).
   - `NationalNetworks.canonicalFor(name)` maps provider and ESPN names to canonical national keys (`espn`, `fox`, `cbs`, `nbc`, `tnt`, `prime video`, etc.).
   - `MarketNetworks.channelFor(name, market)` and `affiliateFor(network, market)` resolve market RSNs (`nbc sports philadelphia`) and local broadcast affiliates (`wtxf`, `kyw`, `wcau`, `wpvi`).

2. **ESPN Schedule & Ingestion (`SportsModels.kt`, `EspnScoreboardParser.kt`, `EspnScheduleFetcher.kt`, `EspnCachePolicy.kt`):**
   - Models: `League`, `GameState`, `Game(id, league, kickoffMillis, name, homeTeam, awayTeam, state, networks)`.
   - Single-day and month query URL builders (`EspnScoreboardUrl`).
   - Streaming JSON parsing of ESPN scoreboard responses into `Game` records.
   - Rolling multi-day schedule fetcher with dependency-injected HTTP fetch function.
   - Cache TTL policy based on Eastern Time day boundaries.

3. **Ranking & Evidence Tiers (`SourceRanker.kt`):**
   - `MatchTier`: 7-level ordinal tier (`USER_PICK` 0, `EVENT_CHANNEL` 1, `EPG_TITLE` 2, `NATIONAL` 3, `IN_MARKET` 4, `EPG_DESCRIPTION` 5, `MAYBE` 6).
   - `SourceCandidate`: channel reference with tier, probe count, favorite flag, playlist health, quality, and kickoff distance.
   - `SourceRanker.rank()`: stable ordering by tier, probe agreement, favorite, playlist health, quality, recents, and kickoff proximity; determines Lume's `isConfident` threshold.
   - `SourceFilter.apply()`: drops `TEST`, `BACKUP`, and `24/7` channels, and merges quality variants (e.g. HD/FHD) within a playlist into the single best feed.

4. **Discrete Probes & Helpers (`EpgTeamMatch.kt`, `EventNameParser.kt`, `ChannelLanguage.kt`, `MatchRate.kt`):**
   - `TeamTokens.of(team, opponent)`: extracts distinctive team words (>= 4 letters, non-stopwords, pruned of shared opponent words).
   - `EpgTeamMatch.match(...)`: matches title, subtitle, and description to `MatchTier.EPG_TITLE` or `MatchTier.EPG_DESCRIPTION`.
   - `EventNameParser.parse(raw)` and `timeFits(...)`: extracts competing sides and ET start times from event channel strings, checking kickoff time within a tolerance window.
   - `ChannelLanguage.of(name)` and `passes(...)`: detects ISO language code and tests against allowed language sets.
   - `MatchRate.measure(...)`: summary verification helper calculating the ratio of national games with an available channel.

---

## 2. What S0 Still Lacks (per `SPORTS_HUB.md`)

While the individual building blocks exist, they are isolated units. `SPORTS_HUB.md` §3 defines a matching engine that discovers sources across four probes, filters them, and ranks them for each game. S0 currently lacks:

1. **Event Channel to Game Correlation (Probe 3c):**
   - `EventNameParser` only parses a string into two generic text sides and minutes past midnight.
   - It does not test whether those sides match a specific `Game`'s `homeTeam` and `awayTeam` (in either order), nor does it validate the parsed time against `game.kickoffMillis` to produce a `MatchTier.EVENT_CHANNEL` candidate.

2. **Linear Network Matcher (Probe 3a):**
   - `NationalNetworks` and `MarketNetworks` exist as lookup tables, but there is no pure matcher connecting a `Game`'s `networks: List<String>` and user `market` to a channel name.
   - It must evaluate whether a channel is a national feed (`MatchTier.NATIONAL`), a local market RSN or affiliate carrying the network (`MatchTier.IN_MARKET`), or an out-of-market/fallback feed (`MatchTier.MAYBE`).

3. **Unified Game Source Resolver (Wiring All Probes & Filters):**
   - There is no unified resolver that takes a `Game`, available channels, and EPG entries, and executes the complete S0 pipeline:
     - User saved picks / network mappings (Probe 3d -> `USER_PICK`).
     - Event channels (Probe 3c -> `EVENT_CHANNEL`).
     - Linear network broadcast matching (Probe 3a -> `NATIONAL` / `IN_MARKET` / `MAYBE`).
     - EPG programme overlap within kickoff -2h to +4h (Probe 3b -> `EPG_TITLE` / `EPG_DESCRIPTION`).
     - Multi-probe agreement aggregation (summing probe counts when multiple probes find the same channel).
     - Language filtering (dropping unallowed languages, bypassed by exact event channels).
     - Flag filtering and quality deduplication (`SourceFilter.apply`).
     - Tier ranking and confidence evaluation (`SourceRanker.rank`).

---

## 3. Why These Tasks Were Picked, In Order

To keep each step small, pure, and verifiable under the trust ladder, the work is divided into 3 tasks:

### Task 018: `018-event-channel-match.md` (Probe 3c)
- **What it does:** Implements `EventChannelMatch`. Wires `EventNameParser`, `TeamTokens`, and `Game`.
- **Why first:** Event channels (PPV, League Pass, game-specific feeds) are the highest automated evidence tier (Tier 1). They bypass language filters and provide direct access to the live event.
- **Pure scope:** Operates strictly on string matching, regex parsing, and time arithmetic (`java.time`).

### Task 019: `019-network-broadcast-match.md` (Probe 3a)
- **What it does:** Implements `NetworkBroadcastMatch`. Wires `Game.networks`, `NationalNetworks`, `MarketNetworks`, and `ChannelNameNormalizer`.
- **Why second:** Linear channels form the bulk of traditional sports viewing (Tier 3 National, Tier 4 In-Market, Tier 6 Maybe). This completes the second major automated probe.
- **Pure scope:** Operates on normalized strings, alias lookups, and market affiliate mappings.

### Task 020: `020-game-source-resolver.md` (End-to-End Matcher)
- **What it does:** Implements `GameSourceResolver`. Wires Probe 3d (user picks), Probe 3c (Task 018), Probe 3a (Task 019), Probe 3b (EPG matching with kickoff time window), `ChannelLanguage`, `SourceFilter`, and `SourceRanker`.
- **Why third:** Once the individual probes are verified, this task orchestrates them into a single, clean pipeline that outputs `RankedSources`.
- **Exit criterion enabled:** This completes the S0 pure matching engine, enabling the subsequent on-device measurement run against real provider playlists to verify the >= 80% national match threshold.
