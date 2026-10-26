# Settings — the end-state shell, built now (user decision, 2026-09-13, Opus session)

**Why now.** The current Settings screen is three buttons and a playlist list stacked in the
middle of the page - it reads as unfinished because it is. The user's call: build the *final*
Settings page structure today, with every category and every planned row present, and fill it in
as modules land. Two payoffs: the app stops looking half-built in the one screen every tester
opens, and Settings becomes the visible, always-current backlog for every settings-shaped idea -
a row exists before the feature does.

**One-line design:** *TiviMate decides what's there and where; StreamVault decides what it looks
like.* References: `docs/vision/references/tivimate/TiviMateSettingsMenu.jpg` for the taxonomy,
`docs/vision/references/streamvault/Settings.png` for the layout.

## Layout — from StreamVault

Two panes under the existing top nav strip, inside the 48dp safe area like every other screen:

- **Left: category rail.** Nine categories, each a row with a small square glyph (initial letter
  on a tinted tile, exactly like StreamVault's) and the name. Focused/selected states per
  `UI_SPEC.md` - the same `GroupsColumn` row treatment, not a new component.
- **Right: settings list.** Rows are `Label ······ Current value`, value right-aligned in the
  accent colour. Thin dividers between sections inside a category. Same `ChannelsColumn`
  skeleton: header, then a `TvLazyColumn` of rows.
- **Focus:** LEFT/RIGHT move between rail and list; UP/DOWN move within. Selecting a category
  on the rail (focus, not OK - same as Live TV's categories) switches the right pane. OK on a live
  row acts (toggle, open a picker, run an action). Back from the list returns to the rail; Back
  from the rail goes to Home, as the shell already does.
- **Palette:** ours (red/black, `UI_SPEC.md`), not StreamVault's blue. Borrow the structure,
  never transplant the look.

## Taxonomy — from TiviMate, in TiviMate's order

Live rows are the ones that exist and work today. **Everything else is a grey row.**

| Category | Live today | Grey rows (real names, planned defaults) |
|---|---|---|
| **General** | — | Resume last channel on launch · Off (lands with Phase 2 #2.5) / Start on · Live TV / Language · System |
| **Playlists** | Loaded playlists (each: name, type, **Remove** w/ confirm) · Add playlist · *Danger zone:* Reset everything (confirm) | per playlist: Rename · Refresh / re-sync · Content · Live+VOD (the selector the on-screen forms lack) |
| **EPG** | — | Sources · none / Time offset · 0h / Refresh every · 12h (all Phase 3) |
| **Appearance** | — | Resolution badge · Class (SD/HD/FHD/4K) vs Pixels / Hide nav strip when idle · Off / Accent · Red |
| **Playback** | — | Black screen between zaps · Off / Auto frame rate · Off / Buffer · Default / Preferred audio · Auto / Preferred subtitles · Off / Default aspect · Fit |
| **Remote control** | — | Long-press OK · Context menu / Digit keys · Channel number entry |
| **Parental controls** | — | PIN · Not set / Hidden groups · none |
| **Other** | — | Backup & restore / Clear watch history / Diagnostics & logs |
| **About** | Version (`BuildConfig.VERSION_NAME`) · Check for updates + inline status (moved here from the top of the old screen - this is where people look for it) | Auto-update · On / **Created by · Faraz Ahmad** (user idea, 2026-09-13 - exact wording/role not yet confirmed) |

Where a grey row's feature is already in `AGENTS.md`'s backlog, this table is now the canonical
home for it; keep the two in sync (backlog entry says which Settings row it becomes).

## Grey rows — the rule (user decision, 2026-09-13)

- **Real name, real shape, planned default value** - the page previews the finished product.
- **Grey and unfocusable.** The D-pad skips them entirely; there is nothing to press. No
  "coming soon" copy - grey + skipped says it. This is the "never claim it works" rule applied to
  UI: a tester can never land on fiction.
- **A category with zero live rows** (EPG, Parental controls, Remote control, Other today):
  RIGHT from the rail has nowhere to go, so focus stays on the rail. That is correct - and it is
  exactly the focus-escape shape hunted in the player on 2026-09-12. *(As built, 2026-09-13: not
  via the `focusProperties { exit = Cancel }` trap this line originally specified - that turned
  out to behave inconsistently by direction in practice and was replaced with explicit
  `onPreviewKeyEvent` interception. Same intent, different mechanism - see `SPRINT_LOG.md`.)*
- **Flipping a row live** is: make it focusable, wire the value, give OK an action. Nothing
  about its position or label changes. Log it under the category in this file.

## Not in scope

- No real settings persistence layer beyond what a live row needs today (Playlists rows hit the
  DB directly, as they already do). `SettingsManager`/`PlayerSettings` exist in the codebase but
  are unwired (`AGENTS.md`, "Built but never wired up") - do not resurrect them for grey rows.
- No search across settings, no icons beyond the initial-letter tile.

## Acceptance

**Machine-verifiable (ADB sweep):**
1. From Home, nav to Settings; rail has nine categories in the order above (uiautomator dump).
2. Rail UP/DOWN through all nine; right pane content changes per category (logcat
   `settings -> category`).
3. On Playlists: RIGHT lands on the first live row; DOWN walks only live rows - grey rows never
   report focus; Back returns to the rail.
4. On a zero-live-row category: RIGHT leaves focus on the rail (logcat `focus ->` unchanged).
5. Playlists → Remove → confirm → the row is gone and Live TV's categories no longer show that
   playlist's groups; the other playlist's groups remain.
6. About: version row shows the running `versionName`; Check for updates transitions
   Idle → Checking → UpToDate/Available in logcat.
7. Focus discipline: leave Settings for Live TV and come back - the same category is selected.

**Feel/vision (user):**
- Against `streamvault/Settings.png`: does the two-pane read as the same family, in our palette?
- Are grey rows obviously grey - never mistaken for something to press?
- Is "Check for updates" where you'd look for it?
- Does the rail's initial-letter tile look deliberate or cheap? (Opus judgment call if cheap.)

## Order

Build this **before** the Player sprint - it is small, self-contained, and gives #2.5's "Resume
last channel" toggle a real row to land in rather than another stub. Sonnet-lane.

## Status: built, swept, and feel/vision-reviewed, 2026-09-13

All 7 machine-verifiable acceptance lines pass, on device, over two sweep passes - three real
bugs found and fixed along the way (LEFT-to-rail navigation, focus lost on confirm dialogs, and
a pre-existing bug where removing the last playlist never returned to Onboarding). Full account:
`docs/plans/SPRINT_LOG.md`'s 2026-09-13 entry.

**Feel/vision results (user, same day):**
1. Two-pane layout: **good first draft, close to StreamVault.** Textual parity with TiviMate's
   own copy per row not yet individually verified.
2. Grey rows: **clear enough** - not visually heavy-grey, but focus skipping them reads as
   "disabled" unambiguously. No change requested.
3. "Check for updates" location: **confirmed correct.** Two follow-ups from this question, both
   logged in `AGENTS.md`'s Backlog: (a) the rail doesn't scroll - "About" is cut off and DOWN
   can't reach it, a real bug, not a taste call; (b) add a "Created by" row to About (this file's
   table above).
4. Rail tile treatment: not yet judged - the user asked what the question referred to rather
   than answering it. Re-ask once the scroll bug (3a) is fixed, since it currently affects
   whether "About" (bottom of the rail) is even visible to judge.
5. Not answered as asked (LEFT/Back's nearest-row landing) - the user redirected to two unrelated
   Live TV bugs instead, both logged in `AGENTS.md`'s Backlog: the GroupsColumn RIGHT/OK-entry
   question is now **decided** (always the first channel), and a new one was found (recent-channel
   tile selection across categories doesn't update `selectedGroup`, so Back lands on the wrong
   category). The original question here is still open - re-ask separately.

## As-built update, 2026-10-26 (overnight run, v0.44.29-30)

Rows flipped live since the table above was written, all verified on the TV:
- **Playlists → per-playlist page** (v0.44.29): Rename (RIGHT to Save), Update channels, Update
  EPG, grouped channel counts. Opened by OK on a playlist row; Back returns to that row.
- **EPG → Refresh every** (v0.44.30): 6h / 12h / **Daily** (default) / 2 days. Changing it
  reschedules every playlist's periodic sync (confirmed in `dumpsys jobscheduler`: ~2-day
  latencies on "2 days", under 24h on "Daily"). Time offset is still grey.
- **Playback → Default aspect** (Fit / Fill / Zoom, default Fit): applied when fullscreen. The
  setting persists and reaches the player; the visual difference wasn't checkable on a 16:9
  stream (screenshots show video as black).
- **Playback → Auto frame rate** (default Off): only switches display modes in fullscreen;
  restores the original mode otherwise.
- **Playback → Preferred audio**: shows the remembered language (or Auto); OK clears it.
  Preferred subtitles is still grey.

### Later the same morning (v0.44.31-36)
- **Playback → Preferred subtitles**: remembered language (or Off), applied on every play; OK
  clears it. Persistence across a relaunch not yet watched on the TV.
- **About → Auto-update** (default On): Off stops the launch-time OTA offer; "Check for updates"
  still works by hand.
- **General → Start on**: Live TV / Home / Last used. Verified across a relaunch.
- **EPG → Time offset**: global, -12h to +12h in 1h steps, shifts the guide and hero times.
  Verified on the TV. (TiviMate's is per playlist; global is enough for one family's lists.)
- **Other → Clear watch history**: two-press confirm in place ("Press OK again"; losing focus
  disarms). The arm/disarm was verified; the clear itself was deliberately not run on the
  family's TV.
- **Parental controls → Hidden categories**: hold OK on a category in Live TV → "Hide category".
  Focus moves to the neighbouring category. The pane lists every hidden category (with the
  playlist name when more than one is loaded); OK toggles Hidden/Shown in place. Verified on the
  TV, including persistence across a relaunch.
- **Other → Diagnostics** (read-only): device, Android version, free RAM, app heap, database and
  cache size, read off the main thread when the pane opens.

### Late morning (v0.44.38-39)
- **Parental controls → PIN** (TiviMate's "PIN code"): four digits from an on-screen keypad or the
  remote's number keys; set it by entering it twice, remove it by entering it. Stored as a salted
  SHA-256, never in clear. With a PIN set, un-hiding a hidden category asks for it; one unlock lasts
  until you leave Parental controls or Settings. Setting a PIN was verified on the TV
  (VERIFY-PENDING: wrong PIN, unlock, remove).
- **Remote control → Digit keys** (default On): in the player, number keys type a channel number
  (shown top right); it tunes after 2s, or at once on OK. Searches the current playlist, skips
  hidden categories, "No channel N" when there's no match. VERIFY-PENDING on the TV.
- Fix: the "Hide category" popup no longer survives leaving Live TV and coming back.
- **Playback → Audio output** (v0.44.40, `PLAYER_ENGINEERING_BRIEF.md` §2.4): Auto / Force stereo.
  Force stereo pins the audio sink to stereo PCM, so AC3/E-AC3 are decoded instead of passed through
  (for a TV/receiver that goes silent on passthrough); changing it rebuilds the player.
  VERIFY-PENDING on the TV. Real surround behaviour can't be checked here: the TV's audio path is
  unknown, so only "the toggle works and playback still plays" is checkable.
- Player, not Settings, but the same "clear history" ask: the History picker (player Actions row)
  has a "Clear history" row at its end and now scrolls (v0.44.41). It wipes Room's
  `recent_channels`; never pressed during testing (it would wipe the family's recents).
- **Playlists → (a playlist) → Connection** (v0.44.42): edit the server/URL, username and password
  of an existing playlist. The password is never shown; blank keeps the current one. "Save &
  update" first checks the login with the provider (Xtream `player_api.php`), then rewrites the
  stored stream URLs' prefix and re-imports. VERIFY-PENDING: renders; a real save was deliberately
  not tried on the family's playlists.
- **Remote control → Long-press OK** (v0.44.43): what holding OK does in the fullscreen player -
  Context menu (default) / Add/remove favourite (with the usual toast) / Recent channels (opens the
  History picker). TiviMate remaps every long key; this is its most-used one. VERIFY-PENDING.

Still grey: General → Language (the app is English only); Appearance → Accent (themes wait for their own sprint); Other → Backup & restore (overlaps Cloud Sync's credentials decision D1).
Removed 2026-10-26: Appearance → "Show full category name on hold" - not a TiviMate setting, the
focused-row marquee already shows full names, and hold-OK on a category is now Hide.
