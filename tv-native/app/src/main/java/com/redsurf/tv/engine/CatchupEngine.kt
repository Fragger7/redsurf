package com.redsurf.tv.engine

import com.redsurf.tv.db.PlaylistEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object CatchupEngine {
    
    // Xtream Codes standard timeshift URL format:
    // http://domain:port/timeshift/username/password/duration/YYYY-MM-DD:HH-MM/stream_id.ts
    fun generateCatchupUrl(
        playlist: PlaylistEntity,
        streamId: String,
        startTimestampUnix: Long,
        durationMinutes: Int
    ): String {
        val cleanServer = if (playlist.serverUrl.endsWith("/")) playlist.serverUrl.dropLast(1) else playlist.serverUrl
        
        val idWithExt = streamId.substringAfterLast("/")
        val id = idWithExt.substringBeforeLast(".")
        
        val sdf = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val startTimeStr = sdf.format(Date(startTimestampUnix * 1000))

        return "\$cleanServer/timeshift/\${playlist.username}/password/\$durationMinutes/\$startTimeStr/\$id.ts"
    }
}
