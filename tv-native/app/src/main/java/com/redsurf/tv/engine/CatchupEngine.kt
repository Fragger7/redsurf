package com.redsurf.tv.engine

import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * World-class IPTV feature: Catch-up TV.
 * Xtream Codes servers support appending timestamps to URLs to rewind live TV.
 * Standard format: /live/user/pass/123.ts?utc=1690000000&lutc=1690003600
 */
object CatchupEngine {
    
    // Types of catchup formats seen in the wild
    enum class CatchupType {
        DEFAULT,   // ?utc=START_UNIX&lutc=END_UNIX
        APPEND,    // ?play_token=xyz&utc=START_UNIX
        SHIFT      // /timeshift/USER/PASS/DURATION/YYYYMMDD:HHMM/CHANNEL_ID.ts
    }

    /**
     * Reconstructs a live stream URL into an archive stream URL for catch-up playback.
     */
    fun buildArchiveUrl(
        liveUrl: String, 
        startTimeUnix: Long, 
        durationMinutes: Int, 
        type: CatchupType = CatchupType.DEFAULT
    ): String {
        return when (type) {
            CatchupType.DEFAULT -> {
                val connector = if (liveUrl.contains("?")) "&" else "?"
                "$liveUrl${connector}utc=$startTimeUnix&lutc=${startTimeUnix + (durationMinutes * 60)}"
            }
            CatchupType.APPEND -> {
                val connector = if (liveUrl.contains("?")) "&" else "?"
                "$liveUrl${connector}utc=$startTimeUnix"
            }
            CatchupType.SHIFT -> {
                // Highly provider specific. Example conversion:
                // FROM: http://server.com:8080/live/user/pass/123.ts
                // TO:   http://server.com:8080/timeshift/user/pass/60/20231015:0800/123.ts
                try {
                    val sdf = SimpleDateFormat("yyyyMMdd:HHmm", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val dateString = sdf.format(Date(startTimeUnix * 1000L))
                    
                    val parts = liveUrl.split("/")
                    if (parts.size >= 5 && parts[parts.size - 4] == "live") {
                        val host = parts.subList(0, parts.size - 4).joinToString("/")
                        val user = parts[parts.size - 3]
                        val pass = parts[parts.size - 2]
                        val channelId = parts.last()
                        
                        "$host/timeshift/$user/$pass/$durationMinutes/$dateString/$channelId"
                    } else {
                        liveUrl // Fallback if format is unknown
                    }
                } catch (e: Exception) {
                    Log.e("CatchupEngine", "Failed to build shift URL", e)
                    liveUrl
                }
            }
        }
    }
}
