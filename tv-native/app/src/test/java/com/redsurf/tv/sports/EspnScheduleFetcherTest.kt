package com.redsurf.tv.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EspnScheduleFetcherTest {

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun event(id: String, date: String) =
        """{"id":"$id","name":"A at B","date":"$date","competitions":[{"competitors":[
        {"homeAway":"home","team":{"displayName":"B"}},{"homeAway":"away","team":{"displayName":"A"}}],
        "status":{"type":{"state":"pre"}}}]}"""

    private fun body(vararg events: String) = """{"events":[${events.joinToString(",")}]}"""

    @Test
    fun windowDaysUsesNewYorkDate() {
        assertEquals(
            listOf("20261028", "20261029", "20261030"),
            EspnScheduleFetcher.windowDays(ms("2026-10-29T03:00:00Z"), 3),
        )
    }

    @Test
    fun windowDaysCrossesMonthEnd() {
        assertEquals(
            listOf("20261031", "20261101"),
            EspnScheduleFetcher.windowDays(ms("2026-10-31T16:00:00Z"), 2),
        )
    }

    @Test
    fun windowDaysRejectsOutOfRange() {
        val now = ms("2026-10-29T12:00:00Z")
        for (bad in listOf(0, 15)) {
            try {
                EspnScheduleFetcher.windowDays(now, bad)
                throw AssertionError("expected IllegalArgumentException for $bad")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun callsEveryUrlInLeagueThenDayOrder() {
        val urls = mutableListOf<String>()
        val now = ms("2026-10-29T12:00:00Z")
        EspnScheduleFetcher { urls.add(it); null }.fetchWindow(listOf(League.NFL, League.NBA), now, 2)
        assertEquals(
            listOf(
                EspnScoreboardUrl.forDay(League.NFL, "20261029"),
                EspnScoreboardUrl.forDay(League.NFL, "20261030"),
                EspnScoreboardUrl.forDay(League.NBA, "20261029"),
                EspnScoreboardUrl.forDay(League.NBA, "20261030"),
            ),
            urls,
        )
    }

    @Test
    fun nullAndThrowingFetchesAreReportedAndOthersKept() {
        val now = ms("2026-10-29T12:00:00Z")
        val u1 = EspnScoreboardUrl.forDay(League.NFL, "20261029")
        val u2 = EspnScoreboardUrl.forDay(League.NFL, "20261030")
        val result = EspnScheduleFetcher { url ->
            when (url) {
                u1 -> null
                u2 -> throw RuntimeException("boom")
                else -> body(event("1", "2026-10-31T17:00Z"))
            }
        }.fetchWindow(listOf(League.NFL), now, 3)
        assertEquals(listOf(u1, u2), result.failedUrls)
        assertEquals(listOf("1"), result.games.map { it.id })
    }

    @Test
    fun malformedBodyCountsAsFailure() {
        val now = ms("2026-10-29T12:00:00Z")
        val result = EspnScheduleFetcher { "not json" }.fetchWindow(listOf(League.NFL), now, 1)
        assertEquals(1, result.failedUrls.size)
        assertTrue(result.games.isEmpty())
    }

    @Test
    fun duplicateIdAcrossDaysAppearsOnce() {
        val now = ms("2026-10-29T12:00:00Z")
        val result = EspnScheduleFetcher { body(event("7", "2026-10-30T01:00Z")) }
            .fetchWindow(listOf(League.NFL), now, 2)
        assertEquals(1, result.games.size)
        assertTrue(result.failedUrls.isEmpty())
    }

    @Test
    fun gamesAreSortedByKickoff() {
        val now = ms("2026-10-29T12:00:00Z")
        val result = EspnScheduleFetcher {
            body(event("c", "2026-10-31T20:00Z"), event("a", "2026-10-29T18:00Z"), event("b", "2026-10-29T18:00Z"))
        }.fetchWindow(listOf(League.NFL), now, 1)
        assertEquals(listOf("a", "b", "c"), result.games.map { it.id })
    }
}
