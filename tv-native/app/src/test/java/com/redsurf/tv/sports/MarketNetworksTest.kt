package com.redsurf.tv.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarketNetworksTest {

    private fun philly(raw: String) = MarketNetworks.channelFor(raw, "philadelphia")?.canonical

    @Test
    fun rsnSpellingsResolveToOneKey() {
        for (raw in listOf("US| NBC SPORTS PHILADELPHIA ᴴᴰ", "US - NBC SPORTS PHILADELPHIA HD", "NBC Sports Phil", "NBCSP")) {
            assertEquals(raw, "nbc sports philadelphia", philly(raw))
        }
    }

    @Test
    fun plusRsnIsItsOwnChannel() {
        assertEquals("nbc sports philadelphia plus", philly("US - NBC SPORTS PHILADELPHIA PLUS HD"))
    }

    // The test provider's real affiliate names, 2026-10-29.
    @Test
    fun realProviderAffiliateNamesMatch() {
        assertEquals("wtxf", philly("US - FOX 29 PHILADELPHIA PA (WTXF) HD"))
        assertEquals("kyw", philly("US - CBS 3 PHILADELPHIA PA (KYW) FHD"))
        assertEquals("wcau", philly("US - NBC 10 PHILADELPHIA PA (WCAU) FHD"))
        assertEquals("wpvi", philly("US - ABC 06 PHILADELPHIA PA (WPVI) HD"))
        assertEquals("wpvi", philly("6ABC"))
    }

    @Test
    fun sameNumberInAnotherMarketDoesNotMatch() {
        assertNull(philly("US - FOX 29 BUFFALO NY (WUTV) HD"))
        assertNull(philly("US| FOX 29 HD"))
        assertNull(philly("US - CBS 3 SPRINGFIELD MA (WSHM) HD"))
    }

    @Test
    fun otherPhiladelphiaChannelsDoNotMatch() {
        assertNull(philly("US - CBSN PHILADELPHIA HD"))
        assertNull(philly("US - NBC PHILADELPHIA NEWS HD"))
        assertNull(philly("US - NBA GAME 01 : INDIANA PACERS @ PHILADELPHIA 76ERS"))
    }

    @Test
    fun affiliateByNetwork() {
        assertEquals("wpvi", MarketNetworks.affiliateFor("abc", "philadelphia")?.canonical)
        assertEquals(MarketKind.AFFILIATE, MarketNetworks.affiliateFor("cbs", "philadelphia")?.kind)
    }

    @Test
    fun unknownMarketOrNetworkIsNull() {
        assertNull(MarketNetworks.channelFor("NBCSP", "boston"))
        assertNull(MarketNetworks.affiliateFor("espn", "philadelphia"))
        assertNull(philly("ESPN"))
    }
}
