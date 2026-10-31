package com.redsurf.tv.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec test for Task 025 (SPORTS_HUB.md §5, §9.6, §10). Do not edit; make it pass. */
class SportsHubFeedBuilderTest {

    private fun game(
        id: String,
        league: League,
        homeTeam: String,
        awayTeam: String,
        state: GameState,
        kickoffMillis: Long = 1700000000000L,
    ) = Game(
        id = id,
        league = league,
        kickoffMillis = kickoffMillis,
        name = "$awayTeam at $homeTeam",
        homeTeam = homeTeam,
        awayTeam = awayTeam,
        state = state,
        networks = listOf("FOX"),
    )

    private fun candidate(streamId: Int, tier: MatchTier = MatchTier.NATIONAL) = SourceCandidate(
        playlistId = "p1",
        streamId = streamId,
        tier = tier,
        probes = 1,
        isFavorite = false,
        playlistWorking = true,
        quality = QualityTier.HD,
        lastSuccessMillis = null,
        kickoffDistanceMillis = 0L,
    )

    @Test
    fun liveSectionPopulatedOnlyWithInGames() {
        val g1 = game("1", League.NFL, "Philadelphia Eagles", "Dallas Cowboys", GameState.IN)
        val g2 = game("2", League.NBA, "Philadelphia 76ers", "Boston Celtics", GameState.PRE)
        val g3 = game("3", League.NHL, "Boston Bruins", "New York Rangers", GameState.POST)

        val resolved = listOf(
            g1 to RankedSources(listOf(candidate(10)), isConfident = true),
            g2 to RankedSources(listOf(candidate(20)), isConfident = true),
            g3 to RankedSources(emptyList(), isConfident = false),
        )

        val feed = SportsHubFeedBuilder.buildFeed(resolved, market = "philadelphia")

        assertNotNull(feed.liveSection)
        assertEquals("Live Now", feed.liveSection?.title)
        assertEquals(1, feed.liveSection?.cards?.size)
        assertEquals("1", feed.liveSection?.cards?.get(0)?.game?.id)
    }

    @Test
    fun liveSectionIsNullWhenNoLiveGames() {
        val g1 = game("1", League.NFL, "Kansas City Chiefs", "Buffalo Bills", GameState.PRE)
        val g2 = game("2", League.NBA, "Los Angeles Lakers", "Golden State Warriors", GameState.POST)

        val resolved = listOf(
            g1 to RankedSources(listOf(candidate(10)), isConfident = true),
            g2 to RankedSources(listOf(candidate(20)), isConfident = true),
        )

        val feed = SportsHubFeedBuilder.buildFeed(resolved)
        assertNull(feed.liveSection)
    }

    @Test
    fun followedSectionPopulatedWithPhiladelphiaMarketTeams() {
        val eaglesGame = game("1", League.NFL, "Philadelphia Eagles", "Dallas Cowboys", GameState.PRE)
        val chiefsGame = game("2", League.NFL, "Kansas City Chiefs", "Buffalo Bills", GameState.PRE)

        val resolved = listOf(
            eaglesGame to RankedSources(listOf(candidate(10)), isConfident = true),
            chiefsGame to RankedSources(listOf(candidate(20)), isConfident = true),
        )

        val feed = SportsHubFeedBuilder.buildFeed(resolved, market = "philadelphia")

        assertNotNull(feed.followedSection)
        assertEquals("Philadelphia", feed.followedSection?.title)
        assertEquals(1, feed.followedSection?.cards?.size)
        assertEquals("1", feed.followedSection?.cards?.get(0)?.game?.id)
        assertTrue(feed.followedSection?.cards?.get(0)?.isFollowedTeam == true)
    }

    @Test
    fun leagueSectionsCreatedInOrderAndEmptyLeaguesOmitted() {
        val nflGame = game("1", League.NFL, "Dallas Cowboys", "San Francisco 49ers", GameState.PRE)
        val nbaGame = game("2", League.NBA, "Boston Celtics", "Milwaukee Bucks", GameState.PRE)

        val resolved = listOf(
            nflGame to RankedSources(listOf(candidate(10)), isConfident = true),
            nbaGame to RankedSources(listOf(candidate(20)), isConfident = true),
        )

        val feed = SportsHubFeedBuilder.buildFeed(resolved)

        // Only NFL and NBA have games; MLB, NHL, UFC are omitted
        assertEquals(2, feed.leagueSections.size)
        assertEquals(League.NFL, feed.leagueSections[0].league)
        assertEquals("NFL", feed.leagueSections[0].title)
        assertEquals(League.NBA, feed.leagueSections[1].league)
        assertEquals("NBA", feed.leagueSections[1].title)
    }

    @Test
    fun cardsWithinSectionSortedLiveFirstThenKickoff() {
        val gLate = game("late", League.NFL, "Kansas City Chiefs", "Denver Broncos", GameState.PRE, kickoffMillis = 2000L)
        val gLive = game("live", League.NFL, "Dallas Cowboys", "Philadelphia Eagles", GameState.IN, kickoffMillis = 1000L)
        val gEarly = game("early", League.NFL, "Green Bay Packers", "Chicago Bears", GameState.PRE, kickoffMillis = 1000L)

        val resolved = listOf(
            gLate to RankedSources(listOf(candidate(1)), isConfident = true),
            gLive to RankedSources(listOf(candidate(2)), isConfident = true),
            gEarly to RankedSources(listOf(candidate(3)), isConfident = true),
        )

        val feed = SportsHubFeedBuilder.buildFeed(resolved)
        val nflCards = feed.leagueSections.first { it.league == League.NFL }.cards

        assertEquals(3, nflCards.size)
        // Live game is ordered first
        assertEquals("live", nflCards[0].game.id)
        // Earlier pre-game is next
        assertEquals("early", nflCards[1].game.id)
        // Later pre-game is last
        assertEquals("late", nflCards[2].game.id)
    }

    @Test
    fun cardReflectsRankedSourcesAndConfidence() {
        val top = candidate(42, MatchTier.EVENT_CHANNEL)
        val g = game("1", League.NFL, "Philadelphia Eagles", "Dallas Cowboys", GameState.PRE)
        val resolved = listOf(
            g to RankedSources(sources = listOf(top), isConfident = true),
        )

        val feed = SportsHubFeedBuilder.buildFeed(resolved)
        val card = feed.leagueSections[0].cards[0]

        assertEquals(top, card.topSource)
        assertEquals(1, card.sourceCount)
        assertTrue(card.isConfident)
    }

    @Test
    fun cardIncludesResolvedLogoUrls() {
        val g = game("1", League.NFL, "Philadelphia Eagles", "Dallas Cowboys", GameState.PRE)
        val resolved = listOf(
            g to RankedSources(emptyList(), isConfident = false),
        )

        val feed = SportsHubFeedBuilder.buildFeed(resolved)
        val card = feed.leagueSections[0].cards[0]

        assertEquals("https://a.espncdn.com/i/teamlogos/nfl/500/phi.png", card.homeLogoUrl)
        assertEquals("https://a.espncdn.com/i/teamlogos/nfl/500/dal.png", card.awayLogoUrl)
    }
}
