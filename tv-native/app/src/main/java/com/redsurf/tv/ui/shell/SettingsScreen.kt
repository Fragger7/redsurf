package com.redsurf.tv.ui.shell

import com.redsurf.tv.ui.livetv.formatGroupName
import androidx.compose.ui.graphics.RectangleShape
import com.redsurf.tv.settings.BufferSize
import com.redsurf.tv.settings.DefaultAspect
import com.redsurf.tv.settings.StartOn
import com.redsurf.tv.settings.EpgRefresh
import android.util.Log
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import java.io.File
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.redsurf.tv.BuildConfig
import com.redsurf.tv.R
import com.redsurf.tv.EpgSyncStatus
import com.redsurf.tv.UpdateCheckStatus
import com.redsurf.tv.db.PlaylistEntity
import com.redsurf.tv.ui.theme.hiddenButComposed
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.CrestButton
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.RedSurfType
import com.redsurf.tv.ui.theme.Surface as SurfaceColor
import com.redsurf.tv.ui.theme.SurfaceRaised
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState

private const val TAG = "SettingsScreen"

/**
 * The end-state Settings shell (`docs/plans/SETTINGS.md`, built 2026-09-13 sprint). One-line
 * design: *TiviMate decides what's there and where; StreamVault decides what it looks like.*
 * Two panes under the existing nav strip: a category rail (left, [CategoryRail]) and a settings
 * list (right, [SettingsPane]) - the same `GroupsColumn`/`ChannelsColumn` skeleton Live TV
 * already uses, not a new component family.
 *
 * Only Playlists and About have real content today ([SettingsPane]'s `when`); every other
 * category renders entirely from [SETTINGS_GREY_ROWS] - real names, planned defaults, never
 * wrapped in a focusable `Surface` so the D-pad skips them and a tester can never land on
 * fiction (the brief's "never claim it works" applied to UI).
 *
 * Escape guard: `NavStrip` is a composed sibling one level up in `AppShell`'s `Column`, so
 * UP/DOWN/RIGHT with nowhere to go inside this screen (rail top/bottom, or RIGHT into a
 * zero-live-row category) would otherwise escape into it via Compose's whole-composition
 * directional search - the same bug class `PlayerScreen.kt` hit on 2026-09-12, though the fix
 * here ended up explicit key interception rather than `focusProperties { exit = ... }`, which
 * turned out to behave inconsistently by direction in this layout - see the guard's own comment
 * for what was actually observed.
 *
 * Back peels one layer, mirroring `PlayerScreen`'s own pattern: from the pane back to the rail,
 * only then falling through to `AppShell`'s own Home-jump `BackHandler` (mutually exclusive via
 * `enabled`, same mechanism `AppShell`'s doc comment already describes for `LiveTvScreen`).
 *
 * [selectedCategory]/[onCategorySelected] are hoisted to `AppShell` (state/focus discipline,
 * `AGENTS.md`) - leaving Settings for another destination and coming back must land on the same
 * category, and this composable is torn down and recomposed fresh on every re-entry (the
 * conditional-composition trap `AppShell.kt` already documents for `LiveTvScreen`/fullscreen), so
 * a `remember` here alone would reset to [SettingsCategory.General] every time.
 */
