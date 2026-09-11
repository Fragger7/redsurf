package com.redsurf.tv.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val serverUrl: String,
    val username: String,
    val type: String, // "xtream", "m3u", or "stalker"
    
    // Feature 1 & 3: Custom User-Agent & EPG Offset
    val userAgent: String? = null,
    val epgOffsetHours: Float = 0f,
    
    // Feature 5: Stalker support
    val macAddress: String? = null,
    
    // Content filter: "live", "vod", "both"
    val contentType: String = "both"
)

@Entity(tableName = "channel_groups")
data class ChannelGroupEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val groupName: String,
    val isHidden: Boolean = false,
    val groupType: String // "live", "vod", "series"
)

// Indexed for the per-group Live TV queries (PHASE_1.md #2b) - a GROUP BY over tens of
// thousands of rows without this index is a full table scan and a visible stall.
@Entity(tableName = "channels", indices = [Index(value = ["playlistId", "streamType", "groupName"])])
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
