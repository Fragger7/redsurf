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
 * Fullscreen playback (#1.5) is the one exception to both: while Live TV is fullscreen, the nav
 * strip is hidden and the safe-area padding is skipped so video goes edge to edge, not inset.
 *
 * BACK on any non-LiveTv destination returns to LiveTv. LiveTvScreen owns its own BackHandler for
 * leaving fullscreen (mutually exclusive with the one here via the `enabled` flags, so only one
 * is ever live at a time). BACK on the root Live TV screen intentionally has no handler here at
 * all - it falls through to Android's default (exit to the TV home screen). A previous
 * always-enabled handler at the Activity level deliberately no-op'd there, which trapped a real
 * user with no way out; removed 2026-09-11.
 *
 * Settings (user request, 2026-09-11) is a real minimal screen, not a placeholder like the other
 * four unbuilt destinations - one button to reset the saved playlist for testing a different
 * source, since there's no other way to do that without reinstalling.
 */
@Composable
fun AppShell(viewModel: MainViewModel, activePlaylistId: String?) {
    var destination by remember { mutableStateOf(NavDestination.LiveTv) }
    var liveTvFullscreen by remember { mutableStateOf(false) }

    BackHandler(enabled = !liveTvFullscreen && destination != NavDestination.LiveTv) {
        destination = NavDestination.LiveTv
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
            destination == NavDestination.Settings ->
                SettingsScreen(onResetPlaylist = { viewModel.resetAndAddNewPlaylist() })
            else ->
                PlaceholderScreen(destination.label, "Coming in a later phase")
        }
    }

    if (liveTvFullscreen) {
        content()
    } else {
        Column(modifier = Modifier.fillMaxSize().tvSafeArea()) {
            NavStrip(current = destination, onSelect = { destination = it })
            content()
        }
    }
}