@Composable
fun SettingsScreen(
    selectedCategory: SettingsCategory,
    onCategorySelected: (SettingsCategory) -> Unit,
    updateStatus: UpdateCheckStatus,
    playlists: List<PlaylistEntity>,
    onCheckForUpdates: () -> Unit,
    autoUpdate: Boolean,
    onToggleAutoUpdate: () -> Unit,
    // 2026-09-21 - Settings → EPG's live rows, owned by MainViewModel like updateStatus.
    epgSyncStatus: EpgSyncStatus,
    onUpdateEpgNow: () -> Unit,
    onResetPlaylist: () -> Unit,
    onAddPlaylist: () -> Unit,
    onDeletePlaylist: (String) -> Unit,
    // BACKLOG_SWEEP.md #10/#11/#12 - AppPreferences-backed, owned by AppShell.
    blackScreenBetweenZaps: Boolean,
    onToggleBlackScreenBetweenZaps: () -> Unit,
    // PREVIEW.md, 2026-09-20 - same AppPreferences pattern as the row above.
    previewOnSelect: Boolean,
    onTogglePreviewOnSelect: () -> Unit,
    bufferSize: BufferSize,
    onSetBufferSize: (BufferSize) -> Unit,
    forceStereo: Boolean,
    onToggleForceStereo: () -> Unit,
    defaultAspect: DefaultAspect,
    onCycleDefaultAspect: () -> Unit,
    afrEnabled: Boolean,
    onToggleAfr: () -> Unit,
    preferredAudio: String?,
    onResetPreferredAudio: () -> Unit,
    preferredSubtitles: String?,
    onResetPreferredSubtitles: () -> Unit,
    showRawResolution: Boolean,
    onToggleShowRawResolution: () -> Unit,
    doubleHeightRow: Boolean = true,
    onToggleDoubleHeightRow: () -> Unit = {},
    publicEpgEnabled: Boolean = true,
    onTogglePublicEpg: () -> Unit = {},
    epgRefresh: EpgRefresh = EpgRefresh.Daily,
    epgTimeOffsetHours: Int = 0,
    onCycleEpgTimeOffset: () -> Unit = {},
    onCycleEpgRefresh: () -> Unit = {},
    navAutoHideSeconds: Int = 5,
    onSetNavAutoHide: (Int) -> Unit = {},
    // AGENTS.md backlog, 2026-09-15 - same AppPreferences pattern as the two above.
    autoPlayLastChannelOnLaunch: Boolean,
    onToggleAutoPlayLastChannelOnLaunch: () -> Unit,
    startOn: StartOn,
    onCycleStartOn: () -> Unit,
    onClearWatchHistory: () -> Unit,
    // Parental "Hidden categories": every key hidden at any point this session (so a row never
    // disappears under focus when unhidden), which of them are hidden now, and the toggle.
    hiddenGroupRows: List<String> = emptyList(),
    hiddenGroups: Set<String> = emptySet(),
    onToggleGroupHidden: (String) -> Unit = {},
    // Parental "PIN": whether one is set, a checker, and set/clear (null clears).
    parentalPinSet: Boolean = false,
    checkParentalPin: (String) -> Boolean = { true },
    onSetParentalPin: (String?) -> Unit = {},
    // TELEPORT_MENU.md decision 1, 2026-09-19 - same AppPreferences pattern, the first live row
    // Remote control has ever had.
    teleportMenuEnabled: Boolean,
    onToggleTeleportMenuEnabled: () -> Unit,
    digitKeysEnabled: Boolean = true,
    onToggleDigitKeys: () -> Unit = {},
    longPressOkLabel: String = "Context menu",
    onCycleLongPressOk: () -> Unit = {},
    // Sprint 2, 2026-09-23 (Finding 9, FOCUS_MODEL.md rule 4) - AppShell used to remove this whole
    // composable from composition on every destination switch away from Settings, the same
    // conditional-composition trap Sprint 1 already fixed for LiveTvScreen. `selectedSettingsCategory`
    // survives that (already hoisted to AppShell), and Settings has no expensive async state to
    // lose the way Live TV's grid did - but the rail's own re-entry claim below was still a
    // single-shot `runCatching` with no retry, the identical reliability gap as LiveTvScreen's
    // fullscreen-exit bug (Finding 5), just never reported live because a 9-row rail composes fast.
    // Now permanently composed once reached; `visible` swaps real layout for zero size instead.
    visible: Boolean = true,
    // UP from the rail's top row: AppShell lands focus on the Settings pill itself (the canonical
    // "current destination's pill", same as Live TV's Categories) - default spatial search
    // picked whichever pill sat nearest, which is "Home" in this layout (found live 2026-09-30).
    onEscapeUp: () -> Unit = {},
    // DOWN from the Settings pill (AppShell sends a new nonce): land on the selected category's
    // rail row - spatial search dropped into the pane, which sits directly under that pill.
    railEntryRequest: Long = 0L,
    // Settings → Playlists → <playlist> (TiviMate's per-playlist page), owned by MainViewModel.
    playlistChannelCounts: Map<String, Int> = emptyMap(),
    playlistSyncStatus: Map<String, String> = emptyMap(),
    onRenamePlaylist: (String, String) -> Unit = { _, _ -> },
    onSavePlaylistConnection: suspend (String, String, String, String) -> String? = { _, _, _, _ -> null },
    loadPlaylistAccount: suspend (String) -> com.redsurf.tv.vod.XtreamUserInfo? = { null },
    onRefreshPlaylist: (String) -> Unit = {},
    onUpdatePlaylistEpg: (String) -> Unit = {},
) {
    val railFocus = remember { FocusRequester() }
    // A playlist's own page is open in the pane - Back closes it (the pane's own BackHandler)
    // instead of jumping to the rail.
    var playlistDetailOpen by remember { mutableStateOf(false) }
    val paneFirstRowFocus = remember { FocusRequester() }
    var focusInPane by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Initial focus on entry/re-entry: the rail's currently-selected row, never wherever Compose
    // would otherwise default to (state/focus discipline) - same pattern as GroupsColumn/
    // PlayerScreen's own initial-focus effects. Keyed on [visible] itself, not `Unit` - now that
    // this composable stays permanently alive, `Unit` would only ever fire once, on first entry,
    // and never again on a later re-entry. `LaunchedEffect` only re-runs its body when the key's
    // *value* changes, so this fires exactly on the false->true edge (a genuine re-entry) and does
    // nothing on true->false (leaving) - the same "key on the event" idiom `LiveTvScreen`'s own
    // grid-entry claim uses (FOCUS_MODEL.md rule 5). Bounded retry, not a single attempt (rule 3).
    // 2026-09-30: success = the rail actually holds focus afterwards, not "requestFocus() didn't
    // throw" - found live that choosing the Settings pill left focus on the pill (the request on
    // a just-shown, not-yet-placed rail silently did nothing and the old check called it done).
    val railListState = rememberLazyListState()
    var railHasFocus by remember { mutableStateOf(false) }
    suspend fun claimRail() {
        repeat(10) { attempt ->
            delay(100)
            val index = SettingsCategory.entries.indexOf(selectedCategory)
            if (railListState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                runCatching { railListState.scrollToItem(index) }
            }
            runCatching { railFocus.requestFocus() }
            delay(40)
            if (railHasFocus) {
                Log.d(TAG, "settings entry focus -> rail $selectedCategory attempt=$attempt")
                return
            }
        }
        Log.w(TAG, "settings entry focus gave up")
    }
    LaunchedEffect(visible) {
        if (visible) claimRail()
    }
    LaunchedEffect(railEntryRequest) {
        if (railEntryRequest != 0L && visible) claimRail()
    }

    LaunchedEffect(selectedCategory) {
        Log.d(TAG, "settings -> $selectedCategory")
    }
    LaunchedEffect(focusInPane) {
        Log.d(TAG, "focus -> ${if (focusInPane) "pane" else "rail"}")
    }

    // FocusManager.moveFocus(Left) - the same default search RIGHT already relies on below to
    // cross from the rail into the pane, run in the opposite direction - not
    // `railFocus.requestFocus()`. Found live, 2026-09-13: an explicit `requestFocus()` targeting
    // a node outside the currently-focused node's immediate focus branch turned out to silently
    // do nothing once anything resembling a focus-group boundary sits between them (no exception,
    // focus just doesn't move) - a real Compose quirk this session hit twice trying to use it for
    // exactly this rail-return case (see [SettingsPane]'s doc comment for the fuller account).
    // Plain arrow-key-driven `moveFocus()` never has this problem, so Back uses it too.
    BackHandler(enabled = focusInPane && !playlistDetailOpen) {
        focusManager.moveFocus(FocusDirection.Left)
    }

    // Whether the current category has any real, focusable content in its pane - drives the
    // explicit escape guard below. Playlists and About always have at least one live row (Add
    // playlist / Check for updates) even with nothing else in them; every other category is
    // entirely [SETTINGS_GREY_ROWS] today.
    // Playback and Appearance joined this list (BACKLOG_SWEEP.md #11/#12) - each now has exactly
    // one live toggle row alongside its grey ones.
    // General joined this list too (AGENTS.md backlog, 2026-09-15) - "Resume last channel on
    // launch" is now its one live toggle row.
    // Remote control joined this list (TELEPORT_MENU.md decision 1, 2026-09-19) - "Teleport Menu"
    // is now its one live toggle row, same layering as every other category's live-rows-then-
    // grey-rows shape.
    // EPG joined this list (2026-09-21) - "Update EPG now" is its live action row.
    val hasLiveContent = selectedCategory == SettingsCategory.Playlists ||
        selectedCategory == SettingsCategory.About ||
        selectedCategory == SettingsCategory.Epg ||
        selectedCategory == SettingsCategory.Playback ||
        selectedCategory == SettingsCategory.Appearance ||
        selectedCategory == SettingsCategory.General ||
        selectedCategory == SettingsCategory.RemoteControl ||
        selectedCategory == SettingsCategory.Other ||
        selectedCategory == SettingsCategory.ParentalControls

    Row(
        modifier = Modifier.hiddenButComposed(visible).fillMaxSize()
            // Escape guard, explicit rather than `focusProperties { exit = ... }` - found live,
            // 2026-09-13: `exit` turned out to behave inconsistently by direction in this exact
            // layout (blocking LEFT even where it needed to work, while *not* reliably blocking
            // RIGHT/UP/DOWN where it needed to) rather than being a clean "block anything leaving
            // this subtree" - not worth chasing further given `PlayerScreen.kt`'s onKeyEvent
            // router already sets the precedent that explicit interception beats relying on
            // Compose's focus-search internals for exactly this "don't let a key escape this
            // screen" job. NavStrip is a composed sibling one level up in AppShell's Column, so
            // either of these two, left unguarded, can land real D-pad focus on a nav pill instead
            // of leaving it inside Settings: DOWN past the rail's last row, or RIGHT into a
            // category with nothing focusable in its pane (confirmed live for both). Only fires
            // while focus is on the rail (`!focusInPane`) - once inside the pane, its own content
            // and `LazyColumn` handle their own UP/DOWN normally.
            //
            // UP at the rail's top row ("General") is deliberately NOT guarded (BACKLOG_SWEEP.md
            // #3, found live 2026-09-14/15) - the pane side of this same row was never guarded and
            // already let UP escape to the Settings NavStrip pill correctly; the rail side blocked
            // it for no functional reason (there's nothing above "General" to protect), so the
            // asymmetry was just a bug, not an intentional guard. Dropped. **Revised 2026-09-30:**
            // UP there now goes explicitly to the Settings pill via [onEscapeUp] - the default
            // escape did work, but landed on the spatially-nearest pill ("Home"), not Settings.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || focusInPane) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> (selectedCategory == SettingsCategory.entries.first()).also { atTop ->
                        if (atTop) onEscapeUp()
                    }
                    Key.DirectionDown -> selectedCategory == SettingsCategory.entries.last()
                    // Deterministic entry (FOCUS_MODEL.md): straight to the pane's first row. Left
                    // to directional search, RIGHT from the rail landed on the nav strip's Live TV
                    // pill after the Compose 1.11 upgrade (device-verified 2026-10-20, every row).
                    Key.DirectionRight -> if (!hasLiveContent) true else runCatching { paneFirstRowFocus.requestFocus() }.isSuccess
                    else -> false
                }
            },
    ) {
        CategoryRail(
            selected = selectedCategory,
            onFocused = onCategorySelected,
            railFocus = railFocus,
            listState = railListState,
            onRailFocusChanged = { railHasFocus = it },
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(20.dp))
        SettingsPane(
            visible = visible,
            category = selectedCategory,
            playlists = playlists,
            updateStatus = updateStatus,
            onCheckForUpdates = onCheckForUpdates,
            autoUpdate = autoUpdate,
            onToggleAutoUpdate = onToggleAutoUpdate,
            epgSyncStatus = epgSyncStatus,
            onUpdateEpgNow = onUpdateEpgNow,
            onResetPlaylist = onResetPlaylist,
            onAddPlaylist = onAddPlaylist,
            onDeletePlaylist = onDeletePlaylist,
            blackScreenBetweenZaps = blackScreenBetweenZaps,
            onToggleBlackScreenBetweenZaps = onToggleBlackScreenBetweenZaps,
            previewOnSelect = previewOnSelect,
            onTogglePreviewOnSelect = onTogglePreviewOnSelect,
            bufferSize = bufferSize,
            onSetBufferSize = onSetBufferSize,
            forceStereo = forceStereo,
            onToggleForceStereo = onToggleForceStereo,
            defaultAspect = defaultAspect,
            onCycleDefaultAspect = onCycleDefaultAspect,
            afrEnabled = afrEnabled,
            onToggleAfr = onToggleAfr,
            preferredAudio = preferredAudio,
            onResetPreferredAudio = onResetPreferredAudio,
            preferredSubtitles = preferredSubtitles,
            onResetPreferredSubtitles = onResetPreferredSubtitles,
            showRawResolution = showRawResolution,
            onToggleShowRawResolution = onToggleShowRawResolution,
            doubleHeightRow = doubleHeightRow,
            onToggleDoubleHeightRow = onToggleDoubleHeightRow,
            publicEpgEnabled = publicEpgEnabled,
            onTogglePublicEpg = onTogglePublicEpg,
            epgRefresh = epgRefresh,
            epgTimeOffsetHours = epgTimeOffsetHours,
            onCycleEpgTimeOffset = onCycleEpgTimeOffset,
            onCycleEpgRefresh = onCycleEpgRefresh,
            navAutoHideSeconds = navAutoHideSeconds,
            onSetNavAutoHide = onSetNavAutoHide,
            autoPlayLastChannelOnLaunch = autoPlayLastChannelOnLaunch,
            onToggleAutoPlayLastChannelOnLaunch = onToggleAutoPlayLastChannelOnLaunch,
            startOn = startOn,
            onCycleStartOn = onCycleStartOn,
            onClearWatchHistory = onClearWatchHistory,
            hiddenGroupRows = hiddenGroupRows,
            hiddenGroups = hiddenGroups,
            onToggleGroupHidden = onToggleGroupHidden,
            parentalPinSet = parentalPinSet,
            checkParentalPin = checkParentalPin,
            onSetParentalPin = onSetParentalPin,
            teleportMenuEnabled = teleportMenuEnabled,
            onToggleTeleportMenuEnabled = onToggleTeleportMenuEnabled,
            digitKeysEnabled = digitKeysEnabled,
            onToggleDigitKeys = onToggleDigitKeys,
            longPressOkLabel = longPressOkLabel,
            onCycleLongPressOk = onCycleLongPressOk,
            playlistChannelCounts = playlistChannelCounts,
            playlistSyncStatus = playlistSyncStatus,
            onRenamePlaylist = onRenamePlaylist,
            onSavePlaylistConnection = onSavePlaylistConnection,
            loadPlaylistAccount = loadPlaylistAccount,
            onRefreshPlaylist = onRefreshPlaylist,
            onUpdatePlaylistEpg = onUpdatePlaylistEpg,
            onDetailOpenChanged = { playlistDetailOpen = it },
            firstRowFocus = paneFirstRowFocus,
            onFocusChanged = { focusInPane = it },
            modifier = Modifier.weight(1.8f),
        )
    }
}

