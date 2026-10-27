# Sports Hub - research brief (draft, round 2, 2026-10-27)

**Status: research only. Nothing is built.** *(tested)* means fetched with curl on 2026-10-26/27
and the response read; *(reported)* means taken from a store page, README or article, not tested.
Next step: the user answers "Open questions" (section 8), then the S0 spike runs.

## What the user asked for (interview, 2026-10-26)

A Sports Hub on its **own nav pill**: schedules, live games and teams, by league (NFL, NBA, MLB,
NHL, UFC, boxing); a **favourite team** (Philadelphia Eagles); **quick links to sports networks**
(NFL Network, ESPN, Fox Sports, NBC Sports Philadelphia). The secret sauce: each game maps to the
channels carrying it across the installed playlists, as **one card**; OK plays the best or first
working source, a list offers the rest. Round 2 adds: **failover to free streams, including
unofficial ones** (user decision, private one-family app).

## 1. Prior art - apps that already do this

| App | What it does well | How it matches | Take |
|---|---|---|---|
| **TivraTV** ([site](https://www.tivratv.com/), [Sports Hub guide](https://www.tivratv.com/blog/sports-hub-guide.html)), $9.99/yr *(reported)* | Tabs SCORES / MY TEAMS / CHANNELS; Today day-picker; "Live now" row; sport chips with one colour each. Row order: followed teams, then live leagues, then the rest; empty leagues collapse to one "Nothing today" line; 12 games then "See all". Preview pane top-left with an info panel. **OK previews, OK again fullscreen, Back returns to the hub with the game still playing in a corner.** Play/Pause opens the box score; **hold OK opens a sheet** with every carrying channel grouped by provider and broadcaster, plus "Star this game"; hold DOWN in fullscreen shows a scores ticker. **Hide scores** (spoiler-safe: shown only after OK). Settings to add, hide and reorder networks in its League/Sports/Regional rows. | Order: 1. guide data (EPG says the game is on now); 2. the channel name ("Lions @ Bears"); 3. broadcaster name (ESPN, FOX, regional). Ties go to the connected provider, then HD over SD. **A hand pick always wins and is remembered for that game.** "Languages for games" and "Prefer highest quality" settings. Cards say "On your channels". | Closest to what the user described. Borrow the interaction model and spoiler mode. |
| **TivEPG** ([App Store](https://apps.apple.com/app/id6755161632)), iOS/tvOS, $2.99/mo *(reported)* | v1.14 rebuilt the hub: a live **scoreboard rail**, league tables, themes, selections synced across devices. Also Live Match Center, Broadcast Guide, Highlights. | "Smart Channel Finder" on channel names; from v1.5, **manual mapping of official broadcast channels to server channels** | Borrow persistent network-to-channel mapping. The data provider isn't named. |
| **GloIPTV** ([Play](https://play.google.com/store/apps/details?id=com.gloiptvplayer.app)) *(reported)* | "Top games live now, each matched to the channel on your playlist showing it"; day-by-day fixtures; **reminders** for a match or one channel; premium team and league picks | Not disclosed | Borrow reminders. |
| **SportioTV** v1.0.9 ([repo](https://github.com/trenntl/SportioTV), GPL-3.0, Kotlin, tv-material, Room FTS4; fork of OwnTV) *(code read)* | Three columns: a league rail (live counts; long-press to follow), fixtures under Live/Today/Upcoming/Results with a My teams chip row, and a detail pane (teams, venue, up to 4 broadcasts, matched channels, standings, **inline preview after a 700 ms settle**) | Below; documented in its `docs/sport-channel-matching.md`, ported from Lume | GPL: ideas only. **Ships and scrapes no streams** (grepped for aggregator names and m3u8 handling: only player and trailer code). Data is ESPN only, with a spoofed Fire TV User-Agent and an espn.com Referer; 81 leagues; 15-min cache. Optional DeepSeek name suggestions on the user's own key. |
| **Lume** ([repo](https://github.com/bilipp/Lume), AGPL-3.0, Swift) *(code read)* | About 160 leagues from ESPN, no key; per-league snapshot cache fresh for 5 min; a 60 s live poll **only while visible, paused during playback**; follows sync to the cloud, channel picks stay on the device | Tier ranking with one confident pick (below) | Best-documented resolver. AGPL: ideas only. |
| Teamarr, Channels DVR Team Pass, Apple TV "My Sports" (round 1) | Teamarr: regex event-channel extractors and manual "link this stream" fixes. Channels DVR: favourited channels win ties. Apple: "Follow your teams" row, star on teams, scores switch | - | Kept. |
| TiviMate, Smarters, OTT Navigator | No sports features found | - | RedSurf can lead TiviMate here; log in `TIVIMATE_LEARNINGS.md`. |

**Matching lessons that recur across SportioTV, Lume and TivraTV** (adopted in section 3):
- **Require both teams.** One team name alone produces false hits.
- **Discover through EPG descriptions, not just titles.** SportioTV found about 57 channels for a game instead of 6 once EPG discovery stopped sharing the channel-name cap.
- **Minimum token length of 4**, to avoid "New Girl" or "Covenant" hits.
- **Filter by language and quality before capping the list.** Rank 4K > FHD > HD > SD, demote MOBILE, drop TEST/BACKUP/24-7 channels, merge quality variants of one channel.
- **One-tap play only when exactly one source is confident** (Lume). Otherwise open a picker.
- **A user's pick always wins** and is remembered.

## 2. Data sources (schedules, teams, broadcasts)

| Source | Key / limits | Gives | Terms | Status |
|---|---|---|---|---|
| **ESPN site API** `site.api.espn.com/apis/site/v2/sports/{football/nfl, basketball/nba, baseball/mlb, hockey/nhl, mma/ufc}/scoreboard?dates=YYYYMMDD[-YYYYMMDD]` | No key; CORS `*`; `max-age=3` | Games, teams, status and scores. `broadcasts`, plus `geoBroadcasts` with type, market (National/Home/Away) and short name ("NFL Net", "NBC Sports Phil", "MSG", "ESPN+", "Prime Video"). 35-245 KB a day. | Undocumented. The public API closed in 2014 ([CMSWire](https://www.cmswire.com/cms/customer-experience/espn-slam-dunks-its-public-api-026291.php)), so it can break without notice. | *(tested)*: the UFC date range returned 3 events. Boxing returns **400**. Team schedules are huge (Flyers 1.3 MB), so avoid them. Lume says ranges return nothing and uses `?dates=YYYYMM` *(reported)*; that is probably league-dependent, so S0 tests every league. |
| ESPN logo CDN `a.espncdn.com/i/teamlogos/{league}/500/{abbr}.png` | No key | Team logos | Same grey area | *(tested)*: `nhl/500/phi.png` returned 200, 26 KB |
| **NHL** `api-web.nhle.com/v1/schedule/now` (follows a redirect) | No key | `tvBroadcasts`: network, market H/A/N, country (`NBCSP`) | The league's own endpoint, unofficial | *(tested)* |
| **MLB** `statsapi.mlb.com/api/v1/schedule?sportId=1&hydrate=broadcasts(all)` | No key | Broadcasts by home/away/national | "individual, non-commercial, non-bulk" ([terms](http://gdx.mlb.com/components/copyright.txt)) | *(tested)* |
| **TheSportsDB** v1 | **Key `3` is dead:** HTTP 403, "permanently disabled, use the new test key 123 or premium" *(tested)*. **Key `123` works but is thin:** an ice-hockey day returned 3 events, NBA next-league 1 *(tested)*. **Patreon is just a way to pay for premium** ([pricing](https://www.thesportsdb.com/pricing)): $9/mo gives 100 requests/min, 2-min livescores for the 5 US leagues and full JSON; $20/mo is Small Business. The free tier is 30 requests/min *(reported)*. | Team aliases ("Birds"), logos, colours. `lookuptv.php?id=2601717` returned "YES / United States" and "NBA League Pass / World" *(tested)*, so TV listings exist per event. | Public, with terms | Free: team aliases and logos, weekly. $9: a legal backup for scores. |
| balldontlie `api.balldontlie.io/v1/games` | **401 without a key** *(tested)* | Games and scores, no US networks | Commercial | Not needed |
| NFL | No official public schedule API found. ESPN covers the NFL, including `broadcasts`. | - | - | Use ESPN |
| UFC / boxing | UFC through ESPN `mma/ufc` *(tested)*. **No boxing schedule source exists**, on ESPN (400) or on TSDB (null). | - | - | Boxing comes from EPG titles and PPV channel names only (3c) |
| Playlist EPG + EPGSHARE01 `US_SPORTS1` (`epg/PublicEpg.kt`) | Already synced | Programme titles and descriptions on sports channels | In use | The key to matching linear channels |

**Open-source search, round 2.** No reusable US network alias dataset turned up. SportioTV's
`res/raw/sports_matching.json` (team aliases, team and broadcast stopwords, sport words) is the
nearest model, but it is GPL, so RedSurf's alias table is written fresh. Dispatcharr and Threadfin
have no sports matching beyond Teamarr. Reddit's large stream subreddits were banned in 2019, and
what remains is link-sharing, not engineering.

## 3. Matching engine (revised)

Goal: for each game, a ranked list of `(playlistId, streamId)` sources. Nothing is held in memory
beyond today's games.

**Normalisation** is the same as round 1:
1. NFKC and lowercase, so stylised letters such as `ᴴᴰ` fold to plain ones.
2. Strip the `US|`-style prefix and any bracketed text. The prefix's country and language are recorded first, for filtering.
3. Record the quality tier, then drop quality and technical tokens and backup suffixes.
4. Replace `&` with `and` and collapse punctuation into spaces.

Example: "US| NBC SPORTS PHILADELPHIA ᴴᴰ" becomes `nbc sports philadelphia`, tier HD, language EN.

**Discovery** is the union of four probes, each with its own cap so one probe can't starve another:
- **3a. Network alias** (linear channels). A hand-written alias table, `canonical key -> aliases, kind (national|rsn|affiliate), market`, holds every league spelling ("NBC Sports Phil", "NBCSP"). Home market (Q1) turns "FOX" into that market's affiliate: fox 29/wtxf, cbs 3/kyw, nbc 10/wcau, 6abc/wpvi. A WorkManager job after each import does one paged SQL pass, one hash lookup per channel, into `sports_channel_key(playlistId, streamId, key, tier, lang)`. That is a few thousand rows even at 60K channels.
- **3b. EPG**. Programmes on sports-group channels that overlap kickoff -2h to +4h, where **both teams** appear across title, subtitle and description. Team tokens are city, nickname, abbreviation and TSDB alternate names, each at least 4 characters. This needs two schema additions: an index on `epg_programs.startTime`, and XMLTV `<category>` parsing in `XmlTvParser` so non-sports programmes can be skipped cheaply. The job runs in `EpgSyncWorker` for the next 7 days and writes `sports_epg_hit(gameId, playlistId, streamId, tier)`.
- **3c. Event and PPV names.** Regexes match "NFL 03: Eagles vs Cowboys 1:00 PM ET", "UFC 334: A vs B" and "PPV 2 | Boxing: …". The two sides must match the game, and any time in the name must fall in the game window.
- **3d. Remembered picks.** These are TivEPG's network mapping ("NBCSP is channel X on playlist Y") and TivraTV's hand pick for a single game.

**Ranking.** Tiers in the style of Lume replace round 1's decimal scores. They are easier to reason about and to test:

| Tier | Evidence |
|---|---|
| 0 | The user's own pick for this game, or their saved network mapping |
| 1 | An event channel naming both sides, with a time inside the window |
| 2 | The EPG names both teams in the title or subtitle |
| 3 | A national network the leagues list for this game |
| 4 | An RSN or affiliate in the user's market |
| 5 | Both teams appear only in the EPG description |
| 6 | Out of market, or any "fox" fallback (shown as "maybe") |

Within a tier, ties break on: agreement between probes, then a favourite channel (the Channels DVR
lesson), then the connected-and-working playlist, then quality tier, then a recent success in
`recent_channels`, then proximity to kickoff.

Filters run **before** the caps:
- The language set ("Languages for games", Q6). Exact event channels bypass it.
- Drop TEST, BACKUP and 24/7 channels.
- Merge quality variants of the same channel.
- The card shows the top source. "More sources" lists up to 20, grouped by playlist.
- **OK plays immediately only when tier 0-2 has a single winner, or when the top tier has exactly one source.** Otherwise OK opens the sources sheet (Lume's `isConfident` rule).
- "This is or isn't the game" corrections persist.

**Playback fallback.** If the stall watchdog fires, or the stream returns HTTP 456, 884 or 404:
**stop the old stream first** (`max_connections: 1`, `IPTV_DOMAIN_KNOWLEDGE.md` §10), try the next
source, and show a "Source 2 of 4" chip. Order: provider playlists, then legal free sources, then
unofficial ones, the last only if enabled (section 4). A failed source sinks for that game only.

**Fetch and cache.**
- Tables: `sports_game(id, league, start, status, home, away, scores, updatedAt)` and `sports_broadcast(gameId, network, market, type)`.
- ESPN is fetched for today plus 6 days per league, stream-parsed with `JsonReader`.
- Cache lifetimes follow Teamarr's tiers: today 30 min, tomorrow 4 h, later 8-24 h.
- Live scores poll every 60 s **only while the Sports pill is visible, paused in fullscreen** (Lume).
- TSDB teams and logos refresh weekly, through the existing bounded image cache.
- Memory should be negligible next to the guide, but **this must be measured on the device**.

## 4. Failover streams - legal and unofficial, labelled separately

### 4a. Legal free sources

| Source | What it has for sports | How a URL is obtained | Status |
|---|---|---|---|
| **Pluto TV** | `api.pluto.tv/v2/channels.json` returned 442 channels (1 MB), 26 of them sports: NFL Channel, MLB, NBC Sports NOW, The NBA Channel, UFC, FOX Sports, CBS Sports HQ, Golazo, PGA TOUR, DAZN Ringside, Top Rank Classics, PFL/Bellator MMA *(tested)*. These are **24/7 channels with highlights and classics, almost never the live game.** | An unofficial JSON endpoint. Each channel's stitched HLS URL needs device and session parameters *(reported)*. | Unofficial API on a free legal service. Good for the Networks row, rarely for failover. |
| **Samsung TV Plus, Tubi, Roku Channel** | Similar 24/7 sports channels *(reported)* | Community M3U lists (i.mjh.nz). The paths I tried returned **404** *(tested)*, so they have moved. | Not verified. Skip until a working list is found. |
| **iptv-org** `iptv-org.github.io/iptv/categories/sports.m3u` | **467 entries** *(tested)*: beIN Sports 1-8 (MENA), beIN XTRA (US), CBS Sports HQ and Golazo, FIFA+ and more. Its legal note says links are "user-submitted… to the best of our knowledge… made publicly by the copyright holders" and that it has "no control over the destination" *(tested)*. | Plain M3U, already importable as a playlist | **Mixed.** Most entries are official free channels, but some aren't licensed. Treat it as "probably legal", not certain. |
| Official free live games | NFL+/NBA League Pass trials, YouTube official channels, and leagues' free-game weeks *(reported)* | Mostly app or DRM, not plain HLS; YouTube needs a Data API key | Out of scope for failover, though a "Watch on YouTube" deep link is possible later. |

### 4b. Unofficial sources (user-authorised; private family app)

**What they are.** Ad-funded aggregator sites built around a page per event. Streameast, the best
known, was shut down by ACE with Egyptian authorities in September 2025 after 1.6 billion visits
across about 80 domains ([AP via KSAT](https://www.ksat.com/tech/2025/09/03/notorious-online-soccer-piracy-network-streameast-shut-down-antipiracy-group-says/),
[Sportico](https://www.sportico.com/law/news/2025/streameast-shutdown-sports-stream-piracy-illegal-ace-1234869187/)).
US authorities had already seized some of its domains in August 2024, and the operators claimed
400 or more spares. Reddit banned r/nflstreams, r/nbastreams and r/soccerstreams in 2019 *(reported)*.
Clones appear within days. **Expect domain churn measured in weeks.**

**How streams are reached, in general terms.** An event page embeds a third-party player, often
inside nested iframes and obfuscated JavaScript, which loads an HLS (`.m3u8`) stream. That stream
is commonly locked to a Referer or Origin header, or to a short-lived token minted by the page's
script. Some aggregators also publish plain M3U lists or JSON schedules, and those are the only
kind RedSurf can use safely (below).

**Safety risks** ([NDSS 2016, "It's Free for a Reason"](https://www.ndss-symposium.org/ndss2016/accepted-papers/),
5,685 domains, [summary](https://www.helpnetsecurity.com/2016/06/17/malicious-ads-free-livestreaming/)):
- **Malicious overlay ads.** In about 50% of cases, clicking an overlay ad led to malware.
- Fake "close" buttons and fake "update your player" downloads, including APKs on Android.
- Malicious browser extensions, push-notification spam, scams and phishing.
- Tracking scripts, and occasional cryptominers *(reported)*.
- The stream host can see the TV's IP address.

**Design, revised after the coordinator's update** (the repo is going private, so takedown no
longer shapes the design):
1. **Source definitions may live in the repo**, but the app loads them from a **list it can update at runtime**: a small JSON manifest (a repo raw URL, or later a Cloud Sync document). It is fetched at launch with an ETag and cached on the device. The reason is operational, not legal: these domains rot in weeks, and a manifest update mustn't need an APK release. A copy bundled in the app is the offline fallback.
2. Each manifest entry is `{id, label, kind: m3u|xmltv|json-schedule, url, headers?, legality: legal|unofficial, enabled}`. Unofficial entries are imported **as ordinary extra playlists** through the existing M3U and XMLTV parsers. The matching engine then treats them exactly like provider channels, with nothing scraper-specific in the core.
3. **Never run their pages.** No WebView, no JavaScript evaluation, no following iframes, no APK downloads. RedSurf only accepts direct HLS URLs from M3U, XMLTV or JSON, plus optional static headers (User-Agent and Referer, which the app already supports). A source that needs script-minted tokens is dropped, not worked around.
4. **Sandboxing.** Requests to unofficial sources carry no provider credentials and no cookies, and the app keeps a separate HTTP client for them (no shared cookie jar, no auth interceptors). Only HLS and TS content types are accepted. Redirects are capped, and none go to non-media types. Unofficial sources never open the in-app OTA updater or any install intent.
5. **Labelling.** A **separate Settings toggle, off by default** (Q7). Unofficial sources come last in the fallback order, and their chip reads "Unofficial source". The two kinds are never mixed silently.
6. **Deliberately left out of this brief:** step-by-step m3u8 extraction from embed pages, and any list of current pirate domains. Those would go stale within weeks anyway. If the user wants them, they add entries to the manifest themselves.

## 5. Layouts (revised)

Shared Crest rules, unchanged from round 1:
- Square rows, a solid red focus block, Barlow Condensed figures.
- Red only for focus, LIVE and the progress bar. Team colours stay inside the logos.
- No Surf grain behind content, except on an empty state.

**B+ - Sports guide with live corner (recommended MVP).**
- Built from TivraTV's flow, SportioTV's columns and RedSurf's own Live TV.
- **Left rail:** My Teams, Live now, NFL, NBA, MLB, NHL, UFC, Boxing, Networks. Live counts appear as badges; long-press follows a league.
- **Main:** a dense 38dp game list grouped Live / Today / Upcoming / Results. A league with nothing today collapses to "Nothing today".
- **Hero band:** previews the focused game's top source after a 700 ms settle, the same pattern as Preview-on-OK.

D-pad and keys:
- **OK previews, OK again goes fullscreen, and Back returns to the hub with the game still playing in the hero.** This is TivraTV's flow, and RedSurf's shared player already does it.
- RIGHT on a row, or hold OK, opens the **sources sheet**. It groups sources by playlist, shows "Unofficial" ones last, and offers "Always use this channel for NBCSP" (TivEPG's mapping) and "Star this game".
- Play/Pause shows a compact box score.
- **Spoiler mode** (Apple and TivraTV): scores stay hidden until OK.

**A - Scoreboard rows (Apple TV and TivEPG).**
- Rows: My Teams, Live now, Today, one row per league, and a Networks quick-link row with ESPN, NFL Network, FS1, NBCSP and local affiliates.
- On top, TivEPG's slim **live scoreboard rail** across the hub.
- It is the most app-like layout and the most new UI: horizontal card rows need their own frame-time budget, so it waits for the Lite/Full effect tiers.

**C - Team spotlight as the first section of B+ or A**, not a screen of its own.
- Shows the Eagles' next or current game with a countdown, "Where to watch" as focusable source rows, and the last result behind the spoiler switch.
- A **reminder** (GloIPTV) shows a toast 10 min before kickoff with one-press tune, only while RedSurf is open.

**Key conflicts to resolve before building.** Long-press OK in fullscreen is already the context
menu (58226b9). So hold OK opens the sources sheet only in the hub, and a fullscreen scores ticker
(TivraTV's hold DOWN) needs a key that's free in `PlayerScreen`'s router. Check that during S1, and
check against TiviMate first (standing rule).

**Recommendation:** B+ for the MVP, with C as its My Teams entry and top block. Show the user
mock-ups of B+, A and C before building (nav-feedback practice).

## 6. Phases

- **S0 - spike, no UI.**
  - Build the normaliser, a seed alias table (national networks plus the Philadelphia RSNs and affiliates), an ESPN parser, and the date-range check for every league, with unit tests.
  - Measure on the device: match rate on the real playlists, job time and memory.
  - Exit: at least 80% of today's nationally televised games get a source.
- **S1 - MVP.**
  - A Sports pill with layout B+.
  - NFL, NBA, MLB and NHL from ESPN, matched by 3a plus 3d.
  - One card per game, OK with fallback, the sources sheet, My Teams (Eagles), and the Networks entry.
  - Focus and state restore per `FOCUS_MODEL.md`.
- **S2 - matching.**
  - 3b with the `startTime` index and `<category>` parsing, and 3c.
  - The tier ranking and language filter.
  - UFC, plus boxing from EPG and PPV names.
  - NHL and MLB networks as a cross-check.
- **S3 - failover and polish.**
  - Legal free sources (Pluto, iptv-org) as optional playlists.
  - The runtime-updatable source manifest, then unofficial sources behind their toggle with the sandboxing rules in 4b.
  - Spoiler mode, reminders, a Home card, and Cloud Sync of follows and mappings.

Performance gate before each phase: Sports-screen jank no worse than the guide's
(`PERFORMANCE_SALVAGE.md`), measured on the Chromecast.

## 7. What was not verified

- TivEPG's and GloIPTV's data providers and matching internals (closed source).
- TivraTV's behaviour, which comes from its own guide, not hands-on use.
- Samsung TV Plus and Tubi lists.
- Pluto stream URL construction.
- ESPN date-range behaviour per league.
- Every number about unofficial-site risk comes from the 2016 study and press coverage, not from testing.

## 8. Open questions for the next interview

1. **Home market and teams:** Philadelphia? Follow the Phillies, Sixers and Flyers too, or only the Eagles?
2. **Scores:** shown by default, or spoiler mode on by default?
3. **Streaming-only games** (Prime, Peacock, ESPN+, Netflix): show "Not in your playlists" cards, or hide them?
4. **Data backbone:** the undocumented ESPN API (free, could break), or TheSportsDB premium at $9/month as the legal backup?
5. **Scope:** only NFL, NBA, MLB, NHL, UFC and boxing, or college, MLS and F1 later?
6. **Languages for games:** English only, or English and Spanish (Fox Deportes, ESPN Deportes)?
7. **Unofficial failover:** off by default behind its own toggle, and last in the order? Who maintains the manifest: you, or Claude on request?
8. **Layout:** do B+ with the live corner and the sources sheet match what you pictured? And should follows, mappings and picks sync through Cloud Sync?

## 9. User decisions, 2026-10-27 (supersede sections 5 and 8 where they differ)
1. **Home market:** not needed at launch. Later: searchable US cities, pulled from data. Demo with **Philadelphia** (Eagles, 76ers, Phillies, Flyers, Union).
2. **Spoilers:** scores shown by default. A setting switches to blurred or blank scores.
3. **Show every game** whether or not a stream was matched. A "Show only available streams" toggle comes later, and only if matching proves very reliable.
4. **Free data only for now.** ESPN plus the league APIs; no $9/mo TheSportsDB.
5. **Leagues:** NFL, NBA, MLB, NHL, UFC, boxing. Cricket and soccer later.
6. **Layout (user's vision; check it against Apple TV / Peacock / Max sports before building):** Stremio/Nuvio-style rows - one per category (Live now, Philadelphia, NFL, NBA...). Each game is a rectangular card: Away vs Home with logos, local start time, network if known, LIVE badge. OK on a card opens a Teleport-style modal listing matching streams with their playlist, best match first (a confidence label is allowed). OK in the modal plays. Not tied to TivraTV.
7. **Matching hint (user):** streaming-exclusive games (Amazon, Peacock, Netflix, ESPN+) usually also air on the teams' home-market local stations or RSNs, and ESPN games are often simulcast on ABC. For example, an Amazon Eagles game is on Philadelphia's CBS/NBC/FOX/ABC stations, and a Peacock 76ers game is on NBC Philadelphia / NBC Sports Philadelphia. So candidates are the listed networks, their simulcast partners, and both teams' home-market affiliates and RSNs. Verify the rule per league in the S0 spike.
8. **Unofficial failover streams:** deferred until the repo goes private. Who maintains the list depends on how sources are found.
9. **Repo goes private as its own sprint, together with the failover streams.** Hybrid approach: a public repo that holds only APKs, so OTA keeps working.
