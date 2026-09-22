# Outstanding testing — resume here

**Written 2026-09-21, end of session, updated 2026-09-22 (Sprint 2 closed, see section H).**
Everything the user hasn't yet put real eyes/remote on, grouped by build, newest first. The user
drives the next session from this list, giving input on fixes per item. Device is on **v0.37.13**
(versionCode 135) after Sprint 2's real release. Playlists on the device as of 2026-09-22: "Random
Strong", "Random Score", and "Faraz Strong" (all Xtream, all three confirmed intact via
`epgBackfill` logcat lines at the start and end of the Sprint 2 session).

**New test playlist, 2026-09-22 - "Faraz Strong"** (`comepitv.online`, credentials saved at
`~/.redsurf/test-playlist-2.url`, same format/location as the original `test-playlist.url`,
verified live against the real API before saving - `max_connections: 1`, same one-stream-at-a-time
constraint as every other test source). **This is the one with real, populated EPG coverage** -
the user found real programme data under it (US| FOX, US| CW, likely NBC/CBS) on the redesigned
grid - it's the anchor playlist for the rest of this session's Teleport Menu re-testing (D-section
below) and the first place to look for a category with listings on any future EPG check.

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

## G. Performance — Sprint 1 CLOSED, 2026-09-22 (see `SEQUENCING.md` for full detail)

21. **Fixed, verified on device via logcat:** leaving Live TV and returning no longer re-runs the
    categories query, the grid's channel/EPG query, or the scroll/focus-claim retry - all were
    being torn down and rebuilt on every round trip before this fix (`SEQUENCING.md` Sprint 1).
    Worth a fresh real-world check: does Home→Live TV→Home→Live TV feel fast now?
22. **Fixed and verified with real before/after numbers, real release v0.37.8.** The exact
    query originally measured at 6893ms is now **361ms** on a clean cold launch (19x); the
    specific statement Opus's consult identified as a live contention victim,
    `getLiveGroupCounts()`, went from 3286ms to **254ms** (13x) and is the only statement over
    50ms anywhere in a 20-second post-launch window. Real cause was connection-pool/executor
    contention from 3 concurrent EPG syncs plus one genuinely unindexed full-table-scan query
    (not a query-plan problem on the originally-measured query itself, and not the journal-mode
    lock theory first suspected - `dumpsys dbinfo` confirmed WAL was already active). Flat memory
    at the real ~140K-channel scale re-confirmed (~131-136MB PSS, still inside the original
    112.8-145.7MB range from `PHASE_1.md` 1.6). **Does cold launch itself feel meaningfully
    faster now, not just the numbers?**
23. **New, unverified - channel-zap sluggishness check was inconclusive, not resolved.** Four
    UP presses in fullscreen (confirmed playing, real audio focus) produced no `zap dir=` log
    line at all - not root-caused this pass, a plausible but unconfirmed theory is that repeated
    cold-launches for the performance measurements above left the player in a
    `Controls`/picker overlay state rather than plain fullscreen before the zap attempt started.
    **Genuinely open: does channel-to-channel zapping work and feel fast, starting fresh
    (not right after a bunch of other testing)?**

## H. Sprint 2 — CLOSED, 2026-09-22, real release v0.37.13 (see `SEQUENCING.md` for full detail)

All 9 items fixed and device-verified this session except as noted. Full per-item account,
including two bugs found and fixed *during* verification (not just at write-time), is in
`SEQUENCING.md`'s Sprint 2 section - summary here for the user's own quick pass:

24. **Categories UP boundary guard (items 1/8) - fixed, in-list behavior thoroughly verified, the
    true-top escape-to-NavStrip specifically NOT re-verified after its own fix.** The in-list
    "scroll up one row" behavior was hammered hard (dozens of presses across a real ~500+-row
    combined category list) with zero premature escapes. The escape-to-NavStrip case was found
    broken on the first attempt (stuck at row 0, confirmed via `uiautomator`), fixed with an
    explicit callback reusing the same `navPillFocusRequesters` mechanism the long-press-Back
    feature already proves works - but this device's category list turned out too large (several
    hundred rows) to brute-force back to the true boundary a second time to re-check the fix.
    **Worth a 2-minute check next session:** hold UP long enough to genuinely reach the top of
    Categories, confirm it lands on the Live TV nav-strip pill.
