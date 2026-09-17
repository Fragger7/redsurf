package com.redsurf.tv.db

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * groupName + how many live channels are in it, plus which playlist it belongs to (user request,
 * 2026-09-12: add a second playlist for testing without deleting the first, and show both under
 * Live TV rather than only ever showing one "active" playlist). `playlistId` is part of a group's
 * real identity now - two different playlists can legitimately both have a "Sports" group and
 * they must stay distinct, not merge. Powers the Categories column (PHASE_1.md #1.4).
 */
data class GroupCount(val playlistId: String, val playlistName: String, val groupName: String, val count: Int)

@Dao
interface ChannelDao {
    // Kept for other DAO consumers (favorites, hide) - NOT used for rendering Live TV. Loading
    // every channel into memory is exactly the pattern PHASE_1.md #1.2/#2 replaces; see
    // getLiveGroupCounts and getLiveChannelsInGroup below for the paged, per-group equivalents.
    @Query("SELECT * FROM channels WHERE isHidden = 0 ORDER BY num, name")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE groupId = :groupId AND isHidden = 0 ORDER BY num, name")
    fun getChannelsByGroup(groupId: String): Flow<List<ChannelEntity>>

    /** Resume-last-channel-on-launch (AGENTS.md backlog, 2026-09-15) - the one lookup that needs
     * a channel by its composite key directly, not a group/paging query. Null if the persisted
     * channel is gone (playlist re-imported, channel removed) - the caller falls back to the
     * normal first-group default, same as any other "stale reference" case in this app. */
    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND streamId = :streamId LIMIT 1")
    suspend fun getChannel(playlistId: String, streamId: String): ChannelEntity?

    /**
     * Across every loaded playlist, not just one - see [GroupCount]. Joined to `playlists` for
     * the display name; grouped by (playlistId, groupName) so same-named groups from different
     * providers stay distinct rows instead of merging their counts together.
     */
    @Query(
        "SELECT c.playlistId AS playlistId, p.name AS playlistName, c.groupName AS groupName, " +
            "COUNT(*) AS count FROM channels c JOIN playlists p ON p.id = c.playlistId " +
            "WHERE c.streamType = 'live' AND c.isHidden = 0 " +
            "GROUP BY c.playlistId, c.groupName ORDER BY p.name, c.groupName"
    )
    fun getLiveGroupCounts(): Flow<List<GroupCount>>

    @Query(
        "SELECT * FROM channels WHERE playlistId = :playlistId AND streamType = 'live' " +
            "AND groupName = :groupName AND isHidden = 0 ORDER BY num, name"
    )
    fun getLiveChannelsInGroup(playlistId: String, groupName: String): PagingSource<Int, ChannelEntity>

    /**
     * This channel's 0-based position in [getLiveChannelsInGroup]'s own `ORDER BY num, name` -
     * lets a caller seed that Pager's `initialKey` (the offset) so it loads starting at a specific
     * channel instead of page 1 (user-found bug, 2026-09-17: `ChannelListOverlay`'s LEFT panel
     * focused the right category but never the channel actually playing, because its
     * `ChannelsColumn`'s own scroll-to-selected effect only searches Paging pages already loaded
     * via `peek()`, which never happens for a channel deep in a large group nothing has scrolled
     * to yet). Ties are broken by `name`, matching every other query's own tiebreak exactly.
     */
    @Query(
        "SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId AND groupName = :groupName " +
            "AND streamType = 'live' AND isHidden = 0 AND (num < :num OR (num = :num AND name < :name))"
    )
    suspend fun offsetInGroup(playlistId: String, groupName: String, num: Int, name: String): Int

    /**
     * Zap neighbours (PHASE_2.md #2.2, decision 13) - two O(1) indexed lookups, never the whole
     * group loaded into memory. Each can return null at the end of the group; [firstInGroup]/
     * [lastInGroup] are the wrap-around fallback, applied by the caller (`ChannelRepository`) so
     * these stay simple single-purpose queries.
     */
    @Query(
        "SELECT * FROM channels WHERE playlistId = :playlistId AND groupName = :groupName " +
            "AND streamType = 'live' AND isHidden = 0 AND num > :num ORDER BY num, name LIMIT 1"
    )
    suspend fun nextInGroup(playlistId: String, groupName: String, num: Int): ChannelEntity?

    @Query(
        "SELECT * FROM channels WHERE playlistId = :playlistId AND groupName = :groupName " +
            "AND streamType = 'live' AND isHidden = 0 AND num < :num ORDER BY num DESC, name DESC LIMIT 1"
    )
    suspend fun prevInGroup(playlistId: String, groupName: String, num: Int): ChannelEntity?

    @Query(
        "SELECT * FROM channels WHERE playlistId = :playlistId AND groupName = :groupName " +
            "AND streamType = 'live' AND isHidden = 0 ORDER BY num, name LIMIT 1"
    )
    suspend fun firstInGroup(playlistId: String, groupName: String): ChannelEntity?

    @Query(
        "SELECT * FROM channels WHERE playlistId = :playlistId AND groupName = :groupName " +
            "AND streamType = 'live' AND isHidden = 0 ORDER BY num DESC, name DESC LIMIT 1"
    )
    suspend fun lastInGroup(playlistId: String, groupName: String): ChannelEntity?

    /** Zap-order diagnostics (AGENTS.md, 2026-09-14 mini-sprint) - the same order/filter as
     * [getLiveChannelsInGroup] and the zap neighbour queries above, capped, so a debug build can
     * log "what the app thinks this group's sequence actually is" without loading the whole
     * group. Debug logging only - never called from release-path UI. */
    @Query(
        "SELECT * FROM channels WHERE playlistId = :playlistId AND groupName = :groupName " +
            "AND streamType = 'live' AND isHidden = 0 ORDER BY num, name LIMIT :limit"
    )
    suspend fun firstNInGroup(playlistId: String, groupName: String, limit: Int): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY num, name")
    fun getFavorites(): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    // Scoped by playlistId too (BACKLOG_SWEEP.md #13) - streamId alone is only unique within one
    // provider, matching ChannelEntity's own composite primary key.
    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE playlistId = :playlistId AND streamId = :streamId")
    suspend fun updateFavorite(playlistId: String, streamId: String, isFavorite: Boolean)

    @Query("UPDATE channels SET isHidden = :isHidden WHERE playlistId = :playlistId AND streamId = :streamId")
    suspend fun updateHidden(playlistId: String, streamId: String, isHidden: Boolean)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsByPlaylist(playlistId: String)

    /** Testing-enablement (user request, 2026-09-11) - paired with PlaylistDao.deleteAllPlaylists
     * so resetting doesn't leave orphaned channel rows behind on a storage-constrained device. */
    @Query("DELETE FROM channels")
    suspend fun deleteAllChannels()
    
    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' AND isHidden = 0")
    suspend fun searchChannels(query: String): List<ChannelEntity>
}

/**
 * PHASE_2.md decision 14 - see [RecentChannelEntity]'s own doc comment for the composite-key
 * deviation from the brief's literal text. Every read here joins back to `channels` so callers get
 * a real `ChannelEntity` (name, num, icon, ...) in one query, not just the two ids this table
 * actually stores - `recent_channels` is purely a recency index, `channels` stays the one source
 * of truth for everything else about a channel.
 */
@Dao
interface RecentChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: RecentChannelEntity)

    /** Keeps the newest 50 (decision 14), across every playlist combined - not per playlist.
     * `rowid` (SQLite's own, always-unique-per-row implicit column) rather than the composite
     * primary key, since a composite-key `NOT IN` subquery isn't reliably supported by the SQLite
     * versions bundled with Android. Call after every [upsert]. */
    @Query(
        "DELETE FROM recent_channels WHERE rowid NOT IN " +
            "(SELECT rowid FROM recent_channels ORDER BY watchedAt DESC LIMIT 50)"
    )
    suspend fun trim()

    @Query(
        "SELECT c.* FROM recent_channels r JOIN channels c " +
            "ON c.playlistId = r.playlistId AND c.streamId = r.streamId " +
            "WHERE c.isHidden = 0 ORDER BY r.watchedAt DESC LIMIT :limit"
    )
    fun getRecentChannels(limit: Int = 30): Flow<List<ChannelEntity>>

    /** RIGHT's "last channel" zap (decision 14: "RIGHT reads the second-newest row") - the newest
     * row is whatever's actually playing right now, so the *previous* channel is one row back. */
    @Query(
        "SELECT c.* FROM recent_channels r JOIN channels c " +
            "ON c.playlistId = r.playlistId AND c.streamId = r.streamId " +
            "WHERE c.isHidden = 0 ORDER BY r.watchedAt DESC LIMIT 1 OFFSET 1"
    )
    suspend fun getSecondMostRecentChannel(): ChannelEntity?

    /** Testing-enablement, matching every other table's own reset path. */
    @Query("DELETE FROM recent_channels")
    suspend fun deleteAll()
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM channel_groups WHERE playlistId = :playlistId AND groupType = :groupType AND isHidden = 0 ORDER BY groupName")
    fun getGroups(playlistId: String, groupType: String): Flow<List<ChannelGroupEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<ChannelGroupEntity>)
    
    @Query("UPDATE channel_groups SET isHidden = :isHidden WHERE id = :groupId")
    suspend fun updateHidden(groupId: String, isHidden: Boolean)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    /** Testing-enablement (user request, 2026-09-11) - wipes every saved playlist. */
    @Query("DELETE FROM playlists")
    suspend fun deleteAllPlaylists()

    /** Real per-playlist removal (user request, 2026-09-12: Settings playlist management as a
     * critical-testing-path priority, not deferred to the full Settings redesign) - paired with
     * ChannelDao.deleteChannelsByPlaylist so removing one playlist doesn't leave its channels
     * behind, the same reasoning as deleteAllChannels/deleteAllPlaylists above. */
    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)
}

