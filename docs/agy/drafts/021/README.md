# Sports Hub S0 Next Tasks - Architecture & Rationale (Round 2)

**Drafted for:** Sports Hub S0 (`docs/plans/SPORTS_HUB.md` §3, §5, §6, §9, §10)  
**Author:** Gemini (Builder), Task 021  
**Date:** 2026-10-31  

---

## 1. What Is Already Built

Tasks 007–013 and 018–020 built the core matching primitives and resolution engine in `tv-native/app/src/main/java/com/redsurf/tv/sports/`:

1. **Channel Normalisation & Aliases (`ChannelNameNormalizer.kt`):**
   - Quality extraction (`SD`, `HD`, `FHD`, `UHD`), flags (`BACKUP`, `TEST`, `TWENTY_FOUR_SEVEN`), and country prefix tracking.
   - `NationalNetworks`: Canonical key mapping for national cable and broadcast networks (`espn`, `fox`, `cbs`, `nbc`, `tnt`, `prime video`, etc.).
   - `MarketNetworks`: Home market RSNs (`nbc sports philadelphia`, `nbc sports philadelphia plus`) and local broadcast affiliates (`wtxf`, `kyw`, `wcau`, `wpvi`).

2. **ESPN Schedule Ingestion (`SportsModels.kt`, `EspnScoreboardParser.kt`, `EspnScheduleFetcher.kt`, `EspnCachePolicy.kt`):**
   - Core domain models: `League` (NFL, NBA, MLB, NHL, UFC), `GameState` (PRE, IN, POST, UNKNOWN), `Game(id, league, kickoffMillis, name, homeTeam, awayTeam, state, networks)`.
   - Single-day and month query URL generation.
   - Streaming JSON parsing of scoreboard payloads.
   - Rolling multi-day schedule fetching with Eastern Time day-boundary cache TTL.

3. **Ranking & Evidence Tiers (`SourceRanker.kt`):**
   - `MatchTier`: 7-level ordinal tier (`USER_PICK` 0, `EVENT_CHANNEL` 1, `EPG_TITLE` 2, `NATIONAL` 3, `IN_MARKET` 4, `EPG_DESCRIPTION` 5, `MAYBE` 6).
   - `SourceRanker.rank()`: Stable multi-criteria ordering and Lume `isConfident` threshold.
   - `SourceFilter.apply()`: Flag exclusion (`TEST`, `BACKUP`, `24/7`) and per-playlist quality deduplication.

4. **Discrete Probes & Helpers (`EpgTeamMatch.kt`, `EventNameParser.kt`, `ChannelLanguage.kt`, `MatchRate.kt`):**
   - `TeamTokens` and `EpgTeamMatch`: Word-boundary matching across title, subtitle, and description.
   - `EventNameParser`: Extraction of competing sides and kickoff times from event channel names.
   - `ChannelLanguage`: Detection of channel ISO language and language set gating.
   - `MatchRate`: Basic metric for national game coverage.

5. **Integrated Probes & Single-Game Resolver (`EventChannelMatch.kt`, `NetworkBroadcastMatch.kt`, `GameSourceResolver.kt`):**
   - `EventChannelMatch` (Probe 3c): Correlates event channel names with `Game` teams and kickoff window (`MatchTier.EVENT_CHANNEL`).
   - `NetworkBroadcastMatch` (Probe 3a): Maps broadcast networks to national feeds (`MatchTier.NATIONAL`), in-market affiliates/RSNs (`MatchTier.IN_MARKET`), and out-of-market feeds (`MatchTier.MAYBE`).
   - `GameSourceResolver`: Orchestrates Probe 3d (saved pick), Probe 3c (event channels), Probe 3a (network broadcast), and Probe 3b (EPG entries) for an individual game, aggregating probe agreement, filtering language/flags, and ordering candidates.

---

## 2. What S0 Still Lacks (per `SPORTS_HUB.md`)

While a single game can now be resolved against a flat list of channels, S0 still lacks three crucial pure Kotlin capabilities before it is usable end-to-end:

