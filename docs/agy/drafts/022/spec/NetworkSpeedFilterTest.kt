package com.redsurf.tv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Lead-written spec for task 029 (0.5s network speed polling and EMA smoothing). */
class NetworkSpeedFilterTest {

    @Test
    fun instantaneousBpsCalculatedFromBytesOver500ms() {
        val filter = NetworkSpeedFilter(sampleIntervalMs = 500L, alpha = 0.4f)
        // 125,000 bytes in 500ms = 250,000 bytes/sec = 2,000,000 bps (2.0 Mbps)
        val initialBps = filter.onSample(125_000L)
        assertEquals(2_000_000L, initialBps)
    }

    @Test
    fun exponentialMovingAverageSmoothingAlpha40() {
        val filter = NetworkSpeedFilter(sampleIntervalMs = 500L, alpha = 0.4f)
        // Sample 1: 125,000 bytes -> 2,000,000 bps
        filter.onSample(125_000L)
        // Sample 2: 250,000 bytes -> 4,000,000 bps
        // EMA: 2,000,000 * 0.6 + 4,000,000 * 0.4 = 1,200,000 + 1,600,000 = 2,800,000 bps
        val smoothed = filter.onSample(250_000L)
        assertEquals(2_800_000L, smoothed)
    }

    @Test
    fun absorbsBurstySilenceUpToFiveWindows() {
        val filter = NetworkSpeedFilter(sampleIntervalMs = 500L)
        filter.onSample(125_000L)

        // 5 consecutive empty windows (2.5 seconds total silence)
        repeat(5) {
            val result = filter.onSample(0L)
            assertNotNull("Should retain smoothed estimate during short bursty idle", result)
        }
        assertEquals(5, filter.consecutiveEmptyWindows)
    }

    @Test
    fun resetsToNullAfterSixConsecutiveEmptyWindows() {
        val filter = NetworkSpeedFilter(sampleIntervalMs = 500L)
        filter.onSample(125_000L)

        // 5 empty windows -> still active
        repeat(5) { filter.onSample(0L) }
        assertNotNull(filter.smoothedBps)

        // 6th empty window (3.0 seconds total silence) -> resets to null
        val result = filter.onSample(0L)
        assertEquals(6, filter.consecutiveEmptyWindows)
        assertNull("Estimate must reset to null after 3.0s sustained silence", result)
        assertNull(filter.smoothedBps)
    }

    @Test
    fun recoveryAfterSilenceStartsFresh() {
        val filter = NetworkSpeedFilter(sampleIntervalMs = 500L)
        filter.onSample(125_000L)
        repeat(6) { filter.onSample(0L) }
        assertNull(filter.smoothedBps)

        // Fresh packet arrival starts fresh without dragging previous EMA
        val resumed = filter.onSample(62_500L) // 1.0 Mbps
        assertEquals(1_000_000L, resumed)
        assertEquals(0, filter.consecutiveEmptyWindows)
    }
}
