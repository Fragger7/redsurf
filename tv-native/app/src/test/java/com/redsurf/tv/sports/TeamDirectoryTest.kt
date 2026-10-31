package com.redsurf.tv.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec test for Task 024 (SPORTS_HUB.md §2, §5 C, §9.1, §10). Do not edit; make it pass. */
class TeamDirectoryTest {

    @Test
    fun findNflTeamsByNameCodeAndAlias() {
        val eagles = TeamDirectory.find(League.NFL, "Philadelphia Eagles")
        assertNotNull(eagles)
        assertEquals("phi", eagles?.code)
        assertEquals("Eagles", eagles?.name)
        assertEquals("Philadelphia", eagles?.city)
        assertEquals("Philadelphia Eagles", eagles?.fullName)

        assertEquals("phi", TeamDirectory.find(League.NFL, "Eagles")?.code)
        assertEquals("phi", TeamDirectory.find(League.NFL, "phi")?.code)

        val chiefs = TeamDirectory.find(League.NFL, "Kansas City Chiefs")
        assertEquals("kc", chiefs?.code)
        assertEquals("Chiefs", chiefs?.name)

        assertEquals("dal", TeamDirectory.find(League.NFL, "Dallas Cowboys")?.code)
        assertEquals("sf", TeamDirectory.find(League.NFL, "San Francisco 49ers")?.code)
    }

    @Test
    fun findNbaTeamsWithSixersAlias() {
        val sixers = TeamDirectory.find(League.NBA, "Philadelphia 76ers")
        assertNotNull(sixers)
        assertEquals("phi", sixers?.code)
        assertEquals("76ers", sixers?.name)

        // Common colloquial alias
        assertEquals("phi", TeamDirectory.find(League.NBA, "Sixers")?.code)
        assertEquals("phi", TeamDirectory.find(League.NBA, "76ers")?.code)

        assertEquals("bos", TeamDirectory.find(League.NBA, "Boston Celtics")?.code)
        assertEquals("lal", TeamDirectory.find(League.NBA, "Los Angeles Lakers")?.code)
    }

    @Test
    fun findMlbTeams() {
        val phillies = TeamDirectory.find(League.MLB, "Philadelphia Phillies")
        assertNotNull(phillies)
        assertEquals("phi", phillies?.code)
        assertEquals("Phillies", phillies?.name)

        assertEquals("phi", TeamDirectory.find(League.MLB, "Phillies")?.code)
        assertEquals("nyy", TeamDirectory.find(League.MLB, "New York Yankees")?.code)
        assertEquals("lad", TeamDirectory.find(League.MLB, "Los Angeles Dodgers")?.code)
    }

    @Test
    fun findNhlTeams() {
        val flyers = TeamDirectory.find(League.NHL, "Philadelphia Flyers")
        assertNotNull(flyers)
        assertEquals("phi", flyers?.code)
        assertEquals("Flyers", flyers?.name)

        assertEquals("phi", TeamDirectory.find(League.NHL, "Flyers")?.code)
        assertEquals("edm", TeamDirectory.find(League.NHL, "Edmonton Oilers")?.code)
        assertEquals("bos", TeamDirectory.find(League.NHL, "Boston Bruins")?.code)
    }

    @Test
    fun espnLogoUrlConstruction() {
        // NFL
        assertEquals(
            "https://a.espncdn.com/i/teamlogos/nfl/500/phi.png",
            TeamDirectory.logoUrl(League.NFL, "Philadelphia Eagles"),
        )
        assertEquals(
            "https://a.espncdn.com/i/teamlogos/nfl/500/kc.png",
            TeamDirectory.logoUrl(League.NFL, "Chiefs"),
        )

        // NBA
        assertEquals(
            "https://a.espncdn.com/i/teamlogos/nba/500/bos.png",
            TeamDirectory.logoUrl(League.NBA, "Boston Celtics"),
        )

        // MLB
        assertEquals(
            "https://a.espncdn.com/i/teamlogos/mlb/500/nyy.png",
            TeamDirectory.logoUrl(League.MLB, "nyy"),
        )

        // NHL (tested in SPORTS_HUB.md §2 line 41)
        assertEquals(
            "https://a.espncdn.com/i/teamlogos/nhl/500/phi.png",
            TeamDirectory.logoUrl(League.NHL, "Flyers"),
        )
    }

    @Test
    fun unrecognizedTeamOrUfcReturnsNull() {
        // UFC fighters are individual athletes, not franchise teams
        assertNull(TeamDirectory.find(League.UFC, "Jon Jones"))
        assertNull(TeamDirectory.logoUrl(League.UFC, "Jon Jones"))

        // Unrecognized team
        assertNull(TeamDirectory.find(League.NFL, "London Monarchs"))
        assertNull(TeamDirectory.logoUrl(League.NFL, "London Monarchs"))
        assertNull(TeamDirectory.find(League.NBA, "Unknown FC"))
    }

    @Test
    fun philadelphiaMarketTeams() {
        val teams = TeamDirectory.marketTeams("philadelphia")
        assertEquals(4, teams.size)
        assertTrue(teams.any { it.league == League.NFL && it.name == "Eagles" })
        assertTrue(teams.any { it.league == League.NBA && it.name == "76ers" })
        assertTrue(teams.any { it.league == League.MLB && it.name == "Phillies" })
        assertTrue(teams.any { it.league == League.NHL && it.name == "Flyers" })

        // Case-insensitive
        assertEquals(4, TeamDirectory.marketTeams("Philadelphia").size)

        // Unknown market returns empty list
        assertTrue(TeamDirectory.marketTeams("Seattle").isEmpty())
    }

    @Test
    fun isFollowedTeam() {
        val followed = setOf("phi", "Chiefs")
        assertTrue(TeamDirectory.isFollowed(League.NFL, "Philadelphia Eagles", followed))
        assertTrue(TeamDirectory.isFollowed(League.NFL, "Kansas City Chiefs", followed))
        assertFalse(TeamDirectory.isFollowed(League.NFL, "Dallas Cowboys", followed))
    }
}
