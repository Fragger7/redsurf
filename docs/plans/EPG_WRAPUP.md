# EPG wrap-up — two dev sprints + one integration/regression sprint (user, 2026-09-30)

**User's instruction:** finish *all* EPG work in two long development sprints - no per-change
ADB build/test loops - then one dedicated integration + regression sprint that checks every
feature against its design and that nothing broke anything else. Replaces SEQUENCING.md's separate
Sprints 3-6 (their scoping text there is still the design reference).

**Dev-sprint rule:** compile + unit tests per change; one signed release built and installed at
the *end* of each dev sprint (smoke check only: launches, playlists intact, no crash). Real
verification happens in sprint 3. Settings rows are built alongside every togglable behavior
(standing rule).

## Dev sprint 1 — guide behavior (Live TV screen)

| # | Item | Notes |
|---|---|---|
| 1.1 | Preview hints follow the "Preview channel on select" setting | User report K1: one OK went straight to fullscreen while the screen said "Press OK to preview". |
| 1.2 | Empty / sparse rows are steppable in time | K2: a no-listings row is one window-wide cell, so RIGHT can't move. Split gaps into 30-min cells (TiviMate shows empty slots). Re-check forward scroll on listed channels. |
| 1.3 | Past in the grid | K3: window extends back in time; opens scrolled to "now"; LEFT walks into the past. Past cells dimmed. |
| 1.4 | Catch-up data | Store Xtream `tv_archive` / `tv_archive_duration` per channel (non-destructive migration); refreshed by the daily EPG sync so existing playlists get it without re-adding. |
| 1.5 | Catch-up playback | OK on a past cell of a catch-up channel plays it from the provider's timeshift URL. Can only be verified if a provider has archive. |
| 1.6 | Row icons | ▶ (blue) on the channel currently playing; catch-up (history) glyph after the name. |
| 1.7 | Double-height focused row | TiviMate option; Settings row (default on). The focused row grows to 2x and gives the name + icons room. |

## Dev sprint 2 — guide data + screen room

| # | Item | Notes |
|---|---|---|
| 2.1 | Public EPG supplement, Phase A | EPGSHARE01 as a per-channel fallback when the provider has nothing; country files picked from category prefixes; Settings toggle; measured "channels filled" count logged. |
| 2.2 | Public EPG, Phase B essentials | Per-playlist "Fallback with public sources" toggle; supplemented cells visually distinct (cool tint + dashed border, not colour alone). Full Manage Sources catalog only if time allows. |
| 2.3 | Nav-strip auto-hide | SEQUENCING.md Sprint 5 / TESTING_OUTSTANDING F: idle timeout, Settings row 3s/5s/10s/Off, reveal only when focus reaches for it. |
| 2.4 | Preview ↔ fullscreen grow/shrink | SEQUENCING.md Sprint 6. Riskiest item - last; if a persistent-surface version isn't safe on this hardware, ship the visual grow/shrink over the existing two-player handoff and say so. |

## Sprint 3 — integration + regression

A scripted device sweep of every feature on record against its design (Live TV/guide, preview,
fullscreen + Back variants, zapping, Favorites, Teleport rows, Settings navigation, EPG sync,
public supplement, auto-hide, crash battery), plus memory/launch timing vs. earlier numbers.
Failures fixed in batch, then re-swept. Ends with the user's short feel-check list.

## Status

| Sprint | State |
|---|---|
| Dev 1 | built 2026-09-30 (v0.39.0): all 7 items, 35/35 unit tests; device smoke only - real checks in Integration |
| Dev 2 | built 2026-09-30 (v0.40.0): 2.1-2.4; unit-tested; device smoke only |
| Integration | **done 2026-10-05 (v0.41.1)** except the items listed under "Still unverified" |

## Dev sprint 1 - as built (2026-09-30)

