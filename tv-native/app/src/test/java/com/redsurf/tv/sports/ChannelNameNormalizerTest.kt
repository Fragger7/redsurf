package com.redsurf.tv.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelNameNormalizerTest {

    @Test
    fun splitsCountryQualityAndKey() {
        val n = ChannelNameNormalizer.normalize("US| NBC SPORTS PHILADELPHIA ᴴᴰ")
        assertEquals("nbc sports philadelphia", n.key)
        assertEquals("US", n.country)
        assertEquals(Quality.HD, n.quality)
        assertEquals(emptySet<ChannelFlag>(), n.flags)
    }

    @Test
    fun flagsBackupAndTestAndDropsThemFromKey() {
        val n = ChannelNameNormalizer.normalize("UK| ESPN 2 BACKUP [TEST]")
        assertEquals("espn 2", n.key)
        assertEquals(setOf(ChannelFlag.BACKUP, ChannelFlag.TEST), n.flags)
    }

    @Test
    fun dashCountryPrefixIsStripped() {
        val n = ChannelNameNormalizer.normalize("US - FOX 29 PHILADELPHIA PA (WTXF) HD")
        assertEquals("US", n.country)
        assertEquals("fox 29 philadelphia pa wtxf", n.key)
        assertEquals("LAT", ChannelNameNormalizer.normalize("LAT - TELEMUNDO 62 [PHILADELPHIA] HD").country)
        assertEquals("espn", NationalNetworks.canonicalFor("US - ESPN HD"))
        assertNull(NationalNetworks.canonicalFor("US - FOX 29 PHILADELPHIA PA (WTXF) HD"))
    }

    @Test
    fun threeLetterNetworkBeforeDashIsNotACountry() {
        val n = ChannelNameNormalizer.normalize("ABC - NEWS LIVE")
        assertNull(n.country)
        assertEquals("abc news live", n.key)
    }

    @Test
    fun ampersandBecomesAnd() {
        assertEquals("nbc and nfl", ChannelNameNormalizer.normalize("NBC & NFL").key)
    }

    @Test
    fun noCountryPrefixGivesNullCountry() {
        val n = ChannelNameNormalizer.normalize("ESPN 4K")
        assertNull(n.country)
        assertEquals("espn", n.key)
        assertEquals(Quality.UHD, n.quality)
    }

    @Test
    fun fullwidthAndSuperscriptFoldToSameKey() {
        assertEquals(
            ChannelNameNormalizer.normalize("ＣＢＳ").key,
            ChannelNameNormalizer.normalize("CBS").key,
        )
    }

    @Test
    fun nationalAliasesResolveFromProviderNames() {
        assertEquals("nfl network", NationalNetworks.canonicalFor("US| NFL NET ᴴᴰ"))
        assertEquals("espn", NationalNetworks.canonicalFor("US| ESPN FHD"))
        assertEquals("espn+", NationalNetworks.canonicalFor("ESPN Plus"))
        assertEquals("espn", NationalNetworks.canonicalFor("ESPN"))
        assertNull(NationalNetworks.canonicalFor("NBC SPORTS PHILADELPHIA"))
    }

    @Test
    fun pTokenSetsFhd() {
        val n = ChannelNameNormalizer.normalize("US| ESPN 1080p")
        assertEquals("espn", n.key)
        assertEquals(Quality.FHD, n.quality)
    }

    @Test
    fun pTokenSetsHdAndUhd() {
        val hd = ChannelNameNormalizer.normalize("FOX 720p")
        assertEquals("fox", hd.key)
        assertEquals(Quality.HD, hd.quality)

        val uhd = ChannelNameNormalizer.normalize("FOX 2160p")
        assertEquals("fox", uhd.key)
        assertEquals(Quality.UHD, uhd.quality)
    }

    @Test
    fun firstQualityTokenStillWins() {
        val n = ChannelNameNormalizer.normalize("ESPN HD 1080p")
        assertEquals("espn", n.key)
        assertEquals(Quality.HD, n.quality)
    }
}
