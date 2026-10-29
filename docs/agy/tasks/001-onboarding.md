# Task 001 - Onboarding: prove your setup, write your first skill

**Builder:** Gemini, level 1   **Branch:** `agy/001-onboarding`   **Written by:** lead, 2026-10-28

**Goal:** show that you can build and test this project from your worktree, and save the exact
steps as an Antigravity skill so future sessions don't re-derive them. No app code changes.

**Read first (only these):** `GEMINI.md` (all of it), `docs/plans/WORKFLOW.md` section
"Lead and builders".

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. Run the compile check and the unit tests from `GEMINI.md` section 3. Note the real result
   lines (for tests: the number of tests run, passed, failed).
3. Create **one new file**: `.agents/skills/redsurf-build-test/SKILL.md`, in Antigravity's own
   skill format (use the format your harness documents for workspace skills). It must contain:
   when to use it (compile-check or unit-test RedSurf), the exact commands from `GEMINI.md`
   section 3, how long a cold build took for you, and how to read pass/fail from the output.
   Nothing else - no signing, no ADB, no release builds.
4. Fill in the Builder report below, commit both files, push your branch.

**Change exactly:** `.agents/skills/redsurf-build-test/SKILL.md` (new), this file's Builder
report section. Nothing else.

**Acceptance (observed, not assumed):**
- [ ] Compile check ran; result line quoted.
- [ ] Unit tests ran; real counts quoted.
- [ ] Skill file exists and contains only what step 3 lists.
- [ ] Branch `agy/001-onboarding` pushed; `git diff origin/main --stat` shows only the two files.

**Stop and report if:** the build or tests fail (do not try to fix app code - report the error
tail), the environment paths in `GEMINI.md` don't exist, or you are unsure of the skill format.

## Builder report

_(Gemini fills this in.)_
