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
| **Appearance** | — | Resolution badge · Class (SD/HD/FHD/4K) vs Pixels / Hide nav strip when idle · Off / Show full category name on hold · Off / Accent · Red |
| **Playback** | — | Black screen between zaps · Off / Auto frame rate · Off / Buffer · Default / Preferred audio · Auto / Preferred subtitles · Off / Default aspect · Fit |
| **Remote control** | — | Long-press OK · Context menu / Digit keys · Channel number entry |
| **Parental controls** | — | PIN · Not set / Hidden groups · none |
| **Other** | — | Backup & restore / Clear watch history / Diagnostics & logs |
| **About** | Version (`BuildConfig.VERSION_NAME`) · Check for updates + inline status (moved here from the top of the old screen - this is where people look for it) | Auto-update · On |

Where a grey row's feature is already in `AGENTS.md`'s backlog, this table is now the canonical
home for it; keep the two in sync (backlog entry says which Settings row it becomes).

## Grey rows — the rule (user decision, 2026-09-13)

- **Real name, real shape, planned default value** - the page previews the finished product.
- **Grey and unfocusable.** The D-pad skips them entirely; there is nothing to press. No
  "coming soon" copy - grey + skipped says it. This is the "never claim it works" rule applied to
  UI: a tester can never land on fiction.
- **A category with zero live rows** (EPG, Parental controls, Remote control, Other today):
  RIGHT from the rail has nowhere to go, so focus stays on the rail. That is correct - and it is
  exactly the focus-escape shape hunted in the player on 2026-09-12, so the shell carries the
  same `focusProperties { exit = Cancel }` trap the player does, from its first commit.
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

## Status: built and machine-swept, 2026-09-13

All 7 machine-verifiable acceptance lines pass, on device, over two sweep passes - three real
bugs found and fixed along the way (LEFT-to-rail navigation, focus lost on confirm dialogs, and
a pre-existing bug where removing the last playlist never returned to Onboarding). Full account:
`docs/plans/SPRINT_LOG.md`'s 2026-09-13 entry. The 4 feel/vision items above are now yours to
judge - not yet looked at with real eyes. One open question surfaced during the sweep, logged in
`AGENTS.md`'s Backlog: LEFT/Back from the pane return to the rail's *nearest* row, not
necessarily the category you started from (same ambiguity as the existing GroupsColumn
backlog item, mirrored).
