package com.redsurf.tv.sync

import android.content.Context

/**
 * Per-playlist "when did an EPG sync last run to completion" marker. Row counts can't answer
 * that question: a sync that dies a third of the way through a 67MB feed leaves tens of thousands
 * of perfectly valid rows behind (found 2026-09-21 - the device held 66,807 of the feed's 201,463
 * programmes and the count-based check saw a populated guide). Only the worker knows whether the
 * parser actually reached `</tv>`, so it records that here and the cold-launch backfill reads it.
 */
object EpgSyncState {
    private const val PREFS = "redsurf_epg_sync"
    private fun key(playlistId: String) = "last_completed_$playlistId"

    fun markCompleted(context: Context, playlistId: String, at: Long = System.currentTimeMillis()) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(key(playlistId), at).apply()
    }

    /** 0 when no sync has ever completed for this playlist. */
    fun lastCompleted(context: Context, playlistId: String): Long =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(key(playlistId), 0L)

    /** Every attempt's outcome, success or not - what Settings → EPG shows as "Last attempt"
     * when the most recent one failed, so a provider 502 is visible instead of silent. */
    fun markAttempt(context: Context, playlistId: String, error: String?, at: Long = System.currentTimeMillis()) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(attemptKey(playlistId), at)
            .apply { if (error == null) remove(errorKey(playlistId)) else putString(errorKey(playlistId), error) }
            .apply()
    }

    fun lastAttempt(context: Context, playlistId: String): Long =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(attemptKey(playlistId), 0L)

    fun lastError(context: Context, playlistId: String): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(errorKey(playlistId), null)

    fun clear(context: Context, playlistId: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(key(playlistId)).remove(attemptKey(playlistId)).remove(errorKey(playlistId))
            .apply()
    }

    private fun attemptKey(playlistId: String) = "last_attempt_$playlistId"
    private fun errorKey(playlistId: String) = "last_error_$playlistId"
}
