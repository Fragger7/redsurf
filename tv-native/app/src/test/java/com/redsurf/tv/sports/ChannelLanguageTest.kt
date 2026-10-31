package com.redsurf.tv.sports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lead-written spec for task 013 (SPORTS_HUB.md Q6 language filter). Do not edit; make it pass. */
class ChannelLanguageTest {

    private fun lang(raw: String) = ChannelLanguage.of(ChannelNameNormalizer.normalize(raw))

    @Test fun englishCountryPrefixes() {
        assertEquals("en", lang("US| ESPN HD"))
        assertEquals("en", lang("UK| Sky Sports Main Event"))
        assertEquals("en", lang("CA| TSN 1"))
        assertEquals("en", lang("AU| Fox Sports 503"))
    }

    @Test fun spanishCountryPrefixes() {
        assertEquals("es", lang("LAT - ESPN 2"))
        assertEquals("es", lang("MX| Azteca 7"))
        assertEquals("es", lang("ES| Movistar LaLiga"))
    }

    @Test fun frenchCountryPrefix() {
        assertEquals("fr", lang("FR| Canal+ Sport"))
    }

    @Test fun spanishKeywordBeatsCountry() {
        assertEquals("es", lang("US| ESPN Deportes HD"))
        assertEquals("es", lang("US| Fox Deportes"))
        assertEquals("es", lang("US| TUDN"))
        assertEquals("es", lang("US| Telemundo Philadelphia"))
        assertEquals("es", lang("US| Univision"))
        assertEquals("es", lang("US| beIN Sports Español"))
        assertEquals("es", lang("US| beIN Sports Espanol"))
    }

    @Test fun keywordMustBeAWholeWord() {
        assertEquals("en", lang("US| Deportesfan Network"))
    }

    @Test fun unknownCountryAndNoKeywordIsNull() {
        assertNull(lang("ESPN"))
        assertNull(lang("DE| Sky Sport Bundesliga"))
    }

    @Test fun nullLanguageAlwaysPasses() {
        assertTrue(ChannelLanguage.passes(ChannelNameNormalizer.normalize("ESPN"), setOf("en")))
    }

    @Test fun emptyAllowedSetPassesEverything() {
        assertTrue(ChannelLanguage.passes(ChannelNameNormalizer.normalize("FR| Canal+ Sport"), emptySet()))
    }

    @Test fun knownLanguageMustBeAllowed() {
        val deportes = ChannelNameNormalizer.normalize("US| ESPN Deportes")
        assertFalse(ChannelLanguage.passes(deportes, setOf("en")))
        assertTrue(ChannelLanguage.passes(deportes, setOf("en", "es")))
    }
}
