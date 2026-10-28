# PERFORMANCE MAX - is Live TV / the guide as snappy as it can be?

Research brief, 2026-10-26. Nothing here is built. Evidence is labelled **measured** (gfxinfo/
Perfetto on the Chromecast, with a source) or **expected** (reasoning or docs, not yet tried on
this device). Inputs: `PERFORMANCE_SALVAGE.md`, `TIVIMATE_LEARNINGS.md` (F2-F18), `SPRINT_LOG.md`
2026-10-19..26, `HARDWARE.md`, the code at HEAD (c72c85b).

## 1. Verdict: no, not maximized. There is real headroom in two places, and the transition is the third

| Scenario | TiviMate 5.3.3 (measured) | RedSurf best (measured) | RedSurf now | Gap |
|---|---|---|---|---|
| Guide DOWN/UP | 7.6% janky, p50 29, p90 32 | **9.2%**, p50 34, p90 40 (v0.44.4 CI) | 13-15% (caller); S8-era runs had two regimes: ~650 frames/p50 22/14-17% or ~500/p50 31/19-25% | **possibly regressed** since v0.44.4 |
| Guide RIGHT/LEFT | 1.0%, p90 36 | 8.4%, p90 38 | not re-measured | small |
| Categories | 7.5%, p50 18, **1 heavy frame/move** (85-123ms) | 13.4% (a11y off) / 17-18% (Projectivy a11y on), **2 heavy frames/move** (~140-170ms) | same | **largest** |
| OK -> fullscreen first frame | ~57ms p50 | 75-119ms | same | small |
| Back -> preview first frame | p50 7, 79 frames | 75-91ms (was 174) | same | small (it's a cut either way) |

- **Measured:** the salvage closed most of the gap (guide 98% -> 9%, categories 69% -> 13-18%).
- **Measured:** categories still pay two heavy frames per move where TiviMate pays one (F15). This
  is the clearest structural cost left. One attempt to merge them into one frame was worse 4/4
  (reverted), and so was hoisting state into CompositionLocals (22-24% -> 32-33%).
- **Unverified, needs checking first:** the guide's later numbers (13-25%) are worse than the
  v0.44.4 9.2%. Between those builds came the Crest re-skin, the crescent now-line cap, the
  supplement key, the double-height row and the account work. It could be a regression, run-to-run
  regimes (the two-regime pattern suggests thermal state or background CPU, e.g. SmartTube) or
  install state (`verify` vs `speed-profile`). Bisect before optimising anything new.
- **Expected:** ~40ms per guide press is frame-callback work no trace marker covers (batch 3: the
  lazy list re-laying out on every scroll-animation frame). That is the next guide target.
- **Measured (F18):** Projectivy's accessibility service adds ~4 points of jank to Compose and costs
  Views very little. We can't turn it off for the family, but we can make it cheaper (item 7).

**Rewrite?** No (item 12). Every remaining cost found so far is in how we use Compose (node count,
re-layout per animation frame, two-phase category commit), not Compose's floor. TiviMate's lead
comes from doing less work per frame, which we can copy without leaving Compose.

## 2. Ranked candidates

Gain/cost/risk are estimates unless marked measured. "Measure" names the decision metric; every
A/B follows the protocol in §4.

| # | Item | Expected gain | Cost | Risk | Measure |
|---|---|---|---|---|---|
| 1 | **Bisect the guide regression** v0.44.4 -> HEAD (install each CI APK, `compile_app`, 3 interleaved runs) | Back to ~9% if a commit is to blame | 0.5 day | none | gfxinfo guide DOWN/UP |
| 2 | **Category move in one heavy frame** | 13-18% -> ~10%; ~70ms less per move | 1-2 days | medium (focus discipline) | gfxinfo categories + Perfetto heavy-frame count |
| 3 | **Guide: stop re-laying out every scroll frame** | -20..40ms/press of untraced work | 1-2 days | medium | Perfetto `Choreographer#doFrame` breakdown |
| 4 | **Profiles that land on OTA**: macrobenchmark-generated baseline + startup profile, PackageInstaller session with `.dm` | removes the ~10-point "fresh install" penalty after each update; faster cold start | 1 day | low | gfxinfo right after OTA, no `compile_app`; `am start -W` |
| 5 | **Guide as fewer nodes**: one draw layer for the whole timeline | -5..15% node/layout cost per frame | 2-3 days | medium-high | gfxinfo + layout-inspector node count |
| 6 | **Coil / logo pipeline** | fewer GC pauses, no decode on main | 0.5 day | low | Perfetto GC slices, alloc tracking |
| 7 | **Semantics diet** (a11y cost) | ~half of the 4-point Projectivy penalty | 0.5-1 day | low (we don't rely on TalkBack) | categories with a11y on vs off |
| 8 | **Room/I-O during sync** | removes 100-683ms category queries while EPG syncs | 1 day | low | category query timing during a forced sync |
| 9 | **Allocation audit of draw lambdas** | fewer young-gen GCs mid-scroll | 0.5 day | low | Perfetto heap profile / `art` GC events |
| 10 | **Dependency refresh** (activity, lifecycle, core) | small, mostly correctness | 0.5 day | low | regression check only |
| 11 | **Hybrid: guide grid as a View list** (escape hatch from 2026-10-18) | only worth it if 1-5 stall above 10% | 4-6 days | high | same gfxinfo scenarios |
| 12 | **Full rewrite (Views/Leanback)** | uncertain; F18 says Views are a11y-cheap | weeks | very high | not recommended |

**Details, in order:**

**1 - Bisect.** Candidates for a regression: the crescent cap and wake gradient drawn every frame
over the whole grid (`ui/livetv/EpgGridColumn.kt:262-296`, a new `Brush` built per draw at
`:274`); the supplement-key scan over all programmes in every recompose of `programsByChannel`
(`:303-305`); `expanded` height changes (`:394`). Each is cheap alone; bisecting tells us which.

**2 - One heavy frame per category move.** Today `commitGroup` (`ui/livetv/LiveTvScreen.kt:407`)
does a commit recompose, then rows rebuild in the next frame. Options, cheapest first:
- *Lazy prefetch with a cache window* (Compose 1.10+, we're on 1.11.2): `LazyListState(cacheWindow
  = LazyLayoutCacheWindow(ahead = ...))` keeps rows composed beyond the viewport, and Compose
  1.10+'s **pausable composition** spreads prefetch composition over idle frame time. Expected:
  the row rebuild moves out of the key frame. Check the API's `@Experimental` status at our BOM.
- *Pre-compose the neighbour category*: when focus rests on category N, warm N±1's channel list in
  the S3 cache (already there) **and** its first ~10 rows' slot lists (`slotsFor`, `:363`) off the
  main thread. Expected: the commit frame only swaps data, nothing is computed in it.
- *Skip the intermediate state*: if the two frames are "groupName changes" then "channels arrive",
  publish both in one snapshot so the grid composes once. The reverted merge tried a different
  thing (merging focus moves); read F15's notes before repeating it.

**3 - Guide re-layout per frame.** Each row has its own `horizontalScroll(sharedScroll)`
(`EpgGridColumn.kt:477` header, the row timeline at ~`:738`), so every shared-scroll frame
invalidates layout in every visible row. Replace with one scroll offset read **only in draw**
(`drawBehind { translate(-sharedScroll.value) }`) and a fixed-width row that never re-measures;
focus targets are already explicit (`rowJump`, `:342`), so the row no longer needs a real
scroll container. Also check `scrollToItem` inside `LaunchedEffect(rowJump)` (`:345-352`) against
`LazyListState.requestScrollToItem` (no extra layout pass). Expected, high confidence: horizontal
moves (already 8.4%) and DOWN/UP both lose a layout pass per animation frame.

**4 - Profiles that survive OTA.** `app/src/main/baseline-prof.txt` is a wildcard (whole app) and
there's no startup profile or macrobenchmark module. `updater/UpdateManager.kt:113` installs with
`ACTION_VIEW`, so after each OTA the app runs JIT/`verify` until idle dexopt (**measured:** fresh
install ~10 points worse, JIT categories 26-29% vs AOT 15-18%). Plan: add a `:benchmark` module
(macrobenchmark + `BaselineProfileRule`) that drives cold start, guide scroll and category moves;
ship the generated profile + startup profile (dex layout); install via `PackageInstaller.Session`
writing the APK **and** the `.dm` that AGP emits, so ART compiles at install time (Android 9+ does
this for sessions). Cannot run on the TV while it's in use; generation needs an adb session.

**5 - Fewer nodes in the guide.** Row painting is already one `drawBehind` per row (v0.41.10,
22% -> 14% measured), but each row is still a layout node tree (label block, timeline, marquee
`graphicsLayer` at `:868`). Next step: one `Canvas`-like draw over the visible rows, driven by
`LazyListState.layoutInfo`, with rows as empty fixed-height focus boxes. Highest ceiling, highest
rewrite cost; do it only after 1-3 show where the remaining time goes.

**6 - Images.** No app `ImageLoader` (Coil defaults); `ui/livetv/ChannelLogos.kt` keeps a
count-based `LruCache<String, ImageBitmap>(400)` and converts with `drawable.toBitmap()`, which
copies hardware bitmaps back to software (`allowHardware` is on by default, and `toBitmap()` on
a hardware bitmap allocates a fresh one). Configure one `ImageLoader` in `RedSurfApp`:
`allowHardware(false)` for logos that we draw into a Canvas, `bitmapConfig(RGB_565)` is not
needed (logos have alpha), fixed `size(sizePx)` (already done), a size-bounded LruCache
(`sizeOf = byteCount`, ~8 MB) and a disk cache. Coil 3 is a later, optional upgrade.

**7 - Semantics diet.** Every row, slot and pill carries Compose semantics that Projectivy's
service walks. Mark decorative draw-only children `clearAndSetSemantics {}`, merge row
semantics into one node, and keep focusability unchanged. Measure with a11y on, the family's
real state.

**8 - Sync I/O.** Measured: category query 100-683ms during EPG sync vs ~5ms idle, epg-sync
thread in D state. `UserActivity.awaitQuiet()` gates flushes; check WAL is on
(`setJournalMode(WRITE_AHEAD_LOGGING)` is Room's default on non-low-RAM devices - verify
`isLowRamDevice` on this TV, it may disable WAL), smaller transaction batches, and
`Process.THREAD_PRIORITY_BACKGROUND` on the sync worker's thread.

**9 - Allocations.** Per-frame allocations in draw lambdas: `Brush.horizontalGradient` (`:274`),
`Offset`/`Size` boxing is free (inline classes), but `TextLayoutResult` look-ups through the 256
entry cache allocate keys; cache layouts per slot in the row's `remember(slots)`. Confirm with
Perfetto's Java heap sampling during a 20-press run.

**10 - Dependencies.** `core-ktx 1.12.0`, `lifecycle-runtime-ktx 2.7.0`, `activity-compose 1.8.2`
are two years behind the Compose 1.11 BOM. Upgrade for correctness; no measured gain expected.
Compose 1.12 needs compileSdk 37 + AGP 9.1 - wait for it, don't chase it.

**11 - Hybrid View guide.** Already on record as the escape hatch (`TIVIMATE_LEARNINGS.md`). Only
the grid, as a `RecyclerView`/`VerticalGridView` in `AndroidView`, custom-drawn rows. Gate: start
it only if 1-5 leave the guide above 10% janky with a11y off. Costs: two focus systems at the
Compose/View boundary (our whole `FOCUS_MODEL.md` rule set gets a second implementation).

**12 - Full rewrite.** Not recommended. TiviMate's View stack is faster mostly because it
does less per frame, and items 2-5 copy that. A rewrite would throw away months of
device-verified focus/state behaviour for an uncertain gain; the hybrid (11) captures most of
the upside if it turns out we need Views.

## 3. Smooth preview <-> fullscreen (shrink/expand)

**Today (code):** one `LiveVideoView` (`player/PreviewPlayerHost.kt:27`), a Media3 `PlayerView`
with `surface_type="surface_view"` (`res/layout/redsurf_player_view.xml:9`), placed at the hero
rect or the full window by `Modifier.placedAt` (`ui/livetv/LiveTvScreen.kt:937`, `:1476`). The
switch is a single re-layout: fast (75-91ms first frame, measured) but a hard cut. The old
grow/shrink + PixelCopy snapshot (EPG_WRAPUP 2.4, `player/VideoFrames.kt:36`) was removed.

**What TiviMate does - partly unknown.** Its decompiled code is obfuscated; `AbstractC4059`
is Media3's own `PlayerView` (SurfaceView or TextureView by `surface_type`), and the layout
attribute it uses wasn't recovered. Measured: TiviMate's preview -> fullscreen is 36 frames
(28% janky, p50 57), so it does *something* over ~0.6s. Before building, capture it:
`dumpsys SurfaceFlinger --list` / `--latency` while pressing OK in TiviMate, to see whether its
video layer scales (SurfaceView) or the whole UI is composited (TextureView). Cheap and decisive.

**Options:**

| Option | How | Cost per frame | Verdict |
|---|---|---|---|
| A. Animate layout bounds (`animateBounds`, `SharedTransitionLayout` with default resize, or animating `placedAt`'s rect) | SurfaceView size changes every frame | each size change reallocates buffers; on API 34 Media3 wraps SurfaceView resizes in a `SurfaceSyncGroup` (the `Build.VERSION.SDK_INT == 34` path in its PlayerView) that waits for the next video frame - per-frame stalls | no |
| B. **Fixed full-window SurfaceView, animate scale + translation** | lay out once at window size; `graphicsLayer { scaleX/Y, translationX/Y }` on the host, 0.31 <-> 1 | SurfaceView's position/scale follow the View's transform via SurfaceControl (API 29+), synced with the UI frame by BLAST (API 33+, we're 34); no buffer realloc, the decoder keeps its output size | **recommended** |
| C. TextureView | normal GPU view, any animation | GPU composites every video frame (no HW overlay), extra memory, loses HDR/tunnelling; bad on this 4-core Amlogic | no |
| D. Snapshot (PixelCopy) + crossfade | animate a still, cut to live at the end | cheap, but video freezes ~250ms; already tried and removed | fallback only |

**Design for B:**
1. Keep the `PlayerView` measured at window size in both states (it already is in fullscreen).
   In preview, place it at the window origin and apply `scale = heroWidth / windowWidth` and a
   translation to the hero's top-left (pivot 0,0). Video aspect is the same (16:9 hero, 16:9
   window), so `RESIZE_MODE_FIT` letterboxing scales correctly.
2. Animate one `Animatable` 0..1 over ~220ms, `FastOutSlowInEasing`, read **only inside
   `graphicsLayer {}`** - no recomposition or re-layout during the animation.
3. The browse UI stays composed under it (F16: TiviMate's fullscreen is an overlay on an
   unchanged layout); fade the black backdrop and PlayerScreen's overlays in after the scale ends.
4. The crescent cut (drawn over the SurfaceView on the hero's right edge, dfc1d0e) can't be
   clipped onto a SurfaceView; fade it out on frame 0 of the expand, in at the end of the shrink.
5. Risks to verify on the device: whether the SurfaceView's hole-punch and the HW overlay keep up
   with a per-frame transform on this Amlogic HWC (fallback to GPU composition would show as
   jank in `dumpsys SurfaceFlinger`); preview at scale 0.31 of a 1080p surface costs the same
   decode as today (SurfaceFlinger already scales it 0.31 - measured), so no new memory.
6. Interrupts: Back during the expand reverses the same `Animatable` from its current value.

**Acceptance:** first frame <=90ms (no regression), animation >=55fps with no frame >33ms in
gfxinfo framestats, video never freezes, focus lands as today.

## 4. "Performance Max" sprint

Order matters: measure, then the cheap high-confidence wins, then structural work.

| Step | Task | Acceptance (Chromecast, a11y **on**, compiled) |
|---|---|---|
| 0 | Baseline today's CI build: guide DOWN/UP, RIGHT/LEFT, categories, OK->fullscreen, Back; a11y on and off | numbers recorded in `PERFORMANCE_SALVAGE.md` |
| 1 | Bisect the guide v0.44.4 -> HEAD (item 1); fix the culprit | guide DOWN/UP <=10% janky, p90 <=40 |
| 2 | Draw-only shared scroll (item 3) | guide DOWN/UP <=9%, RIGHT/LEFT <=5% |
| 3 | Category move in one heavy frame (item 2) | categories <=12% (a11y on) / <=10% (off), 1 frame >50ms per move |
| 4 | Semantics diet + Coil config + allocation fixes (items 6, 7, 9) | a11y-on penalty <=2 points; no GC slice inside a key frame |
| 5 | Scale-based shrink/expand (§3, after the TiviMate SurfaceFlinger capture) | §3 acceptance |
| 6 | Macrobenchmark module, generated baseline + startup profile, PackageInstaller + `.dm` OTA (item 4) | first run after an OTA within 2 points of `compile_app` numbers; cold start no slower |
| 7 | Sync I/O (item 8) | category query <=20ms during a forced EPG sync |
| 8 | Gate decision: guide still >10%? then prototype item 5, then 11 | written verdict |

**Status 2026-10-27:** steps 0-3 done. Step 3 acceptance **not met** (categories ~28-31% janky,
a11y on; p90 down to ~53-97ms). Heavy frame is now `EpgGridColumn:measure` ~29ms (recompose 13.8,
applyChanges 7.5). The bench's 0.25s gap commits every press (worst case); a held key commits once.
Next: step 4. Details: `SPRINT_LOG.md` 2026-10-27.
**Step 4 built 2026-10-28 (builds clean, 57/57 tests, not measured on device):** one app
`ImageLoader` (`RedSurfApp`: software bitmaps, 8MB byte-sized memory cache, 32MB disk cache);
`ChannelLogos` reads Coil's memory cache by URL key instead of its own 400-entry `LruCache`; the
now-line wake gradient is built once; guide rows cache their number/name and per-slot title/times
layouts. Semantics were already one node per row (guide, categories). Acceptance (a11y-on penalty
<=2 points, no GC slice in a key frame) still needs the 20-press gfxinfo + Perfetto run.
**Step 7 (2026-10-28, builds clean, 57/57, not measured):** already in place before this step -
WAL pinned (and `ro.config.low_ram` is unset on this TV, so it was never at risk), sync threads at
`THREAD_PRIORITY_BACKGROUND`, `UserActivity.awaitQuiet()` before each flush, 2,000-row
transactions. New: `PRAGMA synchronous=NORMAL` on open (`RedSurfDatabase`), so a commit no longer
fsyncs; logcat `RedSurfDb open synchronous=1` confirms it. Acceptance: category query <=20ms during
a forced EPG sync (compare `XmlTvParser flush ... tookMs` with the 1.4-6s measured before).
**Item 10 (2026-10-28):** `core-ktx`/`lifecycle-runtime-ktx` declarations raised to what the
Compose BOM already resolved (1.16.0/2.9.4) - no change to the APK. `activity-compose` is really
1.8.2; bumping it (1.10.x) changes the library under every `BackHandler`, so it waits for a
device-test sprint with a full Back/long-press-Back sweep.

**Measurement protocol** (from the salvage's lessons, all of them learned the hard way):
- Always `adb shell cmd package compile -m speed-profile -f com.redsurf.tv` (or `compile_app` in
  `scripts/perf-measure.sh`) after install; a fresh install sits at `verify` and reads ~10
  points worse. Step 6 is the one exception (it measures the uncompiled path on purpose).
- Interleave A/B at the same versionCode (A, B, A, B, A, B), 20 presses down + 20 up per run.
- `top -n 1` before each run; SmartTube or a launcher in the background invalidates it.
- Run both a11y states: Projectivy on (the family's reality) and off (comparable with TiviMate).
- Decide on `dumpsys gfxinfo com.redsurf.tv framestats` (janky %, p50/p90/p99, frame count);
  explain with Perfetto (`traced()` markers in `diag/Traced.kt`, RenderThread included - gfxinfo
  and UI-thread trace totals can disagree). Record each result in `SPRINT_LOG.md` same session.
- Report each step as *builds clean*, *tests pass* or *verified on device*; the TV must be free.

**Not in scope:** Lite/Full effect tiers, themes, VOD. Devices: the Shield Pro can confirm a
"Full" tier later; it is not a stand-in for the Chromecast's numbers.
