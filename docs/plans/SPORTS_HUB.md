# Sports Hub - research brief (draft, 2026-10-26)

**Status: research only. Nothing is built.** Every API claim marked *(checked)* was fetched with curl
on 2026-10-26 and its response read. Claims marked *(reported)* come from a third-party page and
were not tested. Next step: the user answers "Open questions" below, then the S0 spike runs.

## What the user asked for (interview, 2026-10-26)

A Sports Hub on its **own nav pill**. It shows schedules, games in progress and the teams in them,
grouped by league: NFL, NBA, MLB, NHL, plus UFC and boxing. It supports a **favourite team** (the
Philadelphia Eagles), and **quick links to the sports networks** (NFL Network, ESPN, Fox Sports and
NBC Sports, especially Philadelphia). The secret sauce: each game maps straight to the channels that
carry it in the playlists already installed. If a game is on several channels or playlists, it gets
one card. OK plays the best source, or the first one that works, and a list offers the others.

## 1. Prior art: how others match games to streams

| Project | What it does | How it matches | Lesson for RedSurf |
|---|---|---|---|
| **Teamarr** ([repo](https://github.com/Pharaoh-Labs/teamarr), [event-group docs](https://mintlify.wiki/Pharaoh-Labs/teamarr/event-groups)) - a sports EPG add-on for Dispatcharr, AGPL-3.0 (MIT before v2.18.0) | Builds a sports guide and "event channels" from a provider's stream list | A pipeline of steps: filter streams, classify each stream name (teams, league, date, time), match it against a cached sports schedule, then create the channel. It supports custom regex extractors (`(?P<home>.*)\s*vs\s*(?P<away>.*)`, fighter names, date and league), learns each league's teams from the schedule itself, and lets the user correct a match by hand ("link this stream to this event"). It also previews failed matches. | It is the closest prior art. Copy the ideas, not the code: the licence is AGPL and the UI is not reusable. Known limits: it can't match "NBA 01"-style names, and it does not cover linear channels like ESPN, where the game is only in the EPG. RedSurf needs both cases. |
| Teamarr's TSDB provider ([docs](https://mintlify.wiki/Pharaoh-Labs/teamarr/reference/provider-tsdb)) | Schedule cache | Cache lifetime depends on how far out the data is: today 30 min, tomorrow 4h, days 3-7 8h, 8+ days 24h, teams 24h. On a 429 it backs off from 5s up to 80s. | Use the same tiers. |
| **Channels DVR "Team Pass"** ([product](https://getchannels.com/live-tv/), [forum](https://community.getchannels.com/t/is-there-a-way-to-select-which-channel-to-record-for-a-team-pass/24214)) | Records every game of a team, on any network | It uses commercial guide data that already tags each programme with its teams. When a game aired on both ESPN and ESPNU it picked the wrong feed; users work around it by favouriting the channel they prefer, which then wins. | Favourited channels should rank higher. One card can have several sources, which is exactly the user's model. |
| **Apple TV app, "My Sports"** ([support](https://support.apple.com/guide/tvapp/follow-sports-teams-and-scores-atvb2c74ee59/web), [Six Colors review](https://sixcolors.com/post/2024/02/simple-complexity-apples-trio-of-sports-apps/)) | Sports tab with rows, a star on each followed team, scores can be switched off | Owns the rights, so it has no matching problem | The reference for a 10-foot UI: a "Follow your teams" row, a star on followed teams, and a switch to hide scores. |
| **AIOLiveTV** Stremio add-on ([Docker Hub](https://hub.docker.com/r/mrcanelas/aiolivetv)) | Merges M3U, Xtream and XMLTV sources | Matches the same channel across sources and lets the user review the mappings | Supports the "one card, many sources" model for channels. It does not match games. *(reported)* |
| **Stremio and Nuvio sports add-ons** (StremVerse, Sport-Live, and others) | Live event lists | They pull streams from scraped stream indexes and aggregator sites | **Not recommended, and nothing here borrows from them:** they depend on piracy-adjacent stream scraping. No Nuvio sports add-on with an open matching design turned up. |
| TiviMate, IPTV Smarters, OTT Navigator | EPG grid only | None found. A search for a "sports guide" or game-finder feature in these players came up empty. | **This is a feature TiviMate doesn't have, so RedSurf can lead here.** Log this in `TIVIMATE_LEARNINGS.md` per the standing rule. |

**Not researched in depth: Dribbble and Mobbin.** Mobbin needs a login and covers mobile, not TV.
The Apple TV pattern above serves as the UI reference instead. The three layouts below are drawn
from it, from TiviMate's guide and from RedSurf's own Crest guide.

## 2. Data sources: schedules, teams, broadcasts

| Source | Key / limits | What it gives | Terms | Verdict |
|---|---|---|---|---|
| **ESPN site API** `site.api.espn.com/apis/site/v2/sports/{football/nfl, basketball/nba, baseball/mlb, hockey/nhl, mma/ufc}/scoreboard?dates=YYYYMMDD[-YYYYMMDD]` | No key. `cache-control: max-age=3` *(checked)* | Games with ISO start time, teams (name, abbreviation, location, home or away), status and score. `broadcasts` gives the national network names. `geoBroadcasts` gives each broadcast's type (TV or Streaming), market (National, Home or Away) and network short name. Real examples: "NFL Net", "Prime Video", "NBC Sports Phil", "MSG", "MNMT", "NBA TV", "ESPN+". A day's response is 35-245 KB. UFC returns 3 events for November 2026 *(checked)*. | Undocumented. ESPN shut its public API in 2014 ([CMSWire](https://www.cmswire.com/cms/customer-experience/espn-slam-dunks-its-public-api-026291.php)), and the endpoints in use now were reverse-engineered ([Zuplo guide](https://zuplo.com/blog/2024/10/01/espn-hidden-api-guide), [community list](https://github.com/pseudo-r/Public-ESPN-API)). They can change without notice. | **Primary source.** Low-volume personal use, with aggressive caching, is a grey area the user should accept knowingly (Q4). Team schedule endpoints are large (Flyers 1.3 MB, 76ers 832 KB), so avoid them and build team views from the per-date scoreboards. |
| **NHL** `api-web.nhle.com/v1/schedule/now` (follows a redirect) | No key *(checked)* | `tvBroadcasts`: network, market (H, A or N) and country. Example: CBJ @ PHI on `NBCSP`, home market, US. | Unofficial, but this is the league's own endpoint | Use it to cross-check regional networks. It names the same network differently from ESPN ("NBCSP" vs "NBC Sports Phil"), which is why the alias table matters. |
| **MLB Stats API** `statsapi.mlb.com/api/v1/schedule?sportId=1&date=…&hydrate=broadcasts(all)` | No key *(checked)* | Broadcasts with name, type (TV or AM radio), home or away, and whether national (e.g. "FOX / FOX ONE", "Fox Deportes") | "Only individual, non-commercial, non-bulk use" ([copyright](http://gdx.mlb.com/components/copyright.txt)) | A one-family app falls within those terms. Use it for MLB regional networks. |
| **TheSportsDB** ([docs](https://www.thesportsdb.com/documentation)) | Free key `123`, 30 requests/min, a 429 on excess. Premium is $9/month for 100 requests/min, V2 and livescores. | Teams, with alternate names and keywords (Eagles: `PHI`, "Eagles", "Birds" *(checked)*), plus logos and colours. TV endpoints on the free key are nearly empty (`lookuptv` returned null). | Public API with terms | **Use it for team aliases and logos, fetched once a week.** Not for broadcasts. |
| balldontlie, API-Sports | balldontlie: about 5 requests/min free. API-Sports: 100 requests/day free ([NFL guide](https://www.api-football.com/news/post/how-to-get-started-with-api-nfl-the-complete-beginners-guide)). *(reported)* | Games and scores, no US broadcast networks | Commercial | Not needed. Keep as a fallback if ESPN breaks. |
| **The playlist's own EPG** plus EPGSHARE01 `US_SPORTS1` (already synced, `epg/PublicEpg.kt`) | Already local | Programme titles and descriptions on the sports channels | Already in use | **This is how linear channels get matched** (see 3b). |
| **Boxing** | - | **No source found.** ESPN has no boxing scoreboard (`sports/boxing/boxing` returns 400 *(checked)*). The TSDB boxing league search returned null. | - | For now, boxing comes only from EPG titles and PPV event-channel names (3b, 3c). |

## 3. Matching engine

The goal: for each game, a ranked list of `(playlistId, streamId)` sources, with nothing loaded
into memory beyond today's handful of games. There are three independent ways to find a source, and
their results are merged into one ranking.

**Normalisation** is shared by all three and applied to channel names and network names alike:
1. Apply Unicode NFKC and lowercase. NFKC folds stylised letters such as `ʜᴅ` and `ᴴᴰ` back to plain ones.
2. Strip a leading country or provider tag (`^[a-z]{2,4}\s*[|:•\-]\s*`, e.g. `US|`, `USA:`) and any bracketed text.
3. Drop quality and technical tokens: `hd fhd uhd sd 4k 8k hevc h265 50fps 60fps raw`.
4. Drop backup suffixes: `backup alt b 2` as a trailing token. Record the quality tier first, because it is used for ranking.
5. Replace `&` with `and`, collapse everything that isn't a letter or digit into single spaces, and trim.

Example: "US| NBC SPORTS PHILADELPHIA ᴴᴰ" becomes `nbc sports philadelphia`, with tier HD.
Separator pseudo-channels (`#####`) are already dropped at import.

**3a. Network to channel (linear networks).** A curated alias dictionary ships as an asset, about
300 entries:
`canonical key -> {aliases[], kind: national|rsn|affiliate, market?}`.
- National example: `espn` = "espn", "espn usa".
- RSN example: `nbcsp` = "nbc sports philadelphia", "nbcs philadelphia", "nbc sports phil", "nbcsp", "nbcs philly", with market PHI.
- Every network-name spelling the leagues use ("NFL Net", "NBC Sports Phil", "NBCSP") appears as an alias too, so league data and channel names meet at the same canonical key.

Sunday NFL games on Fox and CBS, and NBC and ABC games, air on the local station. The **home
market** setting (Q1) resolves a broadcast such as "FOX" to that market's affiliate. For
Philadelphia that means "fox 29", "wtxf", "cbs 3", "kyw", "nbc 10", "wcau", "6abc" and "wpvi". If
no affiliate is found, it falls back to any "fox" channel, ranked lower. Streaming-only broadcasts
(Prime Video, Peacock, ESPN+, Paramount+, Netflix) have no linear channel, so they rely on 3c.

Matching happens after each playlist import or sync, in a WorkManager job. The job runs a paged SQL
cursor over `channels` (name and group only) and normalises each name, then looks it up in a hash
map. Any hit is written to a new small table,
`sports_channel_key(playlistId, streamId, key, tier)`, indexed on `key`.
- Expected size: a few thousand rows even for a 60K-channel playlist.
- Expected cost: a single pass with one hash lookup per row, so seconds of CPU, run off the UI.
- At view time, finding a game's sources is `WHERE key IN (…)`, which is effectively instant.

**3b. EPG title match (linear channels showing a specific game).** For channels already in
`sports_channel_key`, and for channels in groups whose name matches `sport|nfl|nba|mlb|nhl|ppv|ufc|
espn|fox sports`, look for programmes that overlap the game window (start -30 min to start +4h).
A programme matches when its title or description contains tokens for **both** teams: city, name,
abbreviation, or a TSDB alternate name. "Eagles at Cowboys" and "NFL Football: Philadelphia vs
Dallas" both qualify.
- This runs in `EpgSyncWorker` after parsing, limited to the next 7 days. It uses a single HashSet
  of team tokens across about 150 teams.
- Hits go into `sports_epg_hit(gameId, playlistId, streamId)`.
- `epg_programs` has no index on start time and no category column. Two small additions would help: an index on `startTime`, and parsing XMLTV `<category>` in `XmlTvParser` (it reads only `title` and `desc` today). The category lets the job skip non-sports programmes cheaply.

**3c. Event and PPV channel names (Teamarr-style).** These are channels whose name *is* the game,
e.g. "NFL 03: Eagles vs Cowboys 1:00 PM ET", "UFC 334: Gane vs Hokit" or "PPV 2 | Boxing: …".
Regex extractors pull out the two sides, plus a date and time if present, and match them to
schedule games by team tokens or fighter names. A date or time must be in the game window when one
is present. Event channels are often only live during the event, so the source list must tolerate
dead ones (see fallback below).

**Confidence and ranking.** The score for one source is the highest of its layers, plus bonuses:

| Evidence | Score |
|---|---|
| Event-channel name matches both sides and the time | 0.90 |
| EPG programme names both teams in the window | 0.85 |
| National network the leagues list for this game | 0.80 |
| RSN or affiliate in the user's market | 0.75 |
| Network matched outside the user's market | 0.50 |
| Fallback affiliate (any "fox") | 0.40 |

Bonuses:
- +0.05 when two layers agree.
- +0.03 when the channel is a favourite (the Channels DVR lesson).
- A small bump for a higher quality tier (FHD over SD).
- Ties go to recently working sources (`recent_channels`).

Display:
- At 0.6 and above, the source shows directly on the card.
- Below 0.6, it appears only in "More sources", marked "maybe".
- Manual corrections ("This is the game" or "Not this game") persist per playlist and channel and override the score, as Teamarr does.

**Playback fallback.** OK plays the top source. If the existing stall watchdog fires, or the stream
returns an HTTP 456 or 884 or a 404, RedSurf **stops the old stream first** (`max_connections: 1`,
`IPTV_DOMAIN_KNOWLEDGE.md` §10). It then tries the next source and shows a "Source 2 of 4" chip. A
failed source drops in rank for that game only.

**Caching and fetch budget.** Two more small Room tables:
- `sports_game(id, league, start, status, home, away, scores, updatedAt)`
- `sports_broadcast(gameId, network, market, type)`

ESPN is fetched for each league over a date range: today plus 6 days, which is 5 calls a refresh,
about 0.5-1 MB in total. The responses are stream-parsed with `JsonReader` per house rule. Refresh
lifetimes follow the Teamarr tiers. Live scores refresh every 60s, and only while the Sports pill is
on screen. No background polling. Teams and logos come from TSDB once a week. Logos go through the
existing bounded image cache.

**Expected memory cost:** a few hundred game rows and the visible cards. That is negligible next to
the guide, so it should fit the Chromecast's roughly 449 MB of free memory comfortably. This must be
**measured** on the device before anyone claims it.

## 4. Three D-pad layouts

All three use Crest:
- Square cards and rows, 0dp radius. Pills only in chrome.
- Focus is a solid red block, sliding at 120ms, no scale.
- Barlow Condensed SemiBold for scores, clocks and caps labels, with tabular figures. Barlow Semi Condensed for team names.
- Red only for focus, the LIVE tag and the game progress bar.

Team colours appear only inside the logos, so they never compete with red. The Sports pill is a
busy screen, so it doesn't get the Surf grain. One exception: Surf may sit behind an empty state
such as "No games today".

**A - Scoreboard rows (Apple TV pattern).**
- Layout: a hero band (152dp) for the focused game: both teams, the clock or score, "on NBC Sports Phil + 3 more", and the crescent cut on the right edge. Below it are horizontal rows: **My Teams**, **Live now**, **Today**, one row per league, and **Networks**, which holds quick-link tiles for ESPN, NFL Network, FS1, NBCSP and the user's local affiliates.
- D-pad: UP and DOWN move between rows, LEFT and RIGHT along a row. OK plays the game. Long-press OK, or RIGHT on the hero, opens the sources list.
- Fit: the most "app-like" and the best on the TV for discovery. It is also the most new code, since horizontal card rows are a new component and need their own frame-time budget.

**B - Sports guide (TiviMate and Crest-guide pattern, recommended for the MVP).**
- Layout: a left rail with My Teams, Live now, NFL, NBA, MLB, NHL, UFC, Boxing and Networks. On the right, a dense 38dp list of games sorted by time: start time, `PHI @ DAL`, the score or a LIVE tag, and the best source's channel name and logo. Up top is the existing hero band, previewing the focused game's top source exactly as Live TV previews a channel.
- D-pad: OK previews, and a second OK goes fullscreen (Preview-on-OK parity). RIGHT on a row opens the sources list.
- Fit: reuses the Live TV list, hero, focus memory and `hiddenButComposed` rules. That is the least new UI and the safest for performance, and it is consistent with the rest of the app.

**C - Team spotlight.**
- Layout: the whole top half belongs to the favourite team. The Eagles' next or current game shows with a countdown, "Where to watch" (the source list itself as focusable rows), and the last result, with a spoiler switch (Q2). Underneath are a slim Live now row and the Networks row.
- Fit: the most personal, and it matches the user's own example. On a day without an Eagles game it is mostly empty, so it works better as **A's or B's first section** than as the whole screen.

**Recommendation:** build B for the MVP, with C's spotlight as B's "My Teams" rail entry and as the
top row. Revisit A once Lite and Full effect tiers exist. Show the user mock-ups of all three first
(standing nav-feedback practice).

## 5. Open questions for the next interview round

1. **Home market:** Philadelphia for local affiliates and RSNs? Which favourite teams besides the Eagles (Phillies, Sixers, Flyers)?
2. **Scores:** show them by default, or hide them to avoid spoilers, with a toggle?
3. **Streaming-only games** (Prime, Peacock, ESPN+, Netflix): show the card with "matched channels" when the provider carries an event channel, and "Not in your playlists" when it doesn't, or hide the card?
4. **Data-source risk:** OK to use ESPN's undocumented API (free, rich, could break or be against its terms), or pay $9/month for TheSportsDB premium as the backbone? A middle path: ESPN primary, NHL and MLB official APIs as a cross-check, and boxing from EPG only.
5. **Scope:** college football or basketball, MLS, F1 or golf later, or strictly NFL, NBA, MLB, NHL, UFC and boxing?
6. **Reminders:** an in-app toast ("Eagles kick off in 10 min - watch") with one-press tune? There would be no background service, so it fires only while RedSurf is open.
7. **Home integration:** a "My Teams" card on Home, and later an Android TV launcher "Play Next" entry?
8. **Cloud Sync:** should favourite teams and manual match corrections ride on the account document (`CLOUD_SYNC.md`)?

## 6. Phased build (MVP first)

- **S0 - spike, no UI (one short session).**
  - Build the normaliser, a seed alias dictionary (national networks, the Philadelphia RSN and affiliates) and an ESPN scoreboard parser, with unit tests.
  - Measure on the device: the match rate against the user's real playlists, the match job's run time and the memory it uses.
  - Exit criteria: on a typical Sunday or weeknight, at least 80% of today's nationally televised games get a source.
- **S1 - MVP.**
  - A Sports pill in `NavDestination`, using layout B.
  - Today plus 6 days for NFL, NBA, MLB and NHL from ESPN, with the 3a network matching only.
  - One card per game with ranked sources. OK plays the top source with fallback to the next, and RIGHT opens the list.
  - My Teams (the Eagles) pinned at the top, and a Networks rail entry.
  - Focus and state discipline per `FOCUS_MODEL.md`: the last rail entry and game are restored on return.
- **S2 - better matching.**
  - EPG title matching (3b), including the `startTime` index and `<category>` parsing.
  - Event-channel parsing (3c).
  - UFC from ESPN. Boxing from EPG and PPV names.
  - Confidence scores and "maybe" sources. NHL and MLB official networks merged into the alias lookup.
- **S3 - polish.**
  - Manual "This is or isn't the game" corrections.
  - Spoiler toggle, reminders toast and the Home card.
  - More favourites. Cloud Sync of teams.
  - Layout A or C variants if the user prefers them after the mock-ups.

Performance gate before every phase: Sports-screen jank must be no worse than the guide's
(`PERFORMANCE_SALVAGE.md`), measured on the Chromecast.
