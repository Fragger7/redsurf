#!/bin/bash
set -e

BASE_DIR="tv-native/app/src/main/java/com/redsurf/tv"

# 1. Update EpgEntities.kt to include Playlist and Group tables
cat << 'KOTLIN' > "$BASE_DIR/db/EpgEntities.kt"
package com.redsurf.tv.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val serverUrl: String,
    val username: String,
    val type: String // "xtream" or "m3u"
)

@Entity(tableName = "channel_groups")
data class ChannelGroupEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val groupName: String,
    val isHidden: Boolean = false,
    val groupType: String // "live", "vod", "series"
)

@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val streamId: String,
    val playlistId: String,
    val groupId: String, // Maps to channel_groups.id
    val num: Int,
    val name: String,
    val streamType: String,
    val streamIcon: String?,
    val epgChannelId: String?,
    val groupName: String,
    val isHidden: Boolean = false,
    val isFavorite: Boolean = false
)

@Entity(tableName = "epg_programs")
data class EpgProgramEntity(
    @PrimaryKey val id: String, // channelId-startTime
    val channelEpgId: String,
    val title: String,
    val description: String,
    val startTime: Long,
    val endTime: Long
)
KOTLIN

# 2. Update DAOs and DB
cat << 'KOTLIN' > "$BASE_DIR/db/RedSurfDatabase.kt"
package com.redsurf.tv.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels WHERE isHidden = 0 ORDER BY num, name")
    fun getAllChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE groupId = :groupId AND isHidden = 0 ORDER BY num, name")
    fun getChannelsByGroup(groupId: String): Flow<List<ChannelEntity>>

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)
}

@Database(entities = [
    ChannelEntity::class, 
    EpgProgramEntity::class,
    PlaylistEntity::class,
    ChannelGroupEntity::class
], version = 3, exportSchema = false)
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
KOTLIN

# 3. Create Global Search Matrix (Kotlin)
mkdir -p "$BASE_DIR/search"
cat << 'KOTLIN' > "$BASE_DIR/search/GlobalSearchEngine.kt"
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
KOTLIN
