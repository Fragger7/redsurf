package com.redsurf.tv.sports

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lead-written spec for task 010 (SPORTS_HUB.md §3c). Do not edit; make it pass. */
class EventNameParserTest {

    private fun et(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneId.of("America/New_York")).toInstant().toEpochMilli()

    @Test fun leagueLabelSidesAndEtTime() {
        assertEquals(ParsedEvent("eagles", "cowboys", 13 * 60), EventNameParser.parse("NFL 03: Eagles vs Cowboys 1:00 PM ET"))
    }

    @Test fun countryPrefixIsDropped() {
        assertEquals(ParsedEvent("eagles", "cowboys", 13 * 60), EventNameParser.parse("US| NFL 03: Eagles vs Cowboys 1:00 PM ET"))
    }

    @Test fun noTimeGivesNullMinutes() {
        assertEquals(ParsedEvent("smith", "jones", null), EventNameParser.parse("UFC 334: Smith vs Jones"))
    }

    @Test fun ppvLabelWithPipeAndVsDot() {
        assertEquals(ParsedEvent("canelo", "crawford", 21 * 60), EventNameParser.parse("PPV 2 | Boxing: Canelo vs. Crawford 9pm"))
    }

    @Test fun atSignSeparatorAndCompactTime() {
        assertEquals(ParsedEvent("lakers", "celtics", 19 * 60 + 30), EventNameParser.parse("NBA: Lakers @ Celtics 7:30pm"))
    }

    @Test fun singleVSeparatorAndEstZone() {
        assertEquals(ParsedEvent("flyers", "rangers", 19 * 60), EventNameParser.parse("NHL 05: Flyers v Rangers 7PM EST"))
    }

    @Test fun edtZoneCountsAsEt() {
        assertEquals(ParsedEvent("phillies", "mets", 19 * 60 + 5), EventNameParser.parse("MLB: Phillies vs Mets 7:05 PM EDT"))
    }

    @Test fun nonEasternZoneKeepsSidesButDropsTime() {
        assertEquals(ParsedEvent("dodgers", "giants", null), EventNameParser.parse("MLB 07: Dodgers vs Giants 7:05 PM PT"))
    }

    @Test fun twelveOClockEdgeCases() {
        assertEquals(0, EventNameParser.parse("NBA: Suns vs Kings 12:00 AM ET")!!.minutesEt)
        assertEquals(12 * 60 + 30, EventNameParser.parse("NBA: Suns vs Kings 12:30 PM ET")!!.minutesEt)
    }

    @Test fun multiWordSidesAreKeptWhole() {
        assertEquals(
            ParsedEvent("new york giants", "green bay packers", null),
            EventNameParser.parse("NFL 01: New York Giants vs Green Bay Packers"),
        )
    }

    @Test fun separatorInsideAWordIsNotASeparator() {
        assertEquals(ParsedEvent("ravens", "vikings", null), EventNameParser.parse("NFL: Ravens vs Vikings"))
    }

    @Test fun noEventScheduledIsNull() {
        assertNull(EventNameParser.parse("NFL 03: No Event Scheduled"))
    }

    @Test fun plainChannelIsNull() {
        assertNull(EventNameParser.parse("US| ESPN HD"))
    }

    @Test fun blankSideIsNull() {
        assertNull(EventNameParser.parse("UFC 334: vs Jones"))
    }

    @Test fun timeFitsWithinTolerance() {
        val kickoff = et(2026, 11, 1, 13, 0)
        assertTrue(EventNameParser.timeFits(13 * 60, kickoff))
        assertTrue(EventNameParser.timeFits(14 * 60 + 30, kickoff))
        assertFalse(EventNameParser.timeFits(15 * 60, kickoff))
        assertFalse(EventNameParser.timeFits(15 * 60, kickoff, toleranceMin = 90))
        assertTrue(EventNameParser.timeFits(15 * 60, kickoff, toleranceMin = 120))
    }

    @Test fun timeFitsUsesEasternDaylightTimeInSummer() {
        assertTrue(EventNameParser.timeFits(19 * 60 + 5, et(2026, 7, 4, 19, 5), toleranceMin = 0))
    }

    @Test fun timeFitsWrapsAroundMidnight() {
        assertTrue(EventNameParser.timeFits(23 * 60 + 50, et(2026, 10, 31, 0, 30)))
        assertTrue(EventNameParser.timeFits(10, et(2026, 10, 30, 23, 30)))
    }
}