/**
 * Left pane: nine fixed rows (no paging needed), each a [CategoryTile] (initial letter on a
 * tinted tile - the exact same visual language as `ChannelsColumn`'s `ChannelLogo`/`PlayerScreen`'s
 * `ChannelTile`, reused rather than inventing a new motif) plus the category name. Focus, not OK,
 * selects - identical convention to `GroupsColumn`'s category rows.
 *
 * `hadFocus`/the `onFocusChanged` redirect below is [SettingsPane]'s own rail-entry-redirect
 * pattern, mirrored (BACKLOG_SWEEP.md #5): once LEFT/Back's default `moveFocus` has already
 * landed focus *somewhere* on the rail (`state.hasFocus` flips false->true), redirect to
 * [railFocus] - the requester already tied to [selected]'s own row - so the row you get back is
 * always the category actually selected, not whatever was spatially nearest to where you left
 * the pane. Redirecting only *after* the crossing, never during it, is the important part: an
 * earlier attempt mid-sprint-1 to call `requestFocus()` directly from the pane's LEFT handler hit
 * the cross-branch `requestFocus()` bug documented on [SettingsPane] and silently did nothing.
 * This redirect never crosses branches - by the time it fires, focus is already inside the rail's
 * own subtree - so it doesn't hit that bug.
 */
