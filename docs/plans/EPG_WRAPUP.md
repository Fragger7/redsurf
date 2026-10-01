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
| Dev 2 | in progress |
| Integration | not started |

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
