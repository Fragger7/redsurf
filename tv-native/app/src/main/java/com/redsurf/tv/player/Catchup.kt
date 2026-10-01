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

    /** True when [startMillis] still lies inside the channel's archive. */
    fun isInArchive(archiveDays: Int, startMillis: Long, now: Long): Boolean =
        archiveDays > 0 && startMillis < now && now - startMillis <= archiveDays * 24L * 60 * 60 * 1000
}