- 1.1 Hints follow the setting ("Press OK to watch" when preview-on-select is off; "Press OK to
  open fullscreen" on the channel already previewing).
- 1.2 Gaps are half-hour cells (`EpgSlots.addGaps`); "No listings" sits on the now-cell.
- 1.3 Window = 6h past + 6h ahead (`PAST_MINUTES`/`FUTURE_MINUTES`, `gridWindowStart`); opens
  scrolled to the current half-hour; entry focus lands on the cell airing now (`nowSlotIndex`).
  Past reach is capped at 6h because that's what EpgSyncWorker retains.
- 1.4 `channels.tvArchiveDays` (Room v11→v12, ALTER TABLE ADD COLUMN DEFAULT 0); parsed from
  `tv_archive`/`tv_archive_duration`; refreshed (plus `server_info.timezone`) at the start of every
  EPG sync, so existing playlists get it on their next sync.
- 1.5 `player/Catchup.kt`: OK on a past programme inside the archive plays
  `/timeshift/{user}/{pass}/{min}/{yyyy-MM-dd:HH-mm}/{id}.ts` in the panel's timezone. **Unverified
  - no test provider has advertised archive yet.** Leaving fullscreen or zapping returns to live.
- 1.6 Blue ▶ on the previewing channel's row; history glyph on catch-up channels.
- 1.7 Focused row 2x tall (two-line name, programme times) - Settings → Appearance →
  "Double-height focused row in guide", default On.

## Dev sprint 2 - as built (2026-09-30)

- 2.1 `epg/PublicEpg.kt`: EPGSHARE01 US2 + US_SPORTS1 + UK1, picked from category prefixes; stored
  once under pseudo-playlist `public:epgshare01`; matched by normalised provider EPG id or channel
  name, exact-normalised only; `channels.epgFallbackId` (Room v12→v13, additive). Runs at the end of
  each provider sync (public files refreshed at most every 20h). Grid/hero use provider listings,
  else the fallback (`ChannelRepository.programmesFor`). The **measured match count** is logged as
  `publicEpg match -> ... candidates=N matched=M` - that number is Phase A's answer. Settings → EPG
  → Sources (default "Provider + public guides"). 56MB US-locals file skipped.
- 2.2 Supplemented programmes: cool tint + **dashed** start tick (not colour alone); hero shows
  "Guide: EPGSHARE01 (public)". Not built: per-playlist toggle (needs the playlist detail page),
  Manage Sources catalog, custom XMLTV URL, priority list - Phase B proper, still on record.
- 2.3 Nav-strip auto-hide: collapses after 3/5/10s idle (default 5s, Settings → Appearance), stays
  composed/focusable, revealed only when focus reaches it.
- 2.4 Grow/shrink, **frame-based**: PixelCopy of the playing view animates between the hero box and
  full screen (320ms) while the other player starts; skipped when animations are off or a frame
  can't be grabbed. Not the true single-persistent-player version - that's an architecture reversal
  of the two-player design, logged as follow-up (needs its own design pass).

## Integration sprint - results so far (2026-09-30, v0.40.0 → v0.41.0)

**Verified on device:** Room v12/v13 migrations kept all playlists; catch-up data is real - 978
(caprichoso67) and 2,193 (bestlina14) channels carry archive; public supplement filled **2,058**
empty channels on the US playlist (US2 65,546 + US_SPORTS1 19,579 programmes) and **699** on Random
Score from the UK file; nav-strip auto-hide; double-height focused row; RIGHT walks forward through
half-hour cells and LEFT into the past on no-listings rows; "Press OK to watch" hint (confirmed
the device's preview-on-select is **off** - the user's one-OK report).

**Found and fixed during the sweep:**
1. Guide opened with "now" at the right edge - scroll-to-now ran before rows measured; now waits
   for layout and re-applies on grid entry. Verified.
2. "✯USA✯ …" categories never fetched US public files (prefix reader stopped at "✯"). Unit-tested.
3. A provider with no XMLTV endpoint (bestlina14, HTTP 404) retried forever and never got the
   public supplement (which only ran after provider success). 404 now = supplement + done; other
   failures supplement then retry. Built, not yet observed on device.

**Not yet checked on device (resume here):** a supplemented channel's tint/dashed tick/hero label;
catch-up playback on an archive channel (caprichoso67 / bestlina14 have them); grow/shrink
transition; the new Teleport row "Last Channel Group (Current Provider)" (user request,
2026-10-05); regression battery (Favorites, Teleport rows, Settings nav, crash battery, zapping,
Back variants); memory/launch timing.

## Integration sprint - completed 2026-10-05 (v0.41.1)

**Verified on device (screenshots + logs):** public supplement now fills real broadcast channels -
News Network shows CNN, CNBC, CW, Cheddar, 7NEWS, THE FIRST TV (the latter visibly marked: cool tint +
dashed blue ticks); match counts after fixes: caprichoso67 2,050, bestlina14 1,903 (provider has no
XMLTV), Random Score 1,614. Catch-up glyph on archive channels. Date label shows today. Sticky titles
(a programme that began off-screen keeps its title at the visible edge). Time-preserving UP/DOWN.
Back and LEFT from the grid reach Categories in one press. Vertical list resets to the top on a new
category. Cold-launch resume. Teleport Nav-Strip and the new "Last Channel Group (Current
Provider)" row (landed on WORLD LIVE SPORTS, index 2,198). Teleport setting restored to On.

**Found and fixed in this pass:**
1. LEFT walked back through 12 empty half-hours before reaching Categories; Back never reached them
   at all (it was a LEFT move). Back is now an explicit Categories landing; LEFT leaves for
   Categories when the row has nothing earlier (no past listings, no catch-up).
2. UP/DOWN landed on a spatially-nearest (often past) cell - now time-preserving, crash-safe.
3. RIGHT pressed while a new category was still loading entered the old rows, which were then
   replaced - focus lost entirely (dead D-pad). RIGHT is held until the category loads (cancelled by
   any other key), and a grid that loses its rows while focused reclaims focus.
4. Public matcher counted *stale* provider rows (ended days ago) as coverage - now only current ones.
5. All three syncs downloaded the same public files at once - now one at a time.
6. Header date showed the window start's date (yesterday after midnight).
7. Previous category's vertical scroll carried over.
8. Catch-up: the plain `.ts` timeshift URL returned non-video. Playback now probes `.ts`, `.m3u8`,
   `timeshift.php` × panel/UTC/device time zones and plays the first real media; otherwise shows
   "Catch-up isn't available for this programme". **On bestlina14 every combination returns HTTP 200
   with an empty body** - the panel flags 2,193 channels with archive but serves none; catch-up
   playback is therefore still unverified end to end.

**Still unverified:** catch-up actually playing (needs a provider that serves archive); the
grow/shrink transition (needs "Preview channel on select" on - it's off on this TV, left as the
user set it); Favorites/crash battery re-run after this sprint's changes; memory/launch timing.
