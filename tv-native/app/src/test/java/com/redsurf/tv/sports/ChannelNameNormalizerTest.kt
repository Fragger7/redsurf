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
}
