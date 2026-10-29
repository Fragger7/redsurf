package com.redsurf.tv.search

import com.redsurf.tv.db.ChannelEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchLogicTest {

    private fun ch(playlist: String, id: String) = ChannelEntity(
        streamId = id, playlistId = playlist, groupId = "g", num = 1, name = "n$id",
        streamType = "live", streamIcon = null, epgChannelId = null, groupName = "g",
    )

    @Test fun addRecent_newestFirst() {
        assertEquals(listOf("b", "a"), SearchLogic.addRecent(listOf("a"), "b"))
    }

    @Test fun addRecent_dedupesCaseInsensitivelyAndMovesToFront() {
        assertEquals(listOf("Sky", "bbc"), SearchLogic.addRecent(listOf("bbc", "sky"), " Sky "))
    }

    @Test fun addRecent_capsAtLimit() {
        val existing = (1..10).map { "q$it" }
        val out = SearchLogic.addRecent(existing, "new")
        assertEquals(SEARCH_HISTORY_LIMIT, out.size)
        assertEquals("new", out.first())
        assertFalse("q10" in out)
    }

    @Test fun addRecent_blankIgnored() {
        assertEquals(listOf("a"), SearchLogic.addRecent(listOf("a"), "   "))
    }

    @Test fun playlistHeaders_onlyWhenMoreThanOnePlaylist() {
        assertFalse(SearchLogic.showPlaylistHeaders(emptyList()))
        assertFalse(SearchLogic.showPlaylistHeaders(listOf(ch("p1", "1"), ch("p1", "2"))))
        assertTrue(SearchLogic.showPlaylistHeaders(listOf(ch("p1", "1"), ch("p2", "2"))))
    }

    @Test fun debounce_rapidTypingRunsOnlyLastQuery() = runTest {
        val ran = mutableListOf<String>()
        val src = flow {
            emit("b"); delay(100); emit("bb"); delay(100); emit("bbc")
            delay(1000)
        }
        val job = launch { SearchLogic.runDebounced(src, 250) { ran += it } }
        advanceTimeBy(1500)
        advanceUntilIdle()
        job.cancel()
        assertEquals(listOf("bbc"), ran)
    }

    @Test fun debounce_blankRunsImmediately() = runTest {
        val ran = mutableListOf<String>()
        val src = flow { emit(""); delay(1000) }
        val job = launch { SearchLogic.runDebounced(src, 250) { ran += it } }
        advanceTimeBy(1)
        assertEquals(listOf(""), ran)
        job.cancel()
    }
}
