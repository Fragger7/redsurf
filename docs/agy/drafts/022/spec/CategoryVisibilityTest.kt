package com.redsurf.tv.ui.livetv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lead-written spec for task 032 (preserving category on-screen position vs centering). */
class CategoryVisibilityTest {

    @Test
    fun targetAlreadyVisiblePreservesScrollPosition() {
        val visible = listOf(2, 3, 4, 5, 6, 7)
        // Item 4 is in the middle of the screen; scrolling must NOT happen
        assertFalse(CategoryVisibility.shouldScroll(targetIndex = 4, visibleIndices = visible))
    }

    @Test
    fun targetAtVisibleBoundariesPreservesScrollPosition() {
        val visible = listOf(1, 2, 3, 4, 5)
        // Top and bottom visible edges do not require scroll
        assertFalse(CategoryVisibility.shouldScroll(targetIndex = 1, visibleIndices = visible))
        assertFalse(CategoryVisibility.shouldScroll(targetIndex = 5, visibleIndices = visible))
    }

    @Test
    fun targetOffScreenAboveTriggersCentering() {
        val visible = listOf(5, 6, 7, 8, 9)
        // Item 2 is above the viewport
        assertTrue(CategoryVisibility.shouldScroll(targetIndex = 2, visibleIndices = visible))
    }

    @Test
    fun targetOffScreenBelowTriggersCentering() {
        val visible = listOf(0, 1, 2, 3, 4)
        // Item 12 is below the viewport
        assertTrue(CategoryVisibility.shouldScroll(targetIndex = 12, visibleIndices = visible))
    }

    @Test
    fun emptyVisibleItemsTriggersFallbackScroll() {
        // Before layout completes or on empty list, fallback allows scrolling
        assertTrue(CategoryVisibility.shouldScroll(targetIndex = 0, visibleIndices = emptyList()))
    }

    @Test
    fun midListFirstIndexCalculationClampsToZero() {
        // Target index 1 with 7 visible slots: (7 - 1) / 2 = 3 rows above. 1 - 3 = -2 -> clamps to 0
        val firstIndex = CategoryVisibility.calculateMidListFirstIndex(targetIndex = 1, visibleItemCount = 7)
        assertEquals(0, firstIndex)
    }

    @Test
    fun midListFirstIndexPositionsItemInMiddle() {
        // Target index 20 with 7 visible slots -> firstVisibleItemIndex = 20 - 3 = 17
        val firstIndex = CategoryVisibility.calculateMidListFirstIndex(targetIndex = 20, visibleItemCount = 7)
        assertEquals(17, firstIndex)
    }
}
