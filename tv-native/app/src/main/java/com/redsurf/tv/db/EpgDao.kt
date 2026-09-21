package com.redsurf.tv.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgramEntity>)

    // PHASE_3.md decision 1: scoped to one playlist, not the whole table - EpgSyncWorker runs
    // per playlist now (decision 2), and a global clearAll() here would wipe every OTHER
    // playlist's EPG data every time just one playlist re-syncs.
    @Query("DELETE FROM epg_programs WHERE playlistId = :playlistId")
    suspend fun clearForPlaylist(playlistId: String)

    /** Cold-launch check (EPG_GRID_REDESIGN.md, found live 2026-09-21): whether this playlist has
     * any EPG at all, so a one-time sync stuck in WorkManager's exponential backoff after a
     * transient provider error (observed: a run of 502s pushed the retry out 4+ hours) gets
     * replaced with a fresh attempt instead of leaving the guide empty until the backoff expires. */
    @Query("SELECT COUNT(*) FROM epg_programs WHERE playlistId = :playlistId")
    suspend fun countForPlaylist(playlistId: String): Int

    @Query(
        "SELECT * FROM epg_programs WHERE playlistId = :playlistId AND channelEpgId = :channelId " +
            "AND endTime >= :currentTime ORDER BY startTime ASC"
    )
    suspend fun getProgramsForChannel(playlistId: String, channelId: String, currentTime: Long): List<EpgProgramEntity>

    /** The Guide grid's own query (PHASE_3.md decision 4) - every channel row visible in the
     * currently-composed window, one query instead of one per channel. Bounded to the grid's
     * rolling window (`windowStart`/`windowEnd`) so this never scales with how much EPG history
     * a provider's feed happens to carry. */
    @Query(
        "SELECT * FROM epg_programs WHERE playlistId = :playlistId AND channelEpgId IN (:channelIds) " +
            "AND endTime >= :windowStart AND startTime <= :windowEnd ORDER BY channelEpgId, startTime ASC"
    )
    suspend fun getProgramsForChannels(
        playlistId: String,
        channelIds: List<String>,
        windowStart: Long,
        windowEnd: Long,
    ): List<EpgProgramEntity>
}
