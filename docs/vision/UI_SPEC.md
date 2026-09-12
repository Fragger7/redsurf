# RedSurf UI Specification

Derived from three sources, in priority order:
1. `mockups/tv_*_red_black.jpg` — **the colour and mood target** (RedSurf's own design)
2. `../../StreamVault UI Mocks/` — **the layout and control-placement target** (borrow structure, NOT colour)
3. `../../TiviMate Screenshots/` — **the workflow and interaction target**

> The user's instruction, verbatim: *"Don't copy the color scheme, rather the placement of the
> UI controls."* StreamVault is blue/navy. RedSurf is red/black. Take StreamVault's geometry,
> never its palette.

## 1. Palette

| Token | Value | Use |
|---|---|---|
| `background` | `#09090B` | App background, behind everything |
| `surface` | `#18181B` | Cards, panels, list rows |
| `surfaceRaised` | `#27272A` | Focused row background, elevated chips |
| `accent` | `#E11D48` | Brand red — focus ring, active nav pill, "now" line, LIVE badge |
| `accentGlow` | `#E11D48` @ 40% blur | The focus glow from the mockups |
| `textPrimary` | `#FFFFFF` | Titles, channel names |
| `textSecondary` | `#A1A1AA` | EPG times, synopsis, "No schedule information" |

Already the de-facto palette in code (`0xFFE11D48` / `0xFF09090B`). Formalise it as tokens; stop
hardcoding colours per composable.

## 2. Focus model (the single most important thing)

On TV, **the focus ring is the interface**. Two visually distinct states that must never be conflated:

- **Focused** (where the D-pad cursor is): `accent` outline ring, 2dp, plus outer glow, and a
  subtle scale-up (~1.04). Exactly the red glow in `tv_settings_red_black.jpg`.
- **Selected / active** (the current tab, the playing channel): filled `accent` pill or left bar,
  no glow.

Both states can be on the same item. StreamVault demonstrates the distinction well: `Home.png`
shows a white outline ring on the focused item while `LiveTV.png` shows a filled pill on the
active one.

Every focusable element gets both states. No exceptions — a screen where focus is invisible is a
broken screen.

## 3. Top navigation strip

Replaces the left drawer. From `StreamVault/Home.png`:

`[ RedSurf ]   ⌂ Home   ▶ Live TV   ★ Movies   ☰ Series   ⓘ Guide   🔍 Search   ⚙ Settings`

- Wordmark left, nav items in a horizontal row, each an icon + label pill.
- Sits in a rounded container inset from screen edges (TV overscan safety: keep 48dp margins).
- Focus = red ring. Active = filled red pill.
- Collapses/hides entirely during fullscreen playback.

## 4. Live TV — three-column layout

From `StreamVault/LiveTV.png`, which matches TiViMate's information architecture:

| Column | Contents |
|---|---|
| **Left — Categories** | Search box, "Quick filters", then group rows each showing **name + channel count** right-aligned |
| **Middle — Channels** | Group title + provider + count header, search box, then rows: logo chip, **channel number**, name, and current programme as a subtitle (`"No schedule information"` when EPG is missing) |
| **Right — Preview** | Live video thumbnail on top, then channel name, programme title, `14:15 – 14:27`, a progress bar, synopsis, and the hint **"Press OK again to open this channel"** |

**Two-press interaction**: first OK previews the channel, second OK goes fullscreen. Adopt this —
it makes browsing cheap and is a large part of why this layout feels good.

Cards: ~16dp radius, generous padding, `surface` on `background`.

## 5. Player overlays

From `TiviMate/Channelplayeroverlay.webp` and `GuideOverlayWhileChannelPlaying.png`. Video keeps
playing underneath at all times — per `PRODUCT_VISION.md`, "The Player IS The App".

- **OK short press** → bottom HUD: programme, timeline, next programme, resolution, audio format,
  plus a quick-controls row (play/pause, audio track, subtitles, aspect ratio).
- **LEFT** → channel list slides in from the left edge.
- **RIGHT** → mini-EPG for the current channel, or last-channel zap (configurable).
- **UP/DOWN with HUD hidden** → zap to prev/next channel in group.
- Full matrix in `PRODUCT_VISION.md` §2.

## 6. VOD / Movies

Target quality: Netflix / Apple TV, per the user. Reference `StreamVault/MovieInfo.png` and
`Movies.png`. Poster grid → detail page with backdrop, poster, rating, synopsis, cast.
Metadata from **TMDB** (free for personal use). TVDB deferred — licensing unverified.

## 7. Multiview

From `TiviMate/MultiviewScreen.webp`: a grid of live tiles, with a context menu per tile —
*Add screen, Search and add, Change channel, Play, Enlarge screen, Full screen, Remove screen*.

**Hardware ceiling on the target device (Chromecast with Google TV, 1st gen): 2 tiles.** See
`../plans/HARDWARE.md`. Build the feature so tile count is a configurable limit, defaulting to 2
on this class of device, rather than assuming 4.

## 8. Overscan and text

- Keep all content within the overscan safe margin — TVs crop edges. Android TV's rule is 5% per
  edge, which on this 960×540dp canvas (1080p at density 2) is **48dp horizontal, 27dp vertical**;
  the app uses 48 / 32. (The earlier 48dp-all-round spent 96 of only 540 vertical dp on margin.)
- Minimum body text 18sp; TV viewing distance is ~3m. Nothing below 14sp anywhere.
- The user's own mockups are correctly sized; match their proportions.
