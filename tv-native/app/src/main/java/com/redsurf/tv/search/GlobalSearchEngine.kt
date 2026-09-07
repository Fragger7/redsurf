package com.redsurf.tv.search

import android.content.Context
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.db.RedSurfDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PRODUCTION GLOBAL SEARCH:
 * Unified matrix search querying Live Channels across all playlists.
 * In a full parity scenario, this would also query VODs and Series tables.
 */
class GlobalSearchEngine(context: Context) {
    private val db = RedSurfDatabase.getDatabase(context)

    suspend fun search(query: String): SearchResults = withContext(Dispatchers.IO) {
        if (query.length < 2) return@withContext SearchResults(emptyList())

        val channels = db.channelDao().searchChannels(query)
        // VOD and Series queries would go here
        
        SearchResults(
            liveChannels = channels
        )
    }
}

data class SearchResults(
    val liveChannels: List<ChannelEntity>
)
