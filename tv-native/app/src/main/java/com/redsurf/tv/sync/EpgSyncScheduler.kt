package com.redsurf.tv.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * PHASE_3.md decision 2 - real WorkManager scheduling, one unique work name per playlist so
 * multiple playlists' syncs never collide or cancel each other (`EpgSyncWorker` was never
 * actually enqueued anywhere before this phase).
 */
object EpgSyncScheduler {
    private fun periodicWorkName(playlistId: String) = "epg_sync_periodic_$playlistId"
    private fun oneTimeWorkName(playlistId: String) = "epg_sync_once_$playlistId"

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private fun inputData(playlistId: String) = workDataOf(EPG_SYNC_INPUT_PLAYLIST_ID to playlistId)

    /** Daily cadence - matches the public-source refresh cadence already on record in
     * `AGENTS.md`'s EPG data-source notes. `KEEP`, not `REPLACE`: re-adding the same playlist
     * (e.g. re-running onboarding) shouldn't reset an already-running schedule's timer. */
    fun schedulePeriodic(context: Context, playlistId: String) {
        val request = PeriodicWorkRequestBuilder<EpgSyncWorker>(24, TimeUnit.HOURS)
            .setInputData(inputData(playlistId))
            .setConstraints(constraints())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            periodicWorkName(playlistId),
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Fired once, right after a playlist is added (`MainViewModel`) - so the guide isn't empty
     * for up to a day while waiting on the periodic schedule's first run. */
    fun syncNow(context: Context, playlistId: String) {
        val request = OneTimeWorkRequestBuilder<EpgSyncWorker>()
            .setInputData(inputData(playlistId))
            .setConstraints(constraints())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            oneTimeWorkName(playlistId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /** Called from `deletePlaylist` - an orphaned periodic sync for a deleted playlist would
     * keep firing forever, hit `EpgSyncWorker`'s own "playlist no longer exists" no-op every
     * time, and never stop. */
    fun cancel(context: Context, playlistId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(periodicWorkName(playlistId))
    }
}
