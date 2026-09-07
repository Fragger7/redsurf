package com.redsurf.tv.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrograms(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs")
    suspend fun clearAll()

    @Query("SELECT * FROM epg_programs WHERE channelEpgId = :channelId AND endTime >= :currentTime ORDER BY startTime ASC")
    suspend fun getProgramsForChannel(channelId: String, currentTime: Long): List<EpgProgramEntity>
}
