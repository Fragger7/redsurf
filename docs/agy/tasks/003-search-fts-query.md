# Task 003 - Search: turn typed text into an FTS4 prefix query

**Builder:** Gemini, level 2 trial   **Branch:** `agy/003-search-fts-query`   **Written by:** lead, 2026-10-28

**Goal:** the coming Search screen (`docs/plans/SEARCH.md` "Proposed shape" item 1) will query a
Room FTS4 table with one prefix match per word: typing `sky spo` must become `sky* spo*`. Build
that conversion as a small, pure, fully unit-tested function. No database, no UI, no Room.

**Read first (only these):** `GEMINI.md`; `docs/plans/SEARCH.md` (context only - do not act on
anything else in it); `tv-native/app/src/main/java/com/redsurf/tv/sports/ChannelNameNormalizer.kt`
(the style to copy: an `object`, a KDoc with an example, `java.text.Normalizer` NFKC).

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. Create `tv-native/app/src/main/java/com/redsurf/tv/search/SearchQuery.kt`, package
   `com.redsurf.tv.search`, containing `object SearchQuery` with one public function:
   `fun toFtsMatch(raw: String): String?` that does, in order:
   a. NFKC-normalise, then `lowercase()`.
   b. Split into words on any run of characters that are not a letter or digit
      (`Regex("""[^\p{L}\p{N}]+""")`); drop empty words. This also removes every FTS operator
      character (`" * - : ^ ( )`), which is the point - user text must never become FTS syntax.
   c. Keep at most the first 6 words.
   d. If no words remain, return `null`.
   e. Otherwise return the words each followed by `*`, joined by one space.
   Add a short KDoc in the style of `ChannelNameNormalizer` (what, why prefix-per-word, one example).
   Lowercasing matters: FTS4 treats upper-case `OR`/`AND`/`NOT`/`NEAR` as operators. Say so in
   one comment line.
3. Create `tv-native/app/src/test/java/com/redsurf/tv/search/SearchQueryTest.kt` with exactly
   these tests (JUnit 4, `assertEquals`/`assertNull`, same style as `ChannelNameNormalizerTest`):
   - `"sky spo"` -> `"sky* spo*"`
   - `"  BBC   One  "` -> `"bbc* one*"`
   - `"US| NBC Sports"` -> `"us* nbc* sports*"`
   - `"news OR sport"` -> `"news* or* sport*"`
   - `"\"quoted\" -minus col:on ^up (x)"` -> `"quoted* minus* col* on* up* x*"`
   - `"ＥＳＰＮ ᴴᴰ"` (fullwidth letters, superscript HD) -> `"espn* hd*"`
   - `"a b c d e f g h"` -> `"a* b* c* d* e* f*"`
   - `""`, `"   "`, `"|||--"` -> `null` (three asserts in one test is fine)
   - `"Télé 5"` -> `"télé* 5*"` (accents kept; FTS tokenizer handles them later)
4. Run the compile check and unit tests (`GEMINI.md` section 3). Read real counts from the report.
5. Fill in the Builder report below, commit, push your branch.

**Change exactly:** the two new files and this file's Builder report. Do NOT touch
`GlobalSearchEngine.kt`, any DAO, `RedSurfDatabase.kt`, or any UI file.

**Acceptance (observed, not assumed):**
- [ ] Every test above exists with exactly those inputs and expected outputs.
- [ ] Compile `BUILD SUCCESSFUL`; tests all pass, real count quoted (73 + your new tests).
- [ ] `git diff origin/main --stat` shows only the three files.

**Stop and report if:** a listed expectation fails and you believe the expectation itself is
wrong - do NOT change the expected value to make it pass; report which one and why.

## Builder report

- **Status:**
- **Branch:**
- **Files changed:**
- **Build output tail:**
- **Test output tail (real counts):**
- **Not verified:**
- **Notes:**