@Composable
private fun CategoryRail(
    selected: SettingsCategory,
    onFocused: (SettingsCategory) -> Unit,
    railFocus: FocusRequester,
    listState: LazyListState,
    onRailFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 2026-09-30 rebuild of the BACKLOG_SWEEP.md #5 redirect, found regressed live: LEFT from a
    // pane landed on the spatially-nearest rail row. Two flaws in the old version: (1) the row
    // focus landed on *selected itself* the instant it got focus (focus selects), so the pane
    // switched away from the category you were in; (2) the redirect called requestFocus() from
    // inside the focus-change callback and never checked it landed (FOCUS_MODEL.md rule 3
    // addendum). Now: the row that receives focus *from outside the rail* doesn't select itself;
    // the redirect runs after the focus change, scrolls the target in, and retries until the
    // selected row really reports focus. [focusedRow] is null whenever the rail doesn't hold
    // focus, which is what identifies "arriving from outside" without depending on whether the
    // row's or the rail's own onFocusChanged fires first.
    var focusedRow by remember { mutableStateOf<SettingsCategory?>(null) }
    val scope = rememberCoroutineScope()
    fun redirectToSelected(landed: SettingsCategory) {
        scope.launch {
            repeat(10) { attempt ->
                if (focusedRow == selected) {
                    Log.d(TAG, "rail entry landed on $selected (via $landed) attempt=$attempt")
                    return@launch
                }
                if (focusedRow == null) return@launch // focus left the rail meanwhile
                val index = SettingsCategory.entries.indexOf(selected)
                if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                    runCatching { listState.scrollToItem(index) }
                }
                delay(30)
                runCatching { railFocus.requestFocus() }
                delay(40)
            }
            Log.w(TAG, "rail entry redirect gave up target=$selected focused=$focusedRow")
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(SurfaceColor, RectangleShape)
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .onFocusChanged { state ->
                if (!state.hasFocus) focusedRow = null
                onRailFocusChanged(state.hasFocus)
            },
    ) {
        Text(
            "Settings",
            style = RedSurfType.sectionTitle,
            color = TextPrimary,
            modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
        )
        // LazyColumn, not a plain Column (found live, BACKLOG_SWEEP.md #2) - nine fixed rows
        // never needed paging, but a plain Column also never scrolls, so "About" (the last row)
        // was clipped by the card's fixed height with no way to bring it into view. Every other
        // real list in the app (GroupsColumn, ChannelsColumn, SettingsPane itself) already uses
        // LazyColumn specifically because it scrolls to keep focus on-screen for free.
        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(SettingsCategory.entries, key = { it.name }) { category ->
                CategoryRow(
                    category = category,
                    selected = category == selected,
                    onFocused = {
                        val enteringFromOutside = focusedRow == null
                        focusedRow = category
                        if (enteringFromOutside && category != selected) {
                            redirectToSelected(category)
                        } else {
                            onFocused(category)
                        }
                    },
                    modifier = if (category == selected) Modifier.focusRequester(railFocus) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: SettingsCategory,
    selected: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = {},
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = RedSurfFocus.shape(8.dp),
        colors = RedSurfFocus.rowColors(selected = selected),
        scale = RedSurfFocus.scale(),
        border = RedSurfFocus.border(),
        glow = RedSurfFocus.glow(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryTile(category.iconRes)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                category.label,
                style = RedSurfType.rowTitle,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Real branded icon (AGENTS.md Branding entry, 2026-09-16), replacing the old initial-letter
 * tile - see [SettingsCategory.iconRes] for which icon and why. */
@Composable
private fun CategoryTile(@androidx.annotation.DrawableRes iconRes: Int) {
    Box(
        modifier = Modifier.size(28.dp).clip(RectangleShape).background(SurfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Right pane: header + a `LazyColumn` of rows, same skeleton as `ChannelsColumn`. Playlists and
 * About get real, bespoke content; every other category is [SETTINGS_GREY_ROWS] verbatim.
 *
 * `firstRowFocus`/the `onFocusChanged` entry-redirect below is the same fix `ChannelsColumn.kt`
 * already applies for LEFT/RIGHT column re-entry: without it, RIGHT from the rail lands wherever
 * Compose's default directional search resolves to (whatever's roughly parallel), not
 * necessarily the pane's first live row - `SETTINGS.md`'s acceptance criterion #3 requires the
 * latter, not left as the unengineered default the way Live TV's own Categories->Channels entry
 * still is (`AGENTS.md` backlog).
 *
 * LEFT back to the rail is deliberately left to Compose's own default directional search (not
 * intercepted here) - found live, 2026-09-13, chasing a LEFT-does-nothing bug: an explicit
 * `FocusRequester.requestFocus()` call targeting the rail silently failed (no exception, no
 * move) once the screen's focus trap was in place, apparently because `requestFocus()`'s own
 * resolution consults the same `exit` property a directional search does and unconditionally
 * cancels it - even for a destination still inside the trapped root. Plain arrow-key `moveFocus`
 * does not hit this (confirmed: RIGHT crossing this exact rail<->pane boundary already worked
 * before this fix existed), so LEFT is left alone to use it too, same as `SettingsScreen`'s own
 * `BackHandler` does explicitly via `FocusManager.moveFocus`.
 */
@Composable
private fun SettingsPane(
    visible: Boolean,
    category: SettingsCategory,
    playlists: List<PlaylistEntity>,
    updateStatus: UpdateCheckStatus,
    onCheckForUpdates: () -> Unit,
    autoUpdate: Boolean,
    onToggleAutoUpdate: () -> Unit,
    epgSyncStatus: EpgSyncStatus,
    onUpdateEpgNow: () -> Unit,
    onResetPlaylist: () -> Unit,
    onAddPlaylist: () -> Unit,
    onDeletePlaylist: (String) -> Unit,
    blackScreenBetweenZaps: Boolean,
    onToggleBlackScreenBetweenZaps: () -> Unit,
    // PREVIEW.md, 2026-09-20 - same AppPreferences pattern as the row above.
    previewOnSelect: Boolean,
    onTogglePreviewOnSelect: () -> Unit,
    bufferSize: BufferSize,
    onSetBufferSize: (BufferSize) -> Unit,
    forceStereo: Boolean,
    onToggleForceStereo: () -> Unit,
    defaultAspect: DefaultAspect,
    onCycleDefaultAspect: () -> Unit,
    afrEnabled: Boolean,
    onToggleAfr: () -> Unit,
    preferredAudio: String?,
    onResetPreferredAudio: () -> Unit,
    preferredSubtitles: String?,
    onResetPreferredSubtitles: () -> Unit,
    showRawResolution: Boolean,
    onToggleShowRawResolution: () -> Unit,
    doubleHeightRow: Boolean = true,
    onToggleDoubleHeightRow: () -> Unit = {},
    publicEpgEnabled: Boolean = true,
    onTogglePublicEpg: () -> Unit = {},
    epgRefresh: EpgRefresh = EpgRefresh.Daily,
    epgTimeOffsetHours: Int = 0,
    onCycleEpgTimeOffset: () -> Unit = {},
    onCycleEpgRefresh: () -> Unit = {},
    navAutoHideSeconds: Int = 5,
    onSetNavAutoHide: (Int) -> Unit = {},
    autoPlayLastChannelOnLaunch: Boolean,
    onToggleAutoPlayLastChannelOnLaunch: () -> Unit,
    startOn: StartOn,
    onCycleStartOn: () -> Unit,
    onClearWatchHistory: () -> Unit,
    // Parental "Hidden categories": every key hidden at any point this session (so a row never
    // disappears under focus when unhidden), which of them are hidden now, and the toggle.
    hiddenGroupRows: List<String> = emptyList(),
    hiddenGroups: Set<String> = emptySet(),
    onToggleGroupHidden: (String) -> Unit = {},
    // Parental "PIN": whether one is set, a checker, and set/clear (null clears).
    parentalPinSet: Boolean = false,
    checkParentalPin: (String) -> Boolean = { true },
    onSetParentalPin: (String?) -> Unit = {},
    teleportMenuEnabled: Boolean,
    onToggleTeleportMenuEnabled: () -> Unit,
    digitKeysEnabled: Boolean = true,
    onToggleDigitKeys: () -> Unit = {},
    longPressOkLabel: String = "Context menu",
    onCycleLongPressOk: () -> Unit = {},
    playlistChannelCounts: Map<String, Int>,
    playlistSyncStatus: Map<String, String>,
    onRenamePlaylist: (String, String) -> Unit,
    onSavePlaylistConnection: suspend (String, String, String, String) -> String?,
    loadPlaylistAccount: suspend (String) -> com.redsurf.tv.vod.XtreamUserInfo?,
    onRefreshPlaylist: (String) -> Unit,
    onUpdatePlaylistEpg: (String) -> Unit,
    onDetailOpenChanged: (Boolean) -> Unit,
    firstRowFocus: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hadFocus by remember { mutableStateOf(false) }
    // Where focus goes *before* a real playlist delete removes the focused block from the list -
    // see PlaylistBlock's "Confirm remove" handler.
    val addPlaylistFocus = remember { FocusRequester() }

    // Playlists → <playlist>: a third level, drilled into from the list (TiviMate's per-playlist
    // page). FOCUS_MODEL.md choreography - a focused node is never structurally removed or
    // unplaced. Open: the page composes *over* the still-placed list (list at alpha 0), focus
    // moves onto the page's first row (bounded retry), and only then is the list unplaced.
    // Close: the list is placed again under the page, focus moves back onto the exact row that
    // opened it (deterministic return, bounded retry - its lazy item recomposes first), and only
    // then is the page removed. A delete closes onto "Add another playlist" and deletes after.
    var detailId by remember { mutableStateOf<String?>(null) }
    var listHidden by remember { mutableStateOf(false) }
    var closeTarget by remember { mutableStateOf<FocusRequester?>(null) }
    var afterClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    var detailHasFocus by remember { mutableStateOf(false) }
    var listHasFocus by remember { mutableStateOf(false) }
    val detailFirstFocus = remember { FocusRequester() }
    val playlistRowFocus = remember { mutableMapOf<String, FocusRequester>() }
    val closeDetail: (FocusRequester?, (() -> Unit)?) -> Unit = { target, then ->
        if (detailId != null && closeTarget == null) {
            listHidden = false
            afterClose = then
            closeTarget = target ?: firstRowFocus
        }
    }
    val resetDetail = {
        detailId = null
        listHidden = false
        closeTarget = null
        afterClose = null
    }
    LaunchedEffect(detailId != null) { onDetailOpenChanged(detailId != null) }
    LaunchedEffect(detailId) {
        if (detailId == null || closeTarget != null) return@LaunchedEffect
        repeat(20) {
            runCatching { detailFirstFocus.requestFocus() }
            delay(50)
            if (detailHasFocus) {
                listHidden = true
                return@LaunchedEffect
            }
            delay(100)
        }
        Log.w(TAG, "playlist page never got focus - back to the list")
        resetDetail() // never got focus - stay on the list rather than strand the user
    }
    LaunchedEffect(closeTarget) {
        val target = closeTarget ?: return@LaunchedEffect
        for (attempt in 0 until 20) {
            runCatching { target.requestFocus() }
            delay(50)
            if (listHasFocus) break
            delay(100)
        }
        val then = afterClose
        resetDetail()
        then?.invoke()
    }
    // LEFT out of a playlist's page lands on the rail; the page doesn't wait there for a return
    // - the list is back. Debounced: the open handoff (list -> page) briefly reports no focus in
    // the pane, and an immediate reset there closed the page the moment it opened.
    var paneHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(paneHasFocus) {
        if (paneHasFocus) return@LaunchedEffect
        delay(250)
        if (detailId != null && closeTarget == null) {
            Log.d(TAG, "playlist page reset: focus left the pane")
            resetDetail()
        }
    }
    LaunchedEffect(category) { if (category != SettingsCategory.Playlists) resetDetail() }
    // Parental PIN: one unlock lasts until the pane leaves Parental controls or Settings.
    var pinPrompt by remember { mutableStateOf<PinPrompt?>(null) }
    var parentalUnlocked by remember { mutableStateOf(false) }
    // Hidden screens stay composed, so leaving Settings must close the prompt and relock by hand.
    LaunchedEffect(category, visible) {
        if (category != SettingsCategory.ParentalControls || !visible) {
            parentalUnlocked = false
            pinPrompt = null
        }
    }
    pinPrompt?.let { prompt ->
        PinDialog(
            title = when (prompt) {
                PinPrompt.Create -> "Set a PIN"
                PinPrompt.Remove -> "Enter PIN to turn it off"
                is PinPrompt.Unlock -> "Enter PIN"
            },
            confirmNew = prompt == PinPrompt.Create,
            check = if (prompt == PinPrompt.Create) null else checkParentalPin,
            onDone = { pin ->
                pinPrompt = null
                when (prompt) {
                    PinPrompt.Create -> onSetParentalPin(pin)
                    PinPrompt.Remove -> onSetParentalPin(null)
                    is PinPrompt.Unlock -> {
                        parentalUnlocked = true
                        onToggleGroupHidden(prompt.key)
                    }
                }
            },
            onDismiss = { pinPrompt = null },
        )
    }
    LaunchedEffect(playlists, detailId) {
        if (detailId != null && closeTarget == null && playlists.none { it.id == detailId }) resetDetail()
    }
    BackHandler(enabled = detailId != null && closeTarget == null) {
        closeDetail(detailId?.let { playlistRowFocus[it] }, null)
    }
    val openPlaylist = playlists.firstOrNull { it.id == detailId }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(top = 14.dp)
            .onFocusChanged { state ->
                // Not while a playlist's page is open: moving focus from the list onto the page
                // reports a transient pane focus loss (found live, v0.44.22), and redirecting
                // here bounced focus back onto the list's first row.
                if (state.hasFocus && !hadFocus && detailId == null) {
                    runCatching { firstRowFocus.requestFocus() }
                }
                paneHasFocus = state.hasFocus
                hadFocus = state.hasFocus
                onFocusChanged(state.hasFocus)
            },
    ) {
        Text(
            if (openPlaylist != null) "${category.label}  ›  ${openPlaylist.name}" else category.label,
            style = RedSurfType.sectionTitle,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .hiddenButComposed(!listHidden)
                .graphicsLayer { alpha = if (detailId != null && closeTarget == null) 0f else 1f }
                .onFocusChanged { listHasFocus = it.hasFocus },
        ) {
            when (category) {
                SettingsCategory.General -> generalContent(
                    autoPlayLastChannelOnLaunch = autoPlayLastChannelOnLaunch,
                    onToggle = onToggleAutoPlayLastChannelOnLaunch,
                    startOn = startOn,
                    onCycleStartOn = onCycleStartOn,
                    firstRowFocus = firstRowFocus,
                )
                SettingsCategory.Playlists -> playlistsContent(
                    playlists = playlists,
                    channelCounts = playlistChannelCounts,
                    onOpen = { id -> if (detailId == null) detailId = id },
                    rowFocus = { id -> playlistRowFocus.getOrPut(id) { FocusRequester() } },
                    onAddPlaylist = onAddPlaylist,
                    onResetPlaylist = onResetPlaylist,
                    firstRowFocus = firstRowFocus,
                    addPlaylistFocus = addPlaylistFocus,
                )
                SettingsCategory.About -> aboutContent(
                    updateStatus = updateStatus,
                    onCheckForUpdates = onCheckForUpdates,
                    autoUpdate = autoUpdate,
                    onToggleAutoUpdate = onToggleAutoUpdate,
                    firstRowFocus = firstRowFocus,
                )
                SettingsCategory.Epg -> epgContent(
                    status = epgSyncStatus,
                    onUpdateNow = onUpdateEpgNow,
                    firstRowFocus = firstRowFocus,
                    publicEpgEnabled = publicEpgEnabled,
                    onTogglePublicEpg = onTogglePublicEpg,
                    epgRefresh = epgRefresh,
                    onCycleEpgRefresh = onCycleEpgRefresh,
                    epgTimeOffsetHours = epgTimeOffsetHours,
                    onCycleEpgTimeOffset = onCycleEpgTimeOffset,
                )
                SettingsCategory.Playback -> playbackContent(
                    blackScreenBetweenZaps = blackScreenBetweenZaps,
                    onToggle = onToggleBlackScreenBetweenZaps,
                    previewOnSelect = previewOnSelect,
                    onTogglePreviewOnSelect = onTogglePreviewOnSelect,
                    bufferSize = bufferSize,
                    onSetBufferSize = onSetBufferSize,
                    forceStereo = forceStereo,
                    onToggleForceStereo = onToggleForceStereo,
                    defaultAspect = defaultAspect,
                    onCycleDefaultAspect = onCycleDefaultAspect,
                    afrEnabled = afrEnabled,
                    onToggleAfr = onToggleAfr,
                    preferredAudio = preferredAudio,
                    onResetPreferredAudio = onResetPreferredAudio,
                    preferredSubtitles = preferredSubtitles,
                    onResetPreferredSubtitles = onResetPreferredSubtitles,
                    firstRowFocus = firstRowFocus,
                )
                SettingsCategory.Appearance -> appearanceContent(
                    showRawResolution = showRawResolution,
                    onToggle = onToggleShowRawResolution,
                    doubleHeightRow = doubleHeightRow,
                    onToggleDoubleHeightRow = onToggleDoubleHeightRow,
                    navAutoHideSeconds = navAutoHideSeconds,
                    onSetNavAutoHide = onSetNavAutoHide,
                    firstRowFocus = firstRowFocus,
                )
                SettingsCategory.RemoteControl -> remoteControlContent(
                    teleportMenuEnabled = teleportMenuEnabled,
                    onToggle = onToggleTeleportMenuEnabled,
                    digitKeysEnabled = digitKeysEnabled,
                    onToggleDigitKeys = onToggleDigitKeys,
                    longPressOkLabel = longPressOkLabel,
                    onCycleLongPressOk = onCycleLongPressOk,
                    firstRowFocus = firstRowFocus,
                )
                SettingsCategory.ParentalControls -> parentalContent(
                    hiddenGroupRows = hiddenGroupRows,
                    hiddenGroups = hiddenGroups,
                    multiplePlaylists = playlists.size > 1,
                    playlistName = { id -> playlists.firstOrNull { it.id == id }?.name.orEmpty() },
                    onToggle = { key ->
                        if (parentalPinSet && !parentalUnlocked) pinPrompt = PinPrompt.Unlock(key)
                        else onToggleGroupHidden(key)
                    },
                    pinSet = parentalPinSet,
                    onPinRow = { pinPrompt = if (parentalPinSet) PinPrompt.Remove else PinPrompt.Create },
                    firstRowFocus = firstRowFocus,
                )
                SettingsCategory.Other -> otherContent(
                    onClearWatchHistory = onClearWatchHistory,
                    firstRowFocus = firstRowFocus,
                )
                else -> items(SETTINGS_GREY_ROWS[category].orEmpty()) { row -> GreyRowContent(row) }
            }
        }
        if (openPlaylist != null) {
            PlaylistDetail(
                playlist = openPlaylist,
                isLast = playlists.size == 1,
                channelCount = playlistChannelCounts[openPlaylist.id],
                syncStatus = playlistSyncStatus[openPlaylist.id],
                onRefresh = { onRefreshPlaylist(openPlaylist.id) },
                onUpdateEpg = { onUpdatePlaylistEpg(openPlaylist.id) },
                onRename = { name -> onRenamePlaylist(openPlaylist.id, name) },
                onSaveConnection = { server, user, pass -> onSavePlaylistConnection(openPlaylist.id, server, user, pass) },
                loadAccount = { loadPlaylistAccount(openPlaylist.id) },
                onDelete = { closeDetail(addPlaylistFocus) { onDeletePlaylist(openPlaylist.id) } },
                firstRowFocus = detailFirstFocus,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (closeTarget != null) 0f else 1f }
                    .onFocusChanged { detailHasFocus = it.hasFocus },
            )
        }
        }
    }
}

/**
 * Order from `SETTINGS.md`: loaded playlists, then Add playlist, then the Reset danger zone.
 * Each playlist is one row that drills into its own page ([PlaylistDetail]) - TiviMate's
 * Settings → Playlists → <playlist> pattern - instead of crowding rename/refresh/remove into
 * the list itself.
 */
private fun LazyListScope.playlistsContent(
    playlists: List<PlaylistEntity>,
    channelCounts: Map<String, Int>,
    onOpen: (String) -> Unit,
    rowFocus: (String) -> FocusRequester,
    onAddPlaylist: () -> Unit,
    onResetPlaylist: () -> Unit,
    firstRowFocus: FocusRequester,
    addPlaylistFocus: FocusRequester,
) {
    if (playlists.isEmpty()) {
        item {
            LiveRow(
                label = "Add another playlist",
                value = "›",
                onClick = onAddPlaylist,
                modifier = Modifier.focusRequester(firstRowFocus),
            )
        }
    } else {
        itemsIndexed(playlists, key = { _, p -> p.id }) { index, playlist ->
            PlaylistRow(
                playlist = playlist,
                channelCount = channelCounts[playlist.id],
                onClick = { onOpen(playlist.id) },
                modifier = Modifier
                    .then(if (index == 0) Modifier.focusRequester(firstRowFocus) else Modifier)
                    .focusRequester(rowFocus(playlist.id)),
            )
        }
        item {
            LiveRow(
                label = "Add another playlist",
                value = "›",
                onClick = onAddPlaylist,
                modifier = Modifier.focusRequester(addPlaylistFocus),
            )
        }
    }

    item {
        Spacer(modifier = Modifier.height(12.dp))
        SectionDivider()
        Text(
            "Danger zone",
            style = RedSurfType.rowSecondary,
            color = TextSecondary,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 4.dp),
        )
    }
    item { ResetRow(onReset = onResetPlaylist) }
}

private fun PlaylistEntity.typeLabel(): String = when (type) {
    "xtream" -> "Xtream Codes"
    "m3u" -> "M3U"
    "stalker" -> "Stalker portal"
    else -> type.uppercase()
}

/**
 * The per-playlist page's login editor (AGENTS.md backlog: "edit existing credentials" - fixing
 * a changed/expired login without removing and re-adding the playlist). Xtream shows server,
 * username and a password field left blank ("keep current", so the password is never put on
 * screen); M3U shows its URL. Save checks the login with the provider before anything is written,
 * then refreshes the channel list (MainViewModel.updatePlaylistConnection).
 */
@Composable
private fun ConnectionEditor(playlist: PlaylistEntity, onSave: suspend (String, String, String) -> String?) {
    val xtream = playlist.type == "xtream"
    var server by remember(playlist.id, playlist.serverUrl) { mutableStateOf(playlist.serverUrl) }
    var user by remember(playlist.id, playlist.username) { mutableStateOf(playlist.username) }
    var pass by remember(playlist.id) { mutableStateOf("") }
    var result by remember(playlist.id) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val changed = server.trim().trimEnd('/') != playlist.serverUrl || user.trim() != playlist.username || pass.isNotBlank()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text("Connection", style = RedSurfType.rowSecondary, color = TextSecondary, modifier = Modifier.padding(vertical = 4.dp))
        com.redsurf.tv.ui.onboarding.TvTextField(value = server, onValueChange = { server = it }, label = if (xtream) "Server" else "Playlist URL")
        if (xtream) {
            com.redsurf.tv.ui.onboarding.TvTextField(value = user, onValueChange = { user = it }, label = "Username")
            com.redsurf.tv.ui.onboarding.TvTextField(value = pass, onValueChange = { pass = it }, label = "Password (blank keeps the current one)", isPassword = true)
        }
        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionChip(
                label = when {
                    saving -> "Checking…"
                    changed -> "Save & update"
                    else -> "No changes"
                },
                onClick = {
                    if (!changed || saving) return@ActionChip
                    saving = true
                    scope.launch {
                        val error = onSave(server, user, pass)
                        saving = false
                        result = error ?: "Saved - updating channels"
                        if (error == null) pass = ""
                    }
                },
            )
            result?.let {
                Spacer(modifier = Modifier.width(12.dp))
                Text(it, style = RedSurfType.rowSecondary, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: PlaylistEntity,
    channelCount: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = RedSurfFocus.shape(8.dp),
        colors = RedSurfFocus.rowColors(),
        scale = RedSurfFocus.scale(),
        border = RedSurfFocus.border(),
        glow = RedSurfFocus.glow(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.name, style = RedSurfType.rowTitle, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    playlist.typeLabel() + (channelCount?.let { " · %,d channels".format(it) } ?: ""),
                    style = RedSurfType.rowSecondary,
                    color = if (focused) TextPrimary else TextSecondary,
                    maxLines = 1,
                )
            }
            Text("›", style = RedSurfType.rowSecondary, color = if (focused) TextPrimary else Accent)
        }
    }
}

/** A static `Label ······ Value` info row - About's "Version" shape. */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = RedSurfType.rowTitle, color = TextPrimary)
        Spacer(modifier = Modifier.width(16.dp))
        Text(value, style = RedSurfType.rowSecondary, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Settings → Playlists → <playlist> (2026-10-26, `AGENTS.md`'s "real third-level page per
 * playlist", decided 2026-09-17). TiviMate's per-playlist page carries update-now actions, name,
 * connection details and Remove; this is that set for what RedSurf can genuinely do today - no
 * grey placeholders for things that don't exist. Actions sit first so entering the page never
 * lands on the text field (which would pop the IME). Server is shown without the password.
 */
@Composable
private fun PlaylistDetail(
    playlist: PlaylistEntity,
    isLast: Boolean,
    channelCount: Int?,
    syncStatus: String?,
    onRefresh: () -> Unit,
    onUpdateEpg: () -> Unit,
    onRename: (String) -> Unit,
    onSaveConnection: suspend (String, String, String) -> String?,
    loadAccount: suspend () -> com.redsurf.tv.vod.XtreamUserInfo?,
    onDelete: () -> Unit,
    firstRowFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var name by remember(playlist.id) { mutableStateOf(playlist.name) }
    val saveFocus = remember { FocusRequester() }
    val canRefresh = playlist.type == "xtream" || playlist.type == "m3u"
    // TiviMate's playlist info: the panel's account object, fetched once per page open. "Checking…"
    // until it answers, "Not responding" if the panel doesn't (TiviMate's active/dead status; M3U and
    // Stalker never show the section).
    var account by remember(playlist.id) { mutableStateOf<com.redsurf.tv.vod.XtreamUserInfo?>(null) }
    var accountLoaded by remember(playlist.id) { mutableStateOf(false) }
    if (playlist.type == "xtream") {
        LaunchedEffect(playlist.id) {
            account = loadAccount()
            accountLoaded = true
        }
    }
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            LiveRow(
                label = "Update channels",
                value = when {
                    !canRefresh -> "Not available for this type"
                    syncStatus != null && !syncStatus.startsWith("Guide") -> syncStatus
                    else -> "Update now"
                },
                onClick = { if (canRefresh) onRefresh() },
                modifier = Modifier.focusRequester(firstRowFocus),
            )
        }
        item {
            LiveRow(
                label = "Update EPG",
                value = if (syncStatus != null && syncStatus.startsWith("Guide")) syncStatus else "Update now",
                onClick = onUpdateEpg,
            )
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SectionDivider()
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // TvTextField leaves LEFT/RIGHT to the text cursor, which strands a D-pad user in
                // the field. The TV keyboard has its own cursor keys, so RIGHT here means "go to Save".
                Box(
                    modifier = Modifier.weight(1f).onPreviewKeyEvent { event ->
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight &&
                            runCatching { saveFocus.requestFocus() }.isSuccess
                    },
                ) {
                    com.redsurf.tv.ui.onboarding.TvTextField(value = name, onValueChange = { name = it }, label = "Name")
                }
                Spacer(modifier = Modifier.width(12.dp))
                ActionChip(
                    label = if (name.trim() == playlist.name) "Saved" else "Save",
                    onClick = { onRename(name) },
                    modifier = Modifier.focusRequester(saveFocus),
                )
            }
        }
        item { InfoRow("Type", playlist.typeLabel()) }
        if (canRefresh) {
            item { ConnectionEditor(playlist = playlist, onSave = onSaveConnection) }
        } else {
            if (playlist.serverUrl.isNotBlank()) item { InfoRow("Server", playlist.serverUrl) }
            if (playlist.username.isNotBlank()) item { InfoRow("Username", playlist.username) }
        }
        playlist.macAddress?.takeIf { it.isNotBlank() }?.let { mac -> item { InfoRow("MAC address", mac) } }
        playlist.userAgent?.takeIf { it.isNotBlank() }?.let { ua -> item { InfoRow("User agent", ua) } }
        item { InfoRow("Channels", channelCount?.let { "%,d".format(it) } ?: "—") }
        item { InfoRow("Content", contentLabel(playlist.contentType)) }
        if (playlist.type == "xtream") {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionDivider()
            }
            val info = account
            if (info == null) {
                item { InfoRow("Account", if (accountLoaded) "Not responding" else "Checking…") }
            } else {
                item {
                    InfoRow(
                        "Account",
                        listOfNotNull(info.status ?: "—", "trial".takeIf { info.isTrial }).joinToString(" · "),
                    )
                }
                item { InfoRow("Expires", info.expiresAtSec?.let { formatAccountExpiry(it) } ?: "Never") }
                if (info.maxConnections != null) {
                    item { InfoRow("Connections", "${info.activeConnections ?: 0} of ${info.maxConnections} in use") }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SectionDivider()
            Spacer(modifier = Modifier.height(4.dp))
        }
        item { RemovePlaylistRow(isLast = isLast, onDelete = onDelete) }
    }
}

