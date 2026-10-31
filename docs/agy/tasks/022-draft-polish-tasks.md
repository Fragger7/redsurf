# Task 022 - DRAFT builder tasks for the §O polish items (docs only)

**Builder:** Gemini   **Branch:** `agy/022-draft-polish-tasks`   **Written by:** lead, 2026-10-31

**Goal:** `docs/agy/drafts/015-polish-o.md` is a brief for the `TESTING_OUTSTANDING.md` §O polish items. Split it into small, independent builder tasks a Gemini builder could do one at a time, each touching as few files as possible. **Change no production code.**

**Read:** `GEMINI.md`; `docs/agy/drafts/015-polish-o.md`; `docs/plans/TESTING_OUTSTANDING.md` §O; `docs/vision/FOCUS_MODEL.md`; task `docs/agy/tasks/013-channel-language.md` (the format); the source files 015 names (read them - cite real file:line).

**Write**, under `docs/agy/drafts/022/`:
- `README.md` - the task list in suggested order, which ones are pure logic (testable by unit test) vs UI (need device checks by the lead), and any that need a user decision first.
- One task file per task (`NNN-name.md`, numbers from 026), same sections as 013. For a pure-logic task, include a spec test under `spec/` (JUnit4, realistic inputs). For a UI task, write exact acceptance steps the lead can check on the TV (keys pressed, what should be focused/visible).

**Change exactly:** files under `docs/agy/drafts/022/` and this file's Builder report. Commit, do not push.

## Builder report

(empty - fill in)
