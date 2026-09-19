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
            Log.d(TAG, "epgSync -> start playlist=$playlistId")
            val client = IptvNetworkModule.getOkHttpClient(playlist.userAgent)
            val request = Request.Builder().url(epgUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "epgSync -> http ${response.code} for playlist $playlistId")
                return@withContext Result.retry()
            }

            var inserted = 0
            response.body?.byteStream()?.use { stream ->
                // Scoped to this playlist only (EpgDao.clearForPlaylist) - a global wipe here
                // would erase every OTHER playlist's EPG on every single sync (PHASE_3.md's
                // EpgDao doc comment).
                db.epgDao().clearForPlaylist(playlistId)
                inserted = XmlTvParser.parseAndInsert(stream, db.epgDao(), playlistId)
            }
            Log.d(TAG, "epgSync -> done playlist=$playlistId inserted=$inserted")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "epgSync -> failed playlist=$playlistId", e)
            Result.retry()
        }
    }
}
