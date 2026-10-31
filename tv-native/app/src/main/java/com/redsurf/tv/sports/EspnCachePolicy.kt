package com.redsurf.tv.sports

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Cache freshness policy for ESPN scoreboard daily data, per `docs/plans/SPORTS_HUB.md` §3.
 * Pure and deterministic: determines how long a day's cached schedule remains valid before
 * refetching based on how close the day is to today in America/New_York time.
 */
object EspnCachePolicy {

    private val EASTERN_ZONE: ZoneId = ZoneId.of("America/New_York")
    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS

    /**
     * Returns the cache time-to-live in milliseconds for a given [day] evaluated at [nowMillis].
     * Today's games refresh every 30 minutes, tomorrow's every 4 hours, 2-3 days out every 8 hours,
     * and distant or past days every 24 hours, calculated using Eastern time.
     */
    fun ttlMillis(day: LocalDate, nowMillis: Long): Long {
        val today = Instant.ofEpochMilli(nowMillis).atZone(EASTERN_ZONE).toLocalDate()
        val daysUntil = ChronoUnit.DAYS.between(today, day)
        return when {
            daysUntil < 0L -> 24 * HOUR_MILLIS
            daysUntil == 0L -> 30 * MINUTE_MILLIS
            daysUntil == 1L -> 4 * HOUR_MILLIS
            daysUntil in 2L..3L -> 8 * HOUR_MILLIS
            else -> 24 * HOUR_MILLIS
        }
    }

    /**
     * Determines whether cached data fetched at [fetchedAtMillis] for [day] is stale at [nowMillis].
     * Returns true if the elapsed duration meets or exceeds the cache TTL, or if the fetch timestamp
     * is in the future indicating system clock skew.
     */
    fun isStale(fetchedAtMillis: Long, day: LocalDate, nowMillis: Long): Boolean {
        if (fetchedAtMillis > nowMillis) return true
        return (nowMillis - fetchedAtMillis) >= ttlMillis(day, nowMillis)
    }
}
