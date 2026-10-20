package com.redsurf.tv.diag

import android.os.Trace

/**
 * A named section in system traces (atrace / Perfetto) - next to free when no trace is running.
 * Wrapping a composable call times its (re)composition; the performance salvage's frame-by-frame
 * traces (PERFORMANCE_SALVAGE.md) read these to tell which part of a slow frame is which.
 */
inline fun <T> traced(name: String, block: () -> T): T {
    Trace.beginSection(name)
    try {
        return block()
    } finally {
        Trace.endSection()
    }
}
