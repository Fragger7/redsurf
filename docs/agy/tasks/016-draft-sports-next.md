# Task 016 - DRAFT the next Sports Hub S0 tasks, with spec tests (docs only)

**Builder:** Gemini   **Branch:** `agy/016-draft-sports-next`   **Written by:** lead, 2026-10-31

**Goal:** Sports Hub S0 has its pieces (normaliser, ESPN parser/fetcher/cache policy, ranker, filter, Philly seed, EPG team match, match rate, channel language, event-name parser). Draft the next 2-3 builder tasks that wire them together (pure, no UI), each with a spec test, in the exact format of `docs/agy/tasks/013-channel-language.md`. **Change no production code.**

**Read:** `GEMINI.md`; `docs/plans/SPORTS_HUB.md` (all); tasks 007-013 in `docs/agy/tasks/` and their spec tests in `docs/agy/tasks/spec/`; everything in `tv-native/app/src/main/java/com/redsurf/tv/sports/` and its tests.

**Write**, under `docs/agy/drafts/016/`:
- `README.md` - what's built, what S0 still lacks (per SPORTS_HUB.md), and why you picked these tasks, in order.
- One task file per task (`NNN-name.md`, numbers from 018), same sections as 013: goal, read first, do exactly, change exactly, acceptance, stop-and-report, empty Builder report. Pure Kotlin only, no Android/UI, no network in tests.
- One spec test per task (`spec/XxxTest.kt`), JUnit4 like the existing specs, real assertions over realistic inputs (use real-looking channel names like the existing tests). **Run them**: copy each spec into `tv-native/app/src/test/...` temporarily with a stub implementation and check it compiles and fails for the right reason, then remove both - leave only the drafts. Say in the report that you did this.

**Change exactly:** files under `docs/agy/drafts/016/` and this file's Builder report. Commit, do not push.

## Builder report

- **Status:** done. Drafted tasks 018, 019, 020 and 3 spec tests under docs/agy/drafts/016/.
- **Branch:** agy/016-draft-sports-next
- **Files changed:**
  - docs/agy/tasks/016-draft-sports-next.md
  - docs/agy/drafts/016/README.md
  - docs/agy/drafts/016/018-event-channel-match.md
  - docs/agy/drafts/016/019-network-broadcast-match.md
  - docs/agy/drafts/016/020-game-source-resolver.md
  - docs/agy/drafts/016/spec/EventChannelMatchTest.kt
  - docs/agy/drafts/016/spec/NetworkBroadcastMatchTest.kt
  - docs/agy/drafts/016/spec/GameSourceResolverTest.kt
- **Build / Test output:** Gradle step skipped per user instruction (JAVA_HOME invalid directory: /Users/agy/.local/opt/jdk17/Contents/Home; did not hunt for java).
- **Not verified:** Gradle test execution of draft spec tests against stubs (skipped due to environment JAVA_HOME).
- **Notes:** Pure Kotlin design; no production code changed.
