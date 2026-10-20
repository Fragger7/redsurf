# RedSurf design language - research and three directions (2026-10-20)

Input for the owner's "signature RedSurf look" decision (`TESTING_OUTSTANDING.md`, the user's visual-design note, item 7:
"juvenile, inconsistent and unpolished", dashed lines feel wireframe-y, real estate lost to rounded
corners and padding, maybe the font; reference SluiceBox). Mock-ups: `mockups.html` in this folder
(open it in a browser; tabs for Current / A / B / C, plus a Lite/Full effect-tier toggle).

**Evidence levels used below.** *Seen* = I looked at the pixels this session. *Measured* = numbers
taken from a file or screenshot with a script. *Reported* = from a web source (linked). *Prior
knowledge, unverified* = from general knowledge of the product; I did not see it this session.

---

## 1. What the current UI actually is (measured from a v0.44 device screenshot)

- Background `#09090B`, panels `#18181B` with 10dp radius, 48/32dp safe margins, categories and
  hero are separate rounded cards, so each section spends padding + radius + gap three times.
- Programme cells are a dark maroon (`#330F11`), focused cell `#5A2B32` + 2dp red ring; so red is
  everywhere at low saturation and nothing reads as the one focused thing.
- "No listings" slots are a **dashed centre line** (`EpgGridColumn.kt`, `dashPathEffect(3dp, 5dp)`),
  and supplemented-EPG cells add a dashed start tick. These are the "perforated" lines.
- Type: tv-material3 default (Roboto) everywhere except the Poppins wordmark; grid text 10-13sp.

## 2. App survey - what makes premium TV apps look premium

