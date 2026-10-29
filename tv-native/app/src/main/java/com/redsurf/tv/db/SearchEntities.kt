package com.redsurf.tv.db

import android.util.Log
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * SEARCH.md - full-text indexes (FTS4, external content: the index stores no copy of the text, it
 * points back into `channels` / `epg_programs` by rowid). Room creates the content-sync triggers
 * on a fresh database; MIGRATION_13_14 writes the same triggers by hand for upgrades.
 */
@Entity(tableName = "channels_fts")
@Fts4(contentEntity = ChannelEntity::class)
data class ChannelFts(val name: String)

@Entity(tableName = "epg_programs_fts")
@Fts4(contentEntity = EpgProgramEntity::class)
data class EpgProgramFts(val title: String)

/** A recent search query. [query] is the primary key, so re-searching a term refreshes it
 * instead of duplicating it. */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long,
)

/** One airing that matched a search, with the channel it airs on. */
data class ProgramHit(
    @Embedded val channel: ChannelEntity,
    val progTitle: String,
    val progStart: Long,
    val progEnd: Long,
)

@Dao
interface SearchDao {
    /** [match] comes from `SearchQuery.toFtsMatch`. Capped at 50 rows (memory discipline); the
     * caller re-ranks them with `SearchRanker`. */
    @Query(
        "SELECT * FROM channels WHERE rowid IN " +
            "(SELECT docid FROM channels_fts WHERE channels_fts MATCH :match) " +
            "AND isHidden = 0 AND streamType = 'live' LIMIT 50"
    )
    suspend fun searchChannels(match: String): List<ChannelEntity>

    /** Programmes that haven't finished yet, on a visible live channel of the same playlist. */
    @Query(
        "SELECT c.*, p.title AS progTitle, p.startTime AS progStart, p.endTime AS progEnd " +
            "FROM epg_programs p JOIN channels c " +
            "ON c.playlistId = p.playlistId AND c.epgChannelId = p.channelEpgId " +
            "WHERE p.rowid IN (SELECT docid FROM epg_programs_fts WHERE epg_programs_fts MATCH :match) " +
            "AND p.endTime > :now AND c.isHidden = 0 AND c.streamType = 'live' " +
            "ORDER BY p.startTime LIMIT 50"
    )
    suspend fun searchPrograms(match: String, now: Long): List<ProgramHit>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHistory(entity: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun recentQueries(limit: Int = 10): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteHistory(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearHistory()

    /** Keeps the newest [keep] rows. */
    @Query(
        "DELETE FROM search_history WHERE `query` NOT IN " +
            "(SELECT `query` FROM search_history ORDER BY searchedAt DESC LIMIT :keep)"
    )
    suspend fun trimHistory(keep: Int = 10)
}

/** Rebuilds the FTS indexes from their content tables. Cheap insurance after a bulk import or
 * EPG sync; blocking, so call it off the main thread. */
object SearchIndex {
    private const val TAG = "SearchIndex"

    fun rebuild(db: SupportSQLiteDatabase, channels: Boolean = true, programs: Boolean = true) {
        val start = System.currentTimeMillis()
        if (channels) db.execSQL("INSERT INTO channels_fts(channels_fts) VALUES('rebuild')")
        if (programs) db.execSQL("INSERT INTO epg_programs_fts(epg_programs_fts) VALUES('rebuild')")
        Log.d(TAG, "rebuild channels=$channels programs=$programs took ${System.currentTimeMillis() - start}ms")
    }

    /** Statements that create both indexes, their sync triggers and the history table - the
     * shape Room generates, so schema validation passes after the upgrade. */
    val MIGRATION_13_14_SQL: List<String> = buildList {
        add("CREATE VIRTUAL TABLE IF NOT EXISTS `channels_fts` USING FTS4(`name` TEXT NOT NULL, content=`channels`)")
        add("CREATE VIRTUAL TABLE IF NOT EXISTS `epg_programs_fts` USING FTS4(`title` TEXT NOT NULL, content=`epg_programs`)")
        for ((fts, content, col) in listOf(
            Triple("channels_fts", "channels", "name"),
            Triple("epg_programs_fts", "epg_programs", "title"),
        )) {
            add("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${fts}_BEFORE_UPDATE BEFORE UPDATE ON `$content` BEGIN DELETE FROM `$fts` WHERE `docid`=OLD.`rowid`; END")
            add("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${fts}_BEFORE_DELETE BEFORE DELETE ON `$content` BEGIN DELETE FROM `$fts` WHERE `docid`=OLD.`rowid`; END")
            add("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${fts}_AFTER_UPDATE AFTER UPDATE ON `$content` BEGIN INSERT INTO `$fts`(`docid`, `$col`) VALUES (NEW.`rowid`, NEW.`$col`); END")
            add("CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_${fts}_AFTER_INSERT AFTER INSERT ON `$content` BEGIN INSERT INTO `$fts`(`docid`, `$col`) VALUES (NEW.`rowid`, NEW.`$col`); END")
        }
        add("CREATE TABLE IF NOT EXISTS `search_history` (`query` TEXT NOT NULL, `searchedAt` INTEGER NOT NULL, PRIMARY KEY(`query`))")
        add("INSERT INTO `channels_fts`(`channels_fts`) VALUES('rebuild')")
        add("INSERT INTO `epg_programs_fts`(`epg_programs_fts`) VALUES('rebuild')")
    }
}
