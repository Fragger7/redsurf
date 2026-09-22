package com.redsurf.tv.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * PHASE_3.md decision 2 - real WorkManager scheduling, one unique work name per playlist so
 * multiple playlists' syncs never collide or cancel each other (`EpgSyncWorker` was never
 * actually enqueued anywhere before this phase).
 *
 * **One work request per playlist, ever** (2026-09-21). The earlier shape - a periodic request
 * plus a separate one-time "sync now" request - fired both at once on a cold launch whose
 * periodic run happened to be due, so the device sent the provider two concurrent 67MB feed
 * requests for the same account and got a 502 for each. A periodic request enqueued with
 * `CANCEL_AND_REENQUEUE` runs immediately *and* resets the daily timer to now, which is exactly
 * what "sync now" meant, without a second request to race against.
 */
object EpgSyncScheduler {
    private fun workName(playlistId: String) = "epg_sync_periodic_$playlistId"
    private fun legacyOneTimeName(playlistId: String) = "epg_sync_once_$playlistId"

    /** Linear, not WorkManager's default exponential: a burst of provider 502s once pushed the
     * retry out 4+ hours with the guide empty the whole time (2026-09-21). A flaky provider is
     * the normal case for this app, so a stall should cost minutes, not the afternoon. */
    private const val BACKOFF_MINUTES = 5L

    /** Sprint 1 performance pass, 2026-09-22 (Opus consult, "must-do" fix set): cold launch used
     * to fire every playlist's sync at once, racing the Live TV screen's own first-paint queries
     * for the same 4-connection Room pool and saturating I/O with N concurrent 67MB downloads.
     * A flat delay gets sync work off the critical path entirely; the per-playlist stagger on top
     * of it means multiple playlists' downloads never start at the same moment by construction,
     * not by scheduling luck. The fully-correct long-term answer (one worker looping over every
     * playlist sequentially, a single app-wide unique work item) is real, cheaper-to-build-right
     * follow-up, logged in `SEQUENCING.md` - not built this pass, this is the must-do version. */
    private const val INITIAL_DELAY_SECONDS = 45L
    private const val STAGGER_SECONDS_PER_PLAYLIST = 5 * 60L

    private fun request(playlistId: String, staggerIndex: Int) =
        PeriodicWorkRequestBuilder<EpgSyncWorker>(24, TimeUnit.HOURS)
            .setInputData(workDataOf(EPG_SYNC_INPUT_PLAYLIST_ID to playlistId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .setInitialDelay(INITIAL_DELAY_SECONDS + staggerIndex * STAGGER_SECONDS_PER_PLAYLIST, TimeUnit.SECONDS)
            .build()

    /**
     * [runNow] = true: sync immediately (after its own delay/stagger below, not instantly), then
     * daily from now (a just-added playlist, or a cold launch that found no completed sync on
     * record). false: keep an existing schedule's timer but apply the current criteria to it
     * (`UPDATE`, WorkManager 2.8+) - `KEEP` would have left every already-installed device on the
     * old exponential backoff forever. [staggerIndex] - this playlist's position in whatever list
     * the caller is iterating (0 for a single playlist, or a manual "update now" trigger where the
     * user is deliberately asking for it right away) - spaces multiple playlists' cold-launch
     * downloads apart so they never start concurrently by construction.
     */
    fun schedule(context: Context, playlistId: String, runNow: Boolean, staggerIndex: Int = 0) {
        val wm = WorkManager.getInstance(context)
        // Devices that ran the previous two-request shape may still hold a backed-off one-time
        // request under the old name; it would race the periodic one exactly as before.
        wm.cancelUniqueWork(legacyOneTimeName(playlistId))
        wm.enqueueUniquePeriodicWork(
            workName(playlistId),
            if (runNow) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.UPDATE,
            request(playlistId, staggerIndex),
        )
    }

    /** Whether this playlist's sync is executing right now - Settings → EPG's "Updating…" state.
     * `.get()` on the future blocks, so call from a background dispatcher. */
    fun isRunning(context: Context, playlistId: String): Boolean =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(workName(playlistId)).get()
            .any { it.state == WorkInfo.State.RUNNING }

    /** Called from `deletePlaylist` - an orphaned periodic sync for a deleted playlist would
     * keep firing forever, hit `EpgSyncWorker`'s own "playlist no longer exists" no-op every
     * time, and never stop. */
    fun cancel(context: Context, playlistId: String) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(workName(playlistId))
        wm.cancelUniqueWork(legacyOneTimeName(playlistId))
    }
}
