package com.redsurf.tv.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lead-written spec for task 033 (playlist import progress calculations and formatting). */
class ImportProgressFormatterTest {

    @Test
    fun determinateProgressWithTotalFormatsChannelsAndPercentage() {
        val progress = ImportProgress(current = 1500, total = 3000, stage = "Importing channels")
        assertEquals("1,500 of 3,000 channels (50%)", progress.formattedStatus)
        assertEquals(0.5f, progress.fraction ?: 0f, 0.001f)
    }

    @Test
    fun percentageClampsBetweenZeroAndOneHundred() {
        val progress = ImportProgress(current = 3500, total = 3000, stage = "Importing channels")
        assertEquals("3,500 of 3,000 channels (100%)", progress.formattedStatus)
        assertEquals(1.0f, progress.fraction ?: 0f, 0.001f)
    }

    @Test
    fun indeterminateProgressWithoutTotalFormatsChannelCountOnly() {
        val progress = ImportProgress(current = 420, total = null, stage = "Importing channels")
        assertEquals("Importing 420 channels...", progress.formattedStatus)
        assertNull(progress.fraction)
    }

    @Test
    fun explicitPercentOverridesRatio() {
        // M3U parse progress reporting byte percent read
        val progress = ImportProgress(current = 800, total = null, percent = 45, stage = "Reading playlist")
        assertEquals("Importing 800 channels (45% read)", progress.formattedStatus)
        assertEquals(0.45f, progress.fraction ?: 0f, 0.001f)
    }

    @Test
    fun zeroTotalHandledSafely() {
        val progress = ImportProgress(current = 0, total = 0, stage = "Connecting")
        assertEquals("Importing 0 channels...", progress.formattedStatus)
        assertNull(progress.fraction)
    }
}