@Database(entities = [
    ChannelEntity::class,
    EpgProgramEntity::class,
    PlaylistEntity::class,
    ChannelGroupEntity::class,
    RecentChannelEntity::class,
], version = 8, exportSchema = false)
// v6: added the (playlistId, streamType, groupName) index (PHASE_1.md #2b).
// v7: ChannelEntity's primary key is now composite (playlistId, streamId) - see EpgEntities.kt's
// doc comment on ChannelEntity (BACKLOG_SWEEP.md #13). Destructive migration was acceptable then -
// no user data existed yet to preserve.
// v8: recent_channels (PHASE_2.md decision 14). A REAL migration this time, not destructive -
// decision 14 is explicit about why: real playlists exist now (unlike v7's migration), and "the
// playlist vanished after an update" is a bug the user has already reported once. See
// MIGRATION_7_8 below.
abstract class RedSurfDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun epgDao(): EpgDao
    abstract fun groupDao(): GroupDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun recentChannelDao(): RecentChannelDao

    companion object {
        @Volatile
        private var INSTANCE: RedSurfDatabase? = null

        /** Additive only - matches [RecentChannelEntity]'s own shape exactly, including the
         * composite primary key (Room enforces the declared PK via SQL constraints identically
         * whether the class is annotated or the DDL is written by hand here). */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recent_channels` (" +
                        "`streamId` TEXT NOT NULL, `playlistId` TEXT NOT NULL, " +
                        "`watchedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`playlistId`, `streamId`))"
                )
            }
        }

        fun getDatabase(context: Context): RedSurfDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RedSurfDatabase::class.java,
                    "redsurf_tv_database"
                )
                    .addMigrations(MIGRATION_7_8)
                    // Still the fallback for any *other* version jump this app doesn't carry an
                    // explicit migration for (e.g. a real install predating v6) - decision 14's
                    // protection is specifically for this release's own upgrade path (v7 -> v8),
                    // which now has a real migration above and will never hit this fallback.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
