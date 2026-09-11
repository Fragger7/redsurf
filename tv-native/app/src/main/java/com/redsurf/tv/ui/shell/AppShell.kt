package com.redsurf.tv.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.redsurf.tv.MainViewModel
import com.redsurf.tv.ui.livetv.LiveTvScreen
import com.redsurf.tv.ui.theme.tvSafeArea

/**
 * What AppState.Loaded renders (replaces the deleted ui/TiViMateLayout.kt mock view -
 * PHASE_1.md #1.3). A nav strip over whichever destination is current, inside the 48dp
 * overscan safe area (UI_SPEC.md #8) applied once here at the shell root.
 *
 * BACK on any non-LiveTv destination returns to LiveTv. BACK on LiveTv itself keeps
 * MainActivity's existing top-level behaviour (it's still enabled there for AppState.Onboarding;
 * on Loaded/LiveTv there is currently nothing further back to go, matching pre-Phase-1 behaviour).
 */
@Composable
fun AppShell(viewModel: MainViewModel, activePlaylistId: String?) {
    var destination by remember { mutableStateOf(NavDestination.LiveTv) }

    BackHandler(enabled = destination != NavDestination.LiveTv) {
        destination = NavDestination.LiveTv
    }

    Column(modifier = Modifier.fillMaxSize().tvSafeArea()) {
        NavStrip(current = destination, onSelect = { destination = it })
        when {
            destination == NavDestination.LiveTv && activePlaylistId != null ->
                LiveTvScreen(viewModel = viewModel, playlistId = activePlaylistId)
            destination == NavDestination.LiveTv ->
                PlaceholderScreen("Live TV", "No active playlist")
            else ->
                PlaceholderScreen(destination.label, "Coming in a later phase")
        }
    }
}
