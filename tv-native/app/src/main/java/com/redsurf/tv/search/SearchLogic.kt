package com.redsurf.tv.search

import com.redsurf.tv.db.ChannelEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/** SEARCH.md S2 - how many recent queries are kept. */
const val SEARCH_HISTORY_LIMIT = 10

/** SEARCH.md - typing pauses this long before a query runs. */
const val SEARCH_DEBOUNCE_MS = 250L

/** A programme result: the airing plus the channel it tunes. */
data class ProgramResult(
    val channel: ChannelEntity,
    val title: String,
    val startTime: Long,
    val endTime: Long,
)

/** What the Search screen shows. Hoisted to AppShell so leaving and returning keeps it. */
data class SearchResults(
    val query: String = "",
    val channels: List<ChannelEntity> = emptyList(),
    val programs: List<ProgramResult> = emptyList(),
) {
    val isEmpty: Boolean get() = channels.isEmpty() && programs.isEmpty()
}

object SearchLogic {

    /**
     * Debounced, cancel-on-new-input query pipeline: every keystroke cancels the pending/running
     * search (collectLatest), and a query only starts after [debounceMs] of quiet. A blank query
     * runs immediately so the screen clears without a lag.
     */
    suspend fun runDebounced(
        queries: Flow<String>,
        debounceMs: Long = SEARCH_DEBOUNCE_MS,
        onQuery: suspend (String) -> Unit,
    ) {
        queries.distinctUntilChanged().collectLatest { q ->
            if (q.isNotBlank()) delay(debounceMs)
            onQuery(q)
        }
    }

    /** Recent-query list after searching [query]: newest first, case-insensitive dedupe (the
     * re-searched term moves to the front, with its new spelling), capped at [limit]. */
    fun addRecent(existing: List<String>, query: String, limit: Int = SEARCH_HISTORY_LIMIT): List<String> {
        val q = query.trim()
        if (q.isEmpty()) return existing.take(limit)
        return (listOf(q) + existing.filterNot { it.equals(q, ignoreCase = true) }).take(limit)
    }

    /** A playlist sub-header is only worth showing when the matches span more than one playlist. */
    fun showPlaylistHeaders(channels: List<ChannelEntity>): Boolean =
        channels.map { it.playlistId }.distinct().size > 1
}
