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
  - *Branding before the Player sprint*, not after Cloud Sync: the point is that everything built
    afterwards carries it, so it goes as early as possible. Scope: Android TV launcher banner
    (320x180) + adaptive icon, the mark in the nav strip, the loading screen, About, the
    onboarding/welcome screen, the web portal's pages, and **Settings' category rail tiles**
    (replacing the plain initial-letter tiles).

    **Design decisions locked 2026-09-16, implementation wired in and device-verified same day
    (commit `0fce206`), corrected same day after user nitpicks on the first pass (commit
    pending).** Everything below lives in `docs/vision/branding/` (source JPEGs, extracted
    transparent PNGs, font files, per-icon SVGs and PNG tiles - `settings-icons/` for the
    Settings set specifically) as the design record; the real app resources are under
    `tv-native/app/src/main/res/` (adaptive icon `drawable/ic_launcher_background.xml` +
    `drawable-xxxhdpi/ic_launcher_foreground.png`, banner `drawable-xxhdpi/tv_banner.png`, mark
    `drawable-xhdpi/ic_mark.png`, six Settings icons as real `drawable/ic_settings_*.xml` vector
    drawables, three (`about`, `epg`, `remote`) as `drawable-xhdpi/*.png`, font
    `font/poppins_semibold.ttf`). Also new this pass: `WaveSpinner`
    (`ui/theme/RedSurfSpinner.kt`) - a custom rotating wave-crest arc (gradient-swept `Canvas`
    `drawArc`, not the generic Material `CircularProgressIndicator`) on the app's own loading
    screen (`MainActivity.kt`'s `AppState.Loading` branch), with the mark centered *inside* the
    spinner ring (concentric, not stacked above it - reads as one loading glyph, closer to how
    e.g. an avatar-with-progress-ring pattern reads, and keeps the wave motif literally orbiting
    the brand mark rather than sitting as two separate stacked elements).

    **Correction pass, same day, from real user nitpicks after seeing it live:**
    1. *Mark color read as pink, not red* - root cause found by sampling actual pixels: the mark's
       fill was `(191,41,45)`, visibly short on red and high on blue relative to `Accent`
       (`0xDC2626` = `(220,38,38)`), while every Phosphor-sourced Settings icon was already exact
       `Accent`. Fixed by recoloring the master `mark-transparent.png` and every derived asset
       (`ic_mark`, `ic_launcher_foreground`, legacy `ic_launcher`, `ic_settings_about`, banner) to
       flat exact `Accent`, not a hue-preserving shift (the master had JPEG-artifact color noise
       that a hue remap would have kept).
    2. *Wordmark still too heavy* - swapped Poppins Black (900) for **Poppins SemiBold (600)**,
       chosen by rendering "RedSurf" at Medium/SemiBold/Bold/ExtraBold/Black side by side against
       a high-res crop of the Gemini concept art's own wordmark and matching stroke-to-counter
       proportions directly, not by eye on the full concept image. `docs/vision/branding/fonts/`
       and the app's `font/` resource both updated; `Poppins-Black.ttf` removed as unused.
    3. *Icons "pixelated in practice"* - real root cause, found by sampling alpha channels: the
       master mark (from the original HSV-threshold background removal) and most derived
       Settings-icon PNGs had **zero anti-aliasing** - pure 0/255 binary alpha, hard stair-step
       edges - not a resolution or density-bucket problem (the assets were already correctly sized
       for this device's actual 320dpi/xhdpi). `ic_mark.png` and `ic_launcher_foreground.png`
       happened to look fine only because their generation path *downscaled* enough (LANCZOS) to
       pick up smoothing as a byproduct; anything reused near full-res (About icon, banner, legacy
       launcher) or generated via a hard luminance-threshold recolor (all 8 Settings PNG icons)
       stayed jagged. **The fix, and the actual "right way" here:** for the six Settings icons
       sourced from real Phosphor SVGs, convert directly to Android `VectorDrawable` XML
       (`res/drawable/ic_settings_*.xml`, `pathData` taken straight from the SVG's own `d`
       attribute) instead of rasterizing to PNG at all - vector rendering is exact at any size on
       any device, permanently immune to this whole class of bug, and is genuinely the correct
       Android-native answer for anything that started as a clean vector shape. For everything
       raster-only (the mark itself, and the two custom-built icons, Remote and EPG, which aren't
       simple single-path shapes) the fix is supersample-then-downsample: render/compose at a
       large intermediate size, do all masking/punching there, then resize down once with LANCZOS
       - that resize is what actually produces the smooth alpha gradient at edges; drawing
       "cleanly" at the final small size does not, regardless of care taken. Remote control was
       re-extracted from the (now AA-corrected) master mark by inverting its alpha within the
       glyph's own region, rather than rebuilding the old binary connected-component mask.
    **Verification note:** pixel-level screenshots still aren't possible this session (`adb
    screencap`/`screenrecord` both fail device-wide on this Chromecast with Google TV, confirmed
    even for the system launcher - a device limitation, not this app). Every corrected asset was
    instead verified by direct pixel sampling (color match to `Accent`, partial-alpha counts
    confirming real AA) and by viewing renders directly; on-device, confirmed via logcat that
    navigating through Home and Settings raises no resource/inflation exceptions with the new
    vector drawables and regenerated PNGs (a bad `pathData` or missing resource throws
    immediately on composition). Open: the web portal branding pass and one Opus taste check on
    the nav strip.
    - **The mark**: the "ultra-flat minimalist" silhouette concept from
      `Gemini_Generated_Image_mecnz9mecnz9mecn.jpeg`'s right side (solid-filled surfer on a
      surfboard riding a red crescent wave, with play/TV/remote/music icons integrated into the
      wave itself - user's read: "we're surfing on content," keep these everywhere, never strip
      for a simplified variant). Background removed cleanly via HSV threshold (saturated red mark
      vs. desaturated textured background) - `mark-transparent.png`, verified edge-clean against
      both a checkerboard and the app's real `Background` color.
    - **Icon vs. banner split**: the small adaptive icon is mark-only (Android masks it into a
      circle/squircle at a size text can't survive on any app - not a style choice). The 320x180
      banner - the actual primary user-facing surface on the Android TV home screen - carries the
      mark **and** the "RedSurf" wordmark together, TiviMate's own convention for their banner.
    - **Wordmark font: Poppins SemiBold (600)**, not Fredoka (an earlier, wrong read of the
      concept art's letterforms as playful/rounded) and not Black/900 (the first correct-family
      pick, but too heavy once seen live - see the correction pass above). Poppins's circular,
      geometric "e"/"d"/"S" bowls were the closest structural match to the concept art; Montserrat
      SemiBold was the close second. Font file at `docs/vision/branding/fonts/`.
    - **Settings category icons - final set, all 9, verified at true ~56dp tile size, not just
      preview size:** Playlists (`stack-fill`), Appearance (`palette-fill`), Playback
      (`play-fill`), Parental controls (`shield-fill`), Other (`dots-three-fill`) - real vector
      icons from Phosphor Icons (MIT-licensed, github.com/phosphor-icons/core), shipped as Android
      `VectorDrawable` XML at exact `Accent` red (see the correction pass above for why XML, not
      PNG). **General and Remote control were swapped again same day** after a second user
      nitpick round on the corrected-AA pass: Phosphor's `gear-six-fill` reads as a flower at
      small size (very rounded, petal-like teeth, no real mechanical notches - confirmed by
      rendering it in isolation, not just at a glance) - replaced with the **classic Material
      Design "Settings" gear** (`google/material-design-icons`, `src/action/settings/
      materialicons/24px.svg` - the same glyph as `Icons.Filled.Settings` already used on the
      Settings nav pill, per the user's own suggestion), which has real trapezoidal teeth and
      reads unambiguously as a gear. The mark-extracted Remote control glyph held too much fine
      internal detail (5-6 tiny button holes) to survive real-render-size legibility even with
      correct anti-aliasing - the fix wasn't AA, it was the source shape being wrong for the size.
      Replaced with Material Symbols' own purpose-built `settings_remote` icon (a simple
      rounded-rect body + one circle + two signal arcs - literally Android's own "TV remote"
      glyph), also shipped as a `VectorDrawable`. EPG is Phosphor's `calendar-blank-fill` with a
      custom "7" (set in the same Poppins SemiBold as the wordmark, not Phosphor's fused-path "12"
      glyph, which isn't editable) plus three staggered offset bars punched into the body as
      negative space, evoking a program-guide grid (TiviMate/DirecTV reference, user request) -
      tuned down from an initial 4-thin-bar version that muddied at true size to 3 thicker, more
      widely-spaced ones; still raster (not a simple single-path vector source), fixed via the
      supersample-downsample technique from the correction pass above. About reuses the full mark,
      at reduced legibility at true size (a known, accepted trade-off - it reads as "a red emblem,"
      not as "the surfer," but the row's own label already says what it is) - re-verified exact
      `Accent` red after the color-correction pass, still correctly wired to
      `R.drawable.ic_settings_about` in `aboutContent()`.
    - **Capability note for future asset work**: no image-generation tool is available - only
      programmatic primitive drawing (weak for original illustration, confirmed live: a hand-drawn
      gear read as a flower until rebuilt with real teeth) and raster extraction/recoloring (which
      *does* work well - the mark background removal and the remote-from-mark extraction both held
      up, once anti-aliasing is handled deliberately - see the correction pass above, this was
      missed the first time and is the actual "right way" lesson here: PIL/threshold-based raster
      work defaults to hard 0/255 alpha with **no** anti-aliasing, so any generated or recolored
      icon needs either true vector output (`VectorDrawable` XML from real SVG path data - always
      correct, no pipeline to get wrong) or an explicit supersample-render-then-LANCZOS-downsample
      step before shipping; visual review at preview size won't catch a missing AA pass, only a
      true-render-size check of the alpha channel itself will). Real icon libraries (fetched
      directly as SVG, e.g. Phosphor) are the right tool for any
      "solved-shape" icon need going forward; hand-drawing from scratch is the fallback only when
      no suitable existing shape exists, and should be visually verified at true render size before
      presenting, the same way every asset above was.

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

**Widened, user directive 2026-09-15 - apply this intuitively, not just when reported.** Everything
above is about focus (where the cursor lands); the same discipline covers *state* more broadly -
what the user last selected, what they were doing, what they'd reasonably expect still be true
when they come back - build it in by default on any new flow, not just fix it reactively once
someone notices it's missing. The model to follow is this session's Live TV fixes: hoisting
`selectedGroup`/`focusedChannel`/`recentChannels` to `AppShell` so leaving for Settings and coming
back doesn't reset to the first category (state/focus discipline, applied), and "Resume last
channel on launch" persisting across a real relaunch, not just a session (real disk persistence,
one level up from in-memory hoisting). Ask this by default when building anything new with
meaningful state: does leaving and returning - to another destination, or a full relaunch - lose
something the user would expect kept? Search's future query/results, a VOD/Series
season-and-episode position, a scroll position in a long list - all the same question, asked
before the report comes in, not after.

## Backlog - explicitly logged, not forgotten

- **Live TV loses its category/channel/recent-tiles on leaving and returning** - **no longer
  backlog: fixed and verified live, 2026-09-15** (user report, three examples: category resets
  every time you leave Live TV, recent-channel tiles disappear, no relaunch-resume).
  Navigate-away-and-back: `selectedGroup`/`focusedChannel`/`recentChannels` hoisted to `AppShell`
  (same conditional-composition-trap fix `selectedSettingsCategory` already needed). Resume across
  a real relaunch: **shipped 2026-09-15, corrected same day, fix verified and shipped 2026-09-16 -
  done.** Pre-selecting the exact channel/category last left on is now the unconditional default
  on cold launch - no toggle needed - via `AppShell`'s restore `LaunchedEffect`, which always seeds
  `selectedGroup`/`focusedChannel` when there's a valid last-watched reference (falls out to the
  normal "first group" default on genuinely fresh state or a stale reference, no special-casing).
  "Auto-play last channel on launch" (renamed from "Resume last channel on launch") now controls
  only whether that pre-selected channel also starts playing immediately - an explicit one-shot
  `liveTvAutoPlayTrigger`, set only from the restore effect alongside the seed (not inferred from
  `focusedChannel` changing generically, which would've misfired on a user's first ordinary
  browse-focus after a truly fresh launch), consumed back to false by `LiveTvScreen` so it can't
  refire mid-session. Verified live via real `force-stop` + relaunch, both paths: toggle off lands
  pre-selected/not-playing; toggle on lands directly in fullscreen playing (a brief pre-select
  frame visible first while the async restore completes, then auto-play - expected, not a bug).

  **Still open regardless**: recent-channel tiles surviving a relaunch (not just tab-switching)
  needs #2.5's real `recent_channels` table (Room), not the in-memory stand-in - user decision
  2026-09-15: hold for the Player sprint, not pulled forward.
- **EPG data source, for when Phase 3 (EPG sync + the merged Live TV/Guide screen, see the
  dedicated entry below) is underway** - user request, 2026-09-15, researched (not built): most
  Xtream providers already ship their own EPG endpoint, which is the first and most accurate
  source for real playlists like the user's - these are the fallback/supplement path for M3U-only
  providers or channels the provider itself doesn't list.
  - [iptv-org/epg](https://github.com/iptv-org/epg) - open-source, self-hosted (you run the
    scraper yourself via Node.js/Docker, it's not a hosted static URL), pulls from hundreds of
    source sites into standard XMLTV, channel data from the iptv-org/database project. Broadest
    coverage, most setup.
  - [EPGSHARE01](https://epgshare01.online/) - free, hosted, static XMLTV files by country/source,
    no self-hosting needed - explicitly "for LEGAL use only," updated roughly daily (uses
    WebGrab+Plus). Closest to a drop-in URL.
  - [open-epg.com](https://www.open-epg.com/app/epgguide.php) - free public XMLTV guides by
    country, plus an editor tool for fixing channel-ID mismatches.
  - "EPG Genius"/"EPG Genie" specifically (as named in the request) didn't turn up as a real,
    distinct service in this research - likely misremembered; closest real matches were EPG.lat
    (frequently cited as integrating well with TiviMate specifically) and the sources above.
  - Integration note for later: XMLTV channel `id` attributes must match the M3U's `tvg-id` (or
    the Xtream `epg_channel_id`) for a source to actually line up with RedSurf's channel rows -
    worth an editor/mapping pass, not just picking a URL.
  - **Resolution order - user decision, 2026-09-15, default behavior:** per channel, provider EPG
    first (already the most accurate for that specific channel/provider pairing); only when the
    provider has no listing for that channel, fill the gap from a free public source above. Not
    an either/or per playlist - a per-channel fallback, since a real playlist can have provider
    listings for some channels and gaps for others (found true of the user's own Ghana/Senegal/etc.
    test data this session - EPG entirely absent everywhere so far, but that's Phase 1/2 not
    having EPG at all yet, not evidence either way about the provider's own coverage once EPG
    sync exists).
  - **Supplemented-data indicator - user idea 2026-09-15, refined: a colour legend on the EPG
    grid, not a per-entry text badge.** Agreed direction - a repeated text badge on every
    supplemented cell would be visual noise across a dense multi-row/multi-column grid; a subtle
    background-tint difference (provider-sourced vs. supplemented), explained once by a small key
    somewhere on the Guide screen, reads clean and matches how real EPG/DVR UIs already
    distinguish program state by colour. **One real caveat, not a rejection - color can't be the
    only signal.** WCAG guidance (and ~8% of men having some form of color vision deficiency) is
    specific about this: pair the tint with a second, non-colour cue (a different border style -
    solid vs. dashed - or a small corner mark) so the distinction still reads for someone who
    can't distinguish the two colours, not just "prettier." Also worth guarding when this is
    actually designed: RedSurf's `Accent` red already carries real meaning everywhere (focus/
    selection) - the supplemented-data tint needs to visually read as a *different kind* of signal
    (e.g. a cool tone, not another red/warm variant) so it never gets confused with "this row is
    focused." Same "never claim something works without showing it's real" principle as grey
    Settings rows, just expressed as colour + a border cue instead of text.
  - **Settings control for this, not just a fixed default** - a user may not want supplemental EPG
    at all (accuracy concerns, a provider whose own listings are already complete, or simply
    preferring "no data" over "possibly wrong data"). Needs an off switch - open question whether
    that's one global toggle (Settings → EPG) or per-playlist (since coverage quality can differ
    provider to provider) - lean per-playlist given `PlaylistEntity` already carries per-playlist
    config (`epgOffsetHours`, `userAgent`), but confirm with the user when this is actually built
    rather than assuming.
- **Optional black-screen between channel zaps** - **no longer backlog: built and machine-swept,
  BACKLOG_SWEEP.md #11, 2026-09-15.** Live toggle, Settings → Playback; `AppPreferences`-backed,
  `PlayerHost` reads it and inverts `setKeepContentOnPlayerReset`. `SPRINT_LOG.md` has the sweep
  account.
- **Real pixel resolution as an alternative to the SD/HD/FHD/4K badge class** - **no longer
  backlog: built and machine-swept, BACKLOG_SWEEP.md #12, 2026-09-15.** Live toggle, Settings →
  Appearance; `PlayerInfoBlock`'s badge row reads `StreamInfo.rawResolution` instead of
  `resolutionClass` when on. `SPRINT_LOG.md` has the sweep account.
- **"Clear history" button on the player's History picker** (user idea, 2026-09-12, "copy
  TiviMate behavior") - makes more sense once #2.5's real `recent_channels` table exists; the
  current in-memory stand-in already clears itself every process restart, so a manual clear
  button for it would be low-value. Build alongside #2.5, not before.
- **Long channel/category names: marquee (scrolling text) on the focused row, not a hover
  tooltip** - **built and device-verified 2026-09-16** (installed on the Chromecast, navigated
  Live TV's Categories and Channels columns with the real playlist live - no crash, real data
  renders correctly through both changed files; pixel-level confirmation of the scroll animation
  itself isn't possible on this device, screencap/screenrecord both fail device-wide, but the
  code paths that own it are confirmed running). `ChannelsColumn.kt`/`GroupsColumn.kt` apply
  `Modifier.basicMarquee()` (Compose Foundation's built-in, not a hand-rolled animation) to the
  channel/category name `Text` only when that row has real D-pad focus - `ChannelRow` already had
  a focus-driven `selected`; `GroupRow`'s `selected` meant the *active* category instead (can
  differ from where focus currently is), so it gained its own local `isFocused` via
  `onFocusChanged`. User decision, 2026-09-15, superseding the tooltip idea this replaces. Pushed back
  on tooltip-on-dwell deliberately: a hover/dwell tooltip is a desktop-mouse pattern ported onto a
  D-pad, needs a timing decision (how long is "resting"?) with no clean answer, and reveals
  nothing until the user waits. Marquee is the actual TV-native convention for this (TiviMate,
  YouTube, Spotify's own TV apps all do it) - self-explaining the instant a row gets focus, no
  dwell timer to tune. Scoped care, not "just make it scroll": only the *focused* row marquees
  (unfocused truncated rows stay static/ellipsized - simultaneous scrolling everywhere would be
  genuinely bad, not just a matter of taste); pause about 1s static before scrolling starts and
  about 1s at the end before looping/reversing, smooth and unhurried, not a tight fast loop
  (that's what reads as cheap - the technique itself doesn't). Applies to channel/category names
  specifically (the actual overflow cases), not blanket-applied to every text element.
- **TiviMate-style categorized Settings screen** — **no longer backlog: built and machine-swept,
  2026-09-13.** `docs/plans/SETTINGS.md` is the spec (StreamVault's two-pane layout, TiviMate's
  nine categories and row placement, every planned row present as a grey, unfocusable row with
  its real name and planned default) and now also the as-built record; `SPRINT_LOG.md` has the
  sweep account. Settings-shaped backlog items below (clear history, tooltip, content-type
  selector - black-screen toggle and resolution badge flipped live, BACKLOG_SWEEP.md #11/#12)
  each have a named row there; when one gets built, flip the row live and log it in that file.
  Feel/vision review still pending (user).
- **Content-type selector (Live/VOD/Both) missing from the on-screen Xtream/M3U forms** (user
  found, 2026-09-12) - the Mobile Phone pairing form has always had this, the on-screen forms
  never did. Deliberately not added yet: the selector is functionally inert everywhere in Phase 1
  right now (VOD isn't stored regardless of this choice - confirmed by reading the code, not
  assumed), so it would be UI that doesn't change behavior. Add it when the VOD phase makes the
  choice actually matter, not before - **the Playlist Name field sibling gap was different (real
  effect right now) and was fixed immediately instead**, see the round below.
- **Back should retrace the same path as LEFT instead of one flat hop to Home** - **no longer
  backlog: built and machine-swept, BACKLOG_SWEEP.md #8, 2026-09-15.** Scoped to Live TV's own
  columns as originally planned: Channels → Categories on one Back, Home on the next, via
  `LiveTvScreen`'s own `BackHandler` kept mutually exclusive with `AppShell`'s Home-jump one
  (`onChannelsFocusChanged`, same `enabled`-flag mechanism as the fullscreen case). `SPRINT_LOG.md`
  has the sweep account.
- **`PlaceholderScreen` (Home, and every other unbuilt destination) never claims initial focus,
  and has no visible focus state at all** - **no longer backlog: built and machine-swept,
  BACKLOG_SWEEP.md #1, 2026-09-15.** Same `FocusRequester` + claim-on-compose pattern every real
  screen uses, routed through a themed `Surface`/`RedSurfFocus`. `SPRINT_LOG.md` has the sweep
  account.
- **Channel rows aren't playlist-scoped in their primary key** - **no longer backlog: built and
  machine-swept, BACKLOG_SWEEP.md #13, 2026-09-15.** `ChannelEntity`'s primary key is now
  composite (`playlistId`, `streamId`), Room v6→v7, destructive migration (user-confirmed
  bundling this in, no live users). Fresh-install import verified correct (`num`/`groupName`
  intact) on real Xtream data. `SPRINT_LOG.md` has the sweep account.
- ~~No playlist management UI at all~~ - **stale entry, removed BACKLOG_SWEEP.md hand-off,
  2026-09-15**: resolved by the Settings sprint (`SETTINGS.md`, 2026-09-13) - Playlists category
  has real per-playlist Remove today. This entry wasn't updated when that shipped.
- **GroupsColumn RIGHT/OK entry into Channels must focus the first channel** - **no longer
  backlog: built and machine-swept, BACKLOG_SWEEP.md #6, 2026-09-15.** `ChannelsColumn` now
  redirects to index 0's row (by `num` order) on a fresh, never-browsed entry, the same
  entry-redirect shape as `returnFocusRequester`/`SettingsPane`'s `firstRowFocus` pattern.
  `SPRINT_LOG.md` has the sweep account.
- **Settings: LEFT/Back from the pane land on the rail's nearest row, not the category you
  started from** - **no longer backlog: built and machine-swept, BACKLOG_SWEEP.md #5, 2026-09-15.**
  `CategoryRail` now mirrors `SettingsPane`'s own `hadFocus`/entry-redirect pattern in reverse,
  redirecting to the selected category's row only after focus has already crossed into the rail
  (never mid-crossing, which is what broke a direct `requestFocus()` fix during the Settings
  sprint). `SPRINT_LOG.md` has the sweep account.
- **`focusProperties { exit = ... }` behaved inconsistently by direction, at least in the Compose/
  tv-foundation versions this project pins** (found live, 2026-09-13, Settings sprint): it blocked
  an explicit `FocusRequester.requestFocus()` call even when the destination was still inside the
  trapped subtree, while not reliably blocking default directional `moveFocus()` escapes it was
  meant to catch. `SettingsScreen.kt` now uses explicit `onPreviewKeyEvent` interception instead
  (matching `PlayerScreen.kt`'s router). Worth knowing before reaching for `focusProperties.exit`
  as the go-to fix for a focus-escape bug elsewhere - it may not behave as documented here.
- **Settings rail: UP at the top row (General) is blocked from reaching NavStrip; the pane's own
  top row isn't** - **no longer backlog: built and machine-swept, BACKLOG_SWEEP.md #3, 2026-09-15.**
  Dropped the `DirectionUp`/rail-top-row branch of `SettingsScreen.kt`'s `onPreviewKeyEvent` guard;
  UP now escapes via default `moveFocus`, same as the pane side always did.
  **The "lands on Home, not Settings" finding this swept up - user decided, 2026-09-15, don't
  patch this one spot, fix the actual missing concept:** every escape-to-NavStrip in the app today
  (this one, and `PlaceholderScreen`'s own RIGHT-from-Home landing on Guide instead of Live TV -
  found live the same day) is really the same bug - Compose's default spatial search picks
  whichever pill is nearest in screen coordinates, and nothing anywhere establishes "the pill for
  the destination you're actually on" as a real, canonical concept the app can target. Patching
  each spot's spatial quirk individually would just find a third and fourth instance later.
  **Superseded by, and expected to be fixed for free by, the long-press-Back nav-jump item below**
  - building a real "resolve the current destination to its own pill" lookup for that feature
  gives every other escape-to-NavStrip case the same deterministic target to reuse, rather than
  four different ad hoc spatial-search outcomes. Don't fix this one in isolation; fix it as part
  of that.
- **Global quick-jump to the NavStrip from anywhere (long-press Back)** - user idea 2026-09-15,
  raised after finding that a deep scroll (150th category, or several tabs deep in Settings) has
  no fast way back to the top-level nav, since the strip lives above content rather than beside it
  (TiviMate's own left rail doesn't have this problem - it's one LEFT away regardless of depth).
  **Decided: long-press Back**, not a new/different key - the two of us landed on this
  independently. Short Back keeps today's one-step-at-a-time peel (Channels→Categories→Home,
  Settings pane→rail) exactly as-is; holding it jumps straight to the NavStrip from anywhere, on
  the pill for whatever destination is actually current (see the entry above - same canonical
  "current destination's pill" lookup both need, built once).
  - **Discoverability, user's real open question - answered, not hand-waved:** the strongest
    option is to make the gesture *self-teaching through its own visual feedback* rather than
    requiring the user already know about it - the same pattern Android's own long-press-to-
    confirm affordances use (a small radial/progress fill under or near the Back hint the instant
    the key is held down, completing exactly when the jump fires; releasing early cancels with
    nothing happening). A user who holds Back even briefly - intentionally or by accident - sees
    it start to reveal itself before it commits, so understanding doesn't depend on reading
    documentation first. Pair with a light, one-time dismissible tip ("Hold BACK to jump to the
    menu") the first time someone scrolls past some depth threshold, as reinforcement, not the
    primary teaching mechanism.
  - **Conflicting system action, user's other real question - checked, low risk but not zero:**
    unlike `KEYCODE_HOME` (frequently reserved by Android TV launchers for Assistant/app-switching
    on long-press), `KEYCODE_BACK` has no universal Android TV system-level long-press reservation
    - this app already intercepts key events explicitly (`PlayerScreen.kt`'s router is the existing
    precedent) and consuming the long-press in our own handler should pre-empt any launcher-level
    fallback. **Caveat: "should" - this needs verifying live on the actual Chromecast/launcher
    combination before relying on it, not assumed from general Android knowledge alone; a rare
    edge case worth a one-line awareness, not a blocker, is an accessibility service (TalkBack)
    reserving long-press-Back for its own gesture on a device where one's active - not expected to
    matter on this family's own devices, but worth a real check, not a guess, before shipping.**
- **Settings' category rail doesn't scroll** - **no longer backlog: built and machine-swept,
  BACKLOG_SWEEP.md #2, 2026-09-15.** `CategoryRail` swapped to `TvLazyColumn`, matching every
  other list in the app; "About" is reachable and fully visible. `SPRINT_LOG.md` has the sweep
  account.
- **About: add a "Created by" row** - **no longer backlog: built and machine-swept,
  BACKLOG_SWEEP.md #9, 2026-09-15.** Live row, "Created by · Faraz Ahmad", styled like Version.
  Feel/vision still open: is the wording final, or does the user want a role appended now that
  it's live?
- **Recent-channel tile selection across categories: Back returns focus to the wrong category**
  - **no longer backlog: built and machine-swept, BACKLOG_SWEEP.md #7, 2026-09-15.**
  `LiveTvScreen`'s `onChannelChanged` (the tile-pick and zap path both use it) now also updates
  `selectedGroup` to the new channel's actual `(playlistId, groupName)`, not just `focusedChannel`
  - verified live: picking a recent channel from a different category, then Back, correctly shows
  that channel's real category selected and the channel itself focused. `SPRINT_LOG.md` has the
  sweep account.

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
- **Zap UP/DOWN order - RESOLVED (pending user re-test), 2026-09-14 mini-sprint.** Was: "total
  chaos" on v0.27.0, up meaning down, no predictable increment. Root cause was exactly hypothesis
  1 below, confirmed by instrumented, on-device measurement - not a guess. Two prior rounds: the
  provider-gap theory (wrong), then the real `tvg-chno`-never-parsed bug (fixed v0.25.4, but
  M3U-only - this user is Xtream Codes, so that fix never applied to their report).

  **What the mini-sprint found:** the direction flip (Phase 2 decision 3 had UP → `prevChannel`,
  backwards vs. TiviMate's confirmed UP → higher number) was applied, then measured with a
  logcat-instrumented 20-press test on the real device against live Xtream data (group "AF |
  AFRICA", 175 channels, `num` 24180-24354, no gaps): **10× UP traced a perfectly clean
  24180→24181→...→24190, 10× DOWN traced the exact reverse back to 24180 - zero repeats, skips,
  or reversals.** A follow-up wrap-around check (DOWN past the group's lowest `num`, UP past the
  highest) also traced perfectly (`lastInGroup`/`firstInGroup` fallback both correct). None of the
  three "unknown" suspects logged 2026-09-13 (mixed real/fallback `num`, duplicate `num`,
  wrap-around) reproduced - on this data, the direction flip was the entire bug. One inverted
  mapping, consistently applied, reads exactly like "random" against a lifetime of opposite
  TiviMate muscle memory - which is what made it worth measuring instead of re-guessing.

  **Shipped:** `PlayerScreen.zap` - UP → `nextChannel`, DOWN → `prevChannel`. `PHASE_2.md`
  decision 3 amended with the correction on record. Diagnostic logging
  (`zap dir=… from=… -> to=… group=…`, the group-snapshot line, and
  `ChannelDao.firstNInGroup`/`ChannelRepository.debugFirstInGroup`) shipped too, deliberately -
  cheap, real Log.d calls with no release-path UI cost, and exactly what the next zap report (if
  any) should be checked against before writing new code. Two logs (before/after the flip) are in
  `SPRINT_LOG.md`'s 2026-09-14 entry.

  **Not fully closed:** this is one group on one provider. "Resolved" here means "the mechanism
  is proven correct and the specific bug reported is fixed," not "guaranteed clean on every
  group in the user's real ~28K-channel list." If the user hits chaos again after re-testing on
  v0.27.1+, it's very likely a *different*, group-specific data issue (the three original
  suspects are still real possibilities for some category, just not this one) - the same
  instrumentation makes that a fast diagnosis, not a new investigation from zero.

- **Channel-switch loading state: branded spinner + timeout + real error feedback, TiviMate-style**
  (user, 2026-09-16). Not a new item - this is `PLAYER_ENGINEERING_BRIEF.md`'s §4.4
  (`PlaybackErrorController`), §4.5 (15s stall watchdog, "give up" after a second stall within
  60s), and §11 (`PlayerErrorMapper` - friendly non-technical copy, never a raw code) all together,
  applied to the zap/tune path specifically. Confirming explicitly, since the user asked for it by
  name: the loading visual for this should be the branded `WaveSpinner`
  (`ui/theme/RedSurfSpinner.kt`, already built for the app's cold-start loading screen), not a
  generic spinner - same component, same OSD badge slot the existing "tuning" indicator ask
  (backlog below, Progress feedback #3) already calls for.
- **Test playlist may be dead as of 2026-09-16** (user report, end of session) - the
  `infinitytv-mgm.online` Xtream credentials in `~/.redsurf/test-playlist.url` stopped returning
  live content. Before seeding a fresh install/debug session with it next time, ask the user for
  new credentials or confirm whether they loaded a working list themselves that session - don't
  assume the file's contents are still valid.
- **Playlist management: edit existing credentials, and a real detail view (server/user/type -
  not just the name)** (user, 2026-09-16). Today Settings → Playlists only shows the name and a
  Remove action (`PLAYLIST_GREY_ROWS` already has "Rename" as a grey row, `SETTINGS.md`) - there's
  no way to see what's actually configured for a loaded playlist, or to fix credentials that
  changed/expired (exactly the situation the dead test list above just created) without deleting
  and re-adding it from scratch. Needs a real edit flow (reuse the onboarding Xtream/M3U forms,
  pre-filled) and a detail/view surface showing server, username, type, and content-type scope per
  playlist - not scoped further than that yet, next session's job to flesh out.

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
