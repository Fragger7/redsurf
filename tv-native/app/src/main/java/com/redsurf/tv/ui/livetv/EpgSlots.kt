package com.redsurf.tv.ui.livetv

import com.redsurf.tv.db.EpgProgramEntity
import com.redsurf.tv.ui.shell.categoryPrefix
import java.util.Calendar

/**
 * EPG_GRID_REDESIGN.md G.1 - the grid's time model, pure Kotlin so it's unit-testable with no
 * device. The single most important change in that redesign: the grid no longer renders a
 * channel's raw programme list (whose widths drifted off the header's scale through minimum
 * widths, inter-cell spacing, and coverage holes collapsing to nothing). It renders a slot list
 * that provably tiles the window - every [EpgSlot.start]/[EpgSlot.end] pair is contiguous, the
 * first starts at `windowStart`, the last ends at `windowEnd`, so the sum of slot widths is
 * exactly the window's width by construction and the header can never disagree with the cells.
 */
sealed interface EpgSlot {
    val start: Long
    val end: Long
}

data class AiredSlot(val programme: EpgProgramEntity, override val start: Long, override val end: Long) : EpgSlot

/** A span of the window with no listing. A channel with no EPG at all is one full-window gap -
 * correct data, drawn as a time-gridded empty span rather than a labelled box. */
data class GapSlot(override val start: Long, override val end: Long) : EpgSlot

const val MINUTE_MS = 60_000L
const val HALF_HOUR_MS = 30 * MINUTE_MS

/** How much of the window fills the viewport at once - exactly six 30-minute columns, on any
 * screen size, since the minute scale is derived from the measured viewport (G.2). */
const val VISIBLE_MINUTES = 180

/** The full scrollable window the grid tiles and the data query covers. */
const val WINDOW_MINUTES = 360

fun slotsFor(programmes: List<EpgProgramEntity>, windowStart: Long, windowEnd: Long): List<EpgSlot> {
    val out = mutableListOf<EpgSlot>()
    var cursor = windowStart
    for (p in programmes.sortedBy { it.startTime }) {
        if (p.endTime <= windowStart || p.startTime >= windowEnd) continue
        // maxOf(cursor) - two listings overlapping each other (bad provider data) get clamped to
        // the previous one's end rather than producing a negative-width slot; the sum stays exact.
        val s = maxOf(p.startTime, cursor)
        val e = minOf(p.endTime, windowEnd)
        if (e <= s) continue
        if (s > cursor) out += GapSlot(cursor, s)
        out += AiredSlot(p, s, e)
        cursor = e
    }
    if (cursor < windowEnd) out += GapSlot(cursor, windowEnd)
    return out
}

/** G.2 - the window starts on the previous :00/:30 in local time, never on the literal current
 * millisecond: header labels come out as round times, the columns are stable across
 * recompositions, and the now-line has a column to actually travel across. */
fun floorToHalfHour(timeMillis: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timeMillis
    cal.set(Calendar.MINUTE, (cal.get(Calendar.MINUTE) / 30) * 30)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun isOnTheHour(timeMillis: Long): Boolean {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timeMillis
    return cal.get(Calendar.MINUTE) == 0
}

private val NAME_SEPARATORS = charArrayOf('-', '|', '•', ':')

/**
 * G.9 - strip a channel name's leading prefix token when it merely repeats a token of the
 * category it's already filed under ("US - ABC" inside "US | ENTERTAINMENT" → "ABC"). Reuses
 * Teleport Menu's own [categoryPrefix] delimiter rule. Deliberately narrow: a prefix that is
 * *not* one of the category's own tokens ("GHANA - X" inside "AF | AFRICA") carries real
 * information in a mixed-country category and is kept; marquee handles the length instead.
 */
fun stripCategoryPrefix(channelName: String, groupName: String?): String {
    if (groupName.isNullOrBlank()) return channelName
    val prefix = categoryPrefix(channelName) ?: return channelName
    val categoryTokens = groupName.split(*NAME_SEPARATORS).map { it.trim().uppercase() }.filter { it.isNotEmpty() }
    if (prefix.uppercase() !in categoryTokens) return channelName
    val separatorIndex = channelName.indices.firstOrNull { channelName[it] in NAME_SEPARATORS } ?: return channelName
    val remainder = channelName.substring(separatorIndex + 1).trim()
    return remainder.ifEmpty { channelName }
}
