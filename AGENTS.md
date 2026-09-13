# Agent instructions — RedSurf

RedSurf is a **TiViMate-grade IPTV player for Android TV**, built for one family's own use, with a
free-tier cloud account for managing playlists. Native Kotlin + Compose for TV in `tv-native/`;
a Next.js web portal at the repo root.

## Read these first, in this order

| Path | What it is |
|---|---|
| `docs/vision/PRODUCT_VISION.md` | **The product.** D-pad control matrix, "The Player IS The App", the cloud credential-locker model. Outranks everything else. |
| `docs/vision/UI_SPEC.md` | The design language: palette, focus model, screen layouts. |
| `docs/vision/IPTV_DOMAIN_KNOWLEDGE.md` | Hard-won IPTV engineering knowledge. Read before touching parsing, networking or playback. |
| `docs/plans/HARDWARE.md` | The target device's real, measured limits. |
| `docs/plans/PHASE_*.md` | What is being built right now, with a status board and acceptance criteria. |
| `docs/plans/WORKFLOW.md` | Which model does which work, when to escalate, the phase gates, and **sprint mode** (how work is paced and device-tested from 2026-09-13). |
| `docs/plans/SETTINGS.md` | The Settings shell brief - **built and machine-swept 2026-09-13**, feel/vision pending. Branding is next. |
| `docs/plans/DRIVING.md` | How the user operates Claude Code here (`/sprint`, permissions, hooks, headless). **Standing instruction:** when you use one of these mechanisms, tell the user in one line what it is and how they'd type it - they asked to learn in the moment. |
| `docs/vision/references/` | Screenshots: `streamvault/` for layout, `tivimate/` for workflow, `../mockups/` for colour. |

**`docs/archive/` is superseded — do not trust it.** It is previous agents' claims, most of which
were false. It is kept for provenance only.

## Current phase: Phase 2 — the Player — `docs/plans/PHASE_2.md`

**Brief written by Opus 2026-09-12, execution is Sonnet-lane.** Every decision is made in the
brief's "Decisions already made" section - including the ones no screenshot could settle,
marked *(confirm at A/B)*. Build to them as written; the user corrects at the checkpoints.
Read the brief's reference table first - the user walked TiviMate on their own TV and described
the three overlay levels precisely; the screenshots in `docs/vision/references/tivimate/` anchor
each one. Why the Player and not VOD/Series: `PRODUCT_VISION.md` §1, "The Player IS The App."

## Phase 1 — done.

`docs/plans/PHASE_1.md` (design system + Live TV screen) **closed 2026-09-11** at 1.6: memory
verified flat across playlist size (large real list used *less* PSS than the small one - 112.8 MB
vs 145.7 MB, paging genuinely works), all 7 acceptance-sweep lines pass, 13/13 tests, signed
release. Real end-to-end playback confirmed on the TV. Six Checkpoint B rounds fixed real,
device-found bugs on top of the original 1.1–1.5 build (state loss across the fullscreen toggle, a
nav-strip overflow bug that was the main cause of "looks unprofessional," one-step playback, OTA
reliability, Back navigation, the accent color, the QR code, a full visual pass against the
StreamVault/TiviMate references) - full history in `PHASE_1.md`'s "Checkpoint B, round N" entries
if you need it; you shouldn't need to re-derive any of it.

Phase 1's own backlog is fully closed - six post-phase quick-fix rounds (see `PHASE_1.md`) fixed
the onboarding text-field D-pad trap, LEFT/RIGHT column focus memory, the 4:3 letterbox
bleed-through, playlist naming and multi-playlist accordion, screen-on and background-pause. What
remains is deliberately deferred with reasons, in "Backlog" below. The Live TV/Guide merge is
Phase 3 territory (needs EPG data) - see "Product decisions on record".