// The playlist's chosen scope. VOD isn't stored yet, so only Live TV is ever imported; say so rather
// than claim content the app doesn't have (AGENTS.md backlog: content type was shown wrong/static).
private fun contentLabel(contentType: String): String = when (contentType) {
    "live" -> "Live TV"
    "vod" -> "VOD only (Live TV shown until VOD lands)"
    else -> "Live TV + VOD (VOD not imported yet)"
}

@Composable
private fun RemovePlaylistRow(isLast: Boolean, onDelete: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    val confirmFocus = remember { FocusRequester() }
    val removeRowFocus = remember { FocusRequester() }

    // ResetRow's crash-safe shape (see the history in git for the old PlaylistBlock comment and
    // FOCUS_MODEL.md): both the row and the confirm block stay composed, the inactive one 0dp,
    // clipped and unfocusable, and every transition moves focus synchronously onto an existing
    // node. "Confirm remove" doesn't touch focus itself - the pane closes this page onto
    // "Add another playlist" first and only deletes once focus has landed there.
    Column(modifier = Modifier.fillMaxWidth()) {
        LiveRow(
            label = "Remove playlist",
            value = "›",
            onClick = {
                confirming = true
                runCatching { confirmFocus.requestFocus() }
            },
            modifier = Modifier
                .focusProperties { canFocus = !confirming }
                .focusRequester(removeRowFocus)
                .then(if (confirming) Modifier.size(0.dp).clipToBounds() else Modifier),
        )
        Column(
            modifier = Modifier.then(
                if (confirming) {
                    Modifier.fillMaxWidth().background(SurfaceRaised, RectangleShape).padding(12.dp)
                } else {
                    Modifier.size(0.dp).clipToBounds()
                },
            ),
        ) {
            Text(
                if (isLast) {
                    "Last playlist - removing it returns to setup. Press OK again to confirm."
                } else {
                    "Removes this playlist and its channels. Press OK again to confirm."
                },
                style = RedSurfType.rowSecondary,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip(
                    label = "Confirm remove",
                    onClick = onDelete,
                    modifier = Modifier
                        .focusProperties { canFocus = confirming }
                        .focusRequester(confirmFocus),
                )
                ActionChip(
                    label = "Cancel",
                    modifier = Modifier.focusProperties { canFocus = confirming },
                    onClick = {
                        confirming = false
                        runCatching { removeRowFocus.requestFocus() }
                    },
                )
            }
        }
    }
}

