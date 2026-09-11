package com.redsurf.tv.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.redsurf.tv.db.ChannelDao
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.db.GroupCount
import com.redsurf.tv.db.PlaylistDao
import com.redsurf.tv.db.PlaylistEntity
import kotlinx.coroutines.flow.Flow

/**
 * Thin wrapper over the DAOs (PHASE_1.md #1.2). Every method here is O(1) in playlist size -
 * nothing loads a whole playlist's channels into memory, which is what makes 60K+ channel
 * playlists safe on a 449 MB device.
 */
class ChannelRepository(
    private val channelDao: ChannelDao,
    private val playlistDao: PlaylistDao,
) {
    fun playlists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    fun liveGroups(playlistId: String): Flow<List<GroupCount>> =
        channelDao.getLiveGroupCounts(playlistId)

    /**
     * Paged live channels for one group. Caller applies `.cachedIn(scope)` when collecting -
     * that's lifecycle-scoped to the consumer (e.g. a Compose screen's coroutine scope), so it
     * doesn't belong baked in here.
     */
    fun liveChannels(playlistId: String, groupName: String): Flow<PagingData<ChannelEntity>> =
        Pager(
            config = PagingConfig(pageSize = 60, prefetchDistance = 120, enablePlaceholders = false),
            pagingSourceFactory = { channelDao.getLiveChannelsInGroup(playlistId, groupName) },
        ).flow
}
