package com.redsurf.tv.ui.shell

import android.app.Activity
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.redsurf.tv.MainViewModel
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.settings.AppPreferences
import com.redsurf.tv.ui.livetv.GroupKey
import com.redsurf.tv.ui.livetv.LiveTvScreen
import com.redsurf.tv.ui.theme.tvSafeArea
import kotlinx.coroutines.launch

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
private const val TAG = "AppShell"

@Composable
fun AppShell(viewModel: MainViewModel, activePlaylistId: String?) {
    var destination by remember { mutableStateOf(NavDestination.LiveTv) }
    var liveTvFullscreen by remember { mutableStateOf(false) }
    var liveTvChannelsFocused by remember { mutableStateOf(false) }
    var selectedSettingsCategory by remember { mutableStateOf(SettingsCategory.General) }

    // Hoisted for the same reason selectedSettingsCategory is (see class doc above) - found
    // live, 2026-09-15 (user report): leaving Live TV for any other destination and coming back
    // reset the category all the way to the first one, discarded whatever channel had focus, and
    // dropped the recent-channels tile row, because all three lived in a plain `remember` inside
    // LiveTvScreen, which the conditional-composition trap tears down on every destination
    // switch. In-memory only, like `selectedSettingsCategory` - survives switching tabs within
    // this app session, not a process death/relaunch (that needs real disk persistence, a
    // separate, bigger piece - AGENTS.md backlog).
    var liveTvSelectedGroup by remember { mutableStateOf<GroupKey?>(null) }
    var liveTvFocusedChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    // Recent channels no longer hoisted here (PHASE_2.md decision 14, 2026-09-17) - they're a
    // real Room table now (`ChannelRepository.recentChannels()`), which is already reactive and
    // already survives a relaunch, not just a tab switch - there's nothing left for AppShell to
    // preserve on this screen's behalf.

    // Explicit one-shot signal for "also start playing" (AGENTS.md backlog, corrected
    // 2026-09-15) - deliberately NOT inferred from focusedChannel changing, which would also
    // fire on a user's first ordinary browse-focus after a genuinely fresh launch (no restore to
    // speak of) and wrongly auto-play whatever they merely focused. Only ever set true from the
    // restore effect below, right alongside the seed it applies to (same coroutine, same
    // recomposition, so LiveTvScreen sees the new focusedChannel and this trigger together);
    // LiveTvScreen consumes it back to false via onAutoPlayTriggerConsumed so it can never refire.
    var liveTvAutoPlayTrigger by remember { mutableStateOf(false) }

    // Cold-launch resume focus fix (AGENTS.md backlog, 2026-09-16) - same one-shot shape as
    // liveTvAutoPlayTrigger, for the same reason: only ever set true from the restore effect
    // below, right alongside the seed it applies to, and consumed back to false by LiveTvScreen
    // so it can't refire. Only meaningful when NOT auto-playing - if auto-play fires instead, the
    // fullscreen entry it triggers claims focus on its own via the existing fullscreenFocus
    // mechanism, and Back out of fullscreen already reactively focuses the channel row via the
    // existing channelReturnFocus effect, so there's nothing this trigger needs to do in that case.
    var liveTvClaimInitialFocusTrigger by remember { mutableStateOf(false) }

    // Long-press Back, anywhere in the app (user request, 2026-09-18, TiviMate parity + the
    // standing "no fast way back to top-level nav from a deep scroll" backlog item, built
    // together since they're the same gesture doing two context-dependent things, not two
    // features). One `FocusRequester` per pill so this can force focus onto whichever one
    // matches wherever the user actually is - `NavStrip` just attaches whichever it's handed.
    val navPillFocusRequesters = remember { NavDestination.entries.associateWith { FocusRequester() } }
    var backHeldLong by remember { mutableStateOf(false) }

    // Teleport Menu (docs/plans/TELEPORT_MENU.md, 2026-09-19) - the power-user layer on top of
    // the plain long-press-Back jump above: instead of one guessed destination, a short list of
    // likely ones. `groups`/`scope` are needed here (not just in LiveTvScreen) so a jump can be
    // resolved and applied without LiveTvScreen needing to be the one driving it.
    var teleportMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(teleportMenuOpen) {
        Log.d(TAG, "teleportMenu -> ${if (teleportMenuOpen) "open" else "closed"}")
    }
    val groups by viewModel.repository.liveGroups().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    // BACKLOG_SWEEP.md #10 - one instance for the whole shell, same lifetime as the ViewModel;
    // AppShell is the natural owner since both destinations that touch these prefs (Settings to
    // flip them, Live TV/PlayerScreen to read them) are composed from here.
    val prefsContext = LocalContext.current
    val appPreferences = remember { AppPreferences(prefsContext) }
    val blackScreenBetweenZaps by appPreferences.blackScreenBetweenZaps.collectAsState()
    val showRawResolution by appPreferences.showRawResolution.collectAsState()
    val autoPlayLastChannelOnLaunch by appPreferences.autoPlayLastChannelOnLaunch.collectAsState()
    val teleportMenuEnabled by appPreferences.teleportMenuEnabled.collectAsState()

    // Resume-last-channel-on-launch restore (AGENTS.md backlog, user decision 2026-09-15,
    // corrected same day from an earlier pass that had this gated behind the toggle - it's
    // **unconditional** now: landing on Live TV with the last-watched channel/category
    // pre-selected happens every cold launch, not just when a setting is on. The only time this
    // is expected to be a no-op is genuinely fresh state - nothing ever watched yet
    // (`getLastWatchedChannel()` returns null), or the persisted channel no longer exists, e.g.
    // after a Reset (`viewModel.repository.getChannel` returns null for a stale composite-key
    // reference) - both already fall out of this exact lookup with no special-casing needed;
    // LiveTvScreen's own "first group" default applies either way, same as any other
    // stale-reference case in this app. LaunchedEffect(Unit) - fires exactly once, this
    // composable's first composition (cold app launch), never again on recomposition, so a
    // deliberate category/channel change later in the session is never overwritten by this.
    LaunchedEffect(Unit) {
        val (playlistId, streamId) = appPreferences.getLastWatchedChannel() ?: return@LaunchedEffect
        val channel = viewModel.repository.getChannel(playlistId, streamId) ?: return@LaunchedEffect
        liveTvSelectedGroup = GroupKey(channel.playlistId, channel.groupName)
        liveTvFocusedChannel = channel
        if (autoPlayLastChannelOnLaunch) liveTvAutoPlayTrigger = true
        else liveTvClaimInitialFocusTrigger = true
    }

    // The write side of the same feature - records whatever channel is actually playing
    // (`liveTvFullscreen`, not just browse-focused - resuming into whatever you idly scrolled
    // past while browsing would be wrong) every time it changes, independent of whether the
    // toggle is currently on. That way turning the toggle on later doesn't start from nothing.
    LaunchedEffect(liveTvFocusedChannel, liveTvFullscreen) {
        val channel = liveTvFocusedChannel
        if (liveTvFullscreen && channel != null) {
            appPreferences.setLastWatchedChannel(channel.playlistId, channel.streamId)
        }
    }

    // Teleport Menu's three group-level jumps (TELEPORT_MENU.md decisions 3/4) - all reuse the
    // already-verified cold-launch-resume machinery above (liveTvClaimInitialFocusTrigger) rather
    // than inventing a second way to move real D-pad focus onto a category/channel row.
    fun jumpToGroup(target: GroupKey) {
        scope.launch {
            val channel = viewModel.repository.firstChannelInGroup(target.playlistId, target.groupName)
            destination = NavDestination.LiveTv
            liveTvSelectedGroup = target
            liveTvFocusedChannel = channel
            if (channel != null) liveTvClaimInitialFocusTrigger = true
        }
    }

    fun returnToChannelGroup(channel: ChannelEntity) {
        destination = NavDestination.LiveTv
        liveTvSelectedGroup = GroupKey(channel.playlistId, channel.groupName)
        liveTvFocusedChannel = channel
        liveTvClaimInitialFocusTrigger = true
    }

    fun returnToFullscreen() {
        destination = NavDestination.LiveTv
        liveTvAutoPlayTrigger = true
    }

    val playlistRootTarget = resolvePlaylistRootTarget(groups, liveTvFocusedChannel)
    val rootCategoryTarget = resolveRootCategoryTarget(groups, liveTvSelectedGroup)
    val teleportCurrentChannel = liveTvFocusedChannel
    val teleportRows = buildList {
        add(TeleportRow.Live("Nav-Strip") { runCatching { navPillFocusRequesters[destination]?.requestFocus() } })
        add(
            if (playlistRootTarget != null) TeleportRow.Live("Playlist Root") { jumpToGroup(playlistRootTarget) }
            else TeleportRow.Grey("Playlist Root"),
        )
        add(TeleportRow.Grey("Playlist Favorites")) // no favorites view exists yet - permanent until it does
        add(
            if (rootCategoryTarget != null) TeleportRow.Live("Root Category") { jumpToGroup(rootCategoryTarget) }
            else TeleportRow.Grey("Root Category"),
        )
        add(
            if (teleportCurrentChannel != null) {
                TeleportRow.Live("Root Channel Group") { returnToChannelGroup(teleportCurrentChannel) }
            } else {
                TeleportRow.Grey("Root Channel Group")
            },
        )
        add(
            if (teleportCurrentChannel != null) TeleportRow.Live("Return to fullscreen") { returnToFullscreen() }
            else TeleportRow.Grey("Return to fullscreen"),
        )
        add(TeleportRow.Live("Exit RedSurf") { (prefsContext as? Activity)?.finish() })
    }

    // BACKLOG_SWEEP.md #8: excluded while Live TV's own Channels column holds focus, mirroring
    // the fullscreen exclusion right below it - LiveTvScreen owns its own BackHandler for that
    // one Back press (Channels -> Categories), the same mutual-exclusivity-via-`enabled` pattern
    // this class doc already describes for fullscreen, just a second instance of it. Also
    // excluded while the Teleport Menu is open (decision 7: Back closes the menu with no other
    // side effect) - its own BackHandler right below takes over then.
    BackHandler(
        enabled = !liveTvFullscreen &&
            !teleportMenuOpen &&
            destination != NavDestination.Home &&
            !(destination == NavDestination.LiveTv && liveTvChannelsFocused),
    ) {
        destination = NavDestination.Home
    }

    BackHandler(enabled = teleportMenuOpen) {
        teleportMenuOpen = false
    }

    val rootModifier = (if (liveTvFullscreen) Modifier.fillMaxSize() else Modifier.fillMaxSize().tvSafeArea())
        // Long-press Back, intercepted once here rather than in every individual screen's own
        // key router - `onPreviewKeyEvent` on this root sees every key event top-down, before
        // whatever's actually focused (Settings' rail, Live TV's channel list) gets a look at
        // it, so this works "no matter how deep" without touching any of those screens' own key
        // routers. Only ever consumes the events that make up a genuine held Back press (the
        // qualifying repeat tick, and that same press's own release) - every ordinary short Back
        // press passes through completely untouched.
        //
        // Deliberately not intercepted while already `liveTvFullscreen` (scope decision, not an
        // oversight): `NavStrip` isn't even composed then (hidden during fullscreen, above), so
        // a pill-focus jump would silently fail, and there's no clean single-press way to also
        // exit fullscreen first without fighting `PlayerScreen`'s own well-established Back-peel
        // discipline. This was asked for "navigating any menus" - the fullscreen player already
        // has its own working Back behaviour, left untouched here.
        .onPreviewKeyEvent { event ->
            if (event.key != Key.Back || liveTvFullscreen) return@onPreviewKeyEvent false
            val isDown = event.type == KeyEventType.KeyDown
            if (isDown && event.nativeKeyEvent.repeatCount == 0) backHeldLong = false
            if (isDown && event.nativeKeyEvent.isLongPress && !backHeldLong) {
                backHeldLong = true
                if (teleportMenuEnabled) {
                    // TELEPORT_MENU.md decision 2: with the setting on, long-press Back opens the
                    // menu instead, in every context this gesture already reaches (fullscreen is
                    // already excluded above, same as the plain fallback below) - including Live
                    // TV, where the plain fallback's TiviMate-parity fullscreen-jump becomes one
                    // of the menu's own rows ("Return to fullscreen") instead of the automatic
                    // action, since the whole point is offering the choice instead of guessing one.
                    teleportMenuOpen = true
                } else if (destination == NavDestination.LiveTv && !liveTvFullscreen && liveTvFocusedChannel != null) {
                    // TiviMate parity (user request, 2026-09-18): on Live TV, with a channel already
                    // focused/previewed and not already fullscreen, jump straight into it - reusing
                    // the exact same trigger cold-launch auto-play already uses, since "set
                    // previewUrl, record it, go fullscreen" is exactly the same action regardless of
                    // what asked for it. Everywhere else (including Live TV with nothing focused, or
                    // already fullscreen), jump real D-pad focus to the pill for wherever the user
                    // actually is - the generic "fast way back to the nav-strip" the deep-scroll
                    // case (Settings, a long channel list, anywhere) needs.
                    liveTvAutoPlayTrigger = true
                } else {
                    runCatching { navPillFocusRequesters[destination]?.requestFocus() }
                }
                return@onPreviewKeyEvent true
            }
            if (event.type == KeyEventType.KeyUp && backHeldLong) {
                // Swallow this same press's own release - same reasoning as the long-press
                // context-menu fix (PlayerScreen.kt): left alone, it would fall through as an
                // ordinary short Back press the instant the physical key comes up.
                backHeldLong = false
                return@onPreviewKeyEvent true
            }
            false
        }

    // Box, not a bare Column, so the Teleport Menu overlay can render on top of everything below
    // regardless of `destination` - `rootModifier` (the safe area + long-press-Back interception)
    // moves here with it; the Column inside is unchanged otherwise.
    Box(modifier = rootModifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!liveTvFullscreen) {
                NavStrip(current = destination, onSelect = { destination = it }, focusRequesters = navPillFocusRequesters)
                Spacer(modifier = Modifier.height(20.dp))
            }

            // One call site, always reached when destination == LiveTv, regardless of
            // liveTvFullscreen - see the class doc above for why that matters.
            when {
                // PHASE_3.md decision 3 - Live TV and Guide are one screen, two entry points.
                // `guideMode` is the only thing that differs between them.
                (destination == NavDestination.LiveTv || destination == NavDestination.Guide) && activePlaylistId != null ->
                    LiveTvScreen(
                        viewModel = viewModel,
                        onFullscreenChanged = { liveTvFullscreen = it },
                        onChannelsFocusChanged = { liveTvChannelsFocused = it },
                        blackScreenBetweenZaps = blackScreenBetweenZaps,
                        showRawResolution = showRawResolution,
                        selectedGroup = liveTvSelectedGroup,
                        onSelectedGroupChanged = { liveTvSelectedGroup = it },
                        focusedChannel = liveTvFocusedChannel,
                        onFocusedChannelChanged = { liveTvFocusedChannel = it },
                        autoPlayTrigger = liveTvAutoPlayTrigger,
                        onAutoPlayTriggerConsumed = { liveTvAutoPlayTrigger = false },
                        claimInitialFocusTrigger = liveTvClaimInitialFocusTrigger,
                        onClaimInitialFocusTriggerConsumed = { liveTvClaimInitialFocusTrigger = false },
                        guideMode = destination == NavDestination.Guide,
                        onOpenGuideFromPlayer = { destination = NavDestination.Guide },
                    )
                destination == NavDestination.LiveTv ->
                    PlaceholderScreen("Live TV", "No active playlist")
                destination == NavDestination.Guide ->
                    PlaceholderScreen("Guide", "No active playlist")
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
                        blackScreenBetweenZaps = blackScreenBetweenZaps,
                        onToggleBlackScreenBetweenZaps = {
                            appPreferences.setBlackScreenBetweenZaps(!blackScreenBetweenZaps)
                        },
                        showRawResolution = showRawResolution,
                        onToggleShowRawResolution = {
                            appPreferences.setShowRawResolution(!showRawResolution)
                        },
                        autoPlayLastChannelOnLaunch = autoPlayLastChannelOnLaunch,
                        onToggleAutoPlayLastChannelOnLaunch = {
                            appPreferences.setAutoPlayLastChannelOnLaunch(!autoPlayLastChannelOnLaunch)
                        },
                        teleportMenuEnabled = teleportMenuEnabled,
                        onToggleTeleportMenuEnabled = {
                            appPreferences.setTeleportMenuEnabled(!teleportMenuEnabled)
                        },
                    )
                }
                else ->
                    PlaceholderScreen(destination.label, "Coming in a later phase")
            }
        }

        TeleportMenu(
            visible = teleportMenuOpen,
            rows = teleportRows,
            onDismiss = { teleportMenuOpen = false },
        )
    }
}
