package com.redsurf.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class CatchupTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test fun timeshiftUrl_usesServerZoneAndDuration() {
        val start = 1_790_000_000_000L // 2026-09-21 ... UTC
        val url = Catchup.timeshiftUrl("http://host.tv:8080/live/u/p/12345.ts", start, start + 90 * 60_000L, utc)
        val expectedStamp = java.text.SimpleDateFormat("yyyy-MM-dd:HH-mm", java.util.Locale.US).apply { timeZone = utc }.format(start)
        assertEquals("http://host.tv:8080/timeshift/u/p/90/$expectedStamp/12345.ts", url)
    }

    @Test fun timeshiftUrl_nullForNonXtream() =
        assertNull(Catchup.timeshiftUrl("http://example.com/stream.m3u8", 0L, 60_000L, utc))

    @Test fun archiveWindow() {
        val now = 10L * 24 * 60 * 60 * 1000
        assertTrue(Catchup.isInArchive(3, now - 60_000L, now))
        assertFalse(Catchup.isInArchive(3, now - 4L * 24 * 60 * 60 * 1000, now))
        assertFalse(Catchup.isInArchive(0, now - 60_000L, now))
        assertFalse(Catchup.isInArchive(3, now + 60_000L, now))
    }
}
