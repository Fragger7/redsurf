package com.redsurf.tv.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarketNetworksTest {

    @Test
    fun rsnSpellingsResolveToOneKey() {
        for (raw in listOf("US| NBC SPORTS PHILADELPHIA ᴴᴰ", "NBC Sports Phil", "NBCSP")) {
            assertEquals(raw, "nbc sports philadelphia", MarketNetworks.channelFor(raw, "philadelphia")?.canonical)
        }
    }

    @Test
    fun affiliateByNameAndByNetwork() {
        assertEquals("wtxf", MarketNetworks.channelFor("US| FOX 29 HD", "philadelphia")?.canonical)
        assertEquals("wpvi", MarketNetworks.affiliateFor("abc", "philadelphia")?.canonical)
        assertEquals(MarketKind.AFFILIATE, MarketNetworks.affiliateFor("cbs", "philadelphia")?.kind)
    }

    @Test
    fun unknownMarketOrNetworkIsNull() {
        assertNull(MarketNetworks.channelFor("NBCSP", "boston"))
        assertNull(MarketNetworks.affiliateFor("espn", "philadelphia"))
        assertNull(MarketNetworks.channelFor("ESPN", "philadelphia"))
    }
}
