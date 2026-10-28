# Builder brief template

The lead (Opus) copies this into the sprint's own brief, one block per task, for a builder (a
Sonnet subagent, or Gemini in Antigravity) to execute. A builder does not decide anything this
brief doesn't say; a gap is a **stop**, not a guess. See `WORKFLOW.md` "Lead and builders".

---

## Task N - <short name>

**Builder:** Sonnet | Gemini (level 1-4)   **Branch:** main (Sonnet) | agy/<name> (Gemini)

**Goal (one sentence, user-visible):**

**Read first (only these):** `path/File.kt` (lines a-b), `docs/...` §x

**Change exactly:**
1. In `path/File.kt`, `functionName()`: ...
2. ...

**Follow this existing pattern:** `path/Example.kt:123` - copy its shape, naming and comment density.

**Do not touch:** <files, modules, behaviour that must stay as is>. Always off-limits: signing
files, `.github/`, `firestore.rules`, Room schema/migrations, `build.gradle*` versions, any doc
except this brief's own "Builder report".

**Acceptance (all must be observed, not assumed):**
- [ ] `./gradlew :app:compileDebugKotlin` clean
- [ ] `./gradlew :app:testDebugUnitTest` - all pass, count stated (was N)
- [ ] New/changed test: `path/FooTest.kt` covers <case>
- [ ] Device check (only if listed): <exact ADB steps + logcat line to see>

**Stop and report instead of continuing if:** the code doesn't match what this brief describes; a
needed change falls outside "Change exactly"; a test fails twice after a fix attempt; anything
needs a design, focus, performance or architecture judgment.

**Builder report (builder fills in, under 15 lines):** done / stopped (why) · files changed ·
build + test output tail (real counts) · what was NOT verified · anything that looked wrong.
