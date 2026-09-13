package com.redsurf.tv.ui.shell

import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyListScope
import androidx.tv.foundation.lazy.list.items
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.redsurf.tv.BuildConfig
import com.redsurf.tv.UpdateCheckStatus
import com.redsurf.tv.db.PlaylistEntity
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.RedSurfType
import com.redsurf.tv.ui.theme.Surface as SurfaceColor
import com.redsurf.tv.ui.theme.SurfaceRaised
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

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
    onResetPlaylist: () -> Unit,
    onAddPlaylist: () -> Unit,
    onDeletePlaylist: (String) -> Unit,
) {
    val railFocus = remember { FocusRequester() }
    val paneFirstRowFocus = remember { FocusRequester() }
    var focusInPane by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Initial focus on entry/re-entry: the rail's currently-selected row, never wherever Compose
    // would otherwise default to (state/focus discipline) - same 50ms-after-attach pattern as
    // GroupsColumn/PlayerScreen's own initial-focus effects (the row needs a frame to attach).
    // Unlike the LEFT/Back cases below, this one has no prior focus to move *from* (nothing is
    // focused yet), so it isn't subject to the same `exit`-vs-`requestFocus()` conflict.
    LaunchedEffect(Unit) {
        delay(50)
        runCatching { railFocus.requestFocus() }
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
    BackHandler(enabled = focusInPane) {
        focusManager.moveFocus(FocusDirection.Left)
    }

    // Whether the current category has any real, focusable content in its pane - drives the
    // explicit escape guard below. Playlists and About always have at least one live row (Add
    // playlist / Check for updates) even with nothing else in them; every other category is
    // entirely [SETTINGS_GREY_ROWS] today.
    val hasLiveContent = selectedCategory == SettingsCategory.Playlists || selectedCategory == SettingsCategory.About

    Row(
        modifier = Modifier
            .fillMaxSize()
            // Escape guard, explicit rather than `focusProperties { exit = ... }` - found live,
            // 2026-09-13: `exit` turned out to behave inconsistently by direction in this exact
            // layout (blocking LEFT even where it needed to work, while *not* reliably blocking
            // RIGHT/UP/DOWN where it needed to) rather than being a clean "block anything leaving
            // this subtree" - not worth chasing further given `PlayerScreen.kt`'s onKeyEvent
            // router already sets the precedent that explicit interception beats relying on
            // Compose's focus-search internals for exactly this "don't let a key escape this
            // screen" job. NavStrip is a composed sibling one level up in AppShell's Column, so
            // any of these three, left unguarded, can land real D-pad focus on a nav pill instead
            // of leaving it inside Settings: UP past the rail's first row, DOWN past its last, or
            // RIGHT into a category with nothing focusable in its pane (confirmed live for all
            // three). Only fires while focus is on the rail (`!focusInPane`) - once inside the
            // pane, its own content and `TvLazyColumn` handle their own UP/DOWN normally.
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || focusInPane) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> selectedCategory == SettingsCategory.entries.first()
                    Key.DirectionDown -> selectedCategory == SettingsCategory.entries.last()
                    Key.DirectionRight -> !hasLiveContent
                    else -> false
                }
            },
    ) {
        CategoryRail(
            selected = selectedCategory,
            onFocused = onCategorySelected,
            railFocus = railFocus,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(20.dp))
        SettingsPane(
            category = selectedCategory,
            playlists = playlists,
            updateStatus = updateStatus,
            onCheckForUpdates = onCheckForUpdates,
            onResetPlaylist = onResetPlaylist,
            onAddPlaylist = onAddPlaylist,
            onDeletePlaylist = onDeletePlaylist,
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
 */
@Composable
private fun CategoryRail(
    selected: SettingsCategory,
    onFocused: (SettingsCategory) -> Unit,
    railFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(SurfaceColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Text(
            "Settings",
            style = RedSurfType.sectionTitle,
            color = TextPrimary,
            modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
        )
        SettingsCategory.entries.forEach { category ->
            CategoryRow(
                category = category,
                selected = category == selected,
                onFocused = { onFocused(category) },
                modifier = if (category == selected) Modifier.focusRequester(railFocus) else Modifier,
            )
            Spacer(modifier = Modifier.height(2.dp))
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
            CategoryTile(category.label.first())
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

@Composable
private fun CategoryTile(letter: Char) {
    Box(
        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(SurfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter.uppercase(), style = RedSurfType.badge, color = TextPrimary)
    }
}

/**
 * Right pane: header + a `TvLazyColumn` of rows, same skeleton as `ChannelsColumn`. Playlists and
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
    category: SettingsCategory,
    playlists: List<PlaylistEntity>,
    updateStatus: UpdateCheckStatus,
    onCheckForUpdates: () -> Unit,
    onResetPlaylist: () -> Unit,
    onAddPlaylist: () -> Unit,
    onDeletePlaylist: (String) -> Unit,
    firstRowFocus: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var hadFocus by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(top = 14.dp)
            .onFocusChanged { state ->
                if (state.hasFocus && !hadFocus) {
                    runCatching { firstRowFocus.requestFocus() }
                }
                hadFocus = state.hasFocus
                onFocusChanged(state.hasFocus)
            },
    ) {
        Text(
            category.label,
            style = RedSurfType.sectionTitle,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 10.dp, start = 4.dp),
        )
        TvLazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when (category) {
                SettingsCategory.Playlists -> playlistsContent(
                    playlists = playlists,
                    onAddPlaylist = onAddPlaylist,
                    onResetPlaylist = onResetPlaylist,
                    onDeletePlaylist = onDeletePlaylist,
                    firstRowFocus = firstRowFocus,
                )
                SettingsCategory.About -> aboutContent(
                    updateStatus = updateStatus,
                    onCheckForUpdates = onCheckForUpdates,
                    firstRowFocus = firstRowFocus,
                )
                else -> items(SETTINGS_GREY_ROWS[category].orEmpty()) { row -> GreyRowContent(row) }
            }
        }
    }
}

/**
 * Order from `SETTINGS.md`: loaded playlists (each with its own real Remove), then Add playlist,
 * then the Reset danger zone - unchanged from the pre-shell `SettingsScreen`'s behavior
 * (`MainViewModel.deletePlaylist`/`resetAndAddNewPlaylist`), just re-hosted in the new shell.
 */
private fun TvLazyListScope.playlistsContent(
    playlists: List<PlaylistEntity>,
    onAddPlaylist: () -> Unit,
    onResetPlaylist: () -> Unit,
    onDeletePlaylist: (String) -> Unit,
    firstRowFocus: FocusRequester,
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
            PlaylistBlock(
                playlist = playlist,
                isLast = playlists.size == 1,
                onDelete = { onDeletePlaylist(playlist.id) },
                removeFocus = if (index == 0) firstRowFocus else null,
            )
        }
        item {
            LiveRow(label = "Add another playlist", value = "›", onClick = onAddPlaylist)
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

@Composable
private fun PlaylistBlock(
    playlist: PlaylistEntity,
    isLast: Boolean,
    onDelete: () -> Unit,
    removeFocus: FocusRequester?,
) {
    var confirming by remember { mutableStateOf(false) }
    val confirmFocus = remember { FocusRequester() }

    // Reclaim focus onto "Confirm remove" the moment the confirm block appears - found live,
    // 2026-09-13: pressing OK on "Remove" tears down the row that held it with nothing claiming
    // the replacement, leaving no focused node in the whole app (confirmed via uiautomator) - the
    // exact "state/focus discipline" bug class already on record for PlayerScreen's Controls
    // floor, just reached via a confirm dialog instead of an overlay swap.
    LaunchedEffect(confirming) {
        if (confirming) {
            delay(50)
            runCatching { confirmFocus.requestFocus() }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.name, style = RedSurfType.rowTitle, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(playlist.type.uppercase(), style = RedSurfType.rowSecondary, color = TextSecondary)
            }
            if (!confirming) {
                ActionChip(
                    label = "Remove",
                    onClick = { confirming = true },
                    modifier = if (removeFocus != null) Modifier.focusRequester(removeFocus) else Modifier,
                )
            }
        }
        if (confirming) {
            Column(
                modifier = Modifier.fillMaxWidth().background(SurfaceRaised, RoundedCornerShape(8.dp)).padding(12.dp),
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
                        onClick = { confirming = false; onDelete() },
                        modifier = Modifier.focusRequester(confirmFocus),
                    )
                    ActionChip(label = "Cancel", onClick = { confirming = false })
                }
            }
        }
        // Per-playlist grey rows (SETTINGS.md) - real names, unfocusable, previewing
        // per-playlist management that doesn't exist yet (rename, re-sync, content type).
        PLAYLIST_GREY_ROWS.forEach { GreyRowContent(it, indent = true) }
    }
}

@Composable
private fun ResetRow(onReset: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    val confirmFocus = remember { FocusRequester() }

    // Same reclaim as PlaylistBlock's - see its comment.
    LaunchedEffect(confirming) {
        if (confirming) {
            delay(50)
            runCatching { confirmFocus.requestFocus() }
        }
    }

    if (!confirming) {
        LiveRow(label = "Reset everything & add a different playlist", value = "›", onClick = { confirming = true })
    } else {
        Column(
            modifier = Modifier.fillMaxWidth().background(SurfaceRaised, RoundedCornerShape(8.dp)).padding(12.dp),
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
                    onClick = { confirming = false; onReset() },
                    modifier = Modifier.focusRequester(confirmFocus),
                )
                ActionChip(label = "Cancel", onClick = { confirming = false })
            }
        }
    }
}

/**
 * About (`SETTINGS.md`): version + Check for updates moved here from the old screen's top - "this
 * is where people look for it" - plus its one grey row.
 */
private fun TvLazyListScope.aboutContent(
    updateStatus: UpdateCheckStatus,
    onCheckForUpdates: () -> Unit,
    firstRowFocus: FocusRequester,
) {
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
    items(ABOUT_GREY_ROWS) { GreyRowContent(it) }
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
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
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
            Text(value, style = RedSurfType.rowSecondary, color = valueColor, maxLines = 1)
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
private fun ActionChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
        colors = RedSurfFocus.colors(),
        scale = RedSurfFocus.scale(),
        border = RedSurfFocus.border(),
        glow = RedSurfFocus.glow(),
    ) {
        Text(
            label,
            style = RedSurfType.rowSecondary,
            color = TextPrimary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}
