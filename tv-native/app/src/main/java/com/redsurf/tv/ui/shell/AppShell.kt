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
import androidx.compose.ui.platform.LocalContext
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
 * Critically, the destination content is composed from exactly ONE call site regardless of
 * `liveTvFullscreen` - only the NavStrip/padding around it are conditional. An earlier version
 * called `content()` from two different structural positions (directly under `if`, nested inside
 * the Column under `else`), which - however identical the two branches read - are different
 * Compose composition groups: toggling fullscreen tore the whole subtree down and remounted it
 * fresh each time, silently wiping every `remember` inside LiveTvScreen (selectedGroup,
 * focusedChannel, its own isFullscreen). That's what caused "duplicate screens to get a channel
 * to play" (the first OK press's fullscreen state was lost the instant AppShell's structural
 * branch flipped) and Back-from-fullscreen resetting to the first group with no focus, instead of
 * where the user actually was. Found and fixed 2026-09-11.
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
 * Settings (user request, 2026-09-11, end-state shell built 2026-09-13 per `docs/plans/SETTINGS.md`)
 * is the real TiviMate-taxonomy/StreamVault-layout two-pane shell now - see `SettingsScreen.kt`'s
 * doc comment for the category rail, grey rows, and its own focus trap/Back-peeling. Only
 * Playlists and About have real content; every other category previews the finished product as
 * grey, unfocusable rows. [selectedSettingsCategory] is hoisted here, not `remember`ed inside
 * `SettingsScreen` itself, for the same reason `destination` already is: that composable is torn
 * down and recomposed fresh every time `destination` switches away from and back to Settings (the
 * conditional-composition trap this class doc already describes for `LiveTvScreen`/fullscreen), so
 * an internal `remember` would reset to the first category on every re-entry instead of keeping
 * whichever one the user was on (state/focus discipline, `AGENTS.md`).
 */
@Composable
fun AppShell(viewModel: MainViewModel, activePlaylistId: String?) {
    var destination by remember { mutableStateOf(NavDestination.LiveTv) }
    var liveTvFullscreen by remember { mutableStateOf(false) }
    var selectedSettingsCategory by remember { mutableStateOf(SettingsCategory.General) }

    BackHandler(enabled = !liveTvFullscreen && destination != NavDestination.Home) {
        destination = NavDestination.Home
    }

    val rootModifier = if (liveTvFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxSize().tvSafeArea()

    Column(modifier = rootModifier) {
        if (!liveTvFullscreen) {
            NavStrip(current = destination, onSelect = { destination = it })
            Spacer(modifier = Modifier.height(20.dp))
        }

        // One call site, always reached when destination == LiveTv, regardless of
        // liveTvFullscreen - see the class doc above for why that matters.
        when {
            destination == NavDestination.LiveTv && activePlaylistId != null ->
                LiveTvScreen(
                    viewModel = viewModel,
                    onFullscreenChanged = { liveTvFullscreen = it },
                )
            destination == NavDestination.LiveTv ->
                PlaceholderScreen("Live TV", "No active playlist")
            destination == NavDestination.Settings -> {
                val updateStatus by viewModel.updateStatus.collectAsState()
                val playlists by viewModel.repository.playlists().collectAsState(initial = emptyList())
                val context = LocalContext.current
                SettingsScreen(
                    selectedCategory = selectedSettingsCategory,
                    onCategorySelected = { selectedSettingsCategory = it },
                    updateStatus = updateStatus,
                    playlists = playlists,
                    onCheckForUpdates = { viewModel.checkForUpdates(force = true) },
                    onResetPlaylist = { viewModel.resetAndAddNewPlaylist() },
                    onAddPlaylist = { viewModel.beginAddPlaylist(context) },
                    onDeletePlaylist = { id -> viewModel.deletePlaylist(id) },
                )
            }
            else ->
                PlaceholderScreen(destination.label, "Coming in a later phase")
        }
    }
}
