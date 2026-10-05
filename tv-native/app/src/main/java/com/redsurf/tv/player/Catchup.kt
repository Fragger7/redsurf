package com.redsurf.tv.player

import android.content.Context
import com.redsurf.tv.vod.XtreamApi
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Catch-up playback (EPG_WRAPUP.md 1.5). Xtream panels serve archived programmes from a timeshift
 * URL: `{server}/timeshift/{user}/{pass}/{minutes}/{yyyy-MM-dd:HH-mm}/{streamId}.ts`, with the start
 * written in the *panel's* local time - which the panel reports itself as `server_info.timezone`
 * (stored per playlist by EpgSyncWorker; the device's own zone is the fallback).
 *
 * Unverified against a real archive: none of the test providers advertised `tv_archive` when this
 * was built - the URL shape is the documented Xtream convention, not something observed working.
 */
object Catchup {
    private const val PREFS = "redsurf_catchup"
    private fun tzKey(playlistId: String) = "server_tz_$playlistId"

    fun setServerTimezone(context: Context, playlistId: String, tzId: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(tzKey(playlistId), tzId).apply()
    }

    fun serverTimezone(context: Context, playlistId: String): TimeZone {
        val id = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(tzKey(playlistId), null)
        return if (id.isNullOrBlank()) TimeZone.getDefault() else TimeZone.getTimeZone(id)
    }

    /** Null when [liveStreamUrl] isn't an Xtream live URL (M3U/Stalker have no timeshift). */
    fun timeshiftUrl(liveStreamUrl: String, startMillis: Long, endMillis: Long, serverZone: TimeZone): String? {
        val (server, user, pass) = XtreamApi.parseXtreamCredentials(liveStreamUrl) ?: return null
        val streamId = liveStreamUrl.substringAfterLast('/').substringBefore('.')
        if (streamId.isBlank()) return null
        val minutes = ((endMillis - startMillis) / 60_000L).coerceAtLeast(1L)
        val format = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply { timeZone = serverZone }
        return "$server/timeshift/$user/$pass/$minutes/${format.format(startMillis)}/$streamId.ts"
    }

    /**
     * Found live 2026-10-05: the `.ts` timeshift URL answered with something that wasn't a video
     * stream ("None of the available extractors"). Panels differ, so try the three common Xtream
     * shapes and keep the first whose first bytes are really media (MPEG-TS sync byte 0x47, or an
     * HLS `#EXTM3U`). Every attempt is logged without credentials. Null when none works.
     */
    suspend fun resolve(liveStreamUrl: String, startMillis: Long, endMillis: Long, serverZone: TimeZone, userAgent: String?): String? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val (server, user, pass) = XtreamApi.parseXtreamCredentials(liveStreamUrl) ?: return@withContext null
            val streamId = liveStreamUrl.substringAfterLast('/').substringBefore('.')
            val minutes = ((endMillis - startMillis) / 60_000L).coerceAtLeast(1L)
            // Panels disagree on which clock the start is written in - found live 2026-10-05:
            // every shape answered 200 with an empty body at the panel-timezone stamp. Try the
            // panel's zone, UTC and the device's, de-duplicated by the stamp they produce.
            val zones = listOf(serverZone, TimeZone.getTimeZone("UTC"), TimeZone.getDefault())
            val stamps = zones.map { z -> SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US).apply { timeZone = z }.format(startMillis) }.distinct()
            val candidates = stamps.flatMap { stamp ->
                listOf(
                    "ts@$stamp" to "$server/timeshift/$user/$pass/$minutes/$stamp/$streamId.ts",
                    "m3u8@$stamp" to "$server/timeshift/$user/$pass/$minutes/$stamp/$streamId.m3u8",
                    "php@$stamp" to "$server/streaming/timeshift.php?username=$user&password=$pass&stream=$streamId&start=$stamp&duration=$minutes",
                )
            }
            val client = com.redsurf.tv.network.IptvNetworkModule.getOkHttpClient(userAgent)
            for ((label, url) in candidates) {
                val ok = runCatching {
                    client.newCall(okhttp3.Request.Builder().url(url).header("Range", "bytes=0-1023").build()).execute().use { r ->
                        val head = r.body?.byteStream()?.readNBytesCompat(16) ?: ByteArray(0)
                        val isTs = head.isNotEmpty() && head[0] == 0x47.toByte()
                        val isHls = String(head, Charsets.US_ASCII).startsWith("#EXTM3U")
                        android.util.Log.d(
                            "Catchup",
                            "probe $label status=${r.code} type=${r.header("Content-Type")} head=${head.joinToString("") { "%02x".format(it) }.take(16)} ts=$isTs hls=$isHls",
                        )
                        r.isSuccessful && (isTs || isHls)
                    }
                }.getOrElse { android.util.Log.d("Catchup", "probe $label failed: ${it.javaClass.simpleName}"); false }
                if (ok) return@withContext url
            }
            null
        }

    private fun java.io.InputStream.readNBytesCompat(n: Int): ByteArray {
        val buf = ByteArray(n)
        var read = 0
        while (read < n) {
            val k = read(buf, read, n - read)
            if (k < 0) break
            read += k
        }
        return buf.copyOf(read)
    }

    /** True when [startMillis] still lies inside the channel's archive. */
    fun isInArchive(archiveDays: Int, startMillis: Long, now: Long): Boolean =
        archiveDays > 0 && startMillis < now && now - startMillis <= archiveDays * 24L * 60 * 60 * 1000
}
