package com.redsurf.tv.ui.shell

import com.redsurf.tv.diag.tracedMeasure
import com.redsurf.tv.sync.UserActivity
import com.redsurf.tv.diag.traced
import android.app.Activity
import android.os.SystemClock
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.graphics.graphicsLayer
import android.util.Log
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.redsurf.tv.MainViewModel
import com.redsurf.tv.data.FAVORITES_GROUP
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.settings.AppPreferences
import com.redsurf.tv.settings.StartOn
import com.redsurf.tv.sync.EpgSyncScheduler
import com.redsurf.tv.ui.livetv.GroupKey
import com.redsurf.tv.ui.livetv.GroupsFocusRequest
import com.redsurf.tv.ui.livetv.key
import com.redsurf.tv.ui.livetv.LiveTvScreen
import com.redsurf.tv.ui.theme.surfBackdrop
import com.redsurf.tv.ui.theme.tvSafeArea
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
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
private const val HINT_MS = 2_500L
private const val EXIT_CONFIRM_MS = 4_000L
private const val HINT_REPEATS = 3
private const val HINT_TELEPORT = "teleport"
private const val HINT_NAV_HIDDEN = "nav_hidden"

@Composable
fun AppShell(viewModel: MainViewModel, activePlaylistId: String?) {
    // Settings -> General "Start on", read once for the first frame so a Home start never
    // flashes Live TV. A throwaway instance: the shell's own one is built further down.
    val startContext = LocalContext.current
    var destination by remember {
        val prefs = AppPreferences(startContext)
        mutableStateOf(
            when (prefs.startOn.value) {
                StartOn.LiveTv -> NavDestination.LiveTv
                StartOn.Home -> NavDestination.Home
                StartOn.LastUsed -> if (prefs.getLastDestination() == NavDestination.Home.name) NavDestination.Home else NavDestination.LiveTv
            },
        )
    }
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

    // Nav feedback (2026-10-26, AGENTS.md plan item 3): one transient hint at a time
    // ([HintChip]), and TiviMate's "Confirm exit by second press Back" (TIVIMATE_LEARNINGS.md
    // F17) - on Home, Back first reveals the strip on the Home pill and says so; only a second
    // Back within [EXIT_CONFIRM_MS] leaves the app. Without this the first Back on Home quit.
    var hintText by remember { mutableStateOf<String?>(null) }
    var hintSeq by remember { mutableIntStateOf(0) }
    fun showHint(text: String) { hintText = text; hintSeq++ }
    LaunchedEffect(hintSeq) {
        if (hintText == null) return@LaunchedEffect
        delay(HINT_MS)
        hintText = null
    }
    var exitArmedAt by remember { mutableLongStateOf(0L) }

    // Teleport Menu (docs/plans/TELEPORT_MENU.md, 2026-09-19) - the power-user layer on top of
    // the plain long-press-Back jump above: instead of one guessed destination, a short list of
    // likely ones. `groups`/`scope` are needed here (not just in LiveTvScreen) so a jump can be
    // resolved and applied without LiveTvScreen needing to be the one driving it.
    var teleportMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(teleportMenuOpen) {
        Log.d(TAG, "teleportMenu -> ${if (teleportMenuOpen) "open" else "closed"}")
    }
    // Sprint "Teleport finish", 2026-09-29 (TELEPORT_MENU.md) - the menu no longer takes Compose
    // focus; this shell's root key handler drives it by index (see TeleportMenu's own doc for why
    // the old focus trap made most rows inert). The chosen row's action is parked in
    // [pendingTeleportAction] and only runs once the close animation has finished, so it always
    // starts from a settled screen with focus exactly where the user left it.
    var teleportIndex by remember { mutableStateOf(0) }
    var teleportFromFullscreen by remember { mutableStateOf(false) }
    var teleportOkArmed by remember { mutableStateOf(false) }
    var pendingTeleportAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    // Where a Teleport jump lands in Live TV: an explicit Categories row, or leaving fullscreen
    // without the usual return-to-grid claim (the action places focus itself).
    var liveTvGroupsFocusRequest by remember { mutableStateOf<GroupsFocusRequest?>(null) }
    var liveTvExitFullscreenRequest by remember { mutableStateOf(0L) }
    var liveTvExitFullscreenStoppedRequest by remember { mutableStateOf(0L) }
    // A Teleport jump that switches `destination` to Live TV places focus itself - the generic
    // "entered Live TV, reclaim the grid" effect below must sit that one out, or it steals focus
    // back from the Categories row the jump just landed on.
    var suppressNextLiveTvEntryClaim by remember { mutableStateOf(false) }
    // Real "did the pill actually get focus" signal for the Nav-Strip jump - requestFocus() not
    // throwing isn't proof (a cancelled request doesn't throw).
    var navHasFocus by remember { mutableStateOf(false) }
    // DOWN from the strip while on Settings - see SettingsScreen's railEntryRequest.
    var settingsRailEntryRequest by remember { mutableStateOf(0L) }
    // Escape-to-strip lands on the *current destination's* pill (AGENTS.md backlog, decided
    // 2026-09-15: fix the missing concept once, not each spatial quirk): when UP carries focus
    // from content into the strip, default search picks the nearest pill (Home from Settings'
    // rail, Search from its pane - found live 2026-09-30). The last key seen decides it, so
    // LEFT/RIGHT along the strip itself is never redirected.
    var lastKeyWasUp by remember { mutableStateOf(false) }
    // Nav-strip auto-hide (EPG_WRAPUP.md 2.3, scoped 2026-09-21): slides away after
    // [navAutoHideSeconds] without a key press while focus is elsewhere; comes back only when
    // focus reaches the strip itself (UP from a screen's top, Back to Home, long-press Back, the
    // Teleport row) - never on any key, or it would pop back on every cursor move. The strip is
    // only collapsed, never removed, so every one of those jumps can still land on a pill.
    // Performance salvage L2 (2026-10-19): a key press restarts the timer through this flow, never
    // through a state value. It used to be a `lastKeyAt` state used as a LaunchedEffect key - read
    // during composition, so every single D-pad press recomposed this whole shell, the root of
    // the app (measured: 98% janky frames scrolling the guide).
    val keyPresses = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST) }
    var navHidden by remember { mutableStateOf(false) }
    val groups by viewModel.repository.liveGroups().collectAsState(initial = emptyList())
    // "Now Playing" / "Return to fullscreen" mean the last channel actually *played* (user,
    // 2026-09-22), not whatever the D-pad last rested on - the newest recent_channels row is
    // written on every real tune (fullscreen promote, zap, tile), never on a mere preview/browse.
    val lastPlayedList by viewModel.repository.recentChannels(1).collectAsState(initial = emptyList())
    val lastPlayedChannel = lastPlayedList.firstOrNull()
    val scope = rememberCoroutineScope()
    val syncContext = LocalContext.current
    val allPlaylists by remember { viewModel.repository.playlists() }.collectAsState(initial = emptyList())
    val playlistIds = remember(allPlaylists) { allPlaylists.map { it.id } }
    val guideSyncing by remember(playlistIds) {
        EpgSyncScheduler.anyRunningFlow(syncContext, playlistIds)
    }.collectAsState(initial = false)

    // BACKLOG_SWEEP.md #10 - one instance for the whole shell, same lifetime as the ViewModel;
    // AppShell is the natural owner since both destinations that touch these prefs (Settings to
    // flip them, Live TV/PlayerScreen to read them) are composed from here.
    val prefsContext = LocalContext.current
    val appPreferences = remember { AppPreferences(prefsContext) }
    val blackScreenBetweenZaps by appPreferences.blackScreenBetweenZaps.collectAsState()
    val previewOnSelect by appPreferences.previewOnSelect.collectAsState()
    val bufferSize by appPreferences.bufferSize.collectAsState()
    val defaultAspect by appPreferences.defaultAspect.collectAsState()
    val afrEnabled by appPreferences.afrEnabled.collectAsState()
    val preferredAudio by appPreferences.preferredAudioLanguage.collectAsState()
    val autoUpdate by appPreferences.autoUpdate.collectAsState()
    val preferredSubtitles by appPreferences.preferredSubtitleLanguage.collectAsState()
    val showRawResolution by appPreferences.showRawResolution.collectAsState()
    val doubleHeightRow by appPreferences.doubleHeightRow.collectAsState()
    val publicEpgEnabled by appPreferences.publicEpgEnabled.collectAsState()
    val epgRefresh by appPreferences.epgRefresh.collectAsState()
    val epgTimeOffsetHours by appPreferences.epgTimeOffsetHours.collectAsState()
    // Pushed into the repository before anything below reads programmes; LiveTvScreen keys its
    // fetches on the same value so a change re-queries.
    viewModel.repository.epgTimeOffsetMs = epgTimeOffsetHours * 3_600_000L
    val navAutoHideSeconds by appPreferences.navAutoHideSeconds.collectAsState()
    // Live TV only (2026-10-26): it hid on Home too, and Back from Live TV then landed on a Home
    // with no strip and nothing for LEFT/RIGHT to reach - it read as the remote going dead.
    LaunchedEffect(navHasFocus, navAutoHideSeconds, destination) {
        if (navHasFocus || navAutoHideSeconds == 0 || destination != NavDestination.LiveTv) {
            navHidden = false
            return@LaunchedEffect
        }
        keyPresses.onStart { emit(Unit) }.collectLatest {
            if (!navHidden) {
                delay(navAutoHideSeconds * 1000L)
                navHidden = true
            }
        }
    }
    // Auto-hide re-layout (performance salvage S4, 2026-10-26; TIVIMATE_LEARNINGS.md F16): the
    // slide is a translation of the whole column, never a height animation - animating the
    // strip's height re-measured all of Live TV underneath on every frame of the 240ms. The
    // layout changes exactly once: after the slide up, or just before the slide down.
    val navSlide = remember { Animatable(1f) }
    var navCollapsed by remember { mutableStateOf(false) }
    var navStripPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(navHidden) {
        if (navHidden) {
            navSlide.animateTo(0f, tween(240))
            navCollapsed = true
            if (appPreferences.hintCount(HINT_NAV_HIDDEN) < HINT_REPEATS) {
                appPreferences.countHint(HINT_NAV_HIDDEN)
                showHint("Menu hidden · UP from the top brings it back")
            }
        } else {
            navCollapsed = false
            navSlide.animateTo(1f, tween(240))
        }
    }
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
        if (autoPlayLastChannelOnLaunch) {
            destination = NavDestination.LiveTv
            liveTvAutoPlayTrigger = true
        } else if (destination == NavDestination.LiveTv) {
            liveTvClaimInitialFocusTrigger = true
        }
    }

    // Start on = Last used: remember the browse destination the user was last on.
    LaunchedEffect(destination) {
        if (destination == NavDestination.Home || destination == NavDestination.LiveTv) {
            appPreferences.setLastDestination(destination.name)
        }
    }
    val startOn by appPreferences.startOn.collectAsState()

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

    // Teleport Menu's jumps (TELEPORT_MENU.md; rebuilt 2026-09-29). Each one leaves fullscreen
    // first when the menu was opened there, then places focus through an explicit, verified
    // mechanism - never by hoping a single requestFocus() landed.
    suspend fun leaveFullscreenForTeleport() {
        if (!liveTvFullscreen) return
        liveTvExitFullscreenRequest = System.nanoTime()
        repeat(20) {
            if (!liveTvFullscreen) return
            delay(50)
        }
    }

    fun enterLiveTvForTeleport() {
        if (destination != NavDestination.LiveTv) {
            suppressNextLiveTvEntryClaim = true
            destination = NavDestination.LiveTv
        }
    }

    fun teleportToNavStrip() {
        scope.launch {
            leaveFullscreenForTeleport()
            repeat(20) { attempt ->
                runCatching { navPillFocusRequesters[destination]?.requestFocus() }
                delay(60)
                if (navHasFocus) {
                    Log.d(TAG, "teleport NavStrip landed on ${destination.label} attempt=$attempt")
                    return@launch
                }
            }
            Log.w(TAG, "teleport NavStrip gave up")
        }
    }

    /** Playlist Root / Playlist Favorites / Root Category - land on a Categories row. [select]
     * also makes that category the selected one (its channels load in the guide); null keeps
     * the current selection (a playlist header isn't a category). */
    fun teleportToCategoryRow(request: GroupsFocusRequest, select: GroupKey?) {
        scope.launch {
            leaveFullscreenForTeleport()
            enterLiveTvForTeleport()
            if (select != null && select != liveTvSelectedGroup) {
                liveTvSelectedGroup = select
                liveTvFocusedChannel = null
            }
            Log.d(TAG, "teleport -> categories $request")
            liveTvGroupsFocusRequest = request
        }
    }

    /** Now Playing - the last-played channel's own row in the guide, its category selected. */
    fun teleportToChannel(channel: ChannelEntity) {
        scope.launch {
            leaveFullscreenForTeleport()
            enterLiveTvForTeleport()
            liveTvSelectedGroup = GroupKey(channel.playlistId, channel.groupName)
            liveTvFocusedChannel = channel
            Log.d(TAG, "teleport -> channel ${channel.name}")
            liveTvClaimInitialFocusTrigger = true
        }
    }

    fun teleportToFullscreen(channel: ChannelEntity) {
        enterLiveTvForTeleport()
        liveTvSelectedGroup = GroupKey(channel.playlistId, channel.groupName)
        liveTvFocusedChannel = channel
        Log.d(TAG, "teleport -> fullscreen ${channel.name}")
        liveTvAutoPlayTrigger = true
    }

    // Performance/state audit, 2026-09-22 (SEQUENCING.md Sprint 1) - the generic counterpart to
    // the three existing call sites below that each already set liveTvClaimInitialFocusTrigger
    // for their own specific path (cold-launch restore, Teleport's jumpToGroup/
    // returnToChannelGroup/returnToFullscreen). This covers the one path none of those do: simply
    // selecting the Live TV pill from the nav-strip. Now that LiveTvScreen stays composed the
    // whole time (see its own `visible` param doc), this reclaim is cheap - the data it's
    // reclaiming focus onto was never actually unloaded, so the retry loop it drives typically
    // succeeds on the first attempt instead of waiting on a fresh load. Keyed on `destination`
    // itself, so it only fires on a genuine change, not every recomposition while already there.
    LaunchedEffect(destination) {
        if (destination == NavDestination.LiveTv && activePlaylistId != null) {
            if (suppressNextLiveTvEntryClaim) suppressNextLiveTvEntryClaim = false
            else liveTvClaimInitialFocusTrigger = true
        }
    }

    // The playlist "you're in": the channel under focus/playing, else the selected category's,
    // else the last one played.
    val teleportPlaylistId = liveTvFocusedChannel?.playlistId ?: liveTvSelectedGroup?.playlistId ?: lastPlayedChannel?.playlistId
    // Built only while the menu is open (performance salvage, 2026-10-20): the targets below scan
    // every category (~2,200 here) and the list holds fresh lambdas - recomputed on every AppShell
    // recomposition, i.e. every category commit, they also made the hidden menu recompose. While
    // closed (including its close animation) the last list is reused, same instance.
    fun buildTeleportRows(): List<TeleportRow> {
        val favoritesTarget = teleportPlaylistId
            ?.let { GroupKey(it, FAVORITES_GROUP) }
            ?.takeIf { key -> groups.any { it.key() == key } }
        val rootCategoryTarget = resolveRootCategoryTarget(groups, liveTvSelectedGroup)
        val lastGroupTarget = resolveLastGroupTarget(groups, teleportPlaylistId)
        return buildList {
            add(TeleportRow.Live("Nav-Strip") { teleportToNavStrip() })
            add(
                if (teleportPlaylistId != null) {
                    TeleportRow.Live("Playlist Root") {
                        teleportToCategoryRow(GroupsFocusRequest.PlaylistHeader(teleportPlaylistId, System.nanoTime()), select = null)
                    }
                } else {
                    TeleportRow.Grey("Playlist Root")
                },
            )
            add(
                if (favoritesTarget != null) {
                    TeleportRow.Live("Playlist Favorites") {
                        teleportToCategoryRow(GroupsFocusRequest.Group(favoritesTarget, System.nanoTime()), select = favoritesTarget)
                    }
                } else {
                    TeleportRow.Grey("Playlist Favorites")
                },
            )
            add(
                if (rootCategoryTarget != null) {
                    TeleportRow.Live("Root Category") {
                        teleportToCategoryRow(GroupsFocusRequest.Group(rootCategoryTarget, System.nanoTime()), select = rootCategoryTarget)
                    }
                } else {
                    TeleportRow.Grey("Root Category")
                },
            )
            // User request, 2026-10-05 - the bottom of the current provider's category list, the
            // mirror of Playlist Root (which lands on the top). Same Categories-row landing.
            add(
                if (lastGroupTarget != null) {
                    TeleportRow.Live("Last Channel Group (Current Provider)") {
                        teleportToCategoryRow(GroupsFocusRequest.Group(lastGroupTarget, System.nanoTime()), select = lastGroupTarget)
                    }
                } else {
                    TeleportRow.Grey("Last Channel Group (Current Provider)")
                },
            )
            // "Root Channel Group" renamed (user, 2026-09-29) - "take me back to what I'm watching."
            add(
                if (lastPlayedChannel != null) TeleportRow.Live("Now Playing") { teleportToChannel(lastPlayedChannel) }
                else TeleportRow.Grey("Now Playing"),
            )
            // Hidden when the menu was opened from fullscreen - you're already there.
            if (!teleportFromFullscreen) {
                add(
                    if (lastPlayedChannel != null) TeleportRow.Live("Return to fullscreen") { teleportToFullscreen(lastPlayedChannel) }
                    else TeleportRow.Grey("Return to fullscreen"),
                )
            }
            add(TeleportRow.Grey("Multi-View")) // no Multi-View feature yet (user, 2026-09-29)
            add(TeleportRow.Live("Exit RedSurf") { (prefsContext as? Activity)?.finish() })
        }
    }
    val lastTeleportRows = remember { arrayOfNulls<List<TeleportRow>>(1) }
    val teleportRows = if (teleportMenuOpen) {
        buildTeleportRows().also { lastTeleportRows[0] = it }
    } else {
        lastTeleportRows[0] ?: emptyList()
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

    BackHandler(enabled = !liveTvFullscreen && !teleportMenuOpen && destination == NavDestination.Home) {
        val now = SystemClock.uptimeMillis()
        if (navHasFocus && now - exitArmedAt < EXIT_CONFIRM_MS) {
            (prefsContext as? Activity)?.finish()
        } else {
            exitArmedAt = now
            runCatching { navPillFocusRequesters[NavDestination.Home]?.requestFocus() }
            showHint("Press BACK again to exit")
        }
    }

    // The safe-area padding stays on in fullscreen too: dropping it resized the whole UI on every
    // entry and exit (a full re-measure of everything); the player escapes it on its own instead
    // (LiveTvScreen's fillWindow, 2026-10-20).
    // Surf's backdrop on the calm destinations only (Crest + Surf, 2026-10-20) - never Live TV.
    val rootModifier = Modifier.fillMaxSize()
        .surfBackdrop { destination != NavDestination.LiveTv }
        .tvSafeArea()
        // Long-press Back, intercepted once here rather than in every individual screen's own
        // key router - `onPreviewKeyEvent` on this root sees every key event top-down, before
        // whatever's actually focused (Settings' rail, Live TV's channel list) gets a look at
        // it, so this works "no matter how deep" without touching any of those screens' own key
        // routers. Only ever consumes the events that make up a genuine held Back press (the
        // qualifying repeat tick, and that same press's own release) - every ordinary short Back
        // press passes through completely untouched.
        //
        // In fullscreen (since 2026-09-29/30): with Teleport on, the long-press opens the menu;
        // with it off, it's TiviMate's own long-press - leave fullscreen with playback stopped.
        // A short Back is never touched here - PlayerScreen's Back-peel owns it.
        .onPreviewKeyEvent { event ->
            val isDown = event.type == KeyEventType.KeyDown
            if (isDown) {
                lastKeyWasUp = event.key == Key.DirectionUp
                keyPresses.tryEmit(Unit)
                UserActivity.touch()
            }
            if (event.key == Key.Back) {
                if (isDown && event.nativeKeyEvent.repeatCount == 0) backHeldLong = false
                if (isDown && event.nativeKeyEvent.isLongPress && !backHeldLong) {
                    if (teleportMenuOpen) {
                        backHeldLong = true
                        return@onPreviewKeyEvent true
                    }
                    if (teleportMenuEnabled) {
                        // TELEPORT_MENU.md decision 2: with the setting on, long-press Back opens
                        // the menu instead, everywhere this gesture reaches - since 2026-09-29
                        // including fullscreen (user decision), where "Return to fullscreen" is
                        // simply left out of the list.
                        backHeldLong = true
                        teleportFromFullscreen = liveTvFullscreen
                        teleportIndex = 0
                        teleportOkArmed = false
                        teleportMenuOpen = true
                        return@onPreviewKeyEvent true
                    }
                    backHeldLong = true
                    // The other half of Teleport's discoverability (TELEPORT_MENU.md T.5): the
                    // moment the plain jump fires is when the menu's extra value is obvious.
                    if (appPreferences.hintCount(HINT_TELEPORT) < HINT_REPEATS) {
                        appPreferences.countHint(HINT_TELEPORT)
                        showHint("Tip: Settings › Remote control › Teleport Menu jumps anywhere")
                    }
                    if (liveTvFullscreen) {
                        // Setting off, fullscreen: TiviMate's own long-press (user, 2026-09-30) -
                        // back to the channel's guide row, playback stopped; OK replays it. (A
                        // short Back keeps it playing in the preview instead - LiveTvScreen.)
                        liveTvExitFullscreenStoppedRequest = System.nanoTime()
                        return@onPreviewKeyEvent true
                    }
                    if (destination == NavDestination.LiveTv && liveTvFocusedChannel != null) {
                        // TiviMate parity (user request, 2026-09-18): on Live TV, with a channel
                        // already focused/previewed, jump straight into it - reusing the exact
                        // trigger cold-launch auto-play already uses. Everywhere else, jump real
                        // D-pad focus to the pill for wherever the user actually is.
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
            }
            if (!teleportMenuOpen) {
                if (navHasFocus && destination == NavDestination.Settings && event.key == Key.DirectionDown) {
                    if (isDown) settingsRailEntryRequest = System.nanoTime()
                    return@onPreviewKeyEvent true
                }
                return@onPreviewKeyEvent false
            }
            // Menu open: every key is the menu's (nothing reaches the screen underneath, whose
            // focus stays exactly where it was).
            when (event.key) {
                Key.DirectionUp -> if (isDown) teleportIndex = nextLiveIndex(teleportRows, teleportIndex, -1)
                Key.DirectionDown -> if (isDown) teleportIndex = nextLiveIndex(teleportRows, teleportIndex, 1)
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                    if (isDown && event.nativeKeyEvent.repeatCount == 0) teleportOkArmed = true
                    if (!isDown && teleportOkArmed) {
                        teleportOkArmed = false
                        val row = teleportRows.getOrNull(teleportIndex) as? TeleportRow.Live
                        if (row != null) {
                            Log.d(TAG, "teleport select -> ${row.label}")
                            pendingTeleportAction = row.onSelect
                            teleportMenuOpen = false
                        }
                    }
                }
                Key.Back -> if (!isDown) teleportMenuOpen = false
                else -> {}
            }
            true
        }

    // Box, not a bare Column, so the Teleport Menu overlay can render on top of everything below
    // regardless of `destination` - `rootModifier` (the safe area + long-press-Back interception)
    // moves here with it; the Column inside is unchanged otherwise.
    Box(modifier = rootModifier) {
        Column(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                translationY = if (liveTvFullscreen) 0f
                else navStripPx * (navSlide.value - if (navCollapsed) 0f else 1f)
            },
        ) {
            // Kept composed through fullscreen, collapsed to zero height (performance salvage,
            // 2026-10-20): it used to be removed and rebuilt, ~75ms of the Back-from-fullscreen
            // frame (composition, text measure, the mark's bitmap decode). The player's focus trap
            // keeps focus off its pills meanwhile.
            // Collapse by measured height (children still placed, just clipped off the top),
            // so pills stay focusable while hidden - see navHidden above.
            Column(
                modifier = Modifier.clipToBounds().layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    if (placeable.height != navStripPx) navStripPx = placeable.height
                    val height = if (liveTvFullscreen || navCollapsed) 0 else placeable.height
                    layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
                },
            ) {
                traced("NavStrip") {
                    NavStrip(
                        current = destination,
                        onSelect = { destination = it },
                        focusRequesters = navPillFocusRequesters,
                        guideSyncing = guideSyncing,
                        modifier = Modifier.tracedMeasure("NavStrip:measure").onFocusChanged {
                            val arrived = it.hasFocus && !navHasFocus
                            navHasFocus = it.hasFocus
                            if (arrived && lastKeyWasUp) {
                                // After the focus change settles, never from inside it (FOCUS_MODEL.md
                                // rule 3 addendum).
                                scope.launch {
                                    delay(16)
                                    if (navHasFocus) runCatching { navPillFocusRequesters[destination]?.requestFocus() }
                                }
                            }
                        },
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Performance/state audit, 2026-09-22 (SEQUENCING.md Sprint 1) - LiveTvScreen now
            // stays composed continuously once a playlist is active, regardless of `destination`
            // (see its own `visible` param doc for the measured cost this replaces). It's
            // declared first so it's always the bottom of this Box's z-order; when hidden it's
            // zero-size anyway, so ordering only matters for the brief instant a transition is
            // still animating, if one's ever added here.
            if (activePlaylistId != null) {
                traced("LiveTvScreen") {
                    LiveTvScreen(
                        viewModel = viewModel,
                        epgTimeOffsetHours = epgTimeOffsetHours,
                        visible = destination == NavDestination.LiveTv,
                        onFullscreenChanged = { liveTvFullscreen = it },
                        onChannelsFocusChanged = { liveTvChannelsFocused = it },
                        blackScreenBetweenZaps = blackScreenBetweenZaps,
                        bufferSize = bufferSize,
                        defaultAspect = defaultAspect,
                        afrEnabled = afrEnabled,
                        previewOnSelect = previewOnSelect,
                        showRawResolution = showRawResolution,
                        doubleHeightRow = doubleHeightRow,
                        selectedGroup = liveTvSelectedGroup,
                        onSelectedGroupChanged = {
                            Log.d("AppShell", "selectedGroup <- ${it?.groupName} ${Throwable().stackTrace.getOrNull(1)?.let { f -> "${f.methodName}:${f.lineNumber}" }}")
                            liveTvSelectedGroup = it
                        },
                        focusedChannel = liveTvFocusedChannel,
                        onFocusedChannelChanged = { liveTvFocusedChannel = it },
                        autoPlayTrigger = liveTvAutoPlayTrigger,
                        onAutoPlayTriggerConsumed = { liveTvAutoPlayTrigger = false },
                        claimInitialFocusTrigger = liveTvClaimInitialFocusTrigger,
                        groupsFocusRequest = liveTvGroupsFocusRequest,
                        onGroupsFocusRequestConsumed = { liveTvGroupsFocusRequest = null },
                        exitFullscreenRequest = liveTvExitFullscreenRequest,
                        exitFullscreenStoppedRequest = liveTvExitFullscreenStoppedRequest,
                        onClaimInitialFocusTriggerConsumed = { liveTvClaimInitialFocusTrigger = false },
                        onEscapeUp = { runCatching { navPillFocusRequesters[NavDestination.LiveTv]?.requestFocus() } },
                    )
                }
            }

            // Sprint 2, 2026-09-23 (Finding 9) - permanently composed once reached, same `visible`
            // treatment as LiveTvScreen above (FOCUS_MODEL.md rule 4), not a plain conditional
            // branch inside the `when` below any more.
            run {
                val updateStatus by viewModel.updateStatus.collectAsState()
                val epgSyncStatus by viewModel.epgSyncStatus.collectAsState()
                val playlists by remember { viewModel.repository.playlists() }.collectAsState(initial = emptyList())
                val playlistSyncStatus by viewModel.playlistSyncStatus.collectAsState()
                val playlistChannelCounts by remember { viewModel.playlistChannelCounts() }.collectAsState(initial = emptyMap())
                val context = LocalContext.current
                // The EPG pane's "Last updated"/"Last attempt" rows read persisted markers -
                // refresh them whenever that category is shown, not once per process.
                LaunchedEffect(selectedSettingsCategory) {
                    if (selectedSettingsCategory == SettingsCategory.Epg) viewModel.refreshEpgSyncStatus()
                }
                traced("SettingsScreen") {
                    SettingsScreen(
                        onEscapeUp = { runCatching { navPillFocusRequesters[NavDestination.Settings]?.requestFocus() } },
                        railEntryRequest = settingsRailEntryRequest,
                        selectedCategory = selectedSettingsCategory,
                        onCategorySelected = { selectedSettingsCategory = it },
                        updateStatus = updateStatus,
                        playlists = playlists,
                        onCheckForUpdates = { viewModel.checkForUpdates(force = true) },
                        autoUpdate = autoUpdate,
                        onToggleAutoUpdate = { appPreferences.setAutoUpdate(!autoUpdate) },
                        epgSyncStatus = epgSyncStatus,
                        onUpdateEpgNow = { viewModel.updateEpgNow() },
                        onResetPlaylist = { viewModel.resetAndAddNewPlaylist() },
                        onAddPlaylist = { viewModel.beginAddPlaylist(context) },
                        onDeletePlaylist = { id -> viewModel.deletePlaylist(id) },
                        blackScreenBetweenZaps = blackScreenBetweenZaps,
                        onToggleBlackScreenBetweenZaps = {
                            appPreferences.setBlackScreenBetweenZaps(!blackScreenBetweenZaps)
                        },
                        previewOnSelect = previewOnSelect,
                        onTogglePreviewOnSelect = {
                            appPreferences.setPreviewOnSelect(!previewOnSelect)
                        },
                        bufferSize = bufferSize,
                        onCycleBufferSize = { appPreferences.cycleBufferSize() },
                        defaultAspect = defaultAspect,
                        onCycleDefaultAspect = { appPreferences.cycleDefaultAspect() },
                        afrEnabled = afrEnabled,
                        onToggleAfr = { appPreferences.setAfrEnabled(!afrEnabled) },
                        preferredAudio = preferredAudio,
                        onResetPreferredAudio = { appPreferences.clearPreferredAudioLanguage() },
                        preferredSubtitles = preferredSubtitles,
                        onResetPreferredSubtitles = { appPreferences.setPreferredSubtitleLanguage(null) },
                        showRawResolution = showRawResolution,
                        doubleHeightRow = doubleHeightRow,
                        onToggleDoubleHeightRow = { appPreferences.setDoubleHeightRow(!doubleHeightRow) },
                        publicEpgEnabled = publicEpgEnabled,
                        onTogglePublicEpg = { appPreferences.setPublicEpgEnabled(!publicEpgEnabled) },
                        epgRefresh = epgRefresh,
                        onCycleEpgRefresh = {
                            appPreferences.cycleEpgRefresh()
                            // UPDATE keeps each playlist's timer and applies the new interval.
                            playlistIds.forEach { EpgSyncScheduler.schedule(syncContext, it, runNow = false) }
                        },
                        epgTimeOffsetHours = epgTimeOffsetHours,
                        onCycleEpgTimeOffset = { appPreferences.cycleEpgTimeOffset() },
                        navAutoHideSeconds = navAutoHideSeconds,
                        onCycleNavAutoHide = { appPreferences.cycleNavAutoHide() },
                        onToggleShowRawResolution = {
                            appPreferences.setShowRawResolution(!showRawResolution)
                        },
                        autoPlayLastChannelOnLaunch = autoPlayLastChannelOnLaunch,
                        startOn = startOn,
                        onCycleStartOn = { appPreferences.cycleStartOn() },
                        onClearWatchHistory = { scope.launch { viewModel.repository.clearRecentChannels() } },
                        onToggleAutoPlayLastChannelOnLaunch = {
                            appPreferences.setAutoPlayLastChannelOnLaunch(!autoPlayLastChannelOnLaunch)
                        },
                        teleportMenuEnabled = teleportMenuEnabled,
                        onToggleTeleportMenuEnabled = {
                            appPreferences.setTeleportMenuEnabled(!teleportMenuEnabled)
                        },
                        visible = destination == NavDestination.Settings,
                        playlistChannelCounts = playlistChannelCounts,
                        playlistSyncStatus = playlistSyncStatus,
                        onRenamePlaylist = { id, name -> viewModel.renamePlaylist(id, name) },
                        onRefreshPlaylist = { id -> viewModel.refreshPlaylistChannels(id) },
                        onUpdatePlaylistEpg = { id -> viewModel.updatePlaylistEpg(id) },
                    )
                }
            }

            // One call site, always reached when destination == LiveTv, regardless of
            // liveTvFullscreen - see the class doc above for why that matters.
            when {
                // LIVE_TV_GUIDE_MERGE.md M.1 - Live TV and Guide are the same real screen now,
                // not two entry points into one composable (PHASE_3.md decision 3's `guideMode`
                // toggle is gone) - there is exactly one NavDestination reaching it. Rendered
                // above now (see comment there); nothing left to do for this branch here.
                destination == NavDestination.LiveTv && activePlaylistId != null -> {}
                destination == NavDestination.LiveTv ->
                    PlaceholderScreen("Live TV", "No active playlist")
                // Rendered unconditionally above now (see comment there); nothing left to do here.
                destination == NavDestination.Settings -> {}
                else ->
                    PlaceholderScreen(destination.label, "Coming in a later phase")
            }
        }

        HintChip(
            text = if (liveTvFullscreen && !teleportMenuOpen) null else hintText,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
        )

        traced("TeleportMenu") {
            TeleportMenu(
                visible = teleportMenuOpen,
                rows = teleportRows,
                selectedIndex = teleportIndex,
                onClosed = {
                    val action = pendingTeleportAction
                    pendingTeleportAction = null
                    action?.invoke()
                },
            )
        }
    }
}
