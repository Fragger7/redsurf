package com.redsurf.tv.sports

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Fetches a rolling window of ESPN scoreboard days for several leagues and merges them into one
 * schedule. Pure: the HTTP call is the injected [fetch] function (url -> body, or null on failure),
 * so it runs on the JVM in unit tests with no network. It holds no state between calls.
 */
class EspnScheduleFetcher(private val fetch: (url: String) -> String?) {

    /** [games] is de-duplicated by id and sorted by kickoff; [failedUrls] lists every URL that returned null, threw or failed to parse. */
    data class Result(val games: List<Game>, val failedUrls: List<String>)

    /**
     * Fetches [days] days starting today (US Eastern, from [nowMillis]) for each of [leagues], in
     * league-then-day order. A failed URL is recorded in [Result.failedUrls] and skipped; the rest
     * still return. Games with a repeated id keep the first seen; the result is sorted by kickoff,
     * ties by id.
     */
    fun fetchWindow(leagues: List<League>, nowMillis: Long, days: Int = 7): Result {
        val dayStrings = windowDays(nowMillis, days)
        val seen = LinkedHashMap<String, Game>()
        val failed = mutableListOf<String>()
        for (league in leagues) {
            for (day in dayStrings) {
                val url = EspnScoreboardUrl.forDay(league, day)
                val games = try {
                    val body = fetch(url)
                    if (body == null) null else EspnScoreboardParser.parse(league, body)
                } catch (_: Exception) {
                    null
                }
                if (games == null) {
                    failed.add(url)
                } else {
                    for (g in games) seen.putIfAbsent(g.id, g)
                }
            }
        }
        val sorted = seen.values.sortedWith(compareBy<Game>({ it.kickoffMillis }, { it.id }))
        return Result(sorted, failed)
    }

    companion object {
        private val EASTERN = ZoneId.of("America/New_York")
        private val DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")

        /** [days] consecutive `yyyyMMdd` dates starting with the US Eastern date of [nowMillis]. [days] must be 1..14. */
        fun windowDays(nowMillis: Long, days: Int = 7): List<String> {
            require(days in 1..14) { "days must be 1..14: $days" }
            val start = Instant.ofEpochMilli(nowMillis).atZone(EASTERN).toLocalDate()
            return (0 until days).map { start.plusDays(it.toLong()).format(DAY_FORMAT) }
        }
    }
}
