# Task 008 - TV: observe three polish-sprint features (level 3, rerun of 006)

**Builder:** Gemini, level 3, **TV task**   **Branch:** `agy/008-tv-polish-check`   **Written by:** lead, 2026-10-29

**Goal:** three features built 2026-10-27 have never been seen on the TV. Drive the steps below
with `redsurf-tv` only, observe, and report what you saw. No code changes. You decide nothing:
if a step's result is unclear, write down the output and the screenshot path and move on.

**Read first (only these):** `GEMINI.md` (sections 1, 4, 5, 8a).

**Hard limits for this task:**
- The only shell commands you may run are `redsurf-tv ...` and `git ...`. Nothing else - no
  `sleep`, `ls`, `cat`, `echo`, `date`. Read files with your file-read tool. (006 was blocked
  because its own steps asked for `sleep`.)
- Never open Settings (`redsurf-tv nav "Settings"` is forbidden in this task).
- The only keys allowed: `DPAD_UP`, `DPAD_DOWN`, `DPAD_LEFT`, `DPAD_RIGHT`, `DPAD_CENTER`, `BACK`.
- In part C you may press `DPAD_CENTER` only where a step says so. Never press `DPAD_CENTER` on
  anything whose `focused` text contains "Clear", "Remove", "Delete" or "History".
- At most 60 key presses in the whole task. Count them.

**Do exactly** (one command at a time; keep every output for the report):
1. Create the branch as `GEMINI.md` section 2 says.
2. `redsurf-tv begin` - anything other than `ok: TV free...` -> go to step 9.
3. `redsurf-tv version`, then `redsurf-tv front` (not in front -> `redsurf-tv relaunch`, `front` again;
   still not -> step 9).

**Part A - the nav strip hides itself.**
4. `redsurf-tv nav "Live TV"`, then `redsurf-tv key DPAD_DOWN 1` twice, then `redsurf-tv focused`.
   Then `redsurf-tv key DPAD_DOWN 4.9` once more and `redsurf-tv shot` (shot A1).
   Then run `redsurf-tv version`, `redsurf-tv front`, `redsurf-tv focused` (this only lets a few
   seconds pass) and `redsurf-tv shot` (shot A2).
   Report: in A1 and A2, is the row of pills (Home / Live TV / ...) visible at the top? Yes/no each.

**Part B - Back walks out to the current tab's pill, with an exit hint, before leaving.**
5. From where part A ended, repeat up to 4 times: `redsurf-tv key BACK 1.5`, `redsurf-tv focused`,
   `redsurf-tv shot`, `redsurf-tv front`. Stop the loop as soon as `front` says RedSurf is not in
   front. Report each round: the `focused` line, the shot path, any hint text you can read in the
   shot (e.g. "Press Back again to exit"). If the app left the front: `redsurf-tv relaunch`, then
   `redsurf-tv front`.

**Part C - the "Stats for nerds" readout in the fullscreen player.**
6. `redsurf-tv nav "Live TV"`, `redsurf-tv key DPAD_DOWN 1` twice, `redsurf-tv focused` (must be a
   channel row - if not, skip to step 8). `redsurf-tv key DPAD_CENTER 4` (preview), then
   `redsurf-tv key DPAD_CENTER 4.9` (fullscreen), `redsurf-tv shot` (C1).
7. `redsurf-tv key DPAD_CENTER 1.5` (opens the player controls), `redsurf-tv focused`, `redsurf-tv shot` (C2).
   Then press `DPAD_DOWN` up to 3 times, `redsurf-tv focused` after each, and in each row press
   `DPAD_RIGHT` up to 8 times with `focused` after each, looking for a `focused` line whose text
   contains "Stats". If found: `redsurf-tv key DPAD_CENTER 3`, `redsurf-tv shot` (C3), and report
   what the corner readout shows (codec, decoder, resolution/fps, bitrate, buffer, network, dropped
   frames - copy what you can read). If not found after those presses: report "Stats tile not
   found" with the last `focused` line and a shot.
8. `redsurf-tv key BACK 1.5` up to 3 times until `focused` shows the Live TV grid or a pill;
   `redsurf-tv front`.

**End.**
9. `redsurf-tv logs` - copy only lines containing `Exception`, `FATAL` or `ANR` (or write "none").
10. `redsurf-tv end` - always, even if you stopped early.
11. Fill in the Builder report below and commit it on your branch (no push).

If any command prints `STOP`, `TV in use` or `asleep`: go straight to step 10, then 11. Never retry.

**Change exactly:** this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] Parts A, B, C each reported with real `focused` lines and screenshot paths, or where/why they stopped.
- [ ] Total key presses counted and <= 60. `end` ran.
- [ ] `git diff origin/main --stat` shows only this file.

**Stop and report if:** anything appears that this file doesn't describe (a dialog, a PIN prompt,
an error panel, Settings) - press nothing more except `BACK` once, then go to step 9.

## Builder report

- **Status:**
- **Branch:**
- **Key presses used:**
- **Part A (A1/A2 pills visible? shot paths):**
- **Part B (each round: focused, shot, hint text, front):**
- **Part C (C1-C3, focused lines, readout text):**
- **Log errors:**
- **Not verified:**
- **Notes:**
