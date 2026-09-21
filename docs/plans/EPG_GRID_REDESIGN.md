# EPG Grid Redesign — "The Lattice"

**Origin:** direct user feedback, 2026-09-20, after seeing the merged Live TV/Guide screen live:
"clumsy, bloated, incomplete, and just generally unappealing," calling out programme-block
rendering, the focus cursor, lack of time-slot grouping, and "only first program block shows."
Diagnosed via a real screenshot (`docs/plans/HARDWARE.md`'s screencap-broken claim was wrong - a
shell-quoting bug, corrected same day) and sent to Opus for full design consultation, 2026-09-20.
**Full consultation transcript is the source of truth for exact values/reasoning below** - this
brief distills it into a buildable status board; if anything here seems under-specified, the
original consultation (this session's transcript) has the precise draw-op sequences.

## Root cause - one bug, four complaints

The grid's cells never matched the time scale the header promises. `ProgrammeCell`'s width used
`coerceAtLeast(90.dp)` (inflating anything under 30min) plus `TvLazyRow`'s `spacedBy(4.dp)` (real
gaps between programmes collapsing toward zero) - so cells drift out of sync with the header's
true `3.dp`/minute scale as they go. The empty/"no data" cell had **no time-mapping at all** -
`widthIn(min = 240.dp)` inside a `Row`, so it just stretches to fill - which is the literal cause
of "only first program block shows": every channel with no EPG renders as one undifferentiated
block ignoring the real time columns above it.

Also found, all confirmed by reading the code directly: the "now" line the last sprint claimed to
ship is drawn **first**, under every row's opaque cell background - it's not missing, it's buried
(visible only in the 6dp gaps between rows in the real screenshot). `now` itself is captured once
via `remember { System.currentTimeMillis() }` and never updates - it and the hero's "N min
remaining" both silently go stale the longer the screen stays open.

## The real vertical-budget problem, measured

Usable canvas after safe area: 864×476dp. Current layout - nav-strip 48dp, `AppShell` spacer 20dp,
hero band 210dp, a redundant "Guide" title row 34dp, time header 26dp - leaves **138dp for the
scrollable grid**, and at the current 70dp row pitch that's **1.97 visible channels**. TiviMate's
own reference shows eight. This is the actual "bloated" complaint, quantified, not a taste
disagreement. Fix is mostly deleting/shrinking chrome, not the grid itself: dropping the
redundant title row, a proper density scale, and a hero band that collapses to a 56dp info bar
when nothing's focused gets the grid to **236dp / 5.9 rows** with nav visible, **304dp / 7.6 rows**
with it hidden (more once the hero also auto-collapses while actively browsing).

## Decisions - build now, one coherent pass (each item unblocks the next)

1. **Slot list, not raw programme list.** Per channel row, build a list that provably tiles the
   window exactly (`Aired`/`Gap` sealed type, cursor walks the window filling real programmes and
   the true gaps between them) - this alone fixes the header/cell mismatch and the "one block"
   bug, and makes the header truthful. Pure Kotlin, unit-testable without a device.
2. **`RedSurfDensity` token object** (new file next to `Color.kt`/`Type.kt`) - named constants for
   row height (40dp, was 64), row gap (0dp - separation becomes a drawn hairline), label column
   width (150dp), logo size (26dp), cell padding/radius, hero heights (136dp expanded / 56dp
   collapsed), categories column width (200dp fixed, was an unbounded weight). Foundational -
   intended to extend beyond this screen later, not a one-screen patch.
3. **Responsive time scale.** Replace the fixed `PxPerMinute = 3.dp` with `timelineWidth /
   WINDOW_MINUTES` measured via `BoxWithConstraints`, and **snap the window start to the previous
   :00/:30** rather than to the literal current millisecond - gives clean header labels, a stable
   grid across recompositions, and somewhere for the now-line to actually move within.
4. **Delete the redundant "Guide" title row**; hero band gains a collapsed 56dp state (mark +
   selected category/count + hint text + a live clock) shown whenever nothing's focused or the
   user has been actively browsing the grid for >1.2s, animated via a single `animateDpAsState`.
5. **"The Lattice"** - programme cells stop being individual `Surface`s with shadow/border/scale.
   Each row draws its own structure once via `Modifier.drawBehind` (row fill, a bottom hairline, a
   "spine" separating the channel column from the timeline, half-hour/hour tick lines), then each
   slot draws one short inset vertical tick at its own start edge (7dp inset top/bottom, not
   full-height - this is what keeps it reading as a ribbon along a timeline instead of a wall of
   boxes). The currently-airing slot is the one exception: filled `Accent @ 0.20`, a 3dp leading
   bar (same left-bar idiom `GroupsColumn` already uses), title in `TextPrimary`. Cheaper than the
   current per-cell `Surface`+`Glow`+`Scale` - no shadow pass, no per-cell render node.
