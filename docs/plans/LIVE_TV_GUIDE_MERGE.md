# Live TV/Guide — the real merge (retire the toggle, TiviMate-parity layout)

**Origin:** direct user feedback, 2026-09-20, after seeing Phase 3 P0's build live: "I still see
the Live TV and Guide being two different sections," and the Guide grid itself "looks so, so, so
far off from the target state" of `docs/vision/references/tivimate/RedThemedEPGLiveTVScreen.jpg`.
Confirmed both complaints directly against the code and the reference image before writing this
(see "What's actually wrong" below) - not a misunderstanding on the user's part, a real gap in how
P0 was scoped. **End state described explicitly by the user this session - this brief records it,
it does not re-derive it:**

> "the end state of our appearance needs to merge the Live TV and EPG pills into one like TiviMate.
> As such, the preview pane must also move to the top, under the RedSurf nav-strip, but
> information displayed in that preview pane must have as much parity with TiviMate as possible.
> The uniqueness here is just the RedSurf nav-strip being at the top, instead of TiviMate's nav
> strip, hidden in the left pane."

**Sequencing:** queued to start once the in-flight Preview-on-OK build (`docs/plans/PREVIEW.md`)
lands - same file (`LiveTvScreen.kt`), avoiding a concurrent-edit collision. Preview-on-OK's actual
player-state work (the `previewingChannel` two-step, the dedicated preview `ExoPlayer` instance)
is reused here, not redone - only its *position and information richness* change in this pass.

## What's actually wrong, confirmed against the code and the reference

**"Two different sections":** `AppShell.kt` does route both `NavDestination.LiveTv` and
`NavDestination.Guide` to the same composable (`guideMode` is the only param that differs) - that
part of the earlier claim was accurate. But `guideMode = false` renders the old plain channel-list
+ static preview card, and `guideMode = true` renders a completely different layout (a wide
timeline grid) - same function, zero visual continuity between the two branches. Sharing a Kotlin
function isn't the same as feeling like one screen; that was the real miss.

**The grid itself, read directly from `EpgGridColumn.kt` against
`RedThemedEPGLiveTVScreen.jpg`:** what's built is channel name + programme title/time-range per
cell + a small LIVE glyph on the current one. Missing entirely, all visible in the reference:
- The hero band across the top - live video thumbnail, large programme title, time range with a
  red progress bar and "N min remaining," a description line, a favorite star, a category label.
  This is the single most visually dominant element in TiviMate's guide; RedSurf has none of it.
- Channel logos and channel numbers in the grid rows (currently name-only).
- A date/time header row above the grid ("Sat, May 16 · 05:00 PM · 05:30 PM...").
- A real synchronized red "now" line - deviated away from mid-P0-build for D-pad/scroll-sync
  complexity reasons (logged in the code's own comment as real per-row highlighting instead); worth
  a second look now that this is a dedicated pass rather than a P0 time-boxed cut corner.

## End state, as described

1. **One nav destination, not two.** Retire `NavDestination.Guide` as a separate pill (or route it
   to the exact same place with zero behavioral difference, if a bare second pill is ever wanted
   for some other reason later - default assumption is a straight collapse to one pill, matching
   the user's own words "merge... into one"). `guideMode`'s two-branch layout split goes away -
   there is one Live TV screen, and it always looks like the merged design below.
2. **Preview pane moves to the top, under RedSurf's own nav-strip.** This is the one deliberate
   RedSurf-vs-TiviMate difference, named explicitly by the user: TiviMate's own top-level nav lives
   hidden in a left panel; RedSurf's lives in the top nav-strip instead. Below that nav-strip, the
   layout should otherwise track TiviMate's reference as closely as possible - the hero preview
   band sits directly under the nav-strip, full-width, the same position/prominence it has in
   `RedThemedEPGLiveTVScreen.jpg`.
3. **Preview pane content, TiviMate parity:** live video (Preview-on-OK's embedded player, once
   that lands, relocated here), current programme title, time range, a progress indicator toward
   "N min remaining," a description line, a category label. **The reference's favorite star has no
   real backing feature yet** (Favorites is still an unbuilt, grey-row-only concept per
   `TELEPORT_MENU.md`'s backlog) - ship it as a grey/disabled glyph matching this project's own
   established grey-row convention, not a fake-functional star, same reasoning as every other
   not-yet-real feature in this codebase. **(confirm when this is picked up: exact description
   truncation/line count, and whether the category label reads from `selectedGroup` or the
   playing channel's own group - low-risk implementation detail, not a design question.)**
4. **Categories + grid below, as already decided (`AGENTS.md`, 2026-09-11) and already built** -
   this part stays: categories left (`GroupsColumn`, unchanged), the timeline grid to the right.
   Enriched per the gap list above: channel logos + numbers in each row, a real date/time header
   row, and a second look at whether a true synchronized "now" line is worth building for real in
   this dedicated pass (no longer a P0 time-box - worth attempting properly this time, falling
   back to the current per-cell-highlight approach only if the shared-scroll-position work proves
   genuinely too expensive on this hardware).

## Non-goals

- Favorites itself (the star stays a grey glyph, per point 3 above) - separate, unscheduled work.
- Any change to Preview-on-OK's underlying player-state logic (`previewingChannel`, the dedicated
  ExoPlayer instance, the OK-swap semantics) - that ships as designed in `PREVIEW.md`; this pass
  only relocates and enriches its *visual* presentation.
- EPG Sources settings UI, public-source fallback, the colour-legend indicator - still P1 from
  `PHASE_3.md`, untouched by this pass.

## Status board

| # | Task | State |
|---|---|---|
| M.1 | Collapse `NavDestination.Guide` into one pill; remove `guideMode`'s two-branch layout split | ⬜ not started |
| M.2 | Relocate the preview pane to a full-width hero band under the nav-strip | ⬜ not started |
| M.3 | Enrich the hero band to TiviMate parity (title, time+progress, description, category label, grey favorite star) | ⬜ not started |
| M.4 | Grid enrichment: channel logos/numbers, date/time header row | ⬜ not started |
| M.5 | Attempt a real synchronized "now" line; fall back to the current per-cell highlight only if genuinely too expensive | ⬜ not started |
| **A** | **Checkpoint - user compares the real screen against the reference image directly** | ⬜ |

## Acceptance - machine-verifiable

1. Only one nav-strip pill reaches Live TV/Guide; no separate "Guide" destination exists (or, if
   kept, is provably behaviorally identical - confirm which before building).
2. The hero preview band renders above the categories/grid, under the nav-strip, in every entry
   path that used to reach either the old list or the old grid.
3. Channel rows show a logo (or a graceful fallback when the channel has none) and a channel
   number, not name-only.

## Acceptance - feel/vision (user)

1. Held up next to `RedThemedEPGLiveTVScreen.jpg`, does this actually read as the same design
   language now, not just "functionally similar"?
2. Does the hero band's information (title, progress, description) feel complete, or is something
   from the reference still missing?
3. Does having RedSurf's nav-strip at the top (vs. TiviMate's hidden-left) feel like the right
   trade, now that everything else tracks the reference closely?
