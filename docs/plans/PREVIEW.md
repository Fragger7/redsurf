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

1. ✅ **Verified on device.** First OK started real playback - confirmed via `dumpsys audio`
   showing RedSurf genuinely holding live `GAIN` audio focus (not just a log line claiming a
   trigger fired - the exact gap that caused the Teleport Menu discrepancy). Fullscreen state
   unchanged: nav strip, Categories, and Channels columns all stayed composed and visible.
2. ✅ **Verified on device.** Second OK on the same still-previewing channel promoted to
   fullscreen (focus bounds became full-screen `[0,0][1920,1080]`); `dumpsys audio` showed a
   *new* `AudioFocusListener` instance requesting and holding focus, and the preview's own
   listener no longer appeared as the live holder - confirms real release, not just state reset.
3. ✅ **Verified on device.** OK on a different channel while one was previewing swapped the
   preview panel's channel name immediately, no need to back out first - confirmed via the panel's
   own text (`GHANA - JOY PRIME SD` → `GHANA - 3ABN INTERNATIONAL HD` on one OK press).
4. ✅ **Verified on device.** Moving focus (DOWN) without OK left the preview panel showing the
   same channel/video; only the hint text changed ("Press OK again to open fullscreen" → "Press OK
   to preview this channel"), confirming `previewingChannel` is independent of focus.
5. **Reasoned correct, not independently re-tested via a full cold relaunch this session** - the
   `autoPlayTrigger` effect's only change was adding `previewingChannel = null` (a no-op, since
   it's already null at that point in every real cold-launch path); low risk by inspection, not
   re-verified end to end with a disruptive force-stop/relaunch cycle given everything else this
   session already covered live.
6. ✅ **Verified on device.** Leaving Live TV for Settings dropped RedSurf from `dumpsys audio`'s
   live focus holder entirely - the preview player was genuinely released, not just backgrounded.
7. ✅ **Verified on device, both directions.** The toggle exists under Settings → Playback,
   defaulted to "On," and flipping it "Off" made a single OK jump straight to fullscreen with zero
   intermediate preview step (confirmed via immediate full-screen focus bounds on one OK press,
   same as pre-Preview-on-OK behavior). Toggled back "On" before hand-off to ship the intended
   default.

## What actually happened (2026-09-20, Sonnet)

Built exactly to spec - no deviations from the decisions above. New file
`player/PreviewPlayerHost.kt` (`PreviewPlayerController` + `rememberPreviewPlayerController` +
`PreviewPlayerHost`, deliberately parallel to but separate from `PlayerHost.kt`'s fullscreen
versions - smaller buffer, no AFR, no stall watchdog, silent error fallback). `LiveTvScreen.kt`
gained `previewingChannel` state, split `openChannel` (two-step, plain list only) from
`promoteToFullscreen` (Guide grid's `onTuneChannel`, `autoPlayTrigger`, and the promotion path all
use this directly). `PreviewStub` now branches on `previewingChannel` to show a real embedded
`PreviewPlayerHost` instead of the old placeholder, with a silent-fallback `previewFailed` flag if
the preview itself errors. `AppPreferences`/`SettingsScreen.kt`/`AppShell.kt` gained the
`previewOnSelect` toggle, threaded the same way `blackScreenBetweenZaps` already was.

One real bug caught before it ever reached the device: `previewFailed` was originally declared
inside the `Box` block that also held the video, but referenced again in the `Column`'s hint-text
logic below it - a scoping error that would have failed to compile. Fixed by hoisting the state to
the composable's own top level before writing the render tree, and cleaning up a confusingly-named
helper (`previewFailedHint`, despite its name, was actually a same-channel check) into a clearly
named `isSameChannel`.

Build/test: `compileDebugKotlin` clean (first attempt after the scoping fix), 15/15 unit tests
(confirmed via the actual result XML, not exit code). Real signed release **v0.35.0**
(versionCode 120, matching the next real number after v0.34.0/119), installed and confirmed
running (no crash, `mResumed=true`) before push. Device data was wiped by the debug/release swap
mid-sprint (expected, per the sprint protocol) - the test playlist will need re-seeding for the
user's own testing.

## Acceptance - feel/vision (user)

1. Does the preview-then-fullscreen two-step feel right on the real remote, TiviMate-familiar?
2. Any perceptible lag/stutter switching preview between channels, or promoting to fullscreen?
3. Audio-on-preview - good call, or should it be muted until fullscreen after all?
