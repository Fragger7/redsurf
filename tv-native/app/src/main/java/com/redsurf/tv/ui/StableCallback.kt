package com.redsurf.tv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * A callback that is the same instance across recompositions but always runs the latest [block].
 * A lambda that captures a local function or fresh state is a new object every recomposition, and
 * handing a new object down a list makes every row recompose (performance salvage, 2026-10-20:
 * each category change recomposed every visible guide row this way).
 */
@Composable
fun stableCallback(block: () -> Unit): () -> Unit {
    val latest = rememberUpdatedState(block)
    return remember { { latest.value() } }
}

@Composable
fun <A> stableCallback(block: (A) -> Unit): (A) -> Unit {
    val latest = rememberUpdatedState(block)
    return remember { { a: A -> latest.value(a) } }
}

@Composable
fun <A, B> stableCallback(block: (A, B) -> Unit): (A, B) -> Unit {
    val latest = rememberUpdatedState(block)
    return remember { { a: A, b: B -> latest.value(a, b) } }
}
