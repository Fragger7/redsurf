package com.redsurf.tv.diag

import android.os.Trace
import androidx.compose.ui.layout.layout

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

/** Times this node's measure pass (its whole subtree) as a named trace section. */
fun androidx.compose.ui.Modifier.tracedMeasure(name: String): androidx.compose.ui.Modifier =
    this.then(
        androidx.compose.ui.Modifier.layout { measurable, constraints ->
            val placeable = traced(name) { measurable.measure(constraints) }
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        },
    )
