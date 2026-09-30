package com.redsurf.tv.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.redsurf.tv.db.ChannelDao
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.db.EpgDao
import com.redsurf.tv.db.EpgProgramEntity
import com.redsurf.tv.db.FavoriteChannelDao
import com.redsurf.tv.db.FavoriteChannelEntity
import com.redsurf.tv.db.GroupCount
import com.redsurf.tv.db.PlaylistDao
import com.redsurf.tv.db.PlaylistEntity
import com.redsurf.tv.db.RecentChannelDao
import com.redsurf.tv.db.RecentChannelEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The Favorites category's `groupName` (TELEPORT_MENU.md, 2026-09-29). A private-use-area
 * character up front so it can never collide with a real provider category name, and has no
 * `-`/`|`/`•`/`:` separator so Root Category never treats it as part of a family. Every
 * group-scoped repository method below routes this to the favorites table instead of
 * `channels.groupName`; display goes through `formatGroupName`.
 */
const val FAVORITES_GROUP = "\uE000favorites"

fun isFavoritesGroup(groupName: String?): Boolean = groupName == FAVORITES_GROUP

/** Stable key for "is this channel a favorite" lookups - see [ChannelRepository.favoriteKeys]. */
fun favoriteKey(playlistId: String, streamId: String): String = playlistId + '\u001f' + streamId

/**
 * Thin wrapper over the DAOs (PHASE_1.md #1.2). Every method here is O(1) in playlist size -
 * nothing loads a whole playlist's channels into memory, which is what makes 60K+ channel
 * playlists safe on a 449 MB device.
 */
