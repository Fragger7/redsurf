# Task 004 - Search: rank matching channel names

**Builder:** Gemini, level 2 trial   **Branch:** `agy/004-search-ranker`   **Written by:** lead, 2026-10-28

**Goal:** FTS4 has no built-in relevance ranking, so the Search screen will fetch candidate
channels and order them itself. Build that ordering as a pure, fully unit-tested function.
No database, no UI. This task gives you rules, not finished code: you write the implementation
and the tests from the rules.

**Read first (only these):** `GEMINI.md`; `docs/plans/SEARCH.md` (context only);
`tv-native/app/src/main/java/com/redsurf/tv/sports/SourceRanker.kt` and its test
`tv-native/app/src/test/java/com/redsurf/tv/sports/SourceRankerTest.kt` (the style to copy).

**Do exactly:**
1. Create the branch as `GEMINI.md` section 2 says.
2. Create `tv-native/app/src/main/java/com/redsurf/tv/search/SearchRanker.kt`, package
   `com.redsurf.tv.search`, with `object SearchRanker` and
   `fun <T> rank(query: String, items: List<T>, name: (T) -> String): List<T>`.
   Comparison uses a *folded* form of both query and name: NFKC, `lowercase()`, every run of
   non-letter/non-digit characters replaced by one space, trimmed. Query words = folded query
   split on spaces. Tiers, best first:
   - **0 exact:** folded name == folded query.
   - **1 name starts with the query:** folded name starts with folded query.
   - **2 every query word starts some name word, in order:** for `sky spo`, `sky sports main`
     qualifies; `sports sky` does not (order matters).
   - **3 every query word starts some name word, any order.**
   - **4 everything else** (FTS already filtered; keep them, last).
   Inside a tier: shorter folded name first, then folded name alphabetically, then original list
   order (a stable sort gives this). Return a new list; never mutate the input. An empty or blank
   query returns the items in their original order.
   KDoc: what, why (FTS4 has no ranking), the tier list in one line each.
3. Create `tv-native/app/src/test/java/com/redsurf/tv/search/SearchRankerTest.kt` (JUnit 4, use
   `String` items and `name = { it }`). Write at least one test per tier and one per tie-break,
   plus: blank query keeps order; input list is not mutated; `"US| Sky Sports"` vs query
   `"sky"` lands in tier 2 (folding drops the `|`, `us sky sports` does not *start* with `sky`).
   Name every test after the rule it checks.
4. Run the compile check and unit tests (`GEMINI.md` section 3). Read real counts from the report.
5. Fill in the Builder report below, commit, push your branch.

**Change exactly:** the two new files and this file's Builder report. Do NOT touch
`GlobalSearchEngine.kt`, any DAO, `RedSurfDatabase.kt`, `SearchQuery.kt`, or any UI file.

**Acceptance (observed, not assumed):**
- [ ] Every tier and tie-break above has a test that would fail if that rule were broken.
- [ ] Compile `BUILD SUCCESSFUL`; tests all pass, real count quoted.
- [ ] `git diff origin/main --stat` shows only the three files.

**Stop and report if:** two rules above seem to contradict each other for some input - report
the input, do not pick one silently.

## Builder report

- **Status:**
- **Branch:**
- **Files changed:**
- **Build output tail:**
- **Test output tail (real counts):**
- **Not verified:**
- **Notes:**
