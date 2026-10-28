# Search - draft brief (2026-10-28, unattended run)

**Status: draft, nothing built.** Written at the end of the 2026-10-28 run so the Search sprint
(roadmap item 4, after Perf Max) can start from decisions, not from zero. Decisions S1-S4 below
are the user's; everything marked *(check TiviMate)* needs the standing-rule look first
(`TIVIMATE_LEARNINGS.md` "Standing rule").

## What exists today (read, not guessed)

- The **Search pill** (`ui/shell/NavStrip.kt:48`) opens `PlaceholderScreen` - no real screen.
- `ChannelDao.searchChannels` (`db/RedSurfDatabase.kt:229`): `name LIKE '%q%' AND isHidden = 0`,
  returning an **unbounded `List`**. On a ~28K-channel playlist a 2-letter query can return
  thousands of rows into memory - breaks binding constraint 3 (memory discipline). It also scans
  every row (a leading `%` can't use an index) and ignores `streamType` (live vs VOD rows).
- `search/GlobalSearchEngine.kt`: a thin wrapper over that query, never wired up (AGENTS.md
  "Built but never wired up"). Replace it, don't build on it.
- EPG programmes are in `epg_programs` (no declared indices, on purpose - Sprint 1 M5).

## Proposed shape

1. **Index:** a Room `@Fts4` table over channel names (external content on `channels`, so no
   duplicated text), queried as a prefix match per word (`"bbc* one*"`). Prefix-per-word is what
   people type on a remote ("sky spo" -> Sky Sports). Additive migration; FTS rebuilt after each
   import (one `INSERT INTO fts(fts) VALUES('rebuild')`, off the main thread, after `awaitQuiet()`).
   Fallback if FTS4 misbehaves on API 23: bounded `LIKE` with `LIMIT`. Measure both on the TV
   before choosing (target: <=30ms per keystroke on the 28K list).
2. **Results, bounded:** `LIMIT 50` per section, Paging if "show all" is ever needed. Sections:
   **Channels** (all playlists, hidden excluded, live only), **On now** (programmes airing now
   whose title matches), **Later today**. VOD/Series join when VOD exists.
3. **Typing:** debounce ~250ms, query on the sync/IO dispatcher, cancel the previous query
   (`collectLatest`), never block a keystroke.
4. **Actions:** OK on a channel -> the same preview/fullscreen path Live TV uses (one shared
   player, PERFORMANCE_SALVAGE); OK on a programme -> tune its channel. Long-press OK -> the same
   context menu as Live TV (favourite etc.).
5. **State and focus discipline (binding):** query, results and the focused result survive
   leaving Search and coming back (hoisted to `AppShell`, like Live TV's state); Back from the
   results returns to the query field, the next Back to the Search pill (F17's peel).
6. **Performance:** results are lean rows (Crest density, one semantics node per row, cached
   text layouts - the step-4 patterns), no animation per keystroke.

## Decisions for the user

- **S1 - input:** the system TV keyboard (Gboard on this TV, with voice) **or** an in-app letter
  grid (no IME pop-up, works with the D-pad alone, more to build). *(check TiviMate: which one it
  uses, and whether results update while typing.)*
- **S2 - scope at launch:** channels only, or channels + EPG programmes (On now / Later today).
- **S3 - entry points:** the Search pill only, or also a "Search" action in the player's context
  menu and Live TV (TiviMate lists "Search" among its long-press actions, `TIVIMATE_LEARNINGS.md`
  line ~128).
- **S4 - recent searches:** keep the last ~10 queries (Room, per device; Cloud Sync could carry
  them later) or none.

## Acceptance (Chromecast, a11y on, compiled)

- Per-keystroke query <=30ms on the 28K playlist; typing never janks (gfxinfo while typing).
- Memory flat across query length (PSS before/after a 1-letter query within ~5MB).
- Leave Search and return: same query, results and focused row. Cold relaunch: per S4.
- OK on a result plays the right channel from the right playlist (test across playlists).
