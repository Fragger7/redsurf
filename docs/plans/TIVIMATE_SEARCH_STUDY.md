# TiviMate Search study (2026-10-31, over adb, TiviMate `ar.tvplayer.tv`)

Screenshots: `/private/tmp/claude-501/-Users-admin-Development-Claude-RedSurf/aebeeaec-a618-4ff6-839f-722b5e06d04c/scratchpad/tm-search/NN-*.png`.
Everything below is OBSERVED unless marked (guess). Nothing in TiviMate was changed.

## Not done (time limit 03:01)
- Query `espn` not typed. Long-press OK on a result not tried. Search gear / TiviMate settings not opened.
- Channel stream used for the OK test was probably dead ("No information"), so preview/fullscreen content was black.

## 1. Entry (01-launch, 02-menu)
- Live TV screen: ~640x360 rounded preview top-left, programme title/time/progress/description right, star top-right, EPG grid below. Background ~#121518.
- DPAD_LEFT opens a ~440px dark left rail: Search, TV (red-brown pill when current), Movies, Shows, Recordings, My list, Settings (bottom). Search is the top entry; UP from TV, then OK.

## 2. Empty Search (03-search-empty)
- Leanback SearchFragment look. Round light-grey mic button top-left (~125px); wide light-grey rounded field (x~356-1556, y~52-164), hint "Speak to search"; grey gear icon top-right.
- Below: "Search history" heading with trash icon at right; two columns of dark pill chips (ESPN hd, nfl, toy story, hum, a / phila, f1, ary, aq, ESPN). Screen dimmed, no results.
- Initial focus is the mic; DPAD_RIGHT goes to the field (04-field-focus): hint becomes "Search", caret, and an on-screen keyboard (~840px wide, bottom-right) with a hold-to-speak banner.

## 3. Typing "phila" (05-phila, 06-phila-nokb)
- Results update live while typing; no submit. Keyboard shows suggestion chips and overlaps results. BACK hides the keyboard and keeps results and the text (the field is still focused, results look dimmed).

## 4. Results layout (06-09)
Vertical stack of typed sections, each with a left label; order: Movies, Shows, Channels, Programs.
- Movies / Shows: playlist chip, then a row of poster cards (~246x420, title overlay at bottom; real posters when available, grey placeholder otherwise; Shows card has a red rounded rating tag like "8.0").
- Channels: playlist chips (act as per-playlist tabs), then cards ~236x180: logo, name truncated with "..", second line current programme or "No information", thin progress bar at the bottom.
- Programs: playlist chips, then a vertical list: channel tile (logo+name) left, programme title in red-tinted text, time range, progress bar, "101 min". A larger detail pane on the right shows full title/time/duration of the focused programme.
- Scrolling vertically collapses the previous section label to a small dim label.

## 5. Focus styling
- Focused chip: solid dark red-brown pill (~#7A2B2B) with white text; unfocused: grey outline/dark pill.
- Focused card: ~1.1x scale, lighter grey fill (NOT red), shadow, name marquees.
- Focused field: lighter; dims to dark grey once focus leaves.
- DPAD_DOWN from the field: Movies chip, then its cards, next section chip, and so on (09-channels-chip, 10-channel-card).

## 6. Selecting a channel (11-ok1, 12-ok1-later, 13-ok2, 14-ok2-later, 15-back)
- First OK on a channel card: no visible change after 4-7 s (no preview, no fullscreen); activity stayed MainActivity. (Could be a dead stream; unconfirmed.)
- Second OK: went to a fullscreen player surface (dark/black frames, nothing playing; the stream seems dead).
- BACK from that: lands on the main Live TV screen (NOT back on Search results), with the chosen channel selected in the EPG grid (red name/number, red-brown current cell) and the preview panel black (15-back). So search results are not restored after playing a result (observed once).

## Takeaways for RedSurf (partly guesses)
- Live-as-you-type, no submit; grouped typed sections with playlist chips per section; history chips when empty; red-fill focus on chips, lifted lighter cards for results.
- TiviMate loses the search results on Back from playback; RedSurf can do better by restoring query, section and the focused card (guess/opportunity, consistent with AGENTS.md focus rules).

## Follow-up 2026-10-31 03:00
Package is `ar.tvplayer.tv`. Screenshots 19-25 in tm-search/. Time limit meant only part was done.
- OBSERVED: with `espn` typed (BACK hid keyboard), DOWN x3 lands on first Movies card, further DOWN reaches Shows, then Channels (chips = per-playlist tabs, cards logo/name/current programme e.g. "US: ESPN HD - College Football"). A Programs section (channel tile + title/time + detail pane) follows.
- NOT DONE (time): the "single OK on a live ESPN channel, preview?" test. The one OK I sent landed on a no-EPG ":ESPN+ 100" card of another playlist (focus moved to the first card of the next chip tab after DOWN) and showed no preview/navigation after 4s (23-ok1.png). Still unknown whether a live channel previews. Earlier study: first OK also did nothing visible, second OK went fullscreen.
- OBSERVED: long-press OK on a channel card opens a small dark popup menu anchored under the card with exactly 2 rows: "Add to Favorites" (focused, red-brown fill) and "Open in external player" (24-longpress.png). BACK closed it with nothing chosen; search screen and focus on the same card unchanged (25-afterback.png).
- Suggested for RedSurf (guess): long-press OK menu on search results mirroring this (favourite + external player).
