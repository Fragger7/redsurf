package com.redsurf.tv.sports

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lead-written spec for task 011 (SPORTS_HUB.md cache lifetimes). Do not edit; make it pass. */
class EspnCachePolicyTest {

    private val min = 60_000L
    private val hour = 60 * min

    private fun et(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneId.of("America/New_York")).toInstant().toEpochMilli()

    private val now = et(2026, 10, 30, 12, 0)
    private val today = LocalDate.of(2026, 10, 30)

    @Test fun todayIsThirtyMinutes() {
        assertEquals(30 * min, EspnCachePolicy.ttlMillis(today, now))
    }

    @Test fun tomorrowIsFourHours() {
        assertEquals(4 * hour, EspnCachePolicy.ttlMillis(today.plusDays(1), now))
    }

    @Test fun twoAndThreeDaysOutAreEightHours() {
        assertEquals(8 * hour, EspnCachePolicy.ttlMillis(today.plusDays(2), now))
        assertEquals(8 * hour, EspnCachePolicy.ttlMillis(today.plusDays(3), now))
    }

    @Test fun fourOrMoreDaysOutIsADay() {
        assertEquals(24 * hour, EspnCachePolicy.ttlMillis(today.plusDays(4), now))
        assertEquals(24 * hour, EspnCachePolicy.ttlMillis(today.plusDays(30), now))
    }

    @Test fun pastDaysAreADay() {
        assertEquals(24 * hour, EspnCachePolicy.ttlMillis(today.minusDays(1), now))
    }

    @Test fun todayIsTheEasternDateNotUtc() {
        // 23:30 ET on Oct 30 is already Oct 31 in UTC; "today" must still be Oct 30.
        val lateNow = et(2026, 10, 30, 23, 30)
        assertEquals(30 * min, EspnCachePolicy.ttlMillis(today, lateNow))
        assertEquals(4 * hour, EspnCachePolicy.ttlMillis(today.plusDays(1), lateNow))
    }

    @Test fun freshUntilTheTtlEnds() {
        assertFalse(EspnCachePolicy.isStale(now - 29 * min, today, now))
        assertTrue(EspnCachePolicy.isStale(now - 30 * min, today, now))
        assertFalse(EspnCachePolicy.isStale(now - 3 * hour, today.plusDays(1), now))
        assertTrue(EspnCachePolicy.isStale(now - 5 * hour, today.plusDays(1), now))
    }

    @Test fun fetchedInTheFutureIsStale() {
        // The clock moved backwards; refetch rather than trust the entry.
        assertTrue(EspnCachePolicy.isStale(now + 5 * min, today, now))
    }
}
