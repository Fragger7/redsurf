package com.redsurf.tv.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Spec test for Task 018 (SPORTS_HUB.md §3c). Do not edit; make it pass. */
class EventChannelMatchTest {

    private fun et(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneId.of("America/New_York")).toInstant().toEpochMilli()

    private fun game(
        id: String = "1",
        league: League = League.NFL,
        homeTeam: String = "Philadelphia Eagles",
        awayTeam: String = "Dallas Cowboys",
        kickoffMillis: Long = et(2026, 11, 1, 13, 0),
        name: String = "$awayTeam at $homeTeam",
        networks: List<String> = listOf("FOX"),
    ): Game = Game(
        id = id,
        league = league,
        kickoffMillis = kickoffMillis,
        name = name,
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        state = GameState.PRE,
        networks = networks,
    )

    @Test
    fun standardNflHomeVsAwayWithMatchingTime() {
        val g = game(homeTeam = "Philadelphia Eagles", awayTeam = "Dallas Cowboys", kickoffMillis = et(2026, 11, 1, 13, 0))
        assertEquals(
            MatchTier.EVENT_CHANNEL,
            EventChannelMatch.match("NFL 03: Eagles vs Cowboys 1:00 PM ET", g),
        )
    }

    @Test
    fun awayVsHomeAtSignOrder() {
        val g = game(homeTeam = "Philadelphia Eagles", awayTeam = "Dallas Cowboys", kickoffMillis = et(2026, 11, 1, 13, 0))
        assertEquals(
            MatchTier.EVENT_CHANNEL,
            EventChannelMatch.match("NFL 07: Cowboys @ Eagles 1:00 PM ET", g),
        )
    }

    @Test
    fun timeMismatchReturnsNull() {
        val g = game(homeTeam = "Philadelphia Eagles", awayTeam = "Dallas Cowboys", kickoffMillis = et(2026, 11, 1, 13, 0))
        assertNull(EventChannelMatch.match("NFL 03: Eagles vs Cowboys 8:00 PM ET", g))
    }

    @Test
    fun noTimeInChannelMatchesIfTeamsMatch() {
        val g = game(
            league = League.UFC,
            homeTeam = "Smith",
            awayTeam = "Jones",
            kickoffMillis = et(2026, 11, 1, 22, 0),
        )
        assertEquals(
            MatchTier.EVENT_CHANNEL,
            EventChannelMatch.match("UFC 334: Smith vs Jones", g),
        )
    }

    @Test
    fun onlyOneTeamMatchesReturnsNull() {
        val g = game(homeTeam = "Philadelphia Eagles", awayTeam = "Dallas Cowboys")
        assertNull(EventChannelMatch.match("NFL 03: Eagles vs Giants 1:00 PM ET", g))
    }

    @Test
    fun bothSidesSameTeamReturnsNull() {
        val g = game(homeTeam = "Philadelphia Eagles", awayTeam = "Dallas Cowboys")
        assertNull(EventChannelMatch.match("NFL 03: Eagles vs Eagles", g))
    }

    @Test
    fun plainLinearChannelReturnsNull() {
        val g = game(homeTeam = "Philadelphia Eagles", awayTeam = "Dallas Cowboys")
        assertNull(EventChannelMatch.match("US| ESPN HD", g))
    }

    @Test
    fun multiWordTeams() {
        val g = game(
            homeTeam = "Green Bay Packers",
            awayTeam = "New York Giants",
            kickoffMillis = et(2026, 11, 1, 16, 25),
        )
        assertEquals(
            MatchTier.EVENT_CHANNEL,
            EventChannelMatch.match("NFL 01: New York Giants vs Green Bay Packers 4:25 PM ET", g),
        )
    }

    @Test
    fun countryPrefixAndPipeHandled() {
        val g = game(homeTeam = "Philadelphia Eagles", awayTeam = "Dallas Cowboys", kickoffMillis = et(2026, 11, 1, 13, 0))
        assertEquals(
            MatchTier.EVENT_CHANNEL,
            EventChannelMatch.match("US| NFL 03: Eagles vs Cowboys 1:00 PM ET", g),
        )
    }

    @Test
    fun nbaMatchupWithShortCity() {
        val g = game(
            league = League.NBA,
            homeTeam = "Boston Celtics",
            awayTeam = "Los Angeles Lakers",
            kickoffMillis = et(2026, 11, 1, 19, 30),
        )
        assertEquals(
            MatchTier.EVENT_CHANNEL,
            EventChannelMatch.match("NBA: Lakers @ Celtics 7:30pm", g),
        )
    }

    @Test
    fun mlbMatchupWithEdtZone() {
        val g = game(
            league = League.MLB,
            homeTeam = "Philadelphia Phillies",
            awayTeam = "New York Mets",
            kickoffMillis = et(2026, 7, 4, 19, 5),
        )
        assertEquals(
            MatchTier.EVENT_CHANNEL,
            EventChannelMatch.match("MLB: Phillies vs Mets 7:05 PM EDT", g),
        )
    }
}
