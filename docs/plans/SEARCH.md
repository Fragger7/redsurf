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

## Decisions (user, 2026-10-29)

TiviMate check first (`TIVIMATE_LEARNINGS.md` "Search"): its search screen is Leanback's
`SearchBar` (system keyboard + "Speak to search"), results in sections **Channels / Programs**
(plus Movies / Shows), a **Search history** with "Remove from History" and "Clear history?", and
settings "Prefer voice search", "Show search history", "Stay on search screen when switching
channels". Whether it updates results per keystroke isn't visible in the strings - check on the TV.

- **S1 - input: the system TV keyboard, with voice.** Same as TiviMate. A mic action on the
  search field starts voice input directly.
- **S2 - scope:** grows over time - **Channels** (grouped by playlist), **Programs**, later
  **VOD**, later **Sports Hub**. Launch with Channels + Programs. Playlist grouping: a
  playlist sub-header only when matches come from more than one playlist (one playlist = flat
  list, no extra D-pad presses). Programs need their own FTS index (`epg_programs` has none) -
  measure its rebuild cost after an EPG sync before committing to it.
- **S3 - entry points:** the Search pill; a "Search" row in the Teleport menu; the remote's
  `KEYCODE_SEARCH` where a remote sends one to apps. The Chromecast remote's Assistant button
  goes to Google Assistant, not to apps, so it can't be used. Google TV's system-wide search
  (a searchable content provider) is a separate, later item.
- **S4 - recent searches: yes**, last ~10 queries in Room, per device. Same pattern as recent
  channels: a trailing "Clear" tile clears all, long-press OK on one removes it ("Removed from
  search history" toast, no menu step).

## Acceptance (Chromecast, a11y on, compiled)

- Per-keystroke query <=30ms on the 28K playlist; typing never janks (gfxinfo while typing).
- Memory flat across query length (PSS before/after a 1-letter query within ~5MB).
- Leave Search and return: same query, results and focused row. Cold relaunch: per S4.
- OK on a result plays the right channel from the right playlist (test across playlists).
