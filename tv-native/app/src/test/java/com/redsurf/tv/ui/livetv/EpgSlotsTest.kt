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
    fun noProgrammes_halfHourGapsAcrossWholeWindow() {
        val slots = slotsFor(emptyList(), windowStart, windowEnd)
        assertEquals(WINDOW_MINUTES / 30, slots.size)
        assertTrue(slots.all { it is GapSlot && it.end - it.start == HALF_HOUR_MS })
        assertTilesWindow(slots)
    }

    @Test
    fun gaps_neverCrossHalfHourBoundaries() {
        val slots = slotsFor(listOf(programme(10, 20), programme(95, 100)), windowStart, windowEnd)
        slots.filterIsInstance<GapSlot>().forEach { gap ->
            assertTrue("gap within one column", (gap.start - windowStart) / HALF_HOUR_MS == (gap.end - 1 - windowStart) / HALF_HOUR_MS)
        }
        assertTilesWindow(slots)
    }

    @Test
    fun nowSlotIndex_landsOnAiringThenUpcoming() {
        val slots = slotsFor(listOf(programme(0, 60, "a"), programme(120, 180, "b")), windowStart, windowEnd)
        assertEquals("a", (slots[nowSlotIndex(slots, windowStart + 10 * MINUTE_MS)] as AiredSlot).programme.title)
        val inGap = slots[nowSlotIndex(slots, windowStart + 70 * MINUTE_MS)]
        assertTrue(inGap is GapSlot && inGap.start <= windowStart + 70 * MINUTE_MS)
    }

    @Test
    fun holesBetweenProgrammes_becomeGaps() {
        val slots = slotsFor(listOf(programme(30, 60, "a"), programme(150, 210, "b")), windowStart, windowEnd)
        assertTrue(slots[0] is GapSlot)
        assertEquals("a", (slots[1] as AiredSlot).programme.title)
        // the 90-minute hole between them is three half-hour gaps
        assertTrue(slots.subList(2, 5).all { it is GapSlot && it.end - it.start == HALF_HOUR_MS })
        assertEquals("b", (slots[5] as AiredSlot).programme.title)
        assertTilesWindow(slots)
    }

    @Test
    fun programmesOverlappingWindowEdges_areClipped() {
        val last = WINDOW_MINUTES.toLong()
        val slots = slotsFor(listOf(programme(-60, 30), programme(last - 30, last + 60)), windowStart, windowEnd)
        assertEquals(windowStart, slots.first().start)
        assertEquals(30 * MINUTE_MS, slots.first().end - slots.first().start)
        assertEquals(windowEnd, slots.last().end)
        assertEquals(30 * MINUTE_MS, slots.last().end - slots.last().start)
        assertTilesWindow(slots)
    }

    @Test
    fun programmesOutsideWindow_areIgnored() {
        val last = WINDOW_MINUTES.toLong()
        val slots = slotsFor(listOf(programme(-120, -60), programme(last + 40, last + 100)), windowStart, windowEnd)
        assertTrue(slots.all { it is GapSlot })
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

    // Row focus (2026-10-20): a row's selected programme is tracked by start time.

    @Test
    fun slotIndexAt_findsTheSlotCoveringTheTime() {
        val slots = slotsFor(listOf(programme(60, 120, "a"), programme(120, 300, "b")), windowStart, windowEnd)
        val a = slotIndexAt(slots, windowStart + 90 * MINUTE_MS)
        assertEquals("a", (slots[a] as AiredSlot).programme.title)
        val b = slotIndexAt(slots, windowStart + 120 * MINUTE_MS) // a programme's start belongs to it
        assertEquals("b", (slots[b] as AiredSlot).programme.title)
    }

    @Test
    fun slotIndexAt_clampsOutsideTheWindow() {
        val slots = slotsFor(emptyList(), windowStart, windowEnd)
        assertEquals(0, slotIndexAt(slots, windowStart - 1))
        assertEquals(slots.lastIndex, slotIndexAt(slots, windowEnd + 1))
    }

    @Test
    fun slotIndexAt_nothingSelectedOrNoSlots_isMinusOne() {
        val slots = slotsFor(emptyList(), windowStart, windowEnd)
        assertEquals(-1, slotIndexAt(slots, Long.MIN_VALUE))
        assertEquals(-1, slotIndexAt(emptyList(), windowStart))
    }

    @Test
    fun slotIndexAt_followsTheProgrammeWhenListingsReplaceTheSlots() {
        // A row focused on the 1:00 gap; listings then arrive and that hour becomes a programme.
        val before = slotsFor(emptyList(), windowStart, windowEnd)
        val selectedStart = before[slotIndexAt(before, windowStart + 60 * MINUTE_MS)].start
        val after = slotsFor(listOf(programme(45, 150, "late")), windowStart, windowEnd)
        assertEquals("late", (after[slotIndexAt(after, selectedStart)] as AiredSlot).programme.title)
    }
}
