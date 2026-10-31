package com.redsurf.tv.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec test for Task 019 (SPORTS_HUB.md §3a, §9.7). Do not edit; make it pass. */
class NetworkBroadcastMatchTest {

    private fun game(
        id: String = "1",
        league: League = League.NFL,
        homeTeam: String = "Philadelphia Eagles",
        awayTeam: String = "Dallas Cowboys",
        networks: List<String>,
    ): Game = Game(
        id = id,
        league = league,
        kickoffMillis = 1700000000000L,
        name = "$awayTeam at $homeTeam",
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        state = GameState.PRE,
        networks = networks,
    )

    @Test
    fun nationalCableNetworkMatchesNationalTier() {
        val g = game(networks = listOf("ESPN"))
        assertEquals(
            MatchTier.NATIONAL,
            NetworkBroadcastMatch.match("US| ESPN HD", g, "philadelphia"),
        )
    }

    @Test
    fun nationalSportsNetworkAlias() {
        val g = game(networks = listOf("NFL Net"))
        assertEquals(
            MatchTier.NATIONAL,
            NetworkBroadcastMatch.match("US| NFL NETWORK FHD", g, "philadelphia"),
        )
    }

    @Test
    fun marketRsnMatchesInMarketTier() {
        val g = game(networks = listOf("NBC Sports Phil"))
        assertEquals(
            MatchTier.IN_MARKET,
            NetworkBroadcastMatch.match("US| NBC SPORTS PHILADELPHIA HD", g, "philadelphia"),
        )
    }

    @Test
    fun marketAffiliateMatchesNationalBroadcast() {
        val g = game(networks = listOf("FOX"))
        assertEquals(
            MatchTier.IN_MARKET,
            NetworkBroadcastMatch.match("US - FOX 29 PHILADELPHIA PA (WTXF) HD", g, "philadelphia"),
        )
    }

    @Test
    fun marketAffiliateAbc6Philadelphia() {
        val g = game(networks = listOf("ABC"))
        assertEquals(
            MatchTier.IN_MARKET,
            NetworkBroadcastMatch.match("US| 6ABC PHILADELPHIA HD", g, "philadelphia"),
        )
    }

    @Test
    fun unrelatedNetworkReturnsNull() {
        val g = game(networks = listOf("TNT"))
        assertNull(NetworkBroadcastMatch.match("US| ESPN HD", g, "philadelphia"))
    }

    @Test
    fun channelWithTestFlagReturnsNull() {
        val g = game(networks = listOf("ESPN"))
        assertNull(NetworkBroadcastMatch.match("US| ESPN HD [TEST]", g, "philadelphia"))
    }

    @Test
    fun channelWithBackupFlagReturnsNull() {
        val g = game(networks = listOf("ESPN"))
        assertNull(NetworkBroadcastMatch.match("US| ESPN BACKUP", g, "philadelphia"))
    }

    @Test
    fun gameWithNoNetworksReturnsNull() {
        val g = game(networks = emptyList())
        assertNull(NetworkBroadcastMatch.match("US| ESPN HD", g, "philadelphia"))
    }

    @Test
    fun outOfMarketBroadcastAffiliateIsMaybe() {
        // FOX affiliate from another market (Buffalo WUTV) when user is in Philadelphia
        val g = game(networks = listOf("FOX"))
        assertEquals(
            MatchTier.MAYBE,
            NetworkBroadcastMatch.match("US - FOX 29 BUFFALO NY (WUTV) HD", g, "philadelphia"),
        )
    }

    @Test
    fun streamingExclusiveSimulcastLocalAffiliate() {
        // Prime Video game simulcast on local affiliate per SPORTS_HUB.md §9.7
        val g = game(networks = listOf("Prime Video"))
        assertEquals(
            MatchTier.IN_MARKET,
            NetworkBroadcastMatch.match("US - FOX 29 PHILADELPHIA PA (WTXF) HD", g, "philadelphia"),
        )
    }

    @Test
    fun noMarketSpecifiedMatchesNationalOnly() {
        val g = game(networks = listOf("ESPN"))
        assertEquals(
            MatchTier.NATIONAL,
            NetworkBroadcastMatch.match("US| ESPN HD", g, market = null),
        )
    }
}
