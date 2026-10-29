# Task 007 - Sports Hub: ESPN 7-day schedule fetcher (level 2)

**Builder:** Gemini, level 2   **Branch:** `agy/007-espn-schedule-fetcher`   **Written by:** lead, 2026-10-29

**Goal:** a small, pure class that builds the 7-day window of ESPN scoreboard URLs, fetches each
through a function it is given (so tests need no network), parses them with the existing parser,
and returns one de-duplicated, sorted list. No UI, no OkHttp, no Android classes.

**Read first (only these):** `GEMINI.md`; `tv-native/app/src/main/java/com/redsurf/tv/sports/SportsModels.kt`;
`.../sports/EspnScoreboardParser.kt`; `tv-native/app/src/test/java/com/redsurf/tv/sports/EspnScoreboardUrlTest.kt`
(copy its test style).

**Change exactly:**
- Create `tv-native/app/src/main/java/com/redsurf/tv/sports/EspnScheduleFetcher.kt`
- Create `tv-native/app/src/test/java/com/redsurf/tv/sports/EspnScheduleFetcherTest.kt`
- This file's Builder report.

**Build exactly this API** (package `com.redsurf.tv.sports`; KDoc on the class and each public member, in the style of `SportsModels.kt`):

```kotlin
class EspnScheduleFetcher(private val fetch: (url: String) -> String?) {
    data class Result(val games: List<Game>, val failedUrls: List<String>)
    fun fetchWindow(leagues: List<League>, nowMillis: Long, days: Int = 7): Result
    companion object {
        fun windowDays(nowMillis: Long, days: Int = 7): List<String>
    }
}
```

Rules:
1. `windowDays`: `days` consecutive dates as `yyyyMMdd`, starting with the date of `nowMillis` in the
   time zone `America/New_York` (use `java.time`: `Instant`, `ZoneId`, `LocalDate`,
   `DateTimeFormatter.ofPattern("yyyyMMdd")`). `require(days in 1..14)`.
2. `fetchWindow`: for each league in the given order, for each day from `windowDays`, call
   `fetch(EspnScoreboardUrl.forDay(league, day))`.
   - `fetch` returns `null`, or throws any `Exception`: add that URL to `failedUrls` and continue.
   - Otherwise `EspnScoreboardParser.parse(league, body)`; if parse throws, same as a failure.
3. Merge all games; de-duplicate by `id` (keep the first seen); sort by `kickoffMillis`, ties by `id`.
4. No logging, no threads, no coroutines, no caching.

**Tests (JUnit 4, like the existing ones), at least these, each a separate `@Test`:**
1. `windowDays` for `2026-10-29T03:00:00Z` (that is 23:00 on 10-28 in New York) with days=3 ->
   `["20261028","20261029","20261030"]`.
2. `windowDays` across a month end (`2026-10-31T16:00:00Z`, days=2) -> `["20261031","20261101"]`.
3. `windowDays` with days=0 and days=15 both throw `IllegalArgumentException`.
4. `fetchWindow` calls exactly `leagues.size * days` URLs, in league-then-day order (record them in a list).
5. A `null` body and a throwing fetch both land in `failedUrls`; the other URLs' games are still returned.
6. The same game id returned on two days appears once.
7. Games come back sorted by kickoff.
For tests 5-7 use small fake JSON bodies that `EspnScoreboardParser` accepts - copy the minimal event
shape from `EspnScoreboardParserTest.kt` (you may read that file too). Do not edit either existing file.

**Verify:** compile and unit-test commands from `GEMINI.md` section 3. Report the real total test count
from the XML/HTML report.

**Acceptance:**
- [ ] Only the 3 listed files changed (`git diff origin/main --stat`).
- [ ] builds clean; tests pass with the real count; your 7+ tests are in that count.

**Stop and report if:** `EspnScoreboardUrl.forDay` or `EspnScoreboardParser.parse` don't have the
signatures above, or the parser test file has no usable fake event JSON.

## Builder report

- **Status:**
- **Branch:**
- **Files changed:**
- **Build + test tail (real counts):**
- **Not verified:**
- **Notes:**
