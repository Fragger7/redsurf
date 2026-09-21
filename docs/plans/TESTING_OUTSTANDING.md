# Outstanding testing — resume here

**Written 2026-09-21, end of session.** Everything the user hasn't yet put real eyes/remote on,
grouped by build, newest first. The user drives the next session from this list, giving input on
fixes per item. Device is on **v0.37.2** (versionCode 124). Playlists on the device: "Random
Strong" and "Random Score" (both Xtream). Random Score's EPG synced completely; Random Strong's
EPG endpoint (`line.bestlina14.cc`) is dead (nginx 502) - user's call, not chased.

## A. The Lattice - EPG grid redesign (v0.37.x) - `EPG_GRID_REDESIGN.md`

Hold next to `docs/vision/references/tivimate/RedThemedEPGLiveTVScreen.jpg`.

1. 6-8 channel rows visible with a real two-tier time ruler above them?
2. **Nobody has seen a populated programme cell yet.** Find a category with listings (news/sports
   usually carry ids; Random Score is the playlist that synced) - cells under the right times, the
   on-air one filled red with the now-line crossing it?
3. LEFT/RIGHT across cells - does the whole grid scroll together with the ruler? (shared-scroll,
   Opus flagged it for live verification, never exercised - no reachable category had listings)
4. The Wash - a 180ms red sweep across a cell when focus lands (never seen; under screencap latency)
5. Now-line creeps right over real minutes and resets at the half-hour?
6. Cursor reads as a thin outline + red left bar on a row band, not a blunt box?
7. Hero band text follows the D-pad cursor, and slims to the 56dp clock bar while scanning?
8. Nits seen in screenshots: hero fallback initial shows "U" (from the "US|" prefix) instead of
   the stripped channel name's initial; UK/US rows show letter-fallback logos - check the Africa
   category still shows real logos (data, or a regression?).
9. **Known regression** (from the merge pass, logged in `LIVE_TV_GUIDE_MERGE.md`): returning from
   fullscreen lands the grid cursor on row 0, not the channel just watched - violates the
   state/focus rule.

## B. EPG sync + Settings → EPG (v0.37.2) - `SPRINT_LOG.md` 2026-09-21 (later)

10. Settings → EPG: press "Update EPG now" - shows "Updating…", then "Last updated" advances?
11. If no category on either playlist ever shows listings, add a test connection with a working
    EPG endpoint (user: "we can add it later").

## C. Preview-on-OK (v0.35.0) - `PREVIEW.md`

12. First OK on a focused channel previews it with audio and stays in browse; second OK on that
    same channel goes fullscreen?
13. OK on a *different* channel while one is previewing swaps immediately?
14. Settings → Playback "Preview channel on select" off → single OK goes straight to fullscreen?
15. Cold launch with auto-play on still lands directly in fullscreen? (reasoned correct, never
    re-tested via a relaunch)
16. Feel: any lag switching previews or promoting? Audio-on-preview the right call, or mute
    until fullscreen?

## D. Teleport Menu (v0.33.0) - still broken - `TELEPORT_MENU.md`

17. Only "Exit RedSurf" worked in the user's real test. Nav-Strip, Playlist Root, Root Category,
    Root Channel Group, Return to fullscreen all need a real debugging session with the user's
    remote in hand - the user maps each item to where focus should land, on a real playlist
    (Random Strong is loaded again; its category prefixes are the Root Category test data).
18. Two refinements queued: slow the portal open/close slightly; a cheap animated outline
    treatment on the panel while open ("like a selected rail", performance over aesthetics).
19. T.5 discoverability tips not built - needs the user's gut on the "held UP for how long"
    threshold before a number is picked.

## E. Older, never live-reproduced

20. The "audio plays, no picture" fix (`PlayerErrorMapper.videoFormatUnsupported()`, 2026-09-18)
    - needs a channel that actually triggers it.

## F. Decisions pending (not tests)

- **Nav-strip auto-hide on idle - scoped 2026-09-21, not built (no allowance left; user
  sequences it against the testing results above).** Estimate: ~1 hour of background build,
  ~150-250 lines. Settings side is trivial - flip the existing grey row under Appearance via the
  usual `AppPreferences` → row → `AppShell` pattern. Mechanism: an idle timer fed by `AppShell`'s
  existing root `onPreviewKeyEvent`, one animated offset on the strip; the grid already sizes
  itself from available height (the ~8→10 rows Opus measured), so it gains rows for free.
  **The part that needs real device verification, not a compile:** reveal must be
  "focus reaches for it" (UP from content's top row, Back-peel to Home, long-press-Back's nav
  jump, Teleport Menu's Nav-Strip row) - never "any key press" (it'd pop back on every cursor
  move). All those paths call `requestFocus()` on a pill, so the strip must stay *composed* while
  hidden (slid off, not removed) or every jump silently fails; and it must never hide while focus
  is already on it. Proposed defaults, unconfirmed: **default On**; one Settings row cycling
  **3s / 5s / 10s / Off** (5s default) rather than a separate toggle; **applies everywhere**, not
  just Live TV.
- App-wide density pass beyond the grid (user asked for foundational consistency, 2026-09-20) -
  `RedSurfDensity` exists now; nothing outside the grid uses it yet.
- Time-preserving UP/DOWN cursor (land on the same time-of-day in the next row) - deferred from
  the Lattice pass, its own follow-up.
- Programme info card as a small corner card (TiviMate's shape), not a centered scrim modal.
- Public-source EPG supplement (P1) + the provider-vs-supplemented cell iconography (`AGENTS.md`
  2026-09-15: cool-tone tint + non-colour cue + a legend) - both land in the same pass.
- Playlist detail/edit page (`AGENTS.md` backlog, decided 2026-09-17).
