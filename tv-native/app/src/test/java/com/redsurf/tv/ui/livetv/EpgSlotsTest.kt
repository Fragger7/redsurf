package com.redsurf.tv.ui.livetv

import com.redsurf.tv.db.EpgProgramEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpgSlotsTest {

    private val windowStart = 1_000_000L * HALF_HOUR_MS
    private val windowEnd = windowStart + WINDOW_MINUTES * MINUTE_MS

    private fun programme(startMin: Long, endMin: Long, title: String = "p") = EpgProgramEntity(
        playlistId = "pl",
        channelEpgId = "ch",
        title = title,
        description = "",
        startTime = windowStart + startMin * MINUTE_MS,
        endTime = windowStart + endMin * MINUTE_MS,
    )

    private fun assertTilesWindow(slots: List<EpgSlot>) {
        assertEquals(windowStart, slots.first().start)
        assertEquals(windowEnd, slots.last().end)
        slots.zipWithNext { a, b -> assertEquals("contiguous", a.end, b.start) }
        slots.forEach { assertTrue("positive width", it.end > it.start) }
        assertEquals(WINDOW_MINUTES * MINUTE_MS, slots.sumOf { it.end - it.start })
    }

    @Test
    fun noProgrammes_oneFullWindowGap() {
        val slots = slotsFor(emptyList(), windowStart, windowEnd)
        assertEquals(1, slots.size)
        assertTrue(slots[0] is GapSlot)
        assertTilesWindow(slots)
    }

    @Test
    fun holesBetweenProgrammes_becomeGaps() {
        val slots = slotsFor(listOf(programme(30, 60, "a"), programme(150, 210, "b")), windowStart, windowEnd)
        assertEquals(5, slots.size)
        assertTrue(slots[0] is GapSlot)
        assertEquals("a", (slots[1] as AiredSlot).programme.title)
        assertTrue(slots[2] is GapSlot)
        assertEquals(90 * MINUTE_MS, slots[2].end - slots[2].start)
        assertEquals("b", (slots[3] as AiredSlot).programme.title)
        assertTrue(slots[4] is GapSlot)
        assertTilesWindow(slots)
    }

    @Test
    fun programmesOverlappingWindowEdges_areClipped() {
        val slots = slotsFor(listOf(programme(-60, 30), programme(330, 420)), windowStart, windowEnd)
        assertEquals(windowStart, slots.first().start)
        assertEquals(30 * MINUTE_MS, slots.first().end - slots.first().start)
        assertEquals(windowEnd, slots.last().end)
        assertEquals(30 * MINUTE_MS, slots.last().end - slots.last().start)
        assertTilesWindow(slots)
    }

    @Test
    fun programmesOutsideWindow_areIgnored() {
        val slots = slotsFor(listOf(programme(-120, -60), programme(400, 460)), windowStart, windowEnd)
        assertEquals(1, slots.size)
        assertTrue(slots[0] is GapSlot)
        assertTilesWindow(slots)
    }

    @Test
    fun overlappingProgrammes_neverProduceNegativeWidths() {
        val slots = slotsFor(listOf(programme(0, 90, "a"), programme(60, 120, "b")), windowStart, windowEnd)
        assertEquals("a", (slots[0] as AiredSlot).programme.title)
        assertEquals(90 * MINUTE_MS, slots[0].end - slots[0].start)
        assertEquals("b", (slots[1] as AiredSlot).programme.title)
        assertEquals(30 * MINUTE_MS, slots[1].end - slots[1].start)
        assertTilesWindow(slots)
    }

    @Test
    fun unsortedInput_isSortedByStart() {
        val slots = slotsFor(listOf(programme(60, 90, "later"), programme(0, 60, "first")), windowStart, windowEnd)
        assertEquals("first", (slots[0] as AiredSlot).programme.title)
        assertEquals("later", (slots[1] as AiredSlot).programme.title)
        assertTilesWindow(slots)
    }

    @Test
    fun floorToHalfHour_snapsDown() {
        val base = floorToHalfHour(System.currentTimeMillis())
        assertEquals(base, floorToHalfHour(base + 17 * MINUTE_MS))
        assertEquals(base + HALF_HOUR_MS, floorToHalfHour(base + 31 * MINUTE_MS))
        assertEquals(base, floorToHalfHour(base))
    }

    @Test
    fun stripCategoryPrefix_stripsOnlyRedundantCategoryTokens() {
        assertEquals("ABC", stripCategoryPrefix("US - ABC", "US | ENTERTAINMENT"))
        assertEquals("NFL NETWORK", stripCategoryPrefix("US| NFL NETWORK", "US • SPORTS"))
        assertEquals("GHANA - 3ABN", stripCategoryPrefix("GHANA - 3ABN", "AF | AFRICA"))
        assertEquals("PLAIN NAME", stripCategoryPrefix("PLAIN NAME", "US | ENTERTAINMENT"))
        assertEquals("US - ", stripCategoryPrefix("US - ", "US | X"))
        assertEquals("X - Y", stripCategoryPrefix("X - Y", null))
    }
}
