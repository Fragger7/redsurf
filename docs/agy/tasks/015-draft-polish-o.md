# Task 015 - DRAFT a brief: the section O polish items (docs only, no code)

**Builder:** Gemini   **Branch:** `agy/015-draft-polish-o`   **Written by:** lead, 2026-10-31

**Goal:** You do the code reading so the lead doesn't have to. For each polish item below, find exactly where it lives in the code and propose the fix, ready for a later builder task. **Change no code.**

**Read:** `GEMINI.md`; `docs/plans/TESTING_OUTSTANDING.md` section O (the user's own words - quote them); `docs/vision/FOCUS_MODEL.md`; `docs/plans/TIVIMATE_LEARNINGS.md` (skim); the relevant code (use grep); TiviMate's strings at `/Users/Shared/tivimate-res/strings.xml` where a setting or label is relevant.

**Items** (check section O for any others and add them):
1. Recents strip in the player: the Clear tile is missing and the last tile is half width.
2. One "Video info" overlay (merge with stats-for-nerds?) with network speed refreshed about every 0.5 s via Media3's `TransferListener` / bandwidth meter.
3. Long-press OK menu in the player should stay open (OK+DOWN tiles).
4. Categories: centre the selected category only when it is off screen.
5. Playlist import: an "N of total" progress bar.
6. Preview <-> fullscreen animation about 30% slower (find the duration constants).
7. EPG "public guide" indicator visibility.
8. Playlist page and Manage Groups redesign - list what TiviMate's strings suggest its screens contain.

**Write `docs/agy/drafts/015-polish-o.md`**: per item - the user's quote, files + functions (real paths you read, with line numbers), the proposed change in a few lines, risks (focus/state, performance), and an S/M/L size. Then, for items with pure logic, proposed JUnit test cases as Kotlin code in fenced blocks. End with numbered open questions for the user.

**Change exactly:** `docs/agy/drafts/015-polish-o.md` and this file's Builder report. Commit, do not push. No Gradle run needed.

## Builder report
- done: Drafted Section O polish items brief with code locations, line numbers, root causes, proposed changes, risks, S/M/L sizes, JUnit tests, and user open questions.
- branch: agy/015-draft-polish-o
- files changed: docs/agy/drafts/015-polish-o.md, docs/agy/tasks/015-draft-polish-o.md
- build/tests: not run (task specified "No Gradle run needed. Change no code")
- what is NOT verified: not verified (docs-only draft; no Kotlin source code changed or executed)
- task notes: None; all 8 items plus 3 additional Section O items documented thoroughly.