1. **Provider Category Classification & Search Prioritization (`SPORTS_HUB.md` §10 & §3b):**
   - In real provider playlists with 20,000 to 60,000 channels, scanning every non-sports channel (movies, series, news, kids) for every game is computationally wasteful and increases false positive risk.
   - `SPORTS_HUB.md` §10 explicitly mandates: *"Matching must search the provider's dedicated sports categories (e.g. 'NFL', 'NBA', 'NFL Game Pass', 'USA | NBA PPV'-style groups). Most providers carry per-event channels there; they are the most likely hit, so search them first, then the regular channels."*
   - S0 currently has no pure classifier to identify whether a provider category (`group-title`) is sports-related, which `League` it targets, or its search priority (`DEDICATED_LEAGUE`, `GENERAL_SPORTS`, `NON_SPORTS`).

2. **Canonical Team Directory, Abbreviations & CDN Logos (`SPORTS_HUB.md` §2, §5 C, §9.1, §10):**
   - `SPORTS_HUB.md` §2 specifies the ESPN logo CDN: `https://a.espncdn.com/i/teamlogos/{league}/500/{abbr}.png` (tested: `nhl/500/phi.png`).
   - `SPORTS_HUB.md` §9.1 specifies the Philadelphia launch market seed: Eagles (NFL), 76ers (NBA), Phillies (MLB), Flyers (NHL), Union (MLS).
   - Currently, team names in `Game` are raw strings from ESPN (e.g. "Philadelphia Eagles", "Eagles", "76ers", "Sixers"). There is no canonical team directory to resolve team abbreviations (`phi`, `kc`, `dal`, `bos`, `nyy`, etc.), construct logo URLs, or identify followed market teams.

3. **Structured Sports Hub Feed Aggregation (`SPORTS_HUB.md` §5 B+ Live Desk, §9.6, §10):**
   - `SPORTS_HUB.md` §9.6 and §10 define the presentation structure: Stremio/Nuvio-style rows for **Live now**, followed market teams (**Philadelphia**), and per-league sections (**NFL**, **NBA**, **MLB**, **NHL**, **UFC**).
   - S0 lacks a pure feed builder that takes resolved games (`Pair<Game, RankedSources>`) and groups them into display-ready `SportsGameCard`s and `SportsFeedSection`s with stable ordering: live in-progress games first, followed by upcoming games by kickoff time ascending, with empty leagues omitted.

---

## 3. Why These Tasks Were Picked, In Order

The drafted tasks (023, 024, 025) complete S0's pure business logic pipeline from provider playlists to structured feed data:

### Task 023: `023-sports-category-matcher.md` (Provider Category Classification)
- **What it does:** Implements `SportsCategoryMatcher` and `CategoryPriority`. Classifies category strings by sports relevance, identifies the target `League`, and assigns search priority (`DEDICATED_LEAGUE`, `GENERAL_SPORTS`, `NON_SPORTS`).
- **Why first:** Satisfies `SPORTS_HUB.md` §10's requirement to search dedicated provider groups first and skip non-sports categories, pruning the 60K channel space before matching runs.
- **Pure scope:** Pure string normalization and token matching; no Android dependencies.

### Task 024: `024-sports-team-directory.md` (Team Directory & Logo Resolution)
- **What it does:** Implements `TeamDirectory` and `Team`. Resolves team names, nicknames, and abbreviations across NFL, NBA, MLB, and NHL; constructs ESPN CDN logo URLs; provides the Philadelphia home-market team roster.
- **Why second:** Provides the essential team identity layer (abbreviations, logos, market membership) required by game cards and market spotlights.
- **Pure scope:** Deterministic lookup tables and URL formatters; no network calls in tests.

### Task 025: `025-sports-hub-feed-builder.md` (Structured Hub Feed Aggregator)
- **What it does:** Implements `SportsHubFeedBuilder`, `SportsHubFeed`, `SportsFeedSection`, and `SportsGameCard`. Aggregates resolved games and ranked sources into Live Now, Followed (Philadelphia), and League sections with stable sorting and logo links.
- **Why third:** Integrates the resolver (`GameSourceResolver`, Task 020), category prioritization (Task 023), and team logos (Task 024) into the exact structured feed required by S0's exit criteria and future UI binding.
- **Pure scope:** Pure functional data transformation and sorting; no UI or Compose dependencies.