25. **Grid entry position (item 2) - fixed and verified.** Every category switch now shows "now"
    at the left edge of the grid, never a stale scrolled-away position from a previous category.
26. **Preview reconnect (item 3) - fixed and compile/structure-verified, the actual reconnect
    behavior not exercised live.** Couldn't safely simulate a real network drop (this device's
    `adb` connection is itself over the same wifi the fix would need to interrupt). Also: this
    device's "Preview channel on select" setting is currently **off** (a pre-existing setting, not
    a bug) - OK jumps straight to fullscreen instead of the two-step preview, so preview itself
    wasn't exercised at all this pass. **Worth checking next session:** turn "Preview channel on
    select" on under Settings → Playback, then either tune to a channel known to be flaky or pull
    the network briefly (from a different control path than this device's own wifi-connected adb)
    to watch the reconnect loop actually fire.
27. **Categories-scroll-steals-grid-focus (item 4) - fixed and verified.** 8+ consecutive DOWN
    presses through Categories never once pulled focus into the grid.
28. **Cold-launch-then-Back focus restore (items 5/6) - fixed and verified, after a real bug found
    mid-verification.** First live attempt failed on a genuinely cold DB cache (group query
    measured 5.23s, longer than the original 3s retry budget) - focus fell back to Categories
    instead of the resumed channel. Fixed by widening the retry to 6s (matching an existing,
    already-proven ceiling elsewhere in this same file). Re-verified twice more on fresh
    `force-stop`+relaunch cycles, both landing correctly on the exact resumed channel's grid row.
29. **Three effects racing on one `gridFocus` (item 7) - fixed and verified.** No conflicting-focus
    races observed across the whole session.
30. **Settings persists across destination switches (item 9) - fixed and verified.** Category
    selection survives a Home→Settings round trip; an in-progress "confirm remove playlist" dialog
    was even observed correctly persisting (not just focus - real state discipline).

**New, found this session, NOT fixed (out of Sprint 2's scope) - a real crash:**
`SettingsScreen`'s Playlists pane force-quits the app (`FATAL EXCEPTION`,
`IllegalStateException: Expected BringIntoViewRequester to not be used before parents are placed`)
after cancelling a remove-playlist confirmation and then pressing UP a few times. Confirmed not
caused by anything in Sprint 2's 6 changed files (no scroll/`BringIntoView` call anywhere in
`SettingsScreen.kt`) - looks like a Compose Foundation internal timing race triggered by the
confirm panel's rows disappearing from the list right as rapid UP presses request bring-into-view
before the shrunk list finishes re-laying-out. No data lost either time it fired. **Needs a real
debugging session, not a guess-and-check patch** - next person should try to find the minimal
repro (does it need the exact "cancel then UP" sequence, or does any rapid input right after this
pane's row count changes trigger it?).

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
- **Catch-up/timeshift (new, 2026-09-21/22)** - real Xtream API concept (`tv_archive: 1` +
  `tv_archive_duration` on `get_live_streams`, not yet stored). User-confirmed priority: TiviMate
  parity is priority A for this whole EPG effort. Icon confirmed via two real screenshots the user
  provided, saved at `docs/vision/references/tivimate/TiviMate_CatchupIconExample.jpg` and
  `...Example2.jpg`: a small white circular-counterclockwise-arrow-with-a-clock-hand ("history")
  glyph, **inline in the channel row right after the channel name** (not per-programme-cell, not
  near the logo) - a per-channel badge (some channels in the reference have it, most don't), not
  scoped to whether a specific past programme is still inside the archive window. Own brief when
  scoped, not part of the current grid work.
- **Currently-playing-channel indicator (was open, now answered by the same reference images)** -
  TiviMate uses a small **blue play triangle (▶)**, inline in the channel row, immediately before
  the catch-up icon if both apply (`name → ▶ → history-icon → grid`) - not a whole-row colour
  change. Row order confirmed: channel name, then play triangle (if this is the tuned channel),
  then catch-up glyph (if archive-enabled), then the timeline starts. Design both together with
  the double-row feature (F above) since double-row is what gives these icons real room.
