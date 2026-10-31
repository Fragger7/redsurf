# Task 021 - DRAFT the next Sports Hub S0 tasks after 020, with spec tests (docs only)

**Builder:** Gemini   **Branch:** `agy/021-draft-sports-next2`   **Written by:** lead, 2026-10-31

**Goal:** Sports Hub S0 has its pieces (normaliser, ESPN parser/fetcher/cache policy, ranker, filter, Philly seed, EPG team match, match rate, channel language, event-name parser, EventChannelMatch, NetworkBroadcastMatch, GameSourceResolver from tasks 018-020). Draft the next 2-3 builder tasks toward S0 being usable (pure, no UI), each with a spec test, in the exact format of `docs/agy/tasks/013-channel-language.md`. **Change no production code.**

**Read:** `GEMINI.md`; `docs/plans/SPORTS_HUB.md` (all); tasks 007-020 in `docs/agy/tasks/` and their spec tests in `docs/agy/tasks/spec/`; everything in `tv-native/app/src/main/java/com/redsurf/tv/sports/` and its tests.

**Write**, under `docs/agy/drafts/021/`:
- `README.md` - what's built, what S0 still lacks (per SPORTS_HUB.md), and why you picked these tasks, in order.
- One task file per task (`NNN-name.md`, numbers from 023), same sections as 013: goal, read first, do exactly, change exactly, acceptance, stop-and-report, empty Builder report. Pure Kotlin only, no Android/UI, no network in tests.
- One spec test per task (`spec/XxxTest.kt`), JUnit4 like the existing specs, real assertions over realistic inputs (use real-looking channel names like the existing tests). **Run them**: copy each spec into `tv-native/app/src/test/...` temporarily with a stub implementation and check it compiles and fails for the right reason, then remove both - leave only the drafts. Say in the report that you did this.

**Change exactly:** files under `docs/agy/drafts/021/` and this file's Builder report. Commit, do not push.

## Builder report

(empty - fill in)
