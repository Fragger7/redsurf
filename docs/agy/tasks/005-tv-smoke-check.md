# Task 005 - TV: read-only smoke check (level 3 trial)

**Builder:** Gemini, level 3 trial, **TV task**   **Branch:** `agy/005-tv-smoke-check`   **Written by:** lead, 2026-10-29

**Goal:** your first time on the TV. Prove you can use `redsurf-tv` exactly as `GEMINI.md`
section 8a says, observe, and report what you saw - nothing else. No code changes. The lead is
watching this run.

**Read first (only these):** `GEMINI.md` (sections 1, 4, 5, 8a especially).

**Do exactly** (one command at a time; copy the full output of each into your notes for the report):
1. Create the branch as `GEMINI.md` section 2 says.
2. `redsurf-tv begin` - if it prints anything other than `ok: TV free...`, go to step 12.
3. `redsurf-tv version`
4. `redsurf-tv front` - if RedSurf is not in front, run `redsurf-tv relaunch`, then `redsurf-tv front` again.
   If it still is not in front, go to step 12.
5. `redsurf-tv clearlogs`
6. `redsurf-tv shot`
7. `redsurf-tv nav "Live TV"`
8. `redsurf-tv focused`
9. `redsurf-tv key DPAD_DOWN 1` twice, then `redsurf-tv focused`. Never press `DPAD_CENTER`,
   `ENTER` or `MEDIA_PLAY_PAUSE` in this task.
10. `redsurf-tv shot`
11. `redsurf-tv logs` - copy only the last 20 lines into your report.
12. `redsurf-tv end` - always, even if you stopped early.
13. Fill in the Builder report below and commit it on your branch (no push).

If any command prints `STOP`, `TV in use` or `asleep`: go straight to step 12, then step 13. Never
retry, never wait.

**Change exactly:** this file's Builder report. Nothing else - no code, no other docs.

**Acceptance (observed, not assumed):**
- [ ] Every command above ran in order, or the report says exactly where and why it stopped.
- [ ] `end` ran.
- [ ] The report quotes real outputs (version line, both `focused` lines, both screenshot paths).
- [ ] `git diff origin/main --stat` shows only this file.

**Stop and report if:** any command's output does not match what `GEMINI.md` section 8a says it
does, or a step seems to need a command not in that table.

## Builder report

- **Status:**
- **Branch:**
- **Commands run (in order, with outputs):**
- **Screenshots:**
- **Last 20 log lines:**
- **Not verified:**
- **Notes:**
