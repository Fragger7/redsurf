package com.redsurf.tv.parser

import android.util.Log
import android.util.Xml
import com.redsurf.tv.db.EpgDao
import com.redsurf.tv.db.EpgProgramEntity
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

    suspend fun parseAndInsert(inputStream: InputStream, epgDao: EpgDao) = withContext(Dispatchers.IO) {
        val programsBatch = mutableListOf<EpgProgramEntity>()
        var insertedCount = 0

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
                            programsBatch.add(
                                EpgProgramEntity(
                                    id = "\$currentChannelId-\$currentStart",
                                    channelEpgId = currentChannelId,
                                    title = currentTitle,
                                    description = currentDesc,
                                    startTime = currentStart,
                                    endTime = currentEnd
                                )
                            )
                            currentTitle = ""
                            currentDesc = ""

                            // Flush batch to disk to save memory
                            if (programsBatch.size >= BATCH_SIZE) {
                                epgDao.insertPrograms(programsBatch)
                                insertedCount += programsBatch.size
                                programsBatch.clear()
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            // Flush remaining
            if (programsBatch.isNotEmpty()) {
                epgDao.insertPrograms(programsBatch)
                insertedCount += programsBatch.size
            }
            Log.d("XmlTvParser", "Successfully parsed and inserted \$insertedCount EPG programs.")

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