@Composable
private fun ResetRow(onReset: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    val confirmFocus = remember { FocusRequester() }
    val resetRowFocus = remember { FocusRequester() }

    // Same crash and same fix as PlaylistBlock - see its comment. Both the row and the confirm
    // block stay composed (inactive one sized to 0dp, clipped, and unfocusable), and every
    // transition moves focus synchronously onto a node that already exists.
    Column(modifier = Modifier.fillMaxWidth()) {
        LiveRow(
            label = "Reset everything & add a different playlist",
            value = "›",
            onClick = {
                confirming = true
                runCatching { confirmFocus.requestFocus() }
            },
            modifier = Modifier
                .focusProperties { canFocus = !confirming }
                .focusRequester(resetRowFocus)
                .then(if (confirming) Modifier.size(0.dp).clipToBounds() else Modifier),
        )
        Column(
            modifier = Modifier.then(
                if (confirming) {
                    Modifier.fillMaxWidth().background(SurfaceRaised, RectangleShape).padding(12.dp)
                } else {
                    Modifier.size(0.dp).clipToBounds()
                },
            ),
        ) {
            Text(
                "This deletes every loaded playlist and its channels. Press OK again to confirm.",
                style = RedSurfType.rowSecondary,
                color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip(
                    label = "Confirm reset",
                    // Reset replaces this whole screen with Onboarding - hand focus back to the
                    // (still-composed) reset row first so no focused node is ever orphaned.
                    onClick = {
                        confirming = false
                        runCatching { resetRowFocus.requestFocus() }
                        onReset()
                    },
                    modifier = Modifier
                        .focusProperties { canFocus = confirming }
                        .focusRequester(confirmFocus),
                )
                ActionChip(
                    label = "Cancel",
                    modifier = Modifier.focusProperties { canFocus = confirming },
                    onClick = {
                        confirming = false
                        runCatching { resetRowFocus.requestFocus() }
                    },
                )
            }
        }
    }
}

/**
 * EPG (2026-09-21): the first live rows this category has had. "Update EPG now" is the manual
 * counterpart to the daily sync - and the retry button for the provider-502 case the sync fix
 * surfaced. Two static info rows (same non-interactive shape as About's "Version") show the last
 * completed sync and, only when the newest attempt failed, what it failed with. Grey rows stay
 * for Sources (P1 public supplement), Time offset, and Refresh every.
 */
