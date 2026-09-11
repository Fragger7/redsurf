package com.redsurf.tv.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redsurf.tv.MainViewModel
import com.redsurf.tv.ui.livetv.LiveTvScreen
import com.redsurf.tv.ui.theme.tvSafeArea

/**
 * What AppState.Loaded renders (replaces the deleted ui/TiViMateLayout.kt mock view -
 * PHASE_1.md #1.3). A nav strip over whichever destination is current, inside the 48dp
 * overscan safe area (UI_SPEC.md #8) applied once here at the shell root.
 *
 * Fullscreen playback (#1.5) is the one exception to both: while Live TV is fullscreen, the nav
 * strip is hidden and the safe-area padding is skipped so video goes edge to edge, not inset.
 *
 * Home is the back-stack root (user request, 2026-09-11: Back from Live TV was exiting the app
 * outright with no stop in between). BACK from any other destination - including Live TV, even
 * though the app opens directly on it - returns to Home; BACK on Home itself has no handler here
 * and correctly falls through to Android's default (exit to the TV home screen). Home is still
 * just a placeholder ("coming in a later phase"), which is fine for now - the point is giving
 * Back one stop before it closes the app, not building a real Home screen yet. LiveTvScreen owns
 * its own BackHandler for leaving fullscreen (mutually exclusive with the one here via the
 * `enabled` flags, so only one is ever live at a time).
 *
 * (Previously Live TV itself was the back-stack root and Back there fell straight through to
 * app-exit - a deliberate earlier fix for a real trap bug, superseded by this one now that the
 * trap is gone and the ask has moved to "give me a stop before exiting.")
 *
 * Settings (user request, 2026-09-11) is a real minimal screen, not a placeholder like the other
 * four unbuilt destinations - one button to reset the saved playlist for testing a different
 * source, since there's no other way to do that without reinstalling.
 */
@Composable
fun AppShell(viewModel: MainViewModel, activePlaylistId: String?) {
    var destination by remember { mutableStateOf(NavDestination.LiveTv) }
    var liveTvFullscreen by remember { mutableStateOf(false) }

    BackHandler(enabled = !liveTvFullscreen && destination != NavDestination.Home) {
        destination = NavDestination.Home
    }

    val content: @Composable () -> Unit = {
        when {
            destination == NavDestination.LiveTv && activePlaylistId != null ->
                LiveTvScreen(
                    viewModel = viewModel,
                    playlistId = activePlaylistId,
                    onFullscreenChanged = { liveTvFullscreen = it },
                )
            destination == NavDestination.LiveTv ->
                PlaceholderScreen("Live TV", "No active playlist")
            destination == NavDestination.Settings -> {
                val updateStatus by viewModel.updateStatus.collectAsState()
                SettingsScreen(
                    updateStatus = updateStatus,
                    onCheckForUpdates = { viewModel.checkForUpdates() },
                    onResetPlaylist = { viewModel.resetAndAddNewPlaylist() },
                )
            }
            else ->
                PlaceholderScreen(destination.label, "Coming in a later phase")
        }
    }

    if (liveTvFullscreen) {
        content()
    } else {
        Column(modifier = Modifier.fillMaxSize().tvSafeArea()) {
            NavStrip(current = destination, onSelect = { destination = it })
            Spacer(modifier = Modifier.height(32.dp))
            content()
        }
    }
}
