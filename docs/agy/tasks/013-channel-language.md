# Task 013 - Sports Hub S0: guess a channel's language for the language filter

**Builder:** Gemini, level 3   **Branch:** `agy/013-channel-language`   **Written by:** lead, 2026-10-30

**Goal:** The Sports Hub hides channels in languages the family doesn't want (Q6). Guess a channel's language from its parsed name; an unknown language must never be hidden. Pure, no UI.
The tests are already written: they are the spec. Your job is the implementation that makes them pass.

**Read first (only these):** `GEMINI.md`; `docs/plans/SPORTS_HUB.md` §3 (context only); `tv-native/app/src/main/java/com/redsurf/tv/sports/ChannelNameNormalizer.kt` and `SourceRanker.kt` (the style to copy: KDoc, small pure objects); the spec test `docs/agy/tasks/spec/ChannelLanguageTest.kt`.

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. Copy `docs/agy/tasks/spec/ChannelLanguageTest.kt` to `tv-native/app/src/test/java/com/redsurf/tv/sports/ChannelLanguageTest.kt` byte for byte (`cp`). Never edit the copy.
3. Create `tv-native/app/src/main/java/com/redsurf/tv/sports/ChannelLanguage.kt`, package `com.redsurf.tv.sports`:
   - `object ChannelLanguage { fun of(name: ChannelName): String?; fun passes(name: ChannelName, allowed: Set<String>): Boolean }`.
   - `of`: if any whole word of `name.key` is `deportes`, `espanol`, `español`, `tudn`, `telemundo` or
     `univision` → `"es"` (this wins over the country). Otherwise by `name.country`: `US`, `UK`, `CA`,
     `AU` → `"en"`; `LAT`, `MX`, `ES` → `"es"`; `FR` → `"fr"`; anything else or none → `null`.
   - `passes`: true when the language is `null`, or `allowed` is empty, or the language is in
     `allowed`. (Exact event channels bypass this filter; that is the caller's job, not this task's.)
   KDoc on each public declaration: what it does and why, one short paragraph.
4. Run the compile check and the unit tests (`GEMINI.md` section 3). Read real counts from the report.
   If a spec test fails, fix the implementation, never the test.
5. Fill in the Builder report below and commit (do not push).

**Change exactly:** `tv-native/app/src/main/java/com/redsurf/tv/sports/ChannelLanguage.kt`, `tv-native/app/src/test/java/com/redsurf/tv/sports/ChannelLanguageTest.kt` (the copy), and this file's Builder report. Nothing else.

**Acceptance (observed, not assumed):**
- [x] `diff docs/agy/tasks/spec/ChannelLanguageTest.kt tv-native/app/src/test/java/com/redsurf/tv/sports/ChannelLanguageTest.kt` prints nothing.
- [x] Compile `BUILD SUCCESSFUL`; all unit tests pass, real counts quoted, including `ChannelLanguageTest`'s count.
- [x] `git diff origin/main --stat` shows only the three files.

**Stop and report if:** a spec test seems to contradict a rule above, or cannot pass without changing
an existing file - report the test name and why; do not edit the test or pick a rule silently.

## Builder report

- **Status:** done. Builds clean, tests pass.
- **Branch:** agy/013-channel-language
- **Files changed:**
  - tv-native/app/src/main/java/com/redsurf/tv/sports/ChannelLanguage.kt
  - tv-native/app/src/test/java/com/redsurf/tv/sports/ChannelLanguageTest.kt
  - docs/agy/tasks/013-channel-language.md
- **Build output tail:**
  BUILD SUCCESSFUL in 3s (18 actionable tasks: 2 executed, 16 up-to-date)
- **Test output tail (real counts):**
  BUILD SUCCESSFUL in 5s (28 actionable tasks: 5 executed, 23 up-to-date)
  155 tests, 0 failures, 0 ignored (ChannelLanguageTest: 9 tests, 0 failures)
- **Not verified:** on-device TV playback, UI integration
- **Notes:** none. Implementation satisfies spec test without touching existing files.
