package com.redsurf.tv.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test

class SearchRankerTest {

    private fun rank(query: String, items: List<String>): List<String> =
        SearchRanker.rank(query, items) { it }

    @Test
    fun tier0ExactMatchRanksFirst() {
        val items = listOf("Sky Sports 2", "Sky Sports", "US| Sky Sports")
        val result = rank("Sky Sports", items)
        assertEquals(listOf("Sky Sports", "Sky Sports 2", "US| Sky Sports"), result)
        assertEquals(0, SearchRanker.tier("Sky Sports", "Sky Sports"))
    }

    @Test
    fun tier1NameStartsWithQuery() {
        val items = listOf("US| Sky Sports", "Sky News")
        val result = rank("Sky", items)
        assertEquals(listOf("Sky News", "US| Sky Sports"), result)
        assertEquals(1, SearchRanker.tier("Sky", "Sky News"))
    }

    @Test
    fun tier2QueryWordsStartNameWordsInOrder() {
        // "sky spo" vs names that do not start with "sky spo"
        val inOrder = "UK| Sky Sports Main"
        val outOfOrder = "UK| Sports Sky Main"
        val result = rank("sky spo", listOf(outOfOrder, inOrder))
        assertEquals(listOf(inOrder, outOfOrder), result)
        assertEquals(2, SearchRanker.tier("sky spo", inOrder))
        assertEquals(3, SearchRanker.tier("sky spo", outOfOrder))
    }

    @Test
    fun tier3QueryWordsStartNameWordsAnyOrder() {
        val anyOrder = "Sports Sky"
        val unmatched = "BBC News"
        val result = rank("sky spo", listOf(unmatched, anyOrder))
        assertEquals(listOf(anyOrder, unmatched), result)
        assertEquals(3, SearchRanker.tier("sky spo", anyOrder))
        assertEquals(4, SearchRanker.tier("sky spo", unmatched))
    }

    @Test
    fun tier4EverythingElseKeptAtEnd() {
        val items = listOf("BBC One", "Sky Sports")
        val result = rank("sky", items)
        assertEquals(listOf("Sky Sports", "BBC One"), result)
        assertEquals(4, SearchRanker.tier("sky", "BBC One"))
    }

    @Test
    fun tieBreakShorterFoldedNameFirst() {
        // Both match tier 1 ("sky"), shorter folded name should come first
        val longer = "Sky Sports Action"
        val shorter = "Sky News"
        val result = rank("sky", listOf(longer, shorter))
        assertEquals(listOf(shorter, longer), result)
    }

    @Test
    fun tieBreakFoldedNameAlphabetically() {
        // Both match tier 1 ("sky"), equal folded length (5 chars)
        val second = "Sky B"
        val first = "Sky A"
        val result = rank("sky", listOf(second, first))
        assertEquals(listOf(first, second), result)
    }

    @Test
    fun tieBreakOriginalListOrderPreservedWhenFoldedNameEqual() {
        // Equal tier, equal length, equal folded name -> preserve input order
        val item1 = "Sky 1!"
        val item2 = "Sky 1?"
        val result1 = rank("sky", listOf(item1, item2))
        assertEquals(listOf(item1, item2), result1)

        val result2 = rank("sky", listOf(item2, item1))
        assertEquals(listOf(item2, item1), result2)
    }

    @Test
    fun blankQueryKeepsOriginalOrder() {
        val items = listOf("Charlie", "Bravo", "Alpha")
        assertEquals(items, rank("   ", items))
        assertEquals(items, rank("\t \n", items))
    }

    @Test
    fun emptyQueryKeepsOriginalOrder() {
        val items = listOf("Charlie", "Bravo", "Alpha")
        assertEquals(items, rank("", items))
    }

    @Test
    fun inputListIsNotMutated() {
        val original = mutableListOf("Bravo", "Alpha")
        val snapshot = ArrayList(original)
        val result = rank("Alpha", original)
        assertEquals(snapshot, original)
        assertNotSame(original, result)
    }

    @Test
    fun usSkySportsVsQuerySkyLandsInTier2() {
        // folding drops the '|', "us sky sports" does not start with "sky", but query word "sky"
        // starts name word "sky" in order -> tier 2.
        assertEquals("us sky sports", SearchRanker.fold("US| Sky Sports"))
        assertEquals(2, SearchRanker.tier("sky", "US| Sky Sports"))

        val items = listOf("US| Other Channel", "US| Sky Sports", "Sky News")
        val result = rank("sky", items)
        assertEquals(listOf("Sky News", "US| Sky Sports", "US| Other Channel"), result)
    }
}
