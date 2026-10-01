package com.redsurf.tv.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PublicEpgTest {
    @Test fun normalize_providerAndPublicFormsMeet() {
        assertEquals("cbs sports network", PublicEpg.normalize("US| CBS Sports Network HD"))
        assertEquals("cbs sports network", PublicEpg.normalize("CBS.Sports.Network.HD".replace('.', ' ')))
        assertEquals("cnbc", PublicEpg.normalize("US: CNBC FHD"))
        assertEquals("cnbc", PublicEpg.normalize("CNBC.HD".replace('.', ' ')))
        assertEquals("fox business", PublicEpg.normalize("US - FOX BUSINESS 4K"))
    }

    @Test fun normalize_keepsDistinctChannelsDistinct() {
        assertNotEquals(PublicEpg.normalize("CBS Sports Network"), PublicEpg.normalize("CBS"))
        assertNotEquals(PublicEpg.normalize("Fox Business"), PublicEpg.normalize("Fox News"))
    }

    @Test fun filesFor_usesCategoryCountryPrefix() {
        val files = PublicEpg.filesFor(listOf("US| NEWS", "UK • SPORT", "AF | AFRICA", "24/7 | CARTOON"))
        assertEquals(setOf("epg_ripper_US2.xml.gz", "epg_ripper_US_SPORTS1.xml.gz", "epg_ripper_UK1.xml.gz"), files)
    }
}
