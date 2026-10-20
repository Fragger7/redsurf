package com.redsurf.tv.sync

import android.os.SystemClock
import kotlinx.coroutines.delay

/**
 * When the user last pressed a key, so background work can yield to them (performance salvage
 * L6, 2026-10-20). Measured during an EPG sync: the guide's category query took 134-683ms instead
 * of ~5ms while the parser flushed 2,000-row batches (1.4-6s each) - the writes saturate storage
 * on this TV. Writers call [awaitQuiet] before each heavy chunk; the sync gets a little longer,
 * browsing stays quick. Process-wide on purpose: WorkManager runs the sync in this process.
 */
object UserActivity {
    @Volatile
    private var lastInputAt = 0L

    /** Call on every key press (AppShell does). */
    fun touch() {
        lastInputAt = SystemClock.uptimeMillis()
    }

    /** Waits while the user has pressed a key within [quietMs], for at most [maxWaitMs] per call -
     * so a long browse delays the sync, never stops it. */
    suspend fun awaitQuiet(quietMs: Long = 1_500, maxWaitMs: Long = 10_000) {
        val start = SystemClock.uptimeMillis()
        while (true) {
            val now = SystemClock.uptimeMillis()
            if (now - lastInputAt >= quietMs || now - start >= maxWaitMs) return
            delay(250)
        }
    }
}