| App | Evidence | What specifically makes it look premium |
|---|---|---|
| **SluiceBox** (`dev.sluice.sluicebox`) | **Seen** - the 5 Play Store screenshots (downloaded); store text **reported**. Website is a maintenance page. No independent reviews found. | Its "uniqueness" is **themes as whole personalities**, not one style: (1) a calm navy theme - teal-slate cells with ~4dp radius and ~6dp gaps, one bright **yellow outline + yellow fill** focus on both row and cell, categories as a **top row of pills**; (2) a sports theme - **full-bleed stadium photo behind a translucent guide**, thin orange grid lines, **condensed bold sans** (Barlow-Condensed-like) for everything, orange now-marker with a time flag, "LIVE" chips; (3) a 1950s retro theme - illustrated TV set, script fonts, cream/teal/orange blocks; (4) VOD: big left-side condensed caps title over a darkened poster backdrop, gold accent. Store copy: "Theme selection and premium visual layouts"; "Premium now includes all themes and theme color packs". Lesson: **one strong focus colour + one distinctive type voice + imagery** does most of the work; the layouts themselves are ordinary. |
| **TiviMate** 5.3.3 | **Seen + measured** (device screenshot from the S2 session; decoded resources in `~/Development/rea-lab`) | Density without cramp comes from: row pitch **44dp** (≈40dp cell + ≈4dp gutter), separators are **background-coloured gutters, not lines**, near-square cells, cells only one tone above the background (`#1B1E21` on `#131619` - a cool blue-grey, not neutral), focused row one big step lighter (`#424547`), selected cell a desaturated red (`#753A37`). It bundles **one font file: a digits-only subset of Roboto Medium with tabular figures** (glyphs `0-9` only) - it cares that numbers line up and don't jitter. |
| YouTube TV | **Reported** ([YouTube blog](https://blog.youtube/news-and-events/youtube-tv-live-guide-and-library/)) | 2025 guide redesign: "more compact", "take in more information at a glance", a highlighted state at the top, side panel for actions. No visual specifics published. |
| Apple TV app / tvOS | Prior knowledge, unverified | Restraint: near-black, SF type with large size jumps between title and meta, focus = **lift** (scale + soft shadow + parallax highlight), almost no borders or lines. Colour comes from artwork, never from chrome. |
| Google TV home | Prior knowledge, unverified | Top pill tabs; focused card gets a white outline/lift; **white (not brand-colour) focus** on chips/buttons; content imagery fills the hero with a scrim. |
| Netflix / Disney+ / Max / Prime | Prior knowledge, unverified | Imagery-led heroes with left-to-right black scrims; one proprietary typeface used at very few sizes; brand colour used sparingly (Netflix red only for logo/progress/key CTA). |
| Plex | **Reported** ([Digital Trends](https://digitaltrends.com/?p=1389715), [Neowin](https://www.neowin.net/news/plex-reverts-tv-app-layout-after-intense-user-backlash/)) | "Modern" interface uses title artwork as the backdrop; users pushed back hard on navigation changes - layout familiarity matters more than novelty. |
| Channels (DVR) | **Reported**, mostly 2017-18 reviews ([iMore](https://www.imore.com/channels-dvr-apple-tv), [Tools and Toys](https://toolsandtoys.net/channels-for-apple-tv-and-ios/)) | Classic grid + info/imagery on top; praised as "premium and polished" and fast. Current layout not verified. |
| Kodi Arctic Fuse 2 | **Reported** as the most popular/customisable skin ([Reddit thread](https://reddit.birdcat.cafe/r/Addons4Kodi/comments/1i5sd4r/what_skin_is_people_using), [CoreELEC](https://discourse.coreelec.org/t/dolby-vision-profile-vs10-plus-other-info-on-skins/52794/78)); look itself not seen | Known (prior knowledge) for fanart backdrops, blurred art behind translucent panels, big clean sans. A user switched to a skin that was "faster, lighter, simpler and more clear" - effects cost real goodwill on weak boxes. |
| Sky Q / Sky Glass | **Reported** ([Sky UI typography docs](https://sky-ui.cf.sky.com/foundations/typography/usage)) | One bespoke family (Sky Text, regular + medium only; a separate Headline cut for display). Guide offers a magnifier and high-contrast mode - accessibility as a setting, not a compromise in the default. |

**Top 3 references for RedSurf:** TiviMate (density, separators, tabular numbers), SluiceBox (a
signature through one focus colour + one type voice + imagery), Google TV / Apple TV (restraint,
focus as lift, colour from content rather than chrome).

## 3. Live-guide design findings (what dense-but-calm guides do)

1. **Separate with tone and gutters, not lines.** TiviMate: 2-4dp background-coloured gutters; no
   strokes at all. Lines only where a scale is needed (the time ruler's ticks).
2. **Cells sit one tone above the background**; the focused *row* is one clear step lighter; the
   focused *cell* is the only saturated thing on the grid.
3. **Few type sizes, numbers tabular.** Channel numbers, times and the clock must use tabular
   figures or columns wobble and the clock jitters every minute.
4. **Corners small or none in the grid.** Radius in a dense grid eats perceived space and turns a
   timeline into "a pile of pills". Save pill shapes for interactive chrome (nav, chips, buttons,
   toasts) where they read as "pressable".
5. **"No data" is quiet, not decorated.** A flat, slightly darker fill with tertiary text. No
   dashes, no hatching.
6. **Aired time is a darker tone inside the current cell**, not a separate element.
7. **The now-line is the second-loudest element** (after focus) and should have a single,
   recognisable head (a dot, a chip, or - in Direction A - the crescent).

## 4. Hardware feasibility / cost (Chromecast with Google TV 4K, Mali-class GPU, 1080p UI)

Costs are **estimates from how Android's renderer (HWUI) works, not measured on the device** -
each should get a `gfxinfo` before/after when built. "Lite-safe" = acceptable on this device.

| Effect | How on Android/Compose | Cost here | Lite-safe? |
|---|---|---|---|
| True backdrop blur (blur what's behind a panel) | None in Compose. `Modifier.blur`/`RenderEffect` (API 31+) blurs the composable's *own* content. You can fake it by drawing a blurred copy of the backdrop clipped to the panel. Window blur-behind (API 31) exists for dialogs but is commonly disabled on TV devices (unverified for this one). | High if live (a blur pass every frame the content changes); low if the blurred thing is static (cached in the layer). | **No** live. Yes for a **pre-blurred static bitmap**. |
| Blur over live video | Impossible from Compose: video is a SurfaceView (separate hardware layer; Compose can't read its pixels). | - | No (not possible). |
| "Glass" faked | Translucent fill + 1dp top highlight + baked grain over a static gradient/backdrop bitmap. | Low (blending a few panels). | **Yes** |
| Colour sampled from video ("ambient") | `PixelCopy` from the SurfaceView into a tiny (e.g. 32x18) bitmap, average colour or upscale with bilinear filter (a free "blur"), once per zap + every ~10s. DRM-protected streams return black (IPTV usually isn't). | Low if throttled; unmeasured. | Borderline - Full by default, Lite if measured fine. Lite fallback: colour from the channel **logo** via `Palette` (one-off, CPU, off main thread). |
| Translucency over video | UI layer with alpha over the video plane; the display hardware composites it. TiviMate does this for its overlays. | Low for small panels; medium if the whole guide is translucent over full-screen video. | Yes (small areas) |
| Gradients / scrims | One `Brush` shader per draw. | ~0 | **Yes** (add dithering or grain to avoid banding) |
| Shadows / elevation | RenderNode shadows; coloured shadows API 28+. Fine on one element, costly on every row. tv-material3 `Glow` is one of these. | Low on one element; medium+ per-row. | Only on a single focused item; Full otherwise |
| Glow on focus | A shadow or a pre-rendered radial gradient image behind the focus. | Shadow: low-medium; pre-rendered image: ~0 | Yes as a pre-rendered image; Full as a live shadow |
| Animated focus (move / scale) | Move the cursor as **its own layer** (`graphicsLayer` translation) over the Canvas grid - the grid itself doesn't redraw. Scaling a cell that is painted inside the grid Canvas forces the whole grid to redraw each frame. | ~0 as a separate layer; medium if inside the grid Canvas | Yes (separate layer), no scale on the grid |
| Shape morphing | `androidx.graphics:graphics-shapes` Morph; recomputes a path per frame. | Low for one element (nav indicator), never per row. | Yes for one element |
| Custom fonts | Static TTF per weight (~70-150 KB each; avoid variable fonts - axes need API 26+, minSdk is 23). Glyphs are cached; per-frame cost same as Roboto. Tabular figures: `TextStyle(fontFeatureSettings = "tnum")` / `Paint.fontFeatureSettings = "tnum"` (API 21+). | ~0 per frame | **Yes** |
| Noise / grain texture | Bake into the backdrop bitmap once (free) vs. a tiled shader over the screen (an extra full-screen blend). Animated grain: no. | Baked ~0; live overlay medium | Baked: **yes** |
| Video behind the whole UI (SluiceBox's stadium) | Full-screen video plane behind a translucent guide. Readability needs heavy scrims; the whole UI becomes translucent blending. | Medium; unmeasured | Full-only, and only as an option |
| Clipping video corners / shapes | SurfaceView can't be clipped like a composable. Paint opaque masks *over* its edges instead (works on a solid background; inexact over a gradient). TextureView can be clipped but costs an extra GPU copy per frame. | Mask: ~0; TextureView: medium | Mask on solid backgrounds: yes |
| Dashed lines (current) | `PathEffect.dashPathEffect` turns a line into a path in HWUI. | Higher than a plain rect (not measured) | Remove regardless - it is also the "wireframe" look |

**Glassmorphism verdict:** real glass (live blur of what's behind, especially video) is not
available on this stack and would cost frame time on this GPU even where it is possible. The
*look* can be faked convincingly on the Lite tier with a **static** red-to-black backdrop bitmap
(grain baked in) + translucent panels + a 1dp top highlight. A Full tier can add a blur of that
static backdrop (RenderEffect, API 31+) and a colour wash sampled from the video. Never blur the
grid, never blur over video.

## 5. Fonts (measured: Google Fonts files checked with fontTools)

| Font | Tabular figures (`tnum`) | x-height / em | Width vs Inter at equal x-height | Notes |
|---|---|---|---|---|
| **Poppins** (wordmark) | **No** - and its default digits are proportional | .554 | 104% | Keep for the wordmark and at most display titles. **Never for times, channel numbers or the clock.** |
| Inter | Yes | .546 | 100% | Largest x-height; most neutral; best legibility at distance. |
| Manrope | Yes | .540 | 93% | Geometric like Poppins, pairs naturally, 7% narrower. |
| Plus Jakarta Sans | Yes | .536 | 101% | Geometric, friendly, pairs with Poppins. |
| Figtree | Yes | .500 | 102% | Friendly; smaller x-height. |
| **Barlow Semi Condensed** | Yes | .509 | **88%** | 12% more text per channel-name slot; still very legible. |
| **Barlow Condensed** | Yes | .511 | **79%** | Display/numerals; the "broadcast graphics" voice. |
| Barlow | Yes | .509 | 98% | |
| Rubik, Saira, Archivo | Yes | .51-.53 | 100-103% | Fine, nothing special here. |
| DM Sans (Google variable build) | **No `tnum`** | .526 | 102% | Avoid for numbers. |
| Sora | `tnum` yes, `pnum` no | .534 | 108% | Wide. |
| Outfit | Yes | .460 | 111% | Small x-height + wide - worst for distance/density. |

Recommendations: **A** Barlow Semi Condensed (UI) + Barlow Condensed (display, numerals, caps
labels). **B** Inter for everything, `tnum` on all numbers. **C** Manrope (UI, `tnum` numbers) with
Poppins SemiBold for hero titles only. All: ship static weights only (2-3 per family).

---

## 6. Three directions

All three keep: Accent `#DC2626`, the mark, the Poppins SemiBold wordmark, the auto-hiding top pill
nav, and the current Live TV structure (nav → hero band → categories + EPG grid, double-height
focused row). All three also share these fixes, which are the biggest wins on their own:

- **No dashed lines anywhere.** Gaps = flat fill one tone darker than a cell + "No guide data" in
  tertiary text. Supplemented-EPG marking (still needed: colour + a non-colour cue) becomes a small
  solid corner notch, not a dashed tick.
- **Gutters, not lines**, between rows and cells (2-3dp of background).
- **No card around the categories or the grid.** Regions separate by tone; the safe margin drops
  from 48/32dp to 24/14dp (TiviMate runs ~16dp; modern TVs rarely overscan - worth one check on the
  family TV with a test pattern before committing).
- **Tabular figures** on every number; one type family per direction at 5-6 sizes.
- **Cursor drawn as its own layer** above the Canvas grid so focus motion never redraws the grid.

Sizes are dp at 1080p (this device: 960x540dp, density 2; the mock-ups are in px = 2 x dp).
"Rows visible" counts channels including the double-height focused one.

### A. Crest - bold / signature

**Idea:** broadcast-graphics confidence: condensed type, square edge-to-edge blocks, and one red
wave - the mark's crescent - that cuts the hero and caps the now-line. Focus is a solid red block,
the loudest and simplest cursor on the screen.

- **Principles:** one loud thing (the red block); square geometry everywhere content lives, pills
  only for chrome; condensed type buys density; the crescent is the only ornament and is used
  exactly twice (hero cut, now-line cap).
- **Colour:** background `#0A0A0B`; label column `#121214`; cell `#161618`; focused row `#202023`;
  aired part of a cell `#111113`; no-data `#0F0F11`; text `#F5F5F4` / `#A8A29E` / `#6B6763` (warm
  greys to sit with the red). Red **is** used for: focused cell/row fill, active nav pill, now-line +
  crest cap, LIVE tag, progress fill, selected-category tick, exit-toast countdown bar. Red is
  **not** used for: idle cells, section labels, text, borders. Toasts are the inverse (off-white
  pill, black text) so they never look like focus.
- **Type:** Barlow Condensed SemiBold - hero title 31, ruler 13, channel numbers 15 (`tnum`), caps
  labels 12 (+1dp tracking); Barlow Semi Condensed - channel/cell titles 15 Medium, meta 14, body
  13.5, secondary 12. Poppins only in the wordmark.
- **Shape:** 0dp radius on cells, rows, categories, settings rows, video; pills (fully round) for
  nav, chips, toggles, toast. The video's right edge is the crescent cut.
- **Density:** margins 24/14dp; nav 34dp; hero 152dp; ruler 22dp; row pitch 38dp (36 + 2dp gutter),
  focused 76dp; label block 176dp (number 28, logo 26); categories 168dp, rows 32dp. **~7 channels
  with the nav shown, ~8 with it hidden.**
- **Separators:** 2dp background gutters; ruler ticks only; a single red/grey rule under the nav.
- **Focus:** grid - solid red cell, white text; focused row band one step lighter. Lists and
  settings - full-width red bar. Nav - red fill = current, white 2dp outline = focused.
- **Motion:** cursor slides 120ms (fast-out-slow-in); hero text crossfade 150ms; no scale.
- **Lite (baseline):** all of the above - flat rects, one static crescent path drawn **over** the
  video edge (SurfaceView can't be clipped), text. Cheaper than today (no round rects, no dash
  paths, no glow).
- **Full adds:** colour wash behind the hero sampled from the video (PixelCopy, low); a one-shot
  light sheen across the focused cell when focus settles (one shader on one cell for ~400ms, low);
  red bloom on the now-line and crescent (static gradient image, ~0 - could even be Lite).

### B. Graphite - restrained premium

**Idea:** quiet, tonal, Apple-TV-calm. Cool graphite surfaces, Inter, a white focus lift like
Google TV; red appears only where something is live, now, or the current tab - so when red appears
it means something.

- **Principles:** tone does the structure; white means "you are here", red means "live/now/yours";
  small consistent radii; generous type contrast between title and meta instead of colour.
- **Colour:** background `#0C0D0F`; cell `#16181B`; focused row `#1E2125`; aired `#121417`; no-data
  `#101215`; focus fill `#F2F2F3` with text `#0C0D0F`; text `#ECEDEE` / `#9BA1A6` / `#5F656B`. Red
  **only** for: now-line + time chip, LIVE dot, progress fill, active nav pill, toggles that are on.
  **Not** for focus, selection, labels or borders.
- **Type:** Inter - hero title 26 SemiBold (-0.4 tracking), meta 13 Medium, body 12.5, channel 14
  Medium, cell 13.5 Medium, secondary 11, ruler 11.5 Medium; all numbers `tnum`.
- **Shape:** cells 4dp; video 8dp (corner masks drawn over the SurfaceView); category and settings
  rows 6-7dp; pills for nav, chips, toggles, toast.
- **Density:** margins 24/14dp; hero 148dp; row pitch 40dp (38 + 2), focused 80dp; label 184dp;
  categories 176dp. **~6-7 channels with nav, ~8 without.**
- **Separators:** 2dp gutters; 4dp ruler ticks in `#2A2E33`.
- **Focus:** white fill + dark text everywhere (grid cell, list row, settings row); selected
  category = raised tone + white 2dp tick.
- **Motion:** cursor slide 100ms; hero crossfade 150ms.
- **Lite:** tonal fills, radii, white focus - no shadows, no blur.
- **Full adds:** soft drop shadow under the focus (on the cursor layer only, low); 1.02 scale lift on
  settings rows and buttons, never the grid (low); a faint tint + pre-blurred ghost of the channel
  logo behind the hero (static bitmap, ~0 after decode).
- **Risk:** the most "correct", least memorable. Its signature has to come from discipline (and the
  wordmark), not an ornament.

### C. Surfglass - atmospheric

**Idea:** the guide floats over deep water: a red-to-black backdrop with baked grain, smoked
translucent panels, pill chips and a red ring. Closest to SluiceBox-level personality, with the
glass honestly faked for a weak GPU.

- **Principles:** atmosphere comes from one static backdrop, not effects per element; translucency
  over a still image, never over video; rounder, softer, more "consumer" - judge whether it reads
  premium or juvenile (it is closest to the current app's softness).
- **Colour:** backdrop radial `#4A0B11` → `#1C0709` → `#07070A` (+ ~7% grain, baked); panels
  `rgba(255,255,255,.035-.045)` + 1dp `rgba(255,255,255,.07)` top highlight; cell `rgba(255,255,255,.055)`;
  focused row `.095`; focus = `rgba(220,38,38,.30)` fill + 1.5dp `#EF4444` ring; text `#FFFFFF` /
  68% / 42%. Red used for: focus ring + wash, selected category chip, now-line, LIVE, progress
  gradient (`#DC2626` → `#F87171`), active nav, and the backdrop itself.
- **Type:** Manrope - cells/channels 13.5 SemiBold, meta 13, body 12.5, secondary 11, numbers `tnum`;
  hero title in Poppins SemiBold 25 (brand voice; no numbers in it).
- **Shape:** cells 6dp; panels 14dp; video 10dp; categories, logos, tags, nav, toast as pills/circles.
- **Density:** margins 24/14dp; hero 148dp (panel with 6dp padding); row pitch 42dp (39 + 3),
  focused 84dp; label 184dp; categories 172dp. **~6 channels with nav, ~7 without** - the least dense.
- **Separators:** 3dp gutters through to the backdrop; no ruler ticks.
- **Focus:** ring + red wash (grid, settings); chip ring for selected category.
- **Motion:** cursor slide 120ms; backdrop colour cross-fade 600ms on zap (Full).
- **Lite:** static backdrop bitmap + translucent fills + ring. Cost: low (a handful of translucent
  panels blending over one bitmap).
- **Full adds:** video-sampled colour wash behind the hero (PixelCopy, low); RenderEffect blur of the
  static backdrop under the panels (API 31+, cached, low-medium); outer glow on focus ring and
  now-line (pre-rendered image ~0, live shadow low-medium); 1.015 scale on focused settings rows.

### How to choose (my read, not a decision)

A is the most *ownable* - nobody else in IPTV looks like broadcast graphics with a crescent, it is
the densest, and it is the cheapest to draw. B is the safest premium and easiest to keep
consistent. C is the closest to SluiceBox's personality but is the softest and least dense, which
is the direction the owner's complaints point away from. A hybrid worth considering: **A's geometry
and type + B's white-on-dark settings/toasts** if A's all-red focus feels too loud in long lists.

## Sources

- SluiceBox Play listing (description, "What's new", 5 screenshots viewed):
  https://play.google.com/store/apps/details?id=dev.sluice.sluicebox&hl=en_US ; developer site
  (maintenance page): https://152.53.171.50.sslip.io/
- YouTube TV guide redesign: https://blog.youtube/news-and-events/youtube-tv-live-guide-and-library/ ;
  https://9to5google.com/2026/01/11/youtube-tv-live-guide-redesign-2/
- Android TV typography guidance: https://developer.android.com/design/ui/tv/guides/styles/typography
- Plex: https://digitaltrends.com/?p=1389715 ; https://www.neowin.net/news/plex-reverts-tv-app-layout-after-intense-user-backlash/
- Channels: https://www.imore.com/channels-dvr-apple-tv ; https://toolsandtoys.net/channels-for-apple-tv-and-ios/
- Arctic Fuse 2: https://reddit.birdcat.cafe/r/Addons4Kodi/comments/1i5sd4r/what_skin_is_people_using ;
  https://discourse.coreelec.org/t/dolby-vision-profile-vs10-plus-other-info-on-skins/52794/78
- Sky typography: https://sky-ui.cf.sky.com/foundations/typography/usage
- Fonts: TTFs from https://github.com/google/fonts (ofl/), inspected with fontTools (GSUB features,
  OS/2 x-height, advance widths of a sample string).
- TiviMate: device screenshot (S2 session, colours sampled) and `~/Development/rea-lab` decoded
  resources (bundled font = Roboto Medium, digits only).
- Current RedSurf: v0.44 device screenshot (colours sampled), `ui/theme/*.kt`, `EpgGridColumn.kt`.
