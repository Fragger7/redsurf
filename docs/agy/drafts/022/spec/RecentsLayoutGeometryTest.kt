package com.redsurf.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lead-written spec for task 028 (recents row tile width, spacing, and viewport overflow calculations). */
class RecentsLayoutGeometryTest {

    @Test
    fun elevenTilesExceedsStandardTvViewport() {
        // 1 TV Guide + 1 History + 8 Recents + 1 Clear = 11 tiles
        val totalWidth = RecentsGeometry.calculateTotalWidthDp(
            tileCount = 11,
            tileWidthDp = 100,
            spacingDp = 8,
            horizontalPaddingDp = 48,
        )
        // 11 * 100 + 10 * 8 + 48 = 1228.dp
        assertEquals(1228, totalWidth)
        assertTrue("11 tiles (1228dp) must overflow standard 960dp viewport width", totalWidth > 960)
    }

    @Test
    fun fiveTilesFitsWithinStandardTvViewport() {
        // 1 Guide + 1 History + 2 Recents + 1 Clear = 5 tiles
        val totalWidth = RecentsGeometry.calculateTotalWidthDp(
            tileCount = 5,
            tileWidthDp = 100,
            spacingDp = 8,
            horizontalPaddingDp = 48,
        )
        // 5 * 100 + 4 * 8 + 48 = 580.dp <= 960.dp
        assertEquals(580, totalWidth)
        assertFalse("5 tiles (580dp) must fit within 960dp viewport without requiring initial scroll", totalWidth > 960)
    }

    @Test
    fun visibleTileCapacityCalculatesEightFullTiles() {
        // Available width inside 960dp with 48dp padding is 912dp.
        // Each tile slot takes 100dp + 8dp = 108dp.
        // 912 / 108 = 8 full tiles, 9th tile cut off, 10th & 11th offscreen.
        val fullyVisibleCount = RecentsGeometry.calculateFullyVisibleTileCount(
            viewportWidthDp = 960,
            horizontalPaddingDp = 48,
            tileWidthDp = 100,
            spacingDp = 8,
        )
        assertEquals(8, fullyVisibleCount)
    }
}