private fun LazyListScope.epgContent(
    status: EpgSyncStatus,
    onUpdateNow: () -> Unit,
    firstRowFocus: FocusRequester,
    publicEpgEnabled: Boolean,
    onTogglePublicEpg: () -> Unit,
    epgRefresh: EpgRefresh,
    onCycleEpgRefresh: () -> Unit,
    epgTimeOffsetHours: Int,
    onCycleEpgTimeOffset: () -> Unit,
) {
    item {
        LiveRow(
            label = "Update EPG now",
            value = if (status.running) "Updating…" else "Update now",
            onClick = { if (!status.running) onUpdateNow() },
            modifier = Modifier.focusRequester(firstRowFocus),
        )
    }
    item {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Last updated", style = RedSurfType.rowTitle, color = TextPrimary)
            Text(
                if (status.lastCompleted == 0L) "Never" else formatEpgTime(status.lastCompleted),
                style = RedSurfType.rowSecondary,
                color = TextSecondary,
            )
        }
    }
    if (status.lastError != null && status.lastAttempt > status.lastCompleted) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Last attempt", style = RedSurfType.rowTitle, color = TextPrimary)
                Text(
                    "Failed · ${status.lastError} · ${formatEpgTime(status.lastAttempt)}",
                    style = RedSurfType.rowSecondary,
                    color = TextSecondary,
                )
            }
        }
    }
    // EPG_WRAPUP.md 2.1 - flipped live from the grey "Sources · Provider only" row. Takes effect
    // on the next sync ("Update EPG now" above, or the daily one).
    item {
        LiveRow(
            label = "Sources",
            value = if (publicEpgEnabled) "Provider + public guides (EPGSHARE01)" else "Provider only",
            onClick = onTogglePublicEpg,
        )
    }
    item { LiveRow(label = "Refresh every", value = epgRefresh.label, onClick = onCycleEpgRefresh) }
    item {
        LiveRow(
            label = "Time offset",
            value = if (epgTimeOffsetHours > 0) "+${epgTimeOffsetHours}h" else "${epgTimeOffsetHours}h",
            onClick = onCycleEpgTimeOffset,
        )
    }
    items(SETTINGS_GREY_ROWS.getValue(SettingsCategory.Epg)) { GreyRowContent(it) }
}

/** Nav strip auto-hide presets, seconds; 0 = never (TiviMate offers a similar fixed list). */
private val NAV_AUTO_HIDE_PRESETS = listOf(0, 3, 5, 10, 15)

private val epgTimeFormat = java.text.SimpleDateFormat("EEE h:mm a", java.util.Locale.getDefault())
private fun formatEpgTime(millis: Long): String = epgTimeFormat.format(java.util.Date(millis))

/**
 * About (`SETTINGS.md`): version + Check for updates moved here from the old screen's top - "this
 * is where people look for it" - plus its one grey row.
 */
private fun LazyListScope.aboutContent(
    updateStatus: UpdateCheckStatus,
    onCheckForUpdates: () -> Unit,
    autoUpdate: Boolean,
    onToggleAutoUpdate: () -> Unit,
    firstRowFocus: FocusRequester,
) {
    // The real mark (AGENTS.md Branding entry, 2026-09-16) - the one place in the app it's shown
    // at full detail and full size, where the play/TV/remote/music icons woven into the wave
    // actually read clearly (unlike the small Settings-rail tile use of the same asset).
    item {
        Image(
            painter = painterResource(R.drawable.ic_settings_about),
            contentDescription = null,
            modifier = Modifier.padding(start = 4.dp, bottom = 16.dp).size(96.dp),
        )
    }
    item {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Version", style = RedSurfType.rowTitle, color = TextPrimary)
            Text(BuildConfig.VERSION_NAME, style = RedSurfType.rowSecondary, color = TextSecondary)
        }
    }
    item {
        // Live, static info row (BACKLOG_SWEEP.md #9, user's own text) - same non-interactive
        // shape as "Version" above (no Surface/focus/click, nothing to act on), not a GreyRowContent
        // stand-in (it's real, not planned) and not a LiveRow (there's no action to take on it).
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Created by", style = RedSurfType.rowTitle, color = TextPrimary)
            Text("Faraz Ahmad", style = RedSurfType.rowSecondary, color = TextSecondary)
        }
    }
    item {
        LiveRow(
            label = "Check for updates",
            value = when (updateStatus) {
                is UpdateCheckStatus.Idle -> "Check now"
                is UpdateCheckStatus.Checking -> "Checking..."
                is UpdateCheckStatus.UpToDate -> "Up to date"
                is UpdateCheckStatus.Available -> "Update available: ${updateStatus.info.newVersion}"
            },
            onClick = onCheckForUpdates,
            modifier = Modifier.focusRequester(firstRowFocus),
        )
    }
    item { LiveRow(label = "Auto-update", value = if (autoUpdate) "On" else "Off", onClick = onToggleAutoUpdate) }
}

/** General (`AGENTS.md` backlog, 2026-09-15, corrected same day): landing on Live TV with the
 * last-watched channel/category pre-selected is the *unconditional* default now (see
 * `AppShell.kt`'s restore effect) - this one live toggle only controls whether that pre-selected
 * channel also starts playing immediately on launch, ahead of its remaining grey rows, same
 * layering as every other category's live-rows-then-grey-rows shape. */
private fun LazyListScope.generalContent(
    autoPlayLastChannelOnLaunch: Boolean,
    onToggle: () -> Unit,
    startOn: StartOn,
    onCycleStartOn: () -> Unit,
    firstRowFocus: FocusRequester,
) {
    item {
        LiveRow(
            label = "Auto-play last channel on launch",
            value = if (autoPlayLastChannelOnLaunch) "On" else "Off",
            onClick = onToggle,
            modifier = Modifier.focusRequester(firstRowFocus),
        )
    }
    item { LiveRow(label = "Start on", value = startOn.label, onClick = onCycleStartOn) }
    items(SETTINGS_GREY_ROWS[SettingsCategory.General].orEmpty()) { GreyRowContent(it) }
}

/** Parental controls: "Hidden categories" - one live row per category hidden from Live TV (hold
 * OK on a category there, TiviMate's "Hide group"). OK toggles Hidden/Shown in place; the row
 * list is this session's union of hidden keys, so unhiding never removes the row under focus. */
private fun LazyListScope.parentalContent(
    hiddenGroupRows: List<String>,
    hiddenGroups: Set<String>,
    multiplePlaylists: Boolean,
    playlistName: (String) -> String,
    onToggle: (String) -> Unit,
    pinSet: Boolean,
    onPinRow: () -> Unit,
    firstRowFocus: FocusRequester,
) {
    item {
        LiveRow(
            label = "PIN",
            value = if (pinSet) "On" else "Not set",
            onClick = onPinRow,
            modifier = Modifier.focusRequester(firstRowFocus),
        )
    }
    if (hiddenGroupRows.isEmpty()) {
        item { GreyRowContent(GreyRow("Hidden categories", "None - hold OK on a category")) }
        return
    }
    itemsIndexed(hiddenGroupRows, key = { _, key -> key }) { index, key ->
        val playlistId = key.substringBefore('\u0000')
        val name = formatGroupName(key.substringAfter('\u0000'))
        LiveRow(
            label = if (multiplePlaylists) "$name · ${playlistName(playlistId)}" else name,
            value = if (key in hiddenGroups) "Hidden" else "Shown",
            onClick = { onToggle(key) },
        )
    }
}

/** What the Parental PIN dialog is asking for. */
private sealed interface PinPrompt {
    data object Create : PinPrompt
    data object Remove : PinPrompt
    data class Unlock(val key: String) : PinPrompt
}

/** Other: "Clear watch history" is live - a two-press confirm done in place (the value reads
 * "Press OK again" once armed, losing focus disarms it), so no row ever appears or disappears
 * under focus. Clears the recent-channel tiles, the History picker and RIGHT's last-channel
 * zap together, since all three read the one `recent_channels` table. */
private fun LazyListScope.otherContent(
    onClearWatchHistory: () -> Unit,
    firstRowFocus: FocusRequester,
) {
    val rows = SETTINGS_GREY_ROWS[SettingsCategory.Other].orEmpty()
    items(rows.take(1)) { GreyRowContent(it) }
    item {
        var armed by remember { mutableStateOf(false) }
        var cleared by remember { mutableStateOf(false) }
        LiveRow(
            label = "Clear watch history",
            value = when {
                armed -> "Press OK again"
                cleared -> "Cleared"
                else -> "Clear"
            },
            onClick = {
                if (armed) {
                    armed = false
                    cleared = true
                    onClearWatchHistory()
                } else {
                    armed = true
                }
            },
            modifier = Modifier
                .focusRequester(firstRowFocus)
                .onFocusChanged { if (!it.isFocused) armed = false },
        )
    }
    item { DiagnosticsRows() }
}

/** Other → Diagnostics: a read-only snapshot taken when the pane opens - device, Android
 * version, RAM headroom (`HARDWARE.md`'s ~449 MB free is the number that matters on the
 * Chromecast), this process's heap, and the on-disk size of the database and cache. Static
 * [InfoRow]s, nothing to focus. */
@Composable
private fun DiagnosticsRows() {
    val context = LocalContext.current
    val rows by produceState(initialValue = emptyList<Pair<String, String>>()) {
        value = withContext(Dispatchers.IO) { diagnostics(context) }
    }
    Column {
        Text(
            "Diagnostics",
            style = RedSurfType.rowSecondary,
            color = TextSecondary,
            modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 4.dp),
        )
        rows.forEach { (label, value) -> InfoRow(label, value) }
    }
}

