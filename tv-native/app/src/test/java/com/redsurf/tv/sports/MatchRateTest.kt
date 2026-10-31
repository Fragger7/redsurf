package com.redsurf.tv.sports

import org.junit.Assert.assertEquals
import org.junit.Test

/** Lead-written spec for task 012 (SPORTS_HUB.md S0 exit criterion). Do not edit; make it pass. */
class MatchRateTest {

    private fun game(id: String, vararg networks: String) = Game(
        id = id, league = League.NFL, kickoffMillis = 0L, name = "A at B",
        homeTeam = "B", awayTeam = "A", state = GameState.PRE, networks = networks.toList(),
    )

    private val market = "philadelphia"

    @Test fun nationalGameWithAMatchingChannelHasASource() {
        val r = MatchRate.measure(listOf(game("1", "ESPN")), listOf("US| ESPN HD"), market)
        assertEquals(1, r.nationalGames)
        assertEquals(1, r.nationalGamesWithSource)
        assertEquals(1.0, r.rate, 0.0)
    }

    @Test fun nationalGameWithoutAChannelHasNoSource() {
        val r = MatchRate.measure(listOf(game("1", "TNT")), listOf("US| ESPN HD"), market)
        assertEquals(1, r.nationalGames)
        assertEquals(0, r.nationalGamesWithSource)
    }

    @Test fun marketAffiliateCountsForItsNetwork() {
        val r = MatchRate.measure(listOf(game("1", "FOX")), listOf("US| FOX 29 Philadelphia HD"), market)
        assertEquals(1, r.nationalGamesWithSource)
    }

    @Test fun affiliateOfAnotherMarketDoesNotCount() {
        val r = MatchRate.measure(listOf(game("1", "FOX")), listOf("US| FOX 29 Philadelphia HD"), "boston")
        assertEquals(0, r.nationalGamesWithSource)
    }

    @Test fun flaggedChannelsAreNotSources() {
        val r = MatchRate.measure(
            listOf(game("1", "ESPN")),
            listOf("US| ESPN BACKUP", "US| ESPN TEST", "US| ESPN 24/7"),
            market,
        )
        assertEquals(0, r.nationalGamesWithSource)
    }

    @Test fun gameWithOnlyUnknownNetworksIsNotNationalAndIsReported() {
        val r = MatchRate.measure(
            listOf(game("1", "NBC Sports Philadelphia"), game("2", "Bally Sports Detroit", "NBC Sports Philadelphia")),
            listOf("US| NBC Sports Philadelphia HD"),
            market,
        )
        assertEquals(0, r.nationalGames)
        assertEquals(0.0, r.rate, 0.0)
        assertEquals(mapOf("NBC Sports Philadelphia" to 2, "Bally Sports Detroit" to 1), r.unmatchedNetworks)
    }

    @Test fun anyOneNationalNetworkMakesTheGameNational() {
        val r = MatchRate.measure(listOf(game("1", "Bally Sports Detroit", "TNT")), listOf("US| TNT"), market)
        assertEquals(1, r.nationalGames)
        assertEquals(1, r.nationalGamesWithSource)
        assertEquals(mapOf("Bally Sports Detroit" to 1), r.unmatchedNetworks)
    }

    @Test fun rateIsWithSourceOverNational() {
        val games = listOf(game("1", "ESPN"), game("2", "TNT"), game("3", "CBS"), game("4", "ABC"))
        val r = MatchRate.measure(games, listOf("US| ESPN", "US| CBS 3 Philadelphia", "US| ABC"), market)
        assertEquals(4, r.nationalGames)
        assertEquals(3, r.nationalGamesWithSource)
        assertEquals(0.75, r.rate, 1e-9)
    }

    @Test fun gameWithNoNetworksIsIgnored() {
        val r = MatchRate.measure(listOf(game("1")), listOf("US| ESPN"), market)
        assertEquals(0, r.nationalGames)
        assertEquals(emptyMap<String, Int>(), r.unmatchedNetworks)
    }
}
