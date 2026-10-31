package com.redsurf.tv.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** Spec test for Task 020 (SPORTS_HUB.md §3, §6). Do not edit; make it pass. */
class GameSourceResolverTest {

    private fun et(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneId.of("America/New_York")).toInstant().toEpochMilli()

    private val kickoff = et(2026, 11, 1, 13, 0)

    private val eaglesVsCowboys = Game(
        id = "401547432",
        league = League.NFL,
        kickoffMillis = kickoff,
        name = "Dallas Cowboys at Philadelphia Eagles",
        homeTeam = "Philadelphia Eagles",
        awayTeam = "Dallas Cowboys",
        state = GameState.PRE,
        networks = listOf("FOX"),
    )

    @Test
    fun singleConfidentEventChannel() {
        val channels = listOf(
            PlayableChannel(playlistId = "p1", streamId = 10, name = "US| NFL 03: Eagles vs Cowboys 1:00 PM ET"),
            PlayableChannel(playlistId = "p1", streamId = 20, name = "US| CNN HD"),
        )
        val result = GameSourceResolver.resolve(eaglesVsCowboys, channels, market = "philadelphia")
        assertEquals(1, result.sources.size)
        assertEquals(MatchTier.EVENT_CHANNEL, result.sources[0].tier)
        assertEquals(10, result.sources[0].streamId)
        assertTrue(result.isConfident)
    }

    @Test
    fun savedUserPickWinsOverAllProbes() {
        val channels = listOf(
            PlayableChannel(playlistId = "p1", streamId = 10, name = "US| NFL 03: Eagles vs Cowboys 1:00 PM ET"),
            PlayableChannel(playlistId = "p1", streamId = 20, name = "US| FOX 29 PHILADELPHIA PA (WTXF) HD"),
        )
        // User previously saved channel 20 as their preferred source for this game
        val result = GameSourceResolver.resolve(
            eaglesVsCowboys,
            channels,
            market = "philadelphia",
            savedPick = Pair("p1", 20),
        )
        assertEquals(2, result.sources.size)
        assertEquals(MatchTier.USER_PICK, result.sources[0].tier)
        assertEquals(20, result.sources[0].streamId)
        assertEquals(MatchTier.EVENT_CHANNEL, result.sources[1].tier)
    }

    @Test
    fun multipleProbesIncreaseAgreement() {
        val channels = listOf(
            PlayableChannel(playlistId = "p1", streamId = 20, name = "US - FOX 29 PHILADELPHIA PA (WTXF) HD"),
            PlayableChannel(playlistId = "p1", streamId = 30, name = "US - FOX 29 BUFFALO NY (WUTV) HD"),
        )
        val epg = listOf(
            EpgProgramEntry(
                playlistId = "p1",
                streamId = 20,
                title = "NFL Football: Dallas Cowboys at Philadelphia Eagles",
                subtitle = "Live from Lincoln Financial Field",
                description = "NFC East rivalry game.",
                startMillis = kickoff,
                endMillis = kickoff + (3 * 3600 * 1000),
            ),
        )
        val result = GameSourceResolver.resolve(eaglesVsCowboys, channels, epgEntries = epg, market = "philadelphia")
        assertEquals(2, result.sources.size)
        // Channel 20 was found by both the local broadcast affiliate probe and EPG probe
        assertEquals(20, result.sources[0].streamId)
        assertEquals(MatchTier.EPG_TITLE, result.sources[0].tier)
        assertTrue(result.sources[0].probes >= 2)
    }

    @Test
    fun languageFilterDropsUnallowedSpanishChannelExceptEventChannels() {
        val channels = listOf(
            PlayableChannel(playlistId = "p1", streamId = 10, name = "LAT| FOX DEPORTES HD"),
            PlayableChannel(playlistId = "p1", streamId = 20, name = "US - FOX 29 PHILADELPHIA PA (WTXF) HD"),
            PlayableChannel(playlistId = "p1", streamId = 30, name = "LAT| NFL: Eagles vs Cowboys 1:00 PM ET"),
        )
        val result = GameSourceResolver.resolve(
            eaglesVsCowboys,
            channels,
            market = "philadelphia",
            allowedLanguages = setOf("en"),
        )
        val streamIds = result.sources.map { it.streamId }
        // Channel 10 (Spanish linear) is filtered out
        assertFalse(streamIds.contains(10))
        // Channel 20 (English linear) is kept
        assertTrue(streamIds.contains(20))
        // Channel 30 (Event channel) bypasses language filter per SPORTS_HUB.md §3
        assertTrue(streamIds.contains(30))
    }

    @Test
    fun qualityVariantsMergedWithinPlaylist() {
        val channels = listOf(
            PlayableChannel(playlistId = "p1", streamId = 10, name = "US| ESPN HD"),
            PlayableChannel(playlistId = "p1", streamId = 11, name = "US| ESPN FHD"),
        )
        val espnGame = Game(
            id = "2",
            league = League.NFL,
            kickoffMillis = kickoff,
            name = "Monday Night Football",
            homeTeam = "Kansas City Chiefs",
            awayTeam = "Baltimore Ravens",
            state = GameState.PRE,
            networks = listOf("ESPN"),
        )
        val result = GameSourceResolver.resolve(espnGame, channels)
        // Merged into single variant with highest quality (FHD)
        assertEquals(1, result.sources.size)
        assertEquals(11, result.sources[0].streamId)
        assertEquals(Quality.FHD, result.sources[0].quality)
    }

    @Test
    fun noMatchingSourcesReturnsEmpty() {
        val channels = listOf(
            PlayableChannel(playlistId = "p1", streamId = 1, name = "US| HGTV HD"),
            PlayableChannel(playlistId = "p1", streamId = 2, name = "US| FOOD NETWORK"),
        )
        val result = GameSourceResolver.resolve(eaglesVsCowboys, channels)
        assertTrue(result.sources.isEmpty())
        assertFalse(result.isConfident)
    }

    @Test
    fun epgOutsideKickoffWindowIsIgnored() {
        val channels = listOf(
            PlayableChannel(playlistId = "p1", streamId = 50, name = "US| NBC SPORTS PHILADELPHIA HD"),
        )
        // EPG entry with matching teams, but aired yesterday (outside kickoff -2h to +4h)
        val epg = listOf(
            EpgProgramEntry(
                playlistId = "p1",
                streamId = 50,
                title = "Dallas Cowboys at Philadelphia Eagles Replay",
                startMillis = kickoff - (24 * 3600 * 1000),
                endMillis = kickoff - (21 * 3600 * 1000),
            ),
        )
        val result = GameSourceResolver.resolve(eaglesVsCowboys, channels)
        // EPG probe rejected due to time window; channel is not matched
        assertTrue(result.sources.isEmpty())
    }

    @Test
    fun inMarketAffiliateWinsOverGenericNational() {
        val channels = listOf(
            PlayableChannel(playlistId = "p1", streamId = 30, name = "US - FOX 29 BUFFALO NY (WUTV) HD"),
            PlayableChannel(playlistId = "p1", streamId = 20, name = "US - FOX 29 PHILADELPHIA PA (WTXF) HD"),
        )
        val result = GameSourceResolver.resolve(eaglesVsCowboys, channels, market = "philadelphia")
        assertEquals(2, result.sources.size)
        assertEquals(20, result.sources[0].streamId)
        assertEquals(MatchTier.IN_MARKET, result.sources[0].tier)
        assertEquals(30, result.sources[1].streamId)
        assertEquals(MatchTier.MAYBE, result.sources[1].tier)
    }
}