6. **Gap-slot treatment.** An empty span draws nothing but the lattice ticks crossing it plus one
   dashed centre-line; a "No listings" label only appears if the gap is ≥45min and is that row's
   first slot. Gaps stay real, focusable targets (needed to reach and tune a no-EPG channel).
7. **`RedSurfFocus.gridCell()`** - a new sibling variant (not a change to the existing
   `RedSurfFocus`, which stays exactly as every other picker in the app uses it): no scale, no
   glow, no border/fill from the shared system. The grid draws its own cursor instead - a
   full-row-width background tint so the channel identity stays visible even when the cursor is
   hours to the right, a 1.5dp `Accent` outline sized for a dense cell (2dp read as "thick and
   blunt" at this size), and the same 3dp leading bar idiom. Plus **"The Wash"** - a one-shot
   180ms left-to-right gradient sweep on focus arrival, gone at rest (`TELEPORT_MENU.md`'s own
   "nothing animates at rest" rule, applied) - the one deliberate piece of real motion.
8. **A real "now" line** - `now` becomes a ticking `produceState` (30s granularity), drawn in one
   overlay pass **on top of** the rows this time (`drawWithContent`, clipped to the timeline
   viewport), a 2dp `Accent` line with a small playhead cap on the header baseline and a soft
   28dp trailing gradient "wake" for legibility - 3 draw ops for the whole screen. Requires every
   row to share one horizontal scroll position to be correct for all rows at once (item 9).
9. **Shared horizontal scroll.** Since item 1 makes every row's total content width identical by
   construction, wrap the whole grid in one shared `horizontalScroll` state rather than N
   independent per-row `TvLazyRow`s; pin the channel-label block per row via a counter-translating
   `graphicsLayer`. Compose's focus `bringIntoView` should drive this automatically - **flagged
   for real on-device verification**, not assumed, given this project's own history with
   `tv-foundation` focus quirks.
10. **Channel row fixes** - strip the redundant category-prefix from channel names using the same
    delimiter-parsing Teleport Menu's Root Category already ships (pure win, done in the data
    mapping, zero render cost); right-align channel numbers; apply the app's own established
    focused-row `basicMarquee()` convention (already used in `GroupsColumn`/`ChannelsColumn`) for
    anything still too long; invert the channel-name/number color weighting (name should be
    `TextPrimary`, not secondary-grey as it is today).

## Explicitly deferred, not part of this pass

- **A hand-written time-preserving focus cursor** (UP/DOWN should land on the same time-of-day in
  the new row, not whatever slot index that row's own independent state happened to hold) - real
  and correct, but a hand-written key router over a 2D grid is its own follow-up, not something to
  ride along with a large visual pass per this project's own history.
- **A fully-owned custom scroll/layout** (replacing item 9's shared-`horizontalScroll` approach
  with a hand-rolled measure/placement pass) - the cheaper, more controllable long-term answer,
  but only worth it once the time-preserving cursor above is also being built (they share the same
  underlying model) - not this pass.
- A TiviMate-literal filled-every-cell alternative treatment - build as a debug toggle only if the
  user wants a side-by-side comparison; don't ship two visual languages at once.
- Vertical safe-area trim (32dp → ~27dp) - a real few dp, but an app-wide change that shouldn't
  ride in under an EPG-specific redesign.
- `ProgrammeInfoCard`'s own restyle (TiviMate's reference shows a small corner card, not a
  centered full-scrim modal) - real, but not what was actually complained about; small follow-up.
- Nav-strip auto-hide - already confirmed wanted, already exists as a grey Settings row; this
  redesign is built to be correct whether it's on or off, doesn't depend on it landing first.

## Status board

| # | Task | State |
|---|---|---|
| G.1 | Slot-list model (`Aired`/`Gap`, tiles the window exactly) | ✅ built, 8 unit tests pass (empty → one Gap, holes → Gaps, window-edge clipping, overlap clamping, unsorted input, exact-sum invariant) |
| G.2 | `RedSurfDensity` tokens + responsive `pxPerMinute` + snapped window | ✅ built & verified on device - ruler shows round :00/:30 labels, re-snapped itself from 3:30 to 4:00 PM during the session (`08-state.png`) |
| G.3 | Delete redundant title row; hero collapsed/expanded states | ✅ built & verified on device - collapsed 56dp bar with category/count/hint/live clock (`03-categories-up.png`), expanded state on cold-launch restore (`02-loaded.png`) |
| G.4 | The Lattice (row structure + inset tick cells + current-slot fill) | ✅ built; row fill/hairlines/spine/half-hour ticks verified on device (`05-cursor-row2-marquee.png`); **aired-cell tick and current-slot fill not eyes-verified** - no category reached during the sweep had matching EPG (see below) |
| G.5 | Gap-slot visual treatment | ✅ built & verified on device - dashed spans with the lattice ticks running through, "No listings" only on the first slot, dash clears the caption (`08-state.png`) |
| G.6 | `RedSurfFocus.gridCell()` + row-band cursor + leading bar + The Wash | ✅ built; row band, 1.5dp outline, fill and leading bar verified on device (`05-cursor-row2-marquee.png`); **The Wash not eyes-verified** - 180ms is under screencap latency |
| G.7 | Ticking now-line, drawn on top, with the trailing wake | ✅ built & verified on device - line + playhead cap + wake, on top of rows, moved with wall-clock across two half-hour re-snaps (`03`, `07`, `08`) |
| G.8 | Shared horizontal scroll (verify `bringIntoView` behavior live) | ⚠️ built (per-row `horizontalScroll(sharedScroll)`, see the deviation note); **LEFT/RIGHT across multiple slots not exercised live** - every category reached during the sweep was a single full-window gap, so there was nothing to scroll to; needs a category with listings |
| G.9 | Channel row: prefix-strip, right-aligned number, marquee, color fix | ✅ built; right-aligned numbers, name in primary, marquee on the cursor row all verified on device (`05-cursor-row2-marquee.png`); prefix-strip covered by unit test, no matching category name seen live |
| **A** | **Checkpoint - user compares the real screen against the reference image directly** | ⬜ ready for the user |

