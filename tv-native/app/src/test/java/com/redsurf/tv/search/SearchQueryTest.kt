package com.redsurf.tv.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchQueryTest {

    @Test
    fun convertsSimpleQueryToPrefixPerWord() {
        assertEquals("sky* spo*", SearchQuery.toFtsMatch("sky spo"))
    }

    @Test
    fun trimsExtraWhitespace() {
        assertEquals("bbc* one*", SearchQuery.toFtsMatch("  BBC   One  "))
    }

    @Test
    fun handlesCountryPrefixDelimiter() {
        assertEquals("us* nbc* sports*", SearchQuery.toFtsMatch("US| NBC Sports"))
    }

    @Test
    fun lowercasesBooleanKeywords() {
        assertEquals("news* or* sport*", SearchQuery.toFtsMatch("news OR sport"))
    }

    @Test
    fun stripsFtsOperatorsAndPunctuation() {
        assertEquals("quoted* minus* col* on* up* x*", SearchQuery.toFtsMatch("\"quoted\" -minus col:on ^up (x)"))
    }

    @Test
    fun foldsFullwidthAndSuperscript() {
        assertEquals("espn* hd*", SearchQuery.toFtsMatch("ＥＳＰＮ ᴴᴰ"))
    }

    @Test
    fun limitsToFirstSixWords() {
        assertEquals("a* b* c* d* e* f*", SearchQuery.toFtsMatch("a b c d e f g h"))
    }

    @Test
    fun returnsNullForEmptyOrPunctuationOnly() {
        assertNull(SearchQuery.toFtsMatch(""))
        assertNull(SearchQuery.toFtsMatch("   "))
        assertNull(SearchQuery.toFtsMatch("|||--"))
    }

    @Test
    fun preservesAccentedCharacters() {
        assertEquals("télé* 5*", SearchQuery.toFtsMatch("Télé 5"))
    }
}
