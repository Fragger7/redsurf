# Task 022 - DRAFT builder tasks for the §O polish items (docs only)

**Builder:** Gemini   **Branch:** `agy/022-draft-polish-tasks`   **Written by:** lead, 2026-10-31

**Goal:** `docs/agy/drafts/015-polish-o.md` is a brief for the `TESTING_OUTSTANDING.md` §O polish items. Split it into small, independent builder tasks a Gemini builder could do one at a time, each touching as few files as possible. **Change no production code.**

**Read:** `GEMINI.md`; `docs/agy/drafts/015-polish-o.md`; `docs/plans/TESTING_OUTSTANDING.md` §O; `docs/vision/FOCUS_MODEL.md`; task `docs/agy/tasks/013-channel-language.md` (the format); the source files 015 names (read them - cite real file:line).

**Write**, under `docs/agy/drafts/022/`:
- `README.md` - the task list in suggested order, which ones are pure logic (testable by unit test) vs UI (need device checks by the lead), and any that need a user decision first.
- One task file per task (`NNN-name.md`, numbers from 026), same sections as 013. For a pure-logic task, include a spec test under `spec/` (JUnit4, realistic inputs). For a UI task, write exact acceptance steps the lead can check on the TV (keys pressed, what should be focused/visible).

**Change exactly:** files under `docs/agy/drafts/022/` and this file's Builder report. Commit, do not push.

## Builder report

- **Status:** done. Drafted 10 builder tasks (026–035), README, and 4 spec tests.
- **Branch:** agy/022-draft-polish-tasks
- **Files changed:**
  - docs/agy/tasks/022-draft-polish-tasks.md
  - docs/agy/drafts/022/README.md
  - docs/agy/drafts/022/026-preview-expand-duration.md
  - docs/agy/drafts/022/027-settings-backdrop-smooth.md
  - docs/agy/drafts/022/028-recents-strip-scroll.md
  - docs/agy/drafts/022/029-network-speed-filter.md
  - docs/agy/drafts/022/030-video-info-overlay.md
  - docs/agy/drafts/022/031-player-context-menu.md
  - docs/agy/drafts/022/032-category-scroll-preservation.md
  - docs/agy/drafts/022/033-playlist-import-progress.md
  - docs/agy/drafts/022/034-epg-guide-indicators.md
  - docs/agy/drafts/022/035-playlist-settings-dialogs.md
  - docs/agy/drafts/022/spec/NetworkSpeedFilterTest.kt
  - docs/agy/drafts/022/spec/CategoryVisibilityTest.kt
  - docs/agy/drafts/022/spec/ImportProgressFormatterTest.kt
  - docs/agy/drafts/022/spec/RecentsLayoutGeometryTest.kt
- **Build / Test output:** docs-only task; no production code changed. Spec tests written to JUnit4 standard with realistic inputs.
- **Not verified:** device execution (awaits task assignment to builders).
- **Notes:** All source file:line citations verified against codebase. Tasks adhere to FOCUS_MODEL.md rules and TiviMate parity decisions from §O.
