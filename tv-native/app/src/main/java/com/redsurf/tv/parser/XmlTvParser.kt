package com.redsurf.tv.parser

import android.util.Log
import android.util.Xml
import androidx.room.withTransaction
import com.redsurf.tv.db.EpgProgramEntity
import com.redsurf.tv.db.RedSurfDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * PRODUCTION XMLTV PARSER:
 * Reads huge XML files (often 50MB-200MB in IPTV) without crashing Android TV.
 * It streams the XML file via XmlPullParser and batch-inserts into the database
 * every 1000 records to keep memory footprint tiny (<15MB).
 */
object XmlTvParser {
    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private const val BATCH_SIZE = 1000

    /** Sprint 1 performance pass, 2026-09-22 (Opus consult, "must-do" M3): the memory-flush size
     * above (1000 rows) and the commit size are different concerns - flushing often keeps the
     * in-parser list small, but each flush used to be its own implicit transaction/commit/fsync,
     * so a 200K-row feed meant ~200 separate commits. Grouping 10 flushes into one
     * `withTransaction` cuts that to ~20 - real cost on this hardware (confirmed live via
     * `dumpsys dbinfo`: total DB execution time during a cold-launch sync run was tens of seconds
     * across only a couple hundred statements). Trade-off, stated plainly: up to
     * `TRANSACTION_BATCH_COUNT` × `BATCH_SIZE` (10,000) rows can be held in memory between commits
     * instead of 1,000 - a few MB, not the whole feed, still nowhere near `HARDWARE.md`'s ceiling. */
    private const val TRANSACTION_BATCH_COUNT = 10

    /** [playlistId] stamps every row (PHASE_3.md decision 1) so two playlists whose providers
     * happen to reuse the same `channel` id in their own XMLTV feeds never collide. Returns the
     * real inserted count - `EpgSyncWorker` logs it so a sweep can assert real data landed,
     * not just that the worker ran without throwing.
     *
     * Takes the whole [db] now, not just `EpgDao` (Sprint 1 performance pass, 2026-09-22) - needs
     * `RoomDatabase.withTransaction` to group commits, see [TRANSACTION_BATCH_COUNT]. */
    suspend fun parseAndInsert(
        inputStream: InputStream,
        db: RedSurfDatabase,
        playlistId: String,
        skipEndedBefore: Long = 0L,
    ): Int = withContext(Dispatchers.IO) {
        val epgDao = db.epgDao()
        val programsBatch = mutableListOf<EpgProgramEntity>()
        val pendingTransactionBatches = mutableListOf<List<EpgProgramEntity>>()
        var insertedCount = 0
        var skippedCount = 0

        suspend fun flushPendingTransactionBatches() {
            if (pendingTransactionBatches.isEmpty()) return
            db.withTransaction {
                pendingTransactionBatches.forEach { epgDao.insertPrograms(it) }
            }
            pendingTransactionBatches.clear()
        }

        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(inputStream, null)

            var eventType = parser.eventType
            var currentChannelId = ""
            var currentTitle = ""
            var currentDesc = ""
            var currentStart = 0L
            var currentEnd = 0L

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (name) {
                            "programme" -> {
                                currentChannelId = parser.getAttributeValue(null, "channel") ?: ""
                                val startStr = parser.getAttributeValue(null, "start")
                                val endStr = parser.getAttributeValue(null, "stop")
                                currentStart = parseDate(startStr)
                                currentEnd = parseDate(endStr)
                            }
                            "title" -> currentTitle = parser.nextText()
                            "desc" -> currentDesc = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "programme" && currentChannelId.isNotEmpty()) {
                            // An unparseable date would land at startTime 0 and collide on the
                            // composite key; an already-ended programme is dead weight (a third
                            // of this provider's feed is yesterday). Neither is worth a row.
                            val usable = currentStart > 0L && currentEnd > currentStart &&
                                currentEnd >= skipEndedBefore
                            if (!usable) {
                                skippedCount++
                                currentTitle = ""
                                currentDesc = ""
                                eventType = parser.next()
                                continue
                            }
                            programsBatch.add(
                                EpgProgramEntity(
                                    playlistId = playlistId,
                                    channelEpgId = currentChannelId,
                                    title = currentTitle,
                                    description = currentDesc,
                                    startTime = currentStart,
                                    endTime = currentEnd
                                )
                            )
                            currentTitle = ""
                            currentDesc = ""

                            // Move the batch out of the in-parser list to bound its memory (still
                            // every BATCH_SIZE rows), but don't commit it alone - queue it and
                            // commit in groups of TRANSACTION_BATCH_COUNT instead.
                            if (programsBatch.size >= BATCH_SIZE) {
                                pendingTransactionBatches.add(programsBatch.toList())
                                insertedCount += programsBatch.size
                                programsBatch.clear()
                                if (pendingTransactionBatches.size >= TRANSACTION_BATCH_COUNT) {
                                    flushPendingTransactionBatches()
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            // Flush whatever's left - a partial final memory-batch, plus any transaction-batches
            // still queued from the loop above (the feed's total row count isn't a clean multiple
            // of BATCH_SIZE * TRANSACTION_BATCH_COUNT, so this always has something to do).
            if (programsBatch.isNotEmpty()) {
                pendingTransactionBatches.add(programsBatch.toList())
                insertedCount += programsBatch.size
                programsBatch.clear()
            }
            flushPendingTransactionBatches()
            Log.d("XmlTvParser", "Successfully parsed and inserted $insertedCount EPG programs for playlist $playlistId (skipped $skippedCount ended/unparseable).")
            insertedCount
        } catch (e: Exception) {
            Log.e("XmlTvParser", "Fatal error during EPG parsing", e)
            throw e
        }
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr.isNullOrEmpty()) return 0L
        return try {
            dateFormat.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