## What actually happened (2026-09-21, Opus)

Built G.1-G.9 as one pass, exactly to the consultation's numbers except where real data on the
real device argued otherwise - each logged below. Real signed release **v0.37.0** (versionCode
122, matching the next CI run number), installed in place three times over the session with no
uninstall and no playlist loss. Compile clean first attempt; 23/23 unit tests (15 existing + 8 new).

**Row count, the acceptance that matters most (#4):** 6 full rows with the nav-strip visible and
the hero expanded (`02-loaded.png`), **8 rows** with the hero collapsed (`03-categories-up.png`,
`08-state.png`) - up from 1.97 measured before this pass.

**Deviations from the consultation, all deliberate:**
- `deviated:` label column 180dp, not 150. On real provider names ("ABC 28 (KXXX)", "UFC 00 :
  CRYPTO.COM ARENA…") the 74dp name slot ellipsized nearly every row on the first screenshot;
  106dp holds ~18 characters, marquee covers the rest. Six 30-minute columns still fit.
- `deviated:` shared scroll is per-row `horizontalScroll(sharedScroll)` on each timeline
  portion, not one scroll on the whole column with counter-translated labels. Reason: under the
  single-container form the pinned label sits over the leftmost 150dp of scrolled content, so
  `bringIntoView` would "reveal" a cell straight underneath the opaque label. Same shared state,
  same effect, no overlap, no `graphicsLayer` per row.
- `deviated:` the gap's dashed line starts 74dp in when the "No listings" caption is shown - the
  first screenshot had the dash running through the caption.
- `deviated:` hero text follows the D-pad cursor (channel + the specific slot under it), via a
  separate `cursorChannel`/`cursorSlot` rather than `focusedChannel` (which carries restore
  semantics). The first on-device pass showed the hero collapsing while the cursor moved through
  rows - the band only knew about OK'd channels. TiviMate's band follows the cursor; that's its
  whole job.
- `deviated:` one small fix outside G.1-G.9, found live: a run of provider `502`s had pushed
  the one-time EPG sync's WorkManager backoff out 4+ hours, guide empty the whole time. Cold
  launch now enqueues a fresh sync (REPLACE) for any playlist with zero EPG rows. In the end the
  device *did* have 66,807 rows for this playlist - a retry had succeeded - so the check correctly
  did nothing; the categories reached during the sweep simply had no channel-id matches.

**Verification stopped early, honestly:** partway through the sweep the launcher, then the
Nuvio app, took the foreground - launched from the launcher's own uid right after an HDMI-CEC
"input active" event, i.e. a person in the household picked up the remote. Key injection stopped
at that point. Everything marked "verified on device" above is from screenshots taken before
that; the three items marked not-verified (aired-cell rendering, LEFT/RIGHT shared scroll, the
Wash) each need a category with real listings and a free TV.

## Acceptance - machine-verifiable

1. Every row's slots sum exactly to the visible window width - no drift, no fabricated duration.
2. A channel with zero EPG data renders real time-gridded gap slots (dashed, ticked), never one
   undifferentiated block; it remains a real, reachable focus target.
3. The now-line is visibly on top of row content (not obscured) and its x-position is verifiably
   `windowStart`-relative, moving over real wall-clock time, not frozen at composition time.
4. Grid shows at minimum ~5-6 full channel rows in the viewport with the nav-strip visible, on the
   real device, at the real measured canvas size - not just "improved," a real row count check.

## Acceptance - feel/vision (user)

1. Held against the TiviMate reference image directly - does this read as the same design
   language now, not just functionally similar?
2. Does the focus cursor feel appropriately weighted for a dense grid (not blunt, not too subtle)?
3. Does the now-line read as "live," moving, anchored - not a static decoration?
4. Does the collapsed hero bar feel useful rather than like something's missing?
