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