/** Runs on IO: the cache walk touches every cached logo file. */
private fun diagnostics(context: Context): List<Pair<String, String>> {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    val rt = Runtime.getRuntime()
    val db = context.getDatabasePath("redsurf_tv_database")
    val dbBytes = listOf("", "-wal", "-shm").sumOf { File(db.path + it).length() }
    val cacheBytes = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    val mb = { bytes: Long -> "%,d MB".format(bytes / (1024 * 1024)) }
    return listOf(
        "Device" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "Memory free" to "${mb(mem.availMem)} of ${mb(mem.totalMem)}" + if (mem.lowMemory) " · low" else "",
        "App heap" to "${mb(rt.totalMemory() - rt.freeMemory())} of ${mb(rt.maxMemory())}",
        "Database" to mb(dbBytes),
        "Cache" to mb(cacheBytes),
    )
}

/** Playback (`BACKLOG_SWEEP.md` #11): one live toggle - the "old cable box" black-screen-between-
 * zaps behavior - ahead of its remaining grey rows, same layering as [aboutContent]'s live rows
 * before the Auto-update toggle. */
private fun LazyListScope.playbackContent(
    blackScreenBetweenZaps: Boolean,
    onToggle: () -> Unit,
    previewOnSelect: Boolean,
    onTogglePreviewOnSelect: () -> Unit,
    bufferSize: BufferSize,
    onSetBufferSize: (BufferSize) -> Unit,
    forceStereo: Boolean,
    onToggleForceStereo: () -> Unit,
    defaultAspect: DefaultAspect,
    onCycleDefaultAspect: () -> Unit,
    afrEnabled: Boolean,
    onToggleAfr: () -> Unit,
    preferredAudio: String?,
    onResetPreferredAudio: () -> Unit,
    preferredSubtitles: String?,
    onResetPreferredSubtitles: () -> Unit,
    firstRowFocus: FocusRequester,
) {
    item {
        LiveRow(
            label = "Black screen between zaps",
            value = if (blackScreenBetweenZaps) "On" else "Off",
            onClick = onToggle,
            modifier = Modifier.focusRequester(firstRowFocus),
        )
    }
    item {
        // docs/plans/PREVIEW.md, built 2026-09-20 - default on. Off restores the old single-OK-
        // jumps-straight-to-fullscreen behavior exactly.
        LiveRow(
            label = "Preview channel on select",
            value = if (previewOnSelect) "On" else "Off",
            onClick = onTogglePreviewOnSelect,
        )
    }
    item {
        // TIVIMATE_LEARNINGS.md F17 - TiviMate's five steps. Bigger rides out a poor provider,
        // at the cost of a slower channel switch.
        PickerRow(
            label = "Buffer size",
            options = BufferSize.entries,
            selected = bufferSize,
            optionLabel = { it.label },
            onPick = onSetBufferSize,
            row = { value, onClick, m -> LiveRow(label = "Buffer size", value = value, onClick = onClick, modifier = m) },
        )
        LiveRow(
            label = "Audio output",
            value = if (forceStereo) "Force stereo" else "Auto",
            onClick = onToggleForceStereo,
        )
    }
    item { LiveRow(label = "Default aspect", value = defaultAspect.label, onClick = onCycleDefaultAspect) }
    item { LiveRow(label = "Auto frame rate", value = if (afrEnabled) "On" else "Off", onClick = onToggleAfr) }
    // Remembered from the player's Audio picker (TrackManager); OK forgets it.
    item {
        LiveRow(
            label = "Preferred audio",
            value = preferredAudio?.let { java.util.Locale(it).displayLanguage.ifBlank { it } } ?: "Auto",
            onClick = onResetPreferredAudio,
        )
    }
    // Remembered from the player's Subtitles picker the same way; OK turns them back off.
    item {
        LiveRow(
            label = "Preferred subtitles",
            value = preferredSubtitles?.let { java.util.Locale(it).displayLanguage.ifBlank { it } } ?: "Off",
            onClick = onResetPreferredSubtitles,
        )
    }
    items(SETTINGS_GREY_ROWS[SettingsCategory.Playback].orEmpty()) { GreyRowContent(it) }
}

/** Appearance (`BACKLOG_SWEEP.md` #12): one live toggle - literal `WxH` vs. the derived
 * SD/HD/FHD/4K class on the player's resolution badge - ahead of its remaining grey rows. */
private fun LazyListScope.appearanceContent(
    showRawResolution: Boolean,
    onToggle: () -> Unit,
    doubleHeightRow: Boolean,
    onToggleDoubleHeightRow: () -> Unit,
    navAutoHideSeconds: Int,
    onSetNavAutoHide: (Int) -> Unit,
    firstRowFocus: FocusRequester,
) {
    item {
        LiveRow(
            label = "Resolution badge",
            value = if (showRawResolution) "Exact (e.g. 1920x1080)" else "Class (SD/HD/FHD/4K)",
            onClick = onToggle,
            modifier = Modifier.focusRequester(firstRowFocus),
        )
    }
    // EPG_WRAPUP.md 1.7 - TiviMate's double-height selected row in the guide.
    item {
        LiveRow(
            label = "Double-height focused row in guide",
            value = if (doubleHeightRow) "On" else "Off",
            onClick = onToggleDoubleHeightRow,
        )
    }
    // EPG_WRAPUP.md 2.3 - flipped live from the grey "Hide nav strip when idle" row.
    item {
        PickerRow(
            label = "Hide nav strip when idle",
            options = NAV_AUTO_HIDE_PRESETS,
            selected = navAutoHideSeconds,
            optionLabel = { if (it == 0) "Off" else "After ${it}s" },
            onPick = onSetNavAutoHide,
            row = { value, onClick, m -> LiveRow(label = "Hide nav strip when idle", value = value, onClick = onClick, modifier = m) },
        )
    }
    items(SETTINGS_GREY_ROWS[SettingsCategory.Appearance].orEmpty()) { GreyRowContent(it) }
}

/** Remote control (`docs/plans/TELEPORT_MENU.md` decision 1, 2026-09-19): "Teleport Menu" is the
 * first live row this category has ever had - default off, see `AppShell.kt`'s long-press-Back
 * handler for what flipping it changes. "Digit keys" (2026-10-26): number keys type a channel
 * number in fullscreen, TiviMate's default - see PlayerScreen's digit entry. */
private fun LazyListScope.remoteControlContent(
    teleportMenuEnabled: Boolean,
    onToggle: () -> Unit,
    digitKeysEnabled: Boolean,
    onToggleDigitKeys: () -> Unit,
    longPressOkLabel: String,
    onCycleLongPressOk: () -> Unit,
    firstRowFocus: FocusRequester,
) {
    item {
        LiveRow(
            label = "Teleport Menu",
            value = if (teleportMenuEnabled) "On" else "Off",
            onClick = onToggle,
            modifier = Modifier.focusRequester(firstRowFocus),
        )
    }
    item {
        LiveRow(
            label = "Digit keys",
            value = if (digitKeysEnabled) "Channel number entry" else "Off",
            onClick = onToggleDigitKeys,
        )
    }
    item {
        // TiviMate's key remap, for the one key that matters most: what holding OK does in the
        // fullscreen player. Cycles Context menu -> Add/remove favourite -> Recent channels.
        LiveRow(label = "Long-press OK", value = longPressOkLabel, onClick = onCycleLongPressOk)
    }
    items(SETTINGS_GREY_ROWS[SettingsCategory.RemoteControl].orEmpty()) { GreyRowContent(it) }
}

/** A real, focusable `Label ······ Value` row (`SETTINGS.md`'s layout) - value right-aligned in
 * the accent colour. */
@Composable
private fun LiveRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    valueColor: Color = Accent,
) {
    // The focused row is a solid Accent block, so an Accent value would vanish into it.
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = RedSurfFocus.shape(8.dp),
        colors = RedSurfFocus.rowColors(),
        scale = RedSurfFocus.scale(),
        border = RedSurfFocus.border(),
        glow = RedSurfFocus.glow(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = RedSurfType.rowTitle, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = RedSurfType.rowSecondary, color = if (focused && valueColor == Accent) TextPrimary else valueColor, maxLines = 1)
        }
    }
}

/** A grey, unfocusable `Label ······ Value` row - the exact same visual shape as [LiveRow] minus
 * the `Surface`/focus/click, so a flipped-live row needs no relayout, just real behavior. */
@Composable
private fun GreyRowContent(row: GreyRow, indent: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 24.dp else 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.label, style = RedSurfType.rowTitle, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(row.plannedValue, style = RedSurfType.rowSecondary, color = TextSecondary, maxLines = 1)
    }
}

@Composable
private fun SectionDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(SurfaceRaised))
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    CrestButton(label, onClick, modifier)

/** "12 Mar 2027 (in 137 days)" - the date plus how long is left, TiviMate's expiry line. */
private fun formatAccountExpiry(epochSec: Long): String {
    val ms = epochSec * 1000
    val date = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(ms))
    val days = Math.floorDiv(ms - System.currentTimeMillis(), 86_400_000L)
    val left = when {
        days < 0 -> "expired"
        days == 0L -> "today"
        days == 1L -> "in 1 day"
        else -> "in $days days"
    }
    return "$date ($left)"
}
