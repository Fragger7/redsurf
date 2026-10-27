package com.redsurf.tv.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EspnScoreboardParserTest {

    private val fixture: String =
        checkNotNull(javaClass.classLoader?.getResource("sports/espn_nfl_scoreboard.json")) { "fixture missing" }
            .readText()

    @Test
    fun parsesEveryEventInTheFixture() {
        val games = EspnScoreboardParser.parse(League.NFL, fixture)
        assertEquals(3, games.size)
    }

    @Test
    fun readsKickoffAsUtcMillis() {
        val game = EspnScoreboardParser.parse(League.NFL, fixture).first { it.id == "401873011" }
        // 2026-10-25T13:30Z
        assertEquals(1792935000000L, game.kickoffMillis)
    }

    @Test
    fun readsHomeAndAwayTeams() {
        val game = EspnScoreboardParser.parse(League.NFL, fixture).first { it.id == "401873011" }
        assertEquals("New Orleans Saints", game.homeTeam)
        assertEquals("Pittsburgh Steelers", game.awayTeam)
        assertEquals(League.NFL, game.league)
    }

    @Test
    fun readsNetworksAndState() {
        val games = EspnScoreboardParser.parse(League.NFL, fixture).associateBy { it.id }
        assertEquals(listOf("NFL Net"), games.getValue("401873011").networks)
        assertEquals(listOf("CBS"), games.getValue("401873013").networks)
        assertEquals(listOf("NBC"), games.getValue("401873022").networks)
        assertEquals(GameState.POST, games.getValue("401873011").state)
    }

    @Test
    fun parsesKickoffWithSeconds() {
        assertEquals(1792935000000L, EspnScoreboardParser.parseKickoff("2026-10-25T13:30:00Z"))
    }

    @Test
    fun returnsNullForUnparseableKickoff() {
        assertNull(EspnScoreboardParser.parseKickoff("not a date"))
    }

    @Test
    fun emptyOrMissingEventsGivesNoGames() {
        assertEquals(emptyList<Game>(), EspnScoreboardParser.parse(League.NFL, "{}"))
    }
}
