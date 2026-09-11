package com.redsurf.tv.db

import android.content.Context
import androidx.paging.PagingSource
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** groupName + how many live channels are in it. Powers the Categories column (PHASE_1.md #1.4). */
data class GroupCount(val groupName: String, val count: Int)

@Dao
interface ChannelDao {
    // Kept for other DAO consumers (favorites, hide) - NOT used for rendering Live TV. Loading
    // every channel into memory is exactly the pattern PHASE_1.md #1.2/#2 replaces; see
    // getLiveGroupCounts and getLiveChannelsInGroup below for the paged, per-group equivalents.
    @Query("SELECT * FROM channels WHERE isHidden = 0 ORDER BY num, name")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE groupId = :groupId AND isHidden = 0 ORDER BY num, name")
    fun getChannelsByGroup(groupId: String): Flow<List<ChannelEntity>>

    @Query(
        "SELECT groupName, COUNT(*) AS count FROM channels " +
            "WHERE playlistId = :playlistId AND streamType = 'live' AND isHidden = 0 " +
            "GROUP BY groupName ORDER BY groupName"
    )
    fun getLiveGroupCounts(playlistId: String): Flow<List<GroupCount>>

    @Query(
        "SELECT * FROM channels WHERE playlistId = :playlistId AND streamType = 'live' " +
            "AND groupName = :groupName AND isHidden = 0 ORDER BY num, name"
    )
    fun getLiveChannelsInGroup(playlistId: String, groupName: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY num, name")
    fun getFavorites(): Flow<List<ChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("UPDATE channels SET isFavorite = :isFavorite WHERE streamId = :streamId")
    suspend fun updateFavorite(streamId: String, isFavorite: Boolean)

    @Query("UPDATE channels SET isHidden = :isHidden WHERE streamId = :streamId")
    suspend fun updateHidden(streamId: String, isHidden: Boolean)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsByPlaylist(playlistId: String)
    
    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' AND isHidden = 0")
    suspend fun searchChannels(query: String): List<ChannelEntity>
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
}

@Database(entities = [
    ChannelEntity::class, 
    EpgProgramEntity::class,
    PlaylistEntity::class,
    ChannelGroupEntity::class
], version = 6, exportSchema = false)
// v6: added the (playlistId, streamType, groupName) index (PHASE_1.md #2b). Destructive
// migration is acceptable - no user data exists yet to preserve.
abstract class RedSurfDatabase : RoomDatabase() {
    abstract fun channelDao(): ChannelDao
    abstract fun epgDao(): EpgDao
    abstract fun groupDao(): GroupDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        @Volatile
        private var INSTANCE: RedSurfDatabase? = null

        fun getDatabase(context: Context): RedSurfDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RedSurfDatabase::class.java,
                    "redsurf_tv_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
