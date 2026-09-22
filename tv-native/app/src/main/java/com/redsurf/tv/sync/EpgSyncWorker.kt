package com.redsurf.tv.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.redsurf.tv.db.RedSurfDatabase
import com.redsurf.tv.network.IptvNetworkModule
import com.redsurf.tv.parser.XmlTvParser
import com.redsurf.tv.vod.XtreamApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

private const val TAG = "EpgSyncWorker"
const val EPG_SYNC_INPUT_PLAYLIST_ID = "PLAYLIST_ID"
private const val EPG_READ_TIMEOUT_SECONDS = 120L
private const val PRUNE_ENDED_BEFORE_MS = 6 * 60 * 60 * 1000L

/**
 * PHASE_3.md decision 2 - one worker instance per playlist, scheduled by [EpgSyncScheduler].
 * Previously took a single global `EPG_URL` input and was never actually enqueued anywhere
 * (`PHASE_3.md`'s "What's already there, unexpectedly" - confirmed by grep before this sprint).
 *
 * No stored password anywhere: the URL is rebuilt from a real channel's own `streamId`
 * (`XtreamApi.parseXtreamCredentials`), the same "don't persist the password a second time"
 * pattern the Connections badge already established.
 */
class EpgSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val playlistId = inputData.getString(EPG_SYNC_INPUT_PLAYLIST_ID)
            ?: return@withContext Result.failure()
        val db = RedSurfDatabase.getDatabase(applicationContext)

        val playlist = db.playlistDao().getPlaylistById(playlistId)
        if (playlist == null) {
            Log.d(TAG, "playlist $playlistId no longer exists, skipping")
            return@withContext Result.success()
        }
        if (playlist.type != "xtream") {
            // PHASE_3.md decision 2 (confirm, accepted): M3U-only/Stalker playlists have no
            // provider EPG endpoint in P0 - an honest no-op, not a failure to retry forever.
            Log.d(TAG, "playlist $playlistId is type=${playlist.type}, no provider EPG source in P0")
            return@withContext Result.success()
        }

        val sample = db.channelDao().firstForPlaylist(playlistId)
        if (sample == null) {
            Log.d(TAG, "playlist $playlistId has no channels yet, skipping")
            return@withContext Result.success()
        }
        val creds = XtreamApi.parseXtreamCredentials(sample.streamId)
        if (creds == null) {
            Log.e(TAG, "playlist $playlistId: couldn't parse Xtream credentials from a channel URL")
            return@withContext Result.failure()
        }
        val (server, user, pass) = creds
        val epgUrl = "$server/xmltv.php?username=$user&password=$pass"

        try {
            // Host only, never the credentials - enough to tell which provider a 502 came from.
            Log.d(TAG, "epgSync -> start playlist=$playlistId host=${server.substringAfter("://")}")
            // The shared client's 15s read timeout is right for API calls and wrong for a
            // 67MB XMLTV stream on this hardware: one 15s stall mid-stream killed the download,
            // which is why the device only ever held a third of the feed (found 2026-09-21).
            val client = IptvNetworkModule.getOkHttpClient(
                playlist.userAgent,
                readTimeoutSeconds = EPG_READ_TIMEOUT_SECONDS,
            )
            val request = Request.Builder().url(epgUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                // A panel's error body says *why* (max connections, bad credentials, a proxy
                // page) - the status code alone didn't (2026-09-21: repeatable 502s that the same
                // URL never produced from a desktop).
                val body = runCatching { response.body?.string()?.take(200) }.getOrNull()
                Log.e(TAG, "epgSync -> http ${response.code} for playlist $playlistId body=${body?.replace('\n', ' ')}")
                EpgSyncState.markAttempt(applicationContext, playlistId, "HTTP ${response.code}")
                return@withContext Result.retry()
            }

            // No clear-before-parse. The composite key (playlistId, channelEpgId, startTime) with
            // REPLACE makes the parse an upsert, so a sync that dies partway leaves the DB strictly
            // better than it found it - never a partial slice where a complete set used to be.
            // Rows the feed no longer carries are pruned only after a full parse succeeds.
            val now = System.currentTimeMillis()
            var inserted = 0
            response.body?.byteStream()?.use { stream ->
                inserted = XmlTvParser.parseAndInsert(
                    stream, db, playlistId,
                    skipEndedBefore = now - PRUNE_ENDED_BEFORE_MS,
                )
            }
            db.epgDao().pruneEnded(playlistId, now - PRUNE_ENDED_BEFORE_MS)
            // Sprint 1 performance pass, 2026-09-22 (Opus "nice-to-have" N2): once, after a real
            // import completes - never on the launch path. `sqlite_stat1` is never populated
            // otherwise (confirmed live this pass: nothing in this app calls ANALYZE), so the
            // planner has no real statistics for a ~140K-row table to reason about. A single
            // ANALYZE here, off the cold-launch critical path already per the scheduling change
            // above, is the cheap way to give it real numbers without ever blocking first paint.
            runCatching { db.openHelper.writableDatabase.execSQL("ANALYZE") }
                .onFailure { Log.w(TAG, "epgSync -> ANALYZE failed for playlist $playlistId", it) }
            EpgSyncState.markCompleted(applicationContext, playlistId, now)
            EpgSyncState.markAttempt(applicationContext, playlistId, null, now)
            Log.d(TAG, "epgSync -> done playlist=$playlistId inserted=$inserted")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "epgSync -> failed playlist=$playlistId", e)
            EpgSyncState.markAttempt(applicationContext, playlistId, e.message ?: e.javaClass.simpleName)
            Result.retry()
        }
    }
}