class ChannelRepository(
    private val channelDao: ChannelDao,
    private val playlistDao: PlaylistDao,
    private val recentChannelDao: RecentChannelDao,
    private val epgDao: EpgDao,
    private val favoriteDao: FavoriteChannelDao,
) {
    fun playlists(): Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()

    /** PLAYER_ENGINEERING_BRIEF.md §6/§9 - so the player's data source can use this playlist's
     * own User-Agent for its actual stream requests, not just the global one its API requests
     * already got. */
    suspend fun getPlaylist(id: String): PlaylistEntity? = playlistDao.getPlaylistById(id)

    /** Across every loaded playlist (user request, 2026-09-12 - multiple playlists can coexist
     * and all show up under Live TV, grouped by playlist name; see [GroupCount]). */
    fun liveGroups(): Flow<List<GroupCount>> =
        combine(channelDao.getLiveGroupCounts(), favoriteDao.countsByPlaylist()) { groups, favoriteCounts ->
            if (favoriteCounts.isEmpty()) return@combine groups
            val favoritesByPlaylist = favoriteCounts.associateBy { it.playlistId }
            val seen = HashSet<String>()
            buildList(groups.size + favoriteCounts.size) {
                for (group in groups) {
                    // Favorites is the first category under each playlist (the user's own
                    // TiviMate observation) - inserted just before that playlist's first group.
                    if (seen.add(group.playlistId)) {
                        favoritesByPlaylist[group.playlistId]?.let { add(it.copy(groupName = FAVORITES_GROUP)) }
                    }
                    add(group)
                }
            }
        }

    /** Every favorite as a [favoriteKey] - one small reactive set for "is this a favorite?"
     * checks (hero star, context-menu label, zap context) without a query per channel. */
    fun favoriteKeys(): Flow<Set<String>> = favoriteDao.keys().map { it.toSet() }

    suspend fun isFavorite(playlistId: String, streamId: String): Boolean = favoriteDao.count(playlistId, streamId) > 0

    /**
     * Paged live channels for one group. Caller applies `.cachedIn(scope)` when collecting -
     * that's lifecycle-scoped to the consumer (e.g. a Compose screen's coroutine scope), so it
     * doesn't belong baked in here. [initialOffset] (from [channelOffsetInGroup]) seeds the first
     * load to start at a specific channel instead of page 1 - see [ChannelDao.offsetInGroup]'s own
     * doc comment for why `ChannelListOverlay` needs this.
     */
    fun liveChannels(playlistId: String, groupName: String, initialOffset: Int = 0): Flow<PagingData<ChannelEntity>> =
        Pager(
            config = PagingConfig(pageSize = 60, prefetchDistance = 120, enablePlaceholders = false),
            initialKey = initialOffset.takeIf { it > 0 },
            pagingSourceFactory = {
                if (isFavoritesGroup(groupName)) favoriteDao.paged(playlistId)
                else channelDao.getLiveChannelsInGroup(playlistId, groupName)
            },
        ).flow

    suspend fun channelOffsetInGroup(playlistId: String, groupName: String, num: Int, name: String): Int =
        if (isFavoritesGroup(groupName)) favoriteDao.offset(playlistId, num, name)
        else channelDao.offsetInGroup(playlistId, groupName, num, name)

    /**
     * Zap neighbours (PHASE_2.md #2.2, decision 13), wrapping at the ends - the DAO's
     * `nextInGroup`/`prevInGroup` return null past either edge of the group, so this falls back
     * to the first/last channel rather than leaving zap dead-ended at whichever end you reach.
     */
    suspend fun nextChannel(playlistId: String, groupName: String, num: Int): ChannelEntity? =
        if (isFavoritesGroup(groupName)) favoriteDao.next(playlistId, num) ?: favoriteDao.first(playlistId)
        else channelDao.nextInGroup(playlistId, groupName, num) ?: channelDao.firstInGroup(playlistId, groupName)

    suspend fun prevChannel(playlistId: String, groupName: String, num: Int): ChannelEntity? =
        if (isFavoritesGroup(groupName)) favoriteDao.prev(playlistId, num) ?: favoriteDao.last(playlistId)
        else channelDao.prevInGroup(playlistId, groupName, num) ?: channelDao.lastInGroup(playlistId, groupName)

    /** Teleport Menu (docs/plans/TELEPORT_MENU.md decision 3) - the group's own first channel by
     * num/name order, for jumps that land on a category rather than an already-known channel
     * (Playlist Root, Root Category). */
    suspend fun firstChannelInGroup(playlistId: String, groupName: String): ChannelEntity? =
        if (isFavoritesGroup(groupName)) favoriteDao.first(playlistId)
        else channelDao.firstInGroup(playlistId, groupName)

    /** Zap-order diagnostics (AGENTS.md, 2026-09-14 mini-sprint) - see `ChannelDao.firstNInGroup`. */
    suspend fun debugFirstInGroup(playlistId: String, groupName: String, limit: Int = 30): List<ChannelEntity> =
        channelDao.firstNInGroup(playlistId, groupName, limit)

    /** Resume-last-channel-on-launch (AGENTS.md backlog, 2026-09-15) - see `ChannelDao.getChannel`. */
    suspend fun getChannel(playlistId: String, streamId: String): ChannelEntity? =
        channelDao.getChannel(playlistId, streamId)

    /** PHASE_2.md decision 14 - call on every deliberate tune (browse OK, zap, a tile, the LEFT
     * overlay), from anywhere. Upsert + trim in one call so no caller can upsert and forget to
     * trim. */
    suspend fun recordRecentChannel(playlistId: String, streamId: String) {
        recentChannelDao.upsert(RecentChannelEntity(streamId, playlistId, System.currentTimeMillis()))
        recentChannelDao.trim()
    }

    /** Newest-first, real `ChannelEntity` rows (joined, not just ids) - the tile row and History
     * picker's shared source now that decision 14's real table exists. */
    fun recentChannels(limit: Int = 30): Flow<List<ChannelEntity>> =
        recentChannelDao.getRecentChannels(limit)

    /** RIGHT's last-channel zap (decision 14) - the second-newest row, since the newest one is
     * whatever's actually playing right now. */
    suspend fun secondMostRecentChannel(): ChannelEntity? = recentChannelDao.getSecondMostRecentChannel()

    /** PHASE_2.md decision 12's context menu - both DAO methods already existed (dead, unused
     * until now). Hiding takes effect immediately end-to-end: every live query already filters
     * `isHidden = 0`. */
    suspend fun setFavorite(playlistId: String, streamId: String, isFavorite: Boolean) {
        // The favorites table is the only source of truth (survives re-import - see
        // FavoriteChannelEntity). The old `channels.isFavorite` column is no longer written or
        // read anywhere - one write per toggle, not two (each waits its turn for SQLite's single
        // writer during an EPG sync).
        val t0 = System.currentTimeMillis()
        if (isFavorite) favoriteDao.insert(FavoriteChannelEntity(streamId, playlistId, System.currentTimeMillis()))
        else favoriteDao.delete(playlistId, streamId)
        // Cheap, permanent: the write that sat 2-3 minutes behind an EPG sync on 2026-09-30.
        android.util.Log.d("RedSurfDb", "setFavorite ms=${System.currentTimeMillis() - t0}")
    }

    suspend fun setHidden(playlistId: String, streamId: String, isHidden: Boolean) =
        channelDao.updateHidden(playlistId, streamId, isHidden)

    /** PHASE_3.md decision 4 - the Guide grid's channel rows, non-paged (see
     * `ChannelDao.allInGroup`'s own doc comment for why this is bounded per-category, not a
     * return to loading a whole playlist into memory). */
    suspend fun channelsInGroup(playlistId: String, groupName: String): List<ChannelEntity> =
        if (isFavoritesGroup(groupName)) favoriteDao.allInPlaylist(playlistId)
        else channelDao.allInGroup(playlistId, groupName)

    /** PHASE_3.md decision 4 - one batch EPG fetch for every channel row currently on screen,
     * bounded to the grid's own rolling time window. */
    suspend fun programsForChannels(
        playlistId: String,
        channelIds: List<String>,
        windowStart: Long,
        windowEnd: Long,
    ): List<EpgProgramEntity> = epgDao.getProgramsForChannels(playlistId, channelIds, windowStart, windowEnd)
}
