# Task 014 - DRAFT a brief: Search redesign (docs only, no code)

**Builder:** Gemini   **Branch:** `agy/014-draft-search-redesign`   **Written by:** lead, 2026-10-31

**Goal:** You do the reading so the lead doesn't have to. Write a build-ready brief for the Search redesign that a later builder task can follow exactly. **Change no code.**

**Read:** `GEMINI.md`; `docs/plans/TIVIMATE_SEARCH_STUDY.md`; `docs/plans/TESTING_OUTSTANDING.md` section O (the user's Search feedback); `docs/plans/SEARCH.md`; `docs/vision/FOCUS_MODEL.md`; the current Search code (`grep -rl "Search" tv-native/app/src/main/java | head -30`, then read the Search screen, view-model and query files); TiviMate's resource strings in `/Users/Shared/tivimate-res/strings.xml` (grep for search-related strings - e.g. `grep -in "search" /Users/Shared/tivimate-res/strings.xml`).

**Write `docs/agy/drafts/014-search-redesign.md`** with:
1. Every TiviMate Search-related string you found (name + text), and what each implies.
2. Scope, as items: branded mic icon replacing the "Voice" pill; live results grouped into typed sections (Channels, Programs; Movies/Shows only if VOD exists - check); per-playlist chip tabs; channel cards (logo, name, current programme, progress bar); a Settings row "Stay on search screen when switching channels" (one OK = translucent preview while staying on Search); long-press OK menu on a result (Add/remove favourite); Back/focus rules for each.
3. Per item: the exact files and functions to change (paths + function names you actually read), the proposed change, and the focus/state rule it must keep (FOCUS_MODEL.md rule numbers).
4. A list of **pure, unit-testable** pieces (e.g. grouping/ordering of results, section visibility rules) with proposed JUnit test cases written out as Kotlin test code in fenced blocks - the lead will review them into spec tests.
5. Open questions for the user, numbered - only things the code and the study can't settle.

**Change exactly:** `docs/agy/drafts/014-search-redesign.md` and this file's Builder report. Commit, do not push. No Gradle run needed.

## Builder report
- done
- branch: agy/014-draft-search-redesign
- files changed: docs/agy/drafts/014-search-redesign.md, docs/agy/tasks/014-draft-search-redesign.md
- build/test: docs only, no code changed, no Gradle run needed
- not verified: visual UI layout, translucent video view presentation, and live D-pad behavior on TV hardware
- notes: checked VOD tables (none exist; streamType = 'live' is enforced throughout), so Movies/Shows sections are deferred; all 27 TiviMate search strings analyzed with architectural implications; 6 JUnit test cases written out.
