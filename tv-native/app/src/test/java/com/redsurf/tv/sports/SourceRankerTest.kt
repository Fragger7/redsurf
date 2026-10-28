package com.redsurf.tv.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRankerTest {

    private fun c(id: Int, tier: MatchTier, pl: String = "a", q: Quality? = null, fav: Boolean = false) =
        SourceCandidate(pl, id, tier, quality = q, isFavorite = fav)

    @Test
    fun ordersByTierFirst() {
        val r = SourceRanker.rank(listOf(c(1, MatchTier.MAYBE), c(2, MatchTier.NATIONAL), c(3, MatchTier.EVENT_CHANNEL)))
        assertEquals(listOf(3, 2, 1), r.sources.map { it.streamId })
    }

    @Test
    fun favouriteBeatsQualityWithinATier() {
        val r = SourceRanker.rank(listOf(c(1, MatchTier.NATIONAL, q = Quality.UHD), c(2, MatchTier.NATIONAL, q = Quality.SD, fav = true)))
        assertEquals(2, r.best?.streamId)
    }

    @Test
    fun qualityBreaksRemainingTies() {
        val r = SourceRanker.rank(listOf(c(1, MatchTier.NATIONAL, q = Quality.SD), c(2, MatchTier.NATIONAL, q = Quality.FHD), c(3, MatchTier.NATIONAL)))
        assertEquals(listOf(2, 1, 3), r.sources.map { it.streamId })
    }

    @Test
    fun duplicatesMergeKeepingStrongestTierAndSummingProbes() {
        val r = SourceRanker.rank(listOf(c(1, MatchTier.NATIONAL), c(1, MatchTier.EPG_TITLE), c(2, MatchTier.EPG_TITLE)))
        assertEquals(2, r.sources.size)
        assertEquals(1, r.best?.streamId)
        assertEquals(MatchTier.EPG_TITLE, r.best?.tier)
        assertEquals(2, r.best?.probes)
    }

    @Test
    fun singleStrongWinnerIsConfident() {
        assertTrue(SourceRanker.rank(listOf(c(1, MatchTier.EPG_TITLE), c(2, MatchTier.NATIONAL), c(3, MatchTier.NATIONAL))).isConfident)
    }

    @Test
    fun twoSourcesInTopTierAreNotConfident() {
        assertFalse(SourceRanker.rank(listOf(c(1, MatchTier.NATIONAL), c(2, MatchTier.NATIONAL, pl = "b"))).isConfident)
        assertFalse(SourceRanker.rank(listOf(c(1, MatchTier.EVENT_CHANNEL), c(2, MatchTier.EVENT_CHANNEL), c(3, MatchTier.NATIONAL))).isConfident)
    }

    @Test
    fun loneTopTierSourceIsConfidentEvenWhenWeak() {
        assertTrue(SourceRanker.rank(listOf(c(1, MatchTier.IN_MARKET), c(2, MatchTier.MAYBE))).isConfident)
    }

    @Test
    fun emptyAndCapped() {
        val empty = SourceRanker.rank(emptyList())
        assertNull(empty.best)
        assertFalse(empty.isConfident)
        assertEquals(SourceRanker.MAX_SOURCES, SourceRanker.rank((1..30).map { c(it, MatchTier.MAYBE) }).sources.size)
    }
}

class SourceFilterTest {

    private fun n(key: String, q: Quality? = null, vararg f: ChannelFlag) = ChannelName(key, "US", q, f.toSet())
    private fun c(id: Int, pl: String = "a", tier: MatchTier = MatchTier.NATIONAL) = SourceCandidate(pl, id, tier)

    @Test
    fun dropsTestBackupAndTwentyFourSeven() {
        val out = SourceFilter.apply(listOf(
            c(1) to n("espn"),
            c(2) to n("espn2", null, ChannelFlag.TEST),
            c(3) to n("fs1", null, ChannelFlag.BACKUP),
            c(4) to n("nfl", null, ChannelFlag.TWENTY_FOUR_SEVEN),
        ))
        assertEquals(listOf(1), out.map { it.streamId })
    }

    @Test
    fun mergesQualityVariantsToBestTier() {
        val out = SourceFilter.apply(listOf(c(1) to n("espn", Quality.HD), c(2) to n("espn", Quality.FHD), c(3) to n("espn")))
        assertEquals(listOf(2), out.map { it.streamId })
    }

    @Test
    fun keepsVariantsAcrossPlaylistsAndPrefersStrongerEvidence() {
        val out = SourceFilter.apply(listOf(
            c(1, "a") to n("espn", Quality.UHD),
            c(2, "a", MatchTier.EPG_TITLE) to n("espn", Quality.SD),
            c(3, "b") to n("espn", Quality.HD),
        ))
        assertEquals(setOf(2, 3), out.map { it.streamId }.toSet())
    }
}