**Still open, not chased further without new detail:** one report of the app returning to
Onboarding with no sign of the previously-loaded playlist after an update — checked, not a Room
schema bump (version's been 6, unchanged, since before v0.19.1), no confirmed cause; and OTA
reported "erratic, might be good enough for now" with no reproducible specifics.

**Workflow — superseded 2026-09-13, read `WORKFLOW.md` "Sprint mode" first.** The paragraph below
is kept for the reasoning, not as instruction. Short version of what replaced it: work is paced
in module-sized sprints (one 5-hour window each), Claude runs a **logcat-driven** ADB test sweep
on the device itself (screenshots only to diagnose), the user gets a short *feel/vision* list per
module instead of a bug hunt, and sprints run on debug builds that are **uninstalled and replaced
with the CI release at sprint end** so OTA keeps working.

*(Historical, 2026-09-12)* stop doing per-task ADB screenshot round-trips — too expensive. Build
+ verify with compile/tests + a signed `assembleRelease` only, batch several changes together,
hand off a build for the user to test on the real TV via OTA instead. Targeted ADB debugging of
one specific reported bug is fine when the user says so explicitly (happened twice this phase,
both times solved a real bug) — that's different from routine verification loops. **Never
install an ad-hoc-versioned local build to the test device** — an early local build with default
versionName `v1.0.0` once out-ranked every real release by semver, permanently blocking OTA until
manually fixed; the intent (don't block OTA) survives in the sprint-end uninstall/reinstall
protocol; outside a sprint, still verify a local `assembleRelease` with an explicit low/obviously-
fake version like `-PversionName=v0.0.0-local-verify`, check its signature, then delete it.
Standing permission to merge to `main` freely is granted (no live users) — don't ask before
merging.

## Product decisions on record

- **Roadmap order** (user, 2026-09-13): Settings shell (`SETTINGS.md`) → **Branding** → Player
  sprint (Phase 2 #2.3 Actions row, #2.4, #2.5) → EPG + Guide (Phase 3) → **Cloud Sync** →
  VOD/Series. Two deliberate reorderings:
  - *Cloud Sync before VOD.* It's the differentiator (the "credential locker" in
    `PRODUCT_VISION.md`), and it changes the data model - `playlists` becomes a cache of cloud
    state - so every module built after it inherits that, and every module built before it
    would need retrofitting. Vision: an account tied to an email; the portal loads/enables/
    disables lists; any device running RedSurf honours the current cloud state. Brief is Opus
    work when its turn comes (security-sensitive). Flags already on record: TV sign-in is a
    pairing code, not a password (the existing pairing flow becomes "link this device"); sync
    playlist *definitions* only, never channel data - each device re-imports from the provider;
    cloud is truth, device is cache, sync on launch + live listener, honour last-known state
    offline; credentials encrypted per account, not just rules-protected; last-write-wins with
    server timestamps, global enable/disable first; shape the account doc so favourites/recents/
    settings can ride the same rails later. Prerequisite: the web portal must build locally
    again (the `@tailwindcss/oxide` issue below) before that sprint starts.
  - *Branding before the Player sprint*, not after Cloud Sync: the user has a logo/icon ready;
    the point is that everything built afterwards carries it, so it goes as early as possible.
    Scope: Android TV launcher banner (320x180) + adaptive icon, the mark in the nav strip, the
    loading screen, About - **and the already-built surfaces**: the onboarding/welcome screen
    (where lists get added), the web portal's pages (dashboard, pairing), and **Settings'
    category rail tiles** (user idea, 2026-09-13, feel/vision review of the Settings shell - the
    rail's plain initial-letter tiles read as close to StreamVault's own reference but obviously
    missing its per-category coloring; rather than patch that in, hold for real branded icons once
    the kit exists). Assets go in `docs/vision/branding/` (SVG + high-res PNG + source) before the
    sprint starts - one logo already there (`Gemini_Generated_Image_mecnz9mecnz9mecn.jpeg`), user
    says they don't love it but it's usable; a typographic wordmark rendered in code is the
    fallback if so. One Opus
    taste check on the nav strip.

- **Cloud pairing: revive** (user, 2026-09-10). Background: `pairingSessions` was locked down for
  security, which surfaced that nothing reads it — the TV app moved to local NanoHttpd pairing and
  was never reconnected, so both web pairing flows (`app/dashboard`'s push, `app/pair/[code]`)
  currently write to a dead end. Decision: keep them; the TV-side Firestore listener gets built in
  the cloud phase. Until then, don't delete anything cloud-related, and don't build on it either.

- **Live TV/Guide merge, deferred to its own phase** (user, 2026-09-11). Vision: keep
  StreamVault's top-pill nav (not TiviMate's left drawer - "modern Google TV style" per the user),
  but redesign the Live TV screen itself on TiviMate's proven layout instead of StreamVault's:
  categories left, a real EPG timeline grid on the right (not the current simple channel-list
  column), a live preview strip up top. Bonus, same request: the top nav auto-hides (slides up,
  disappears after an idle timeout - configurable) to give the preview strip more room, exactly
  how TiviMate's left rail behaves. References: `references/tivimate/RedThemedEPGLiveTVScreen.jpg`
  (the target layout), `references/streamvault/Home.png` (the nav style to keep).
  **Why not now:** the grid needs real EPG programme data to not be an empty fake grid, and EPG
  isn't populated yet (no sync worker scheduled - a Non-goal of Phase 1, see `PHASE_1.md`). Do EPG
  data + this merged-screen redesign + the auto-hide nav together as one phase, not the visual
  layout before the data exists. Not urgent - the user is in no rush.

## State and focus discipline (user directive, 2026-09-12 - binding, not a suggestion)

The user was explicit that losing continuity while testing is actively frustrating and named this
a standing rule, not a one-off fix: **every new screen, navigation path, and control must
preserve where the user was and what had focus, by default - check this before calling any
navigation feature done, the same way "builds clean" and "tests pass" are checked.** This
followed a pattern of real bugs: fullscreen-exit focus landing on the NavStrip instead of the
channel just watched; a debounce gap letting fast D-pad key-repeat rebuild an expensive Pager once
per row flown over. The scope is broad on purpose - which destination/menu item the user was on,
which control they used to navigate away, and where D-pad focus was, left-right and up-down, not
just whatever app-level data state happens to look correct.

Surviving `remember`ed data (per the Compose conditional-composition trap documented in
`AppShell.kt`/`LiveTvScreen.kt`) is necessary but not sufficient - Compose's focus system still
needs something to explicitly claim focus when a focused subtree is torn down, or it picks
whatever's nearest in the tree, which is rarely what the user actually wants. When building any
screen with more than one focusable region or a modal/fullscreen state, ask explicitly: "when the
user leaves this and comes back, where does focus land, and is that actually where they were?" -
don't assume the answer is yes without a `FocusRequester` actually wired to prove it.

**A worse variant found in `PlayerScreen.kt` (2026-09-12), worth naming explicitly:** it's not
enough to restore focus when *leaving* a whole screen (the fullscreen-exit case above) - a screen
with its *own* internal overlay states needs the same discipline for transitions *within* itself.
Picking a tile from `PlayerScreen`'s Controls floor tore down the tile row that held real D-pad
focus, and with nothing reclaiming focus onto the screen's own root, Compose's fallback landed
unpredictably - sometimes nowhere at all, which doesn't just misplace focus, it silently breaks
all further key routing on a screen whose input handling depends on that root staying focused
("the whole button engine seems to crash," in the user's words - a correct description; Back kept
working only because its dispatcher doesn't need focus). Every overlay/modal state a screen owns
internally needs the same explicit reclaim-on-close this rule already asks for at the screen
level - not just once, at the door.

**DECIDED, 2026-09-13 - deterministic return, never spatial-nearest.** Every case below where
leaving a region and coming back doesn't restore the *exact* item the user was on - it just lands
wherever directional search or `FocusManager.moveFocus` resolves to from the new position - is a
bug to fix the same way, not a per-case judgment call anymore. Basis: the user tested real
TiviMate specifically for this (drilling into a settings category and hitting Back correctly
refocuses that exact category, even though TiviMate's own settings isn't a multi-pane layout);
it also matches documented practice (Android TV's own "10-foot UI" focus guidelines, equivalent
console UX guidelines, and the general consistency/recognition-over-recall heuristic) and this
project's own binding rule above, arrived at independently. Applies wherever this pattern exists
in the app, not just where it's been individually reported - check for it as part of "state and
focus discipline," the same way this whole section already asks.

Known instances, all still open (decided *what*, not yet built):
- **Live TV: Categories ↔ Channels via LEFT/RIGHT** doesn't restore the exact row you left -
  it lands wherever directional search resolves to from the new focus position. Needs a real
  "remember focus per column, restore on re-entry" mechanism (an `onFocusChanged` at the column
  level detecting entry-from-outside vs. movement-within, redirecting carefully to avoid focus-
  loop bugs) - deliberately not rushed alongside the fullscreen-specific fix that inspired this
  rule originally, since a hasty version risks new focus bugs of its own.
- **Settings: LEFT/Back from the pane back to the rail** (Settings sprint, 2026-09-13,
  `SPRINT_LOG.md`) - lands on the rail's spatially-nearest row via `FocusManager.moveFocus`, not
  necessarily the category just left. Likely cheaper to fix than it first looked: the pane-entry
  redirect already in `SettingsScreen.kt` (`SettingsPane`'s `hadFocus`/`firstRowFocus` - land
  wherever by default, then redirect once already inside the target region) sidesteps the
  `requestFocus()`-vs-focus-guard conflict that broke a direct fix mid-sprint; the same shape run
  in reverse on `CategoryRail` (redirect to the row matching `selectedCategory` once focus has
  already landed somewhere on the rail, not while still crossing into it) should too.
- **Recent-channel tile selection across categories → Back lands on the wrong category** (found
  live, 2026-09-13, same session) - a stricter version of the same bug: `selectedGroup` never
  even gets updated when a channel change originates from inside the player (only `focusedChannel`
  does), so there's no "restore" to attempt at all yet - fixing that data gap is the prerequisite
  before this one can be fixed the same way as the other two.

All three are backlog items - pick up whichever is most relevant when next touching that screen,
or do all three together as their own small pass; apply the same discipline to any future screen
with more than one focusable region, not just these.

## Backlog - explicitly logged, not forgotten

- **Optional black-screen between channel zaps** (user idea, 2026-09-12): the "no black screen"
  zap behavior (PHASE_2.md decision 13, `PRODUCT_VISION.md` §3's "black screen minimizer") is
  deliberately the opposite of old cable-box channel changing - the user specifically likes that
  old behavior and wants it as a toggle, not a replacement for the default. A "Playback" Settings
  category item once the categorized shell exists (pairs with the resolution-display toggle
  below).
- **Real pixel resolution as an alternative to the SD/HD/FHD/4K badge class** (user idea,
  2026-09-12) - a Settings toggle between the derived class and the literal `WxH` (e.g.
  "1920x1080"). `StreamInfo.rawResolution` already captures the raw value (`PlayerHost.kt`,
  2026-09-12) - only the Settings toggle to switch the badge display is outstanding.
- **"Clear history" button on the player's History picker** (user idea, 2026-09-12, "copy
  TiviMate behavior") - makes more sense once #2.5's real `recent_channels` table exists; the
  current in-memory stand-in already clears itself every process restart, so a manual clear
  button for it would be low-value. Build alongside #2.5, not before.
- **Tooltip on long-focus for truncated category names** (user idea, 2026-09-12): categories long
  enough to always ellipsis, even after the visual pass, could show their full name after the
  D-pad rests on them briefly - not built, needs a design pass on timing/placement first.
- **TiviMate-style categorized Settings screen** — **no longer backlog: built and machine-swept,
  2026-09-13.** `docs/plans/SETTINGS.md` is the spec (StreamVault's two-pane layout, TiviMate's
  nine categories and row placement, every planned row present as a grey, unfocusable row with
  its real name and planned default) and now also the as-built record; `SPRINT_LOG.md` has the
  sweep account. Settings-shaped backlog items below (black-screen toggle, resolution badge,
  clear history, tooltip, content-type selector) each have a named row there; when one gets
  built, flip the row live and log it in that file. Feel/vision review still pending (user).
- **Content-type selector (Live/VOD/Both) missing from the on-screen Xtream/M3U forms** (user
  found, 2026-09-12) - the Mobile Phone pairing form has always had this, the on-screen forms
  never did. Deliberately not added yet: the selector is functionally inert everywhere in Phase 1
  right now (VOD isn't stored regardless of this choice - confirmed by reading the code, not
  assumed), so it would be UI that doesn't change behavior. Add it when the VOD phase makes the
  choice actually matter, not before - **the Playlist Name field sibling gap was different (real
  effect right now) and was fixed immediately instead**, see the round below.
- **Back should retrace the same path as LEFT instead of one flat hop to Home** (user idea,
  2026-09-12): e.g. inside Live TV, Back from Channels could step to Categories first, then Home,
  rather than jumping straight to Home from anywhere. Good idea, not built - scope it first to
  Live TV's own columns (which have a real spatial relationship) rather than a fully general
  breadcrumb stack across every destination (Settings, Search, etc. aren't spatial the same way).
  **New evidence, 2026-09-13 (Settings feel/vision pass):** the flat hop is now visibly worse in
  Settings than it first looked - Back from deep in Settings lands on Home, a placeholder with
  nothing built (see the next two entries for what that Currently does to focus), so the user
  hit it live and asked outright whether Back should land on Home or Settings here. Current
  behavior is the existing, documented, deliberate design (`AppShell.kt`: Back always flat-hops
  to Home) - not a new bug, but this is the second independent report asking for something better,
  which is real signal toward prioritizing the fix above rather than more scope-narrowing.
- **`PlaceholderScreen` (Home, and every other unbuilt destination) never claims initial focus,
  and has no visible focus state at all** (user found, 2026-09-13, chasing the Back-to-Home report
  above). Two stacked violations of already-binding rules, not one: it's `.focusable()` (so
  something *can* land there) but nothing ever calls `requestFocus()` on it the way every real
  screen does (`GroupsColumn`, `SettingsScreen`'s rail, etc.) - state/focus discipline above says
  every screen must; and even when it does hold real Compose focus, it's a plain `Box`, not a
  themed `Surface` with `RedSurfFocus` styling, so there is no ring/glow to see it - **Binding
  technical constraint #4** below ("every focusable element needs a visible focus state") in
  plain violation. Net effect, confirmed live: after Back-to-Home, nothing *looks* focused (though
  the Box actually holds real focus, invisibly) until the first arrow key, which then jumps
  focus to whichever NavStrip pill default search resolves to - not deterministically "Home," the
  pill actually representing where you are (a fourth instance of the deterministic-return
  question above, once Home has real initial focus to return *to*). Concrete, well-scoped fix:
  give `PlaceholderScreen` the same `FocusRequester` + claim-on-compose pattern every real screen
  already has, and route it through a real (even if inert) `Surface`/`RedSurfFocus` so it's
  visible - cheap, and removes an entire category of "where did my focus go" reports at once.
- **Channel rows aren't playlist-scoped in their primary key** (found live, 2026-09-12, while
  chasing the zap-numbering bug): `ChannelEntity.streamId` alone is `@PrimaryKey`, not composite
  with `playlistId`, and `insertChannels` uses `OnConflictStrategy.REPLACE` - re-adding the same
  provider under a new `playlistId` (every `loadPlaylist` call mints a fresh UUID) can silently
  migrate a channel's row onto the new playlist if its `streamId` collides, orphaning it from its
  original playlist's group. Fix is a composite `primaryKeys = ["playlistId", "streamId"]` plus a
  Room version bump (destructive migration, no live users yet - same pattern already used for
  past schema changes) - not done yet because it wipes every currently-installed device's
  playlists on next update, and this session already had enough other changes in flight to not
  want to compound the risk. Do it as its own isolated, clearly-flagged release.
- **No playlist management UI at all** (user found, 2026-09-12): can't delete or re-import a
  single already-loaded playlist - Settings only has Check for updates / Add another playlist /
  Reset, and there's no per-playlist removal anywhere (see whether Reset's full-wipe is the only
  current workaround before assuming one doesn't exist). Matters beyond convenience: it's also the
  only way to pick up fixes to *how* a playlist was imported (e.g. the `num`/`tvg-chno` fix above)
  on already-loaded data, since import-time fields are written once and don't retroactively
  correct themselves. Real playlist management (list, remove, re-sync one) belongs with the
  Settings redesign already planned above, not bolted on ad hoc.
- **GroupsColumn RIGHT/OK entry into Channels must focus the first channel - DECIDED** (user,
  2026-09-13, resolving the 2026-09-12 open question below). Currently, RIGHT/OK from a category
  lands wherever Compose's default directional focus search resolves to (in practice, whatever's
  roughly parallel to the category you left, described by the user as "random, doesn't make
  sense") - never a deliberate choice, just the unengineered default, since `onGroupFocused`
  clears `focusedChannel` to null on every group switch (`LiveTvScreen.kt`) and nothing else
  claims focus for that first entry. **Decision: always the first channel in the category**,
  matching TiviMate/most TV UIs' convention of resetting a list to its top on a fresh selection -
  not built yet, needs a `ChannelsColumn` entry-redirect the same shape as its own
  `returnFocusRequester` mechanism (or `SettingsPane`'s `firstRowFocus`/`hadFocus` pattern from
  the Settings sprint - same fix, third place it'd apply). *(Original 2026-09-12 framing, for
  context: undecided whether "parallel row" or "always first" was more correct - now settled.)*
- **Settings: LEFT/Back from the pane land on the rail's nearest row, not the category you
  started from** - no longer an open question, decided alongside its Live TV twin and a third
  instance; see "State and focus discipline" above ("DECIDED, 2026-09-13 - deterministic return,
  never spatial-nearest") for the full list, the TiviMate/UX-guideline rationale, and the likely
  fix shape for this one specifically.
- **`focusProperties { exit = ... }` behaved inconsistently by direction, at least in the Compose/
  tv-foundation versions this project pins** (found live, 2026-09-13, Settings sprint): it blocked
  an explicit `FocusRequester.requestFocus()` call even when the destination was still inside the
  trapped subtree, while not reliably blocking default directional `moveFocus()` escapes it was
  meant to catch. `SettingsScreen.kt` now uses explicit `onPreviewKeyEvent` interception instead
  (matching `PlayerScreen.kt`'s router). Worth knowing before reaching for `focusProperties.exit`
  as the go-to fix for a focus-escape bug elsewhere - it may not behave as documented here.
- **Settings rail: UP at the top row (General) is blocked from reaching NavStrip; the pane's own
  top row isn't** (user found, 2026-09-13, feel/vision pass) - an inconsistency, and the user's
  report reads as "this is wrong," not "this is right": UP from the *pane*'s top live row (e.g.
  Playlists' "Remove") correctly reaches the top ribbon, but UP from the *rail*'s top row doesn't,
  even though both are the same screen. Root cause: `SettingsScreen.kt`'s `onPreviewKeyEvent`
  guard explicitly blocks `Key.DirectionUp` while on the rail at
  `SettingsCategory.entries.first()` - the pane side has no equivalent guard at all and simply
  relies on default `moveFocus()`, which is what's actually reaching NavStrip successfully. That
  rail guard was added by direct analogy to `PlayerScreen.kt`'s fullscreen escape-prevention
  without separately confirming Settings needed the same treatment - fullscreen video has no
  visible NavStrip to sensibly land on, but Settings does, so the cases aren't equivalent. Likely
  fix: drop the `DirectionUp`/rail-top-row branch of that guard (the `DirectionDown`/rail-bottom
  and `DirectionRight`/empty-pane branches are unrelated and still needed) so the rail matches the
  pane - then, per the deterministic-return decision above, make sure it lands specifically on
  the *Settings* pill, not whichever NavStrip pill default search happens to resolve to.
- **Settings' category rail doesn't scroll** (user found, 2026-09-13, feel/vision pass on
  `v0.27.0`): the last row ("About") is visibly cut off and DOWN does nothing once focus reaches
  it - real bug, not a taste call. Root cause: `CategoryRail` (`SettingsScreen.kt`) lays out all
  nine rows in a plain `Column`, not a `TvLazyColumn` - every other real list in this app
  (`GroupsColumn`, `ChannelsColumn`, `SettingsPane` itself) uses `TvLazyColumn`, which scrolls to
  keep focus on-screen for free; the rail was written as a plain `Column` since nine fixed rows
  never needed paging, but that also means nothing scrolls the viewport when they overflow the
  card's height. Fix is almost certainly swapping it to `TvLazyColumn` for consistency with every
  other column in the app, not a bespoke scroll solution - low-risk, but not done yet per the
  user's explicit "log only, don't fix now."
- **About: add a "Created by" row** (user idea, 2026-09-13): "Created By: Faraz Ahmad, or
  something more accurate of a role" - the user wasn't sure of the exact title to use. Add
  alongside the existing Version/Check for updates rows in `SETTINGS.md`'s About table; confirm
  wording with the user before building (a live row's label is real copy, not a placeholder).
- **Recent-channel tile selection across categories: Back returns focus to the wrong category**
  (user found, 2026-09-13) - the third instance of "State and focus discipline"'s now-decided
  deterministic-return principle above, but with an extra prerequisite (see there): watching a
  channel in category X, opening the tile row and picking a *recent* channel that belongs to a
  different category Y, then pressing Back to leave fullscreen - focus lands back on category X
  (the one playback started in), not Y (the channel actually now playing). Root cause (read, not
  yet fixed): `PlayerScreen`'s tile select and
  `LiveTvScreen`'s zap path both call `onChannelChanged`, which updates `focusedChannel` but never
  `selectedGroup`/`queriedGroup` - so `ChannelsColumn` is still showing category X's paged list
  when fullscreen closes, `returnFocusRequester`'s search for the new channel's id finds no
  matching row in it (the new channel isn't in that list at all), fails silently, and default
  focus picking falls back to whatever's already selected - category X. Same state/focus
  discipline class as every other bug in this section; fix is making a channel change from inside
  the player also update `selectedGroup` to that channel's actual group, not just `focusedChannel`.

- **Progress feedback: three places where the app goes silent** (user, 2026-09-13, after sprint
  1) - a small "Feedback & progress" brief, best folded into the **Branding** sprint since the
  spinner/progress bar should be a branded component used everywhere, not three one-offs:
  1. **Playlist import.** What the user sees: the "Importing N channels..." count climbs to
     ~28K, then the screen sits there "forever" with no change. What's actually happening (read,
     not guessed): the M3U path (`MainViewModel.loadPlaylist`) streams the provider's *entire*
     file - the user's is 327 MB / 1.23M entries - and only counts live entries toward
     `imported`; VOD/series lines are parsed and discarded (`skipped++`), and *nothing on screen
     moves while that happens*. So the freeze is the parser chewing through ~1.2M lines it will
     throw away, not VOD being downloaded - VOD isn't stored anywhere yet (Phase 1 non-goal) and
     `contentType` is inert on both import paths. Fix has two halves: (a) progress that reflects
     *parse* progress (bytes read of `Content-Length`, or lines seen incl. "skipping VOD/series:
     N"), with distinct phases once VOD is real; (b) stop seeding the app via the M3U URL for an
     Xtream provider - the JSON API path is ~28K objects and already what the app prefers
     (sprint protocol updated). Related: the Live/VOD/Both selector is still inert (backlog above).
  2. **OTA update download.** `UpdateManager` downloads the APK with no progress reporting at all
     (confirmed: no `Content-Length`/progress code in it) - the user sees nothing between "Install
     now" and the Package Manager sheet. Add branded download progress; leave install to the
     system installer.
  3. **Channel tune and zap.** From OK on a channel (or UP/DOWN in the player) to first frame,
     nothing indicates work is happening - `PlayerHost` renders no buffering state (confirmed: no
     buffering/playbackState UI in it). Decision 13's "no black screen" zap deliberately keeps the
     old frame up, which is right, but it needs a small, unobtrusive "tuning" indicator so a slow
     stream reads as loading rather than dead. Same component as 1 and 2, smallest form.
- **Zap UP/DOWN order still wrong for the user - open investigation, not yet understood**
  (user, 2026-09-13, after sprint 1): channel numbers in the list are sequential, but zapping is
  "random, and sometimes UP decreases while DOWN increases." Two rounds have already gone at
  this (provider-gap theory - wrong; then the real `tvg-chno`-never-parsed bug, fixed in
  v0.25.4 - a fresh import is required for it to apply). The user's latest report is *after*
  that fix, so either it wasn't enough or the tested data predates it. Hypotheses to eliminate,
  in order, before touching code again: (a) **stale data** - was the zap test on a playlist
  imported after v0.25.4? Sprint 1 wiped the device, so anything added now is fresh; (b)
  **mixed numbering within one group** - `num = channel.chno ?: fallbackNum`; if the provider
  sets `tvg-chno` on *some* entries in a group but not others, real numbers (e.g. 101, 102) and
  0-based fallback counters interleave under `ORDER BY num`, which would look exactly like
  "random" - check by inspecting `num` across one group in the DB (debug build + `run-as` is
  broken on this Chromecast; use a `Log.d` of the group's first 20 `num` values instead); (c)
  **wrap-around at group edges** - `nextChannel` wraps to `firstInGroup` past the end and
  `prevChannel` to `lastInGroup` past the start, which at a boundary reads as UP going *up* in
  number - could explain "sometimes inverted" if the test group is small; (d) **direction
  convention itself** - decision 3 maps UP → previous (lower num), DOWN → next; confirm against
  the user's TiviMate that this is the expected direction and not itself the "inverted" half of
  the report. Needs the user's answers to (a) and the source type (Xtream vs M3U) before a
  third code attempt - see the questions asked 2026-09-13.

## The one rule that matters

**Never claim something works because you wrote plausible code for it.**

This project was rebuilt because previous agents repeatedly reported features as complete when
they were hollow. Compiling is not working. A green CI badge is not an install. "I implemented X"
is only true after you watched X happen.

State plainly which of these you did:
- *builds clean* — compiled, nothing more
- *tests pass* — the tests ran and you read the output
- *verified on device* — you installed it and observed the behaviour

If you cannot verify something (needs the TV, needs the user's IPTV provider), **say so and stop**
rather than asserting success.

## Binding technical constraints

1. **No Dagger Hilt.** It caused the infinite KSP compiler loops that killed the predecessor
   project (`docs/archive/`… see `docs/vision/TVMIME_POST_MORTEM.md`). Use manual constructor
   injection. This project is small enough that it is genuinely the right call.
2. **Borrow techniques, never transplant UI.** Wholesale-porting StreamVault's UI is what produced
   the "Frankenstein" codebase that ended the last attempt.
3. **Memory discipline is mandatory.** The target device has **1.89 GB RAM, ~449 MB free**. Never
   load a full playlist into memory; stream-parse into Room and page from SQLite. See
   `docs/plans/HARDWARE.md`.
4. **Every focusable element needs a visible focus state.** On a TV the focus ring *is* the
   interface. See `docs/vision/UI_SPEC.md` §2.
5. **Never commit signing material.** `*.jks`, `*.keystore`, `signing.properties` are gitignored.
   The repo is public.

## Build & test

```bash
export JAVA_HOME="$HOME/.local/opt/jdk17/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
cd tv-native
./gradlew :app:assembleDebug        # ~2m40s cold
./gradlew :app:assembleRelease      # signed, if ~/.redsurf/keys/signing.properties exists
./gradlew :app:testDebugUnitTest
```

Toolchain: Temurin JDK 17, Gradle 8.7 (wrapper committed), AGP 8.2.2, Kotlin 1.9.22,
compileSdk 34, minSdk 23. SDK footprint is deliberately minimal — no Studio, no emulator.

If your shell tool runs each command in a fresh process (true for Claude Code's `Bash` tool),
`export JAVA_HOME=...` does not persist between calls — re-export it in the *same* call as every
`gradlew`/`apksigner` invocation, or it fails with "Unable to locate a Java Runtime" even though
you exported it moments ago. `apksigner` lives at
`~/Library/Android/sdk/build-tools/34.0.0/apksigner` on this machine (`$ANDROID_HOME` may be
unset in your shell — use the absolute path if so).

**Test device** (only when it's powered on — don't assume it is):
```bash
adb connect 192.172.7.160:35631
adb install -r tv-native/app/build/outputs/apk/release/app-release.apk
adb shell monkey -p com.redsurf.tv -c android.intent.category.LEANBACK_LAUNCHER 1
adb exec-out screencap -p > /tmp/screen.png     # look at what you built
adb logcat -s RedSurf
```
When the TV is off, iterate with local builds and cut a GitHub release for the user to install
via OTA. CI is free — the repo is public.

## Verified state as of 2026-09-10

**Real and working:** builds clean (debug + release); release signing verified end to end — CI
publishes signed releases, and **a genuine in-app OTA update has been observed completing on the
Chromecast** (v0.17.6 → v0.17.7, versionCode 61→62, no manual step); installs and launches (41 MB
PSS at onboarding); M3U parser with genuine unit tests; DoH / custom User-Agent networking;
NanoHttpd LAN pairing server; Room schema; ExoPlayer + track selection; AFR logic wired into the
player. The 17 `\$` interpolation bugs are fixed and the duplicate `EpgSyncWorker` stub is deleted
(0.2). The OTA updater does real semantic version comparison, shows a consent dialog before
installing anything, and checks/explains the install-unknown-apps permission instead of silently
failing — all three **device-verified**, including the negative path (permission denied → the
app's own explainer dialog, not a raw OS block). Two more bugs were caught only by live testing and
are also fixed: the consent dialogs didn't actually trap D-pad focus (a same-composition overlay
`Box` draws on top but doesn't own input focus — fixed by using a real
`androidx.compose.ui.window.Dialog`), and `tv-material3`'s default `Surface` color is light, making
white dialog text unreadable (fixed with explicit dark styling). See `docs/plans/PHASE_0.md` §0.7
for the full account, including how the first test attempt was invalid (it tested v0.17.5, built
*before* these fixes existed) and was caught by checking the running version rather than trusting
the result. `lib/firebase.ts` no longer has hardcoded fallbacks; it now fails loudly if env vars
are missing (0.5, verified directly against the compiled file).

**Built but never wired up:** `GlobalSearchEngine`, `BackupManager`, `CatchupEngine`, `XtreamApi`
(VOD/series), `TmdbApi`, `SettingsManager`, `PlayerSettings`. Favourites, hide and group-management
DAO methods are all dead. `VodDashboard` is hardcoded film titles.

**Known broken:** EPG never populates (no `WorkManager` enqueue exists, so neither sync worker ever
runs); no design system, so nothing shows a focus state; the web app cannot currently be built
locally on this machine — a pre-existing, unrelated native-binary install issue with
`@tailwindcss/oxide`/`lightningcss`, see `docs/plans/PHASE_0.md`.

**Fixed 2026-09-10, verified against the live Firestore project with real HTTP requests, not just
reviewed:** `firestore.rules` no longer leaves `pairingSessions` world-readable, and the playlist
schema now matches what the dashboard actually writes, so "Add Playlist" works. One thing this
surfaced but didn't resolve: nothing reads `pairingSessions` anymore — the native app's Firebase
pairing was replaced by local NanoHttpd pairing and never reconnected — so the two web pairing
flows are currently a dead end. Revive or remove is a product decision, not made here.

**Phase 0 is complete as of 2026-09-10.** Every item in `docs/plans/PHASE_0.md` is done and
verified — most against the actual running system, not just a green build. Phase 1 (the design
system) may begin.

Update this section when the facts change. It is the file everyone reads first, so a stale claim
here is worse than none.
