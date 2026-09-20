# Preview-on-OK — real embedded preview in Live TV's browse view

**Origin:** TiviMate parity, always intended (`LiveTvScreen.kt`'s own doc comment already noted
"Sharing one ExoPlayer between a small embedded preview and a fullscreen view without ever
[crashing/glitching]" as the reason a *static* preview stub shipped instead, back when Phase 3 P0
explicitly descoped this - see `docs/plans/PHASE_3.md`'s "Explicitly not touched" note). Built as
its own small follow-up now that P0 has landed, per user request 2026-09-19/20. Fully decided by
conversation before this file was written - no `(confirm)` list this time, just the record.

## Current behavior (what this replaces)

`LiveTvScreen.kt`'s `openChannel(channel)`: a single OK press on a focused channel sets
`previewUrl` **and** `isFullscreen = true` together - it skips straight to fullscreen. The
right-column `PreviewStub` is a static info card (name + thumbnail-shaped placeholder) that
updates on a 500ms focus-hover debounce - it never plays real video, regardless of `previewUrl`.

## New behavior

1. **First OK on a focused channel** starts that channel playing for real in the preview area,
   with audio - **and stays in browse view** (`isFullscreen` stays `false`). This channel becomes
   `previewingChannel`.
2. **A second OK, pressed while that exact channel is still the current `previewingChannel`**
   (i.e. focus is still on it and it's already the one previewing) promotes to fullscreen -
   today's existing `openChannel` behavior (`previewUrl` set, `isFullscreen = true`), and the
   preview player instance is released the moment fullscreen's own player takes over.
3. **OK on a *different* channel while something is already previewing immediately swaps the
   preview** to the newly-OK'd channel (one more OK away from fullscreen on *that* one) - no need
   to back out of preview mode first. Confirmed behavior, not a guess.
4. **Moving focus around without pressing OK does not change what's previewing.** The preview
   keeps playing whatever was last explicitly OK'd, exactly like TiviMate, even while the user
   browses other rows/categories. The existing 500ms hover-debounce static-info-card behavior
   still applies to *unselected* rows generally (unchanged), but it does not override or interrupt
   an active video preview.
5. **Cold-launch / "Resume last channel" auto-play is unchanged** - `autoPlayTrigger`'s existing
   path still jumps straight to fullscreen, bypassing the new preview-first step entirely. That's
   a distinct "resume where I left off" flow, not a fresh browse-and-select action - don't route it
   through `previewingChannel`.
6. **Leaving Live TV entirely releases the preview player** (decoder freed, same discipline as
   promoting to fullscreen) - don't leave a second decoder alive in the background on a 449MB
   device. Preview state persists across category switches *within* Live TV (it's independent of
   which category is being browsed), just not across navigating away to another destination.

## Technical approach

**A second, dedicated, lightweight ExoPlayer/PlayerView instance owned by the preview area's own
composable scope** - not the same controller instance `PlayerScreen`/`PlayerController` use for
fullscreen. This is the answer to the "safely sharing one ExoPlayer between two surfaces" problem
that got this descoped once before: don't share one - use two, mutually exclusive in time (the
preview instance is always released before/as the fullscreen one takes over, so only one decoder
is ever alive at once). Reuse `PlayerController`'s existing connection-handling/error patterns
where sensible, but a preview instance doesn't need the full stall watchdog / reconnect ladder
built for long-running fullscreen playback - keep it simple (play, and if it errors, just fall
back to the static stub silently rather than surfacing a branded error panel meant for fullscreen).

## Settings

Per the user's own standing instruction (2026-09-20): every new feature should complement the
Settings screen, not bypass it. Add a live row for this - **proposed: "Preview channel on select"
under Settings → Playback, default on** (it's the new TiviMate-parity default behavior; a user who
finds the extra decoder/behavior unwanted can turn it off, which reverts `openChannel` to today's
single-OK-jumps-straight-to-fullscreen behavior). Flag if a different location/default is wanted.

## Acceptance - machine-verifiable

1. First OK on a channel starts real playback in the preview area (confirmed via
   `PlayerController`-equivalent state or a dedicated preview-player log line reaching a playing
   state), fullscreen state unchanged (still browse view).
2. Second OK on the same still-previewing channel promotes to fullscreen; the preview player
   instance is released (no two decoders alive simultaneously - confirmed via logcat/lifecycle).
3. OK on a different channel while one is previewing swaps the preview to the new channel.
4. Moving focus without OK does not change what's previewing.
5. Cold-launch auto-play still lands directly in fullscreen, unchanged.
6. Leaving Live TV releases the preview player.
7. The new Settings toggle exists, defaults on, and turning it off restores today's exact
   single-OK-jumps-to-fullscreen behavior with zero regression.

## Acceptance - feel/vision (user)

1. Does the preview-then-fullscreen two-step feel right on the real remote, TiviMate-familiar?
2. Any perceptible lag/stutter switching preview between channels, or promoting to fullscreen?
3. Audio-on-preview - good call, or should it be muted until fullscreen after all?
