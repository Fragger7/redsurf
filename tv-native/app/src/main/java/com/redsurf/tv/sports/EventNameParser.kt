package com.redsurf.tv.sports

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * An event channel's parsed participants and optional scheduled start time in Eastern Time,
 * used by the sports matcher to correlate provider event streams with league fixtures.
 */
data class ParsedEvent(
    val sideA: String,
    val sideB: String,
    val minutesEt: Int?,
)

/**
 * Parses provider event and pay-per-view channel names into competing sides and kickoff times,
 * and checks whether a channel's scheduled start time aligns with a fixture. Pure and deterministic.
 */
object EventNameParser {

    private val easternZone = ZoneId.of("America/New_York")
    private val countryPrefix = Regex("""^[a-z]{2,3}\s*\|""")
    private val timePattern = Regex("""\s+(1[0-2]|0?[1-9])(?::([0-5][0-9]))?\s*(am|pm)(?:\s+([a-z]+))?$""")
    private val easternZones = setOf("et", "est", "edt")
    private val separators = setOf("vs", "vs.", "v", "@")

    /**
     * Parses a raw channel title by normalizing text, stripping league and country prefixes,
     * extracting any trailing start time, and separating the two competing sides.
     */
    fun parse(raw: String): ParsedEvent? {
        var text = Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase().trim()

        val prefixMatch = countryPrefix.find(text)
        if (prefixMatch != null) {
            text = text.substring(prefixMatch.range.last + 1).trim()
        }

        val colonIndex = text.indexOf(": ")
        if (colonIndex != -1) {
            text = text.substring(colonIndex + 2).trim()
        }

        var minutesEt: Int? = null
        val timeMatch = timePattern.find(text)
        if (timeMatch != null) {
            val hourRaw = timeMatch.groupValues[1].toInt()
            val minuteRaw = timeMatch.groupValues[2].ifEmpty { null }?.toInt() ?: 0
            val amPm = timeMatch.groupValues[3]
            val zone = timeMatch.groupValues[4].ifEmpty { null }

            if (zone == null || zone in easternZones) {
                val hour = when {
                    hourRaw == 12 && amPm == "am" -> 0
                    hourRaw == 12 && amPm == "pm" -> 12
                    amPm == "pm" -> hourRaw + 12
                    else -> hourRaw
                }
                minutesEt = hour * 60 + minuteRaw
            }

            text = text.substring(0, timeMatch.range.first).trim()
        }

        val tokens = text.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val sepIndex = tokens.indexOfFirst { it in separators }
        if (sepIndex <= 0 || sepIndex >= tokens.size - 1) {
            return null
        }

        val sideA = tokens.subList(0, sepIndex).joinToString(" ")
        val sideB = tokens.subList(sepIndex + 1, tokens.size).joinToString(" ")
        if (sideA.isBlank() || sideB.isBlank()) {
            return null
        }

        return ParsedEvent(sideA = sideA, sideB = sideB, minutesEt = minutesEt)
    }

    /**
     * Checks whether a channel's start time in Eastern Time falls within [toleranceMin] of a
     * fixture's kickoff time, accounting for midnight wrapping on a 24-hour clock.
     */
    fun timeFits(minutesEt: Int, kickoffMillis: Long, toleranceMin: Int = 90): Boolean {
        val zdt = Instant.ofEpochMilli(kickoffMillis).atZone(easternZone)
        val kickoffMinutes = zdt.hour * 60 + zdt.minute
        val normalizedMinutesEt = (minutesEt % 1440 + 1440) % 1440
        val diff = abs(normalizedMinutesEt - kickoffMinutes)
        val circularDiff = minOf(diff, 1440 - diff)
        return circularDiff <= toleranceMin
    }
}
