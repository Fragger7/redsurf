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
//
// Composite primary key (BACKLOG_SWEEP.md #13, v6->v7, user-confirmed bundling 2026-09-15): a
// bare streamId is only unique *within* one provider - Xtream/M3U stream IDs are small sequential
// integers assigned by each provider independently, so two loaded playlists can and did collide
// on the same streamId for two unrelated channels. `@Insert(OnConflictStrategy.REPLACE)` on a
// single-column PK meant importing playlist B could silently overwrite rows that belonged to
// playlist A whenever their id ranges overlapped. (playlistId, streamId) together are always
// unique. Destructive migration - no live users, sprints already wipe the device routinely.
@Entity(
    tableName = "channels",
    primaryKeys = ["playlistId", "streamId"],
    indices = [Index(value = ["playlistId", "streamType", "groupName"])],
)
data class ChannelEntity(
    val streamId: String,
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

// PHASE_3.md decision 1 - playlistId scoping, the same cross-playlist collision fix already
// applied to ChannelEntity (BACKLOG_SWEEP.md #13) and RecentChannelEntity (PHASE_2.md decision
// 14): a raw epgChannelId is only unique within one provider's own XMLTV feed, so two playlists
// can legitimately collide on the same id. Destructive migration - no live users.
@Entity(
    tableName = "epg_programs",
    primaryKeys = ["playlistId", "channelEpgId", "startTime"],
    indices = [Index(value = ["playlistId", "channelEpgId"])],
)
data class EpgProgramEntity(
    val playlistId: String,
    val channelEpgId: String,
    val title: String,
    val description: String,
    val startTime: Long,
    val endTime: Long
)

/**
 * PHASE_2.md decision 14 - real backing for the tile row / History picker / RIGHT's "last channel
 * zap", replacing `LiveTvScreen`'s in-memory stand-in. Upserted on every deliberate tune (browse
 * OK, zap, a tile, the LEFT overlay), newest 50 kept.
 *
 * **Deviates from decision 14's literal text on purpose:** the brief specified `streamId PK`
 * alone, written before `ChannelEntity` itself was given a composite `(playlistId, streamId)`
 * primary key (`BACKLOG_SWEEP.md` #13) for exactly the same reason this table needs it too - a
 * bare `streamId` is only unique *within* one provider, so two loaded playlists can collide on
 * the same id. Applying that already-established, more-informed fix here rather than the earlier
 * spec's stale shape, per this file's own precedent for correcting a decision found wrong mid-
 * build rather than building the wrong thing silently (see `PlayerScreen.kt`'s decision-4 fix).
 */
@Entity(tableName = "recent_channels", primaryKeys = ["playlistId", "streamId"])
data class RecentChannelEntity(
    val streamId: String,
    val playlistId: String,
    val watchedAt: Long,
)
