# Backlog sweep — one cleanup sprint before Branding

**Why now** (user, 2026-09-15): before Branding, clear the small, already-root-caused bugs and
wishlist items sitting in `AGENTS.md`'s Backlog and "State and focus discipline" sections -
several live on the exact screen (Settings' rail) Branding is about to touch, so fixing them
first means Branding isn't applied on top of known-broken UI. Everything in scope below already
has a decided fix shape from earlier investigation - this sprint executes, it doesn't re-diagnose.

**Sweep protocol reminder** (this is the point of doing it as one sprint, not nine): build
*everything* below, run the full machine-verifiable list once, log every failure, fix all of
them, **one** rebuild, sweep again. Never fix-one-rebuild-fix-one-rebuild. 3-build cap per pass
still applies (`.claude/commands/sprint.md`) - given the breadth here, more than one *pass* is
expected and fine; more than 3 *builds in one pass* is the thing to avoid.

## In scope

| # | Item | Fix shape (already decided - see `AGENTS.md` for full reasoning) | Files |
|---|---|---|---|
| 1 | `PlaceholderScreen` never claims focus, no visible focus state | `FocusRequester` + claim-on-compose (same pattern every real screen uses); route through a themed `Surface`/`RedSurfFocus` instead of a plain `Box` | `PlaceholderScreen.kt` |
| 2 | Settings rail doesn't scroll ("About" cut off) | Swap `CategoryRail`'s plain `Column` for `TvLazyColumn`, matching every other list in the app | `SettingsScreen.kt` |
| 3 | Settings rail blocks UP at the top row; pane doesn't | Drop the `DirectionUp`/rail-top-row branch of the `onPreviewKeyEvent` guard (keep `DirectionDown`/`DirectionRight` branches - still needed) | `SettingsScreen.kt` |
| 4 | Deterministic focus-return #1: Live TV Categories↔Channels (LEFT/RIGHT) | `onFocusChanged` at the column level, entry-from-outside vs. movement-within, redirect to the exact row left (mirrors `ChannelsColumn`'s existing `returnFocusRequester` fullscreen-exit case) | `GroupsColumn.kt`, `ChannelsColumn.kt` |
| 5 | Deterministic focus-return #2: Settings pane↔rail (LEFT/Back) | Reuse `SettingsPane`'s `hadFocus`/`firstRowFocus` pattern in reverse on `CategoryRail` - redirect to the row matching `selectedCategory` once focus has already landed on the rail (not mid-crossing, which is what broke the direct fix mid-sprint-1) | `SettingsScreen.kt` |
| 6 | Category→Channels (OK/RIGHT) always focuses the first channel | Same entry-redirect shape as #4/#5, third application | `ChannelsColumn.kt` |
| 7 | Recent-channel tile pick across categories → Back lands on wrong category | Prerequisite: a channel change originating from inside the player (`onChannelChanged`) must update `selectedGroup`/`queriedGroup` in `LiveTvScreen`, not just `focusedChannel`. Then #4's redirect covers the rest. | `LiveTvScreen.kt`, `PlayerScreen.kt` |
| 8 | Back should retrace the path, not flat-hop to Home | Scoped to Live TV's own columns only (per the original narrowing): Back from Channels → Categories first, then Home on the next Back. Not a general breadcrumb stack across every destination. | `LiveTvScreen.kt` or `AppShell.kt` |
| 9 | About → "Created by" row | Live row, label "Created by", value "Faraz Ahmad" (the user's own text, as given) | `SettingsScreen.kt`, `SETTINGS.md` |
| 10 | New: minimal local settings persistence | Neither existing `settings/SettingsManager.kt` (DNS/TMDB) nor `settings/PlayerSettings.kt` (buffer tuning, non-reactive `var`s) fit - purpose-built `AppPreferences`: `SharedPreferences`-backed, `StateFlow`-exposed (consistent with `MainViewModel`'s existing reactive pattern), just the two booleans below. Small and additive - not a general settings framework. | new `settings/AppPreferences.kt` |
| 11 | Black-screen-between-zaps toggle (Playback), using #10 | Flip from grey to live in `SETTINGS_GREY_ROWS`; `PlayerScreen.zap` reads the flag and skips the no-black-screen trick when on | `SettingsScreen.kt`/`SettingsCategory.kt`, `PlayerScreen.kt` |
| 12 | Resolution badge: class vs. real `WxH` toggle (Appearance), using #10 | Flip from grey to live; `PlayerInfoBlock`'s badge row reads the flag and shows `StreamInfo.rawResolution` instead of the derived class when on | `SettingsScreen.kt`/`SettingsCategory.kt`, `PlayerScreen.kt` |
| 13 | Channel rows: composite primary key (`playlistId` + `streamId`) | `ChannelEntity` primary key change, Room version bump (6→7), destructive migration - user confirmed bundling this in (2026-09-15), no live users, sprints already wipe the device routinely | `EpgEntities.kt`, `RedSurfDatabase.kt` |

## Deferred - not touching these, reasons already on record

- **Clear-history button** - needs the real `recent_channels` table (#2.5, Player sprint, after Branding). Building it against the in-memory stub is wasted work.
- **Content-type selector on-screen forms** - inert until VOD is stored. Adding UI with no behavior behind it is dishonest per the project's own rule.
- **Truncated-name tooltip** - a timing/placement *feel* decision, not a bug. Better suited to the Branding pass since it's visual polish, not correctness.
- **Progress feedback (import/OTA/zap)** - already earmarked for Branding as one branded component (spinner/progress bar), not three one-offs here.
- **No playlist management UI** - this entry in `AGENTS.md` is stale; resolved by the Settings sprint (Remove exists). Clean up the doc as part of this sprint's hand-off.

## Acceptance

**Machine-verifiable (`scripts/tv-test.sh` + logcat):**
1. Home: a NavStrip pill (or Home's own content) reports real focus immediately on arrival, with a visible ring - not "no focused node found."
2. Settings rail: UP/DOWN through all nine rows reaches "About" and its value is visible (not clipped) - `focused_info` bounds are inside the rail's card, not overflowing it.
3. Settings rail: UP at "General" (top) reaches the Settings NavStrip pill; DOWN at "About" (bottom) still stays put (unrelated guard branch, must still work).
4. Live TV: focus a channel row, LEFT to Categories, RIGHT back to Channels - same `streamId` reports focus both times (not just "some row").
5. Settings: enter a pane row, LEFT back to rail - lands on the category actually selected, not a spatially-nearest one. Re-enter Settings from another destination - same category still selected.
6. Live TV: focus a category, press RIGHT - focus lands on that category's first channel (`num` order), every time, regardless of where you were in the previous category's list.
7. Play a channel, open the tile row, pick a recent channel from a *different* category, press Back - the Categories column shows the new channel's actual category selected, its Channels column shows that channel focused.
8. Live TV: from Channels, press Back once → Categories gets focus (still Live TV). Press Back again → Home.
9. About: "Created by · Faraz Ahmad" row visible, unfocusable-if-still-grey/live-if-flipped (should be live, styled like Version).
10. Playback → "Black screen between zaps" flips to a live toggle; turning it on and zapping shows a real black frame between channels (verifiable via `PlayerHost` state logs, not a screenshot); turning it off restores current behavior.
11. Appearance → resolution badge toggle flips live; with it on, the player's badge row shows literal `WxH` instead of the SD/HD/FHD/4K class.
12. Fresh install after the schema bump: import a playlist, confirm channels load correctly (`num`, `groupName` intact) - the composite-key change must not silently break normal import/paging.

**Feel/vision (user):**
- Does Back-in-Live-TV (Channels → Categories → Home) feel right, or too many presses to exit?
- Does the black-screen zap toggle actually feel like the old cable-box behavior you wanted?
- Is "Created by · Faraz Ahmad" the right wording, or do you want a role appended after seeing it live?

## Order

Sonnet-lane - every fix shape above is already decided, nothing here needs a judgment call mid-build. Build all 13, one debug build, sweep the 12 machine-verifiable lines, fix whatever fails, repeat per protocol. Item 13 (schema bump) last, since it's the one with real (if now accepted) data-loss blast radius - build and verify everything else first so a problem there doesn't block the rest.
