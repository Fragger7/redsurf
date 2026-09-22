package com.redsurf.tv.ui.livetv

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.foundation.lazy.list.rememberTvLazyListState
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.redsurf.tv.db.GroupCount
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.RedSurfType
import com.redsurf.tv.ui.theme.Surface as SurfaceColor
import com.redsurf.tv.ui.theme.SurfaceRaised
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Provider group names are often a raw hierarchy path ("Animation;Kids", "Comedy;Movies;Series")
 * with no space around the separator - unreadable as-is. Displayed with a visual arrow instead;
 * the underlying groupName (used for queries) is untouched.
 */
internal fun formatGroupName(raw: String): String = raw.replace(";", " › ")

/**
 * A group's real identity now that channels/groups are aggregated across every loaded playlist
 * (user request, 2026-09-12) - groupName alone isn't unique any more (two providers can both
 * have "Sports"), so selection, paging queries and list keys all use this pair together.
 */
data class GroupKey(val playlistId: String, val groupName: String)

internal fun GroupCount.key() = GroupKey(playlistId, groupName)

private const val TAG = "GroupsColumn"

/** One row in the rendered list: either a collapsible playlist header or a group beneath one. */
private sealed class GroupsRow {
    data class Header(val playlistId: String, val playlistName: String, val totalCount: Int, val collapsed: Boolean) : GroupsRow()
    data class Item(val group: GroupCount) : GroupsRow()
}

/**
 * Left column: live-channel groups with counts (docs/vision/UI_SPEC.md #4,
 * references/streamvault/LiveTV.png). Focusing a row - not clicking - selects the group; that's
 * what makes browsing cheap, matching TiViMate/StreamVault. onClick is a deliberate no-op on a
 * group row: OK doesn't need to do anything the focus move hasn't already done.
 *
 * Geometry from the reference measured at this canvas (960x540dp): the column is one panel card
 * with the header inside it, and rows are ~36dp tall at 14sp - roughly half the previous pass's
 * 60dp, which is the difference between 3 groups visible and 8. The selected group is marked by
 * an accent bar on its left edge plus a raised fill, not a full red block (see RedSurfFocus).
 *
 * With more than one playlist loaded (user request, 2026-09-12 - multiple playlists coexist and
 * all show up here), groups are organized as a TiviMate-style accordion instead of a flat list
 * with a repeated "PlaylistName › " prefix on every row - the prefix approach shipped first was
 * found to eat most of the column's already-narrow width, hiding the actual group name behind an
 * ellipsis. Each playlist gets one distinctly-styled header row (bold, raised background,
 * chevron, its total channel count) that OK collapses/expands - the common single-playlist case
 * renders with no header at all, pixel-identical to before.
 */
@Composable
fun GroupsColumn(
    groups: List<GroupCount>,
    selectedGroup: GroupKey?,
    onGroupFocused: (GroupKey) -> Unit,
    modifier: Modifier = Modifier,
    // false when a caller is placing focus elsewhere deliberately (ChannelListOverlay, decision
    // 11's "focus lands on the current channel row") - root-caused live, 2026-09-17: this
    // composable's own `groups` param often arrives from a *fresh* `liveGroups()` subscription
    // with no warm cache (confirmed via logcat: ~1.1s to first emission on the user's real ~30K-
    // channel DB), so its own initial-focus claim below - correct and wanted on the browse screen,
    // where nothing else is competing for focus - fires *after* the caller's own claim already
    // succeeded and silently steals it back. True (default) preserves this column's original,
    // still-correct standalone behavior everywhere else (LiveTvScreen).
    claimInitialFocus: Boolean = true,
    // Sprint 2 device verification, 2026-09-22 - real on-device testing of the UP-guard below found
    // the row-0 case (deliberately left to fall through to Compose's own default `moveFocus`) does
    // NOT escape to the NavStrip in practice - three consecutive UP presses at the true top row all
    // stayed on the same row, confirmed via uiautomator focus bounds never changing. This is
    // FOCUS_MODEL.md rule 7 itself ("don't trust Compose's default directional search to stop at a
    // scrollable container's real boundary") applying to the *escape* direction too, not just the
    // in-list case this file's UP-guard already handles explicitly. Fix: an explicit callback, not
    // another default-search attempt - AppShell wires this to the same canonical
    // `navPillFocusRequesters[destination]` lookup the long-press-Back nav-jump feature already
    // established (AGENTS.md's "Global quick-jump to the NavStrip" entry).
    onEscapeUp: () -> Unit = {},
) {
    val multiplePlaylists = remember(groups) { groups.map { it.playlistId }.distinct().size > 1 }
    var collapsedPlaylists by remember { mutableStateOf(setOf<String>()) }

    val rows: List<GroupsRow> = remember(groups, collapsedPlaylists, multiplePlaylists) {
        if (!multiplePlaylists) {
            groups.map { GroupsRow.Item(it) }
        } else {
            buildList {
                groups.groupBy { it.playlistId to it.playlistName }.forEach { (idAndName, groupsForPlaylist) ->
                    val (playlistId, playlistName) = idAndName
                    val collapsed = playlistId in collapsedPlaylists
                    add(GroupsRow.Header(playlistId, playlistName, groupsForPlaylist.sumOf { it.count }, collapsed))
                    if (!collapsed) groupsForPlaylist.forEach { add(GroupsRow.Item(it)) }
                }
            }
        }
    }

    // Initial focus: a TV screen with nothing focused shows no ring, and the first D-pad press
    // is spent just making one appear. Once, when the first group is selected and its row exists,
    // put focus on it - after a frame, so the lazy row is attached. runCatching because a row
    // that isn't attached yet throws rather than no-ops; losing initial focus is harmless,
    // crashing the screen is not.
    //
    // Scroll-to-selected (user-found bug, 2026-09-17): this column had no equivalent of
    // ChannelsColumn's own scroll-to-selected effect at all - it relied entirely on Compose's
    // automatic "bring the newly-focused row into view" behavior, which silently does nothing if
    // that row isn't composed yet (a `TvLazyColumn` only composes rows near its current scroll
    // position). With a real playlist's categories rarely starting near the top, the selected
    // category's row almost never existed yet when `initialFocus.requestFocus()` ran, so it threw
    // (swallowed by `runCatching`), landing the visible list at the top with focus nowhere near
    // the actual selection - correct data, wrong screen. Unlike `ChannelsColumn`'s Paging-backed
    // list, `rows` here is already a fully-realized in-memory list (no async load to race), so a
    // short bounded retry (not the channel fix's longer one) is enough margin for the scroll to
    // actually land before the focus claim.
    val listState = rememberTvLazyListState()
    val initialFocus = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(selectedGroup, rows) {
        if (initialFocusDone || selectedGroup == null) return@LaunchedEffect
        val index = rows.indexOfFirst { it is GroupsRow.Item && it.group.key() == selectedGroup }
        if (index < 0) return@LaunchedEffect
        runCatching { listState.scrollToItem(index) }
        if (!claimInitialFocus) return@LaunchedEffect
        repeat(5) { attempt ->
            if (initialFocusDone) return@repeat
            delay(100)
            initialFocusDone = runCatching { initialFocus.requestFocus() }.isSuccess
            Log.d(TAG, "initialFocus attempt=$attempt success=$initialFocusDone selectedGroup=$selectedGroup")
        }
    }

    // State/focus discipline (AGENTS.md, user directive 2026-09-12): pressing RIGHT from here
    // into Channels and then LEFT back used to land on whatever row directional search resolved
    // to, not the one actually selected - the same class of bug as the fullscreen-exit case, just
    // sideways. `hasFocus` is true for this whole subtree while any row in it is focused and only
    // flips false->true when focus arrives from *outside* it (e.g. RIGHT from Channels back to
    // here) - never during ordinary up/down movement within the column, since hasFocus never
    // leaves true for that. That transition is exactly "just re-entered from a sibling," and is
    // the only time this redirects - it never fights normal in-column navigation afterward.
    var hadFocus by remember { mutableStateOf(false) }

    // Real fix for the fast-scroll bug (user correction, 2026-09-17: not the focus ring lagging -
    // "the list does not follow the focus... instead it waits for the focus to stop, then jumps
    // the scroll to where it landed"). Root cause: this relied entirely on Compose's own default
    // bring-focused-item-into-view behavior, which animates - each new focus event during a held
    // key's repeat stream cancels the prior in-flight animation and starts a new one, so under
    // rapid repeat the scroll position never finishes catching up until the key stops. This tracks
    // the focused row's index explicitly and jumps to it with `scrollToItem` (immediate, no
    // animation to outrun) on every single focus change, independent of Compose's own mechanism.
    var focusedIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(focusedIndex) {
        focusedIndex?.let { runCatching { listState.scrollToItem(it) } }
    }

    // Sprint 2, 2026-09-23 (FOCUS_MODEL.md rule 7) - real boundary guard for UP, where there was
    // none at all before. User report: "UP-scroll escapes to the nav-strip prematurely" - the
    // mechanism the audit found is that this column trusted Compose's default directional search
    // to naturally stop at the list's true top, which a `TvLazyColumn` doesn't reliably do (a row
    // just above the current one that hasn't been composed yet isn't a candidate the search can
    // find, so it looks further afield and finds the NavStrip instead). Fix: intercept UP
    // ourselves whenever the focused row isn't genuinely the first one - explicitly scroll the
    // target row into view first (so it's guaranteed composed), then retry the *default* move
    // (not a manual per-row FocusRequester map, which would mean maintaining one per row) now
    // that the target actually exists. Only a real index-0 UP is left to escape naturally.
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(SurfaceColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .onFocusChanged { state ->
                if (state.hasFocus && !hadFocus) {
                    runCatching { initialFocus.requestFocus() }
                }
                hadFocus = state.hasFocus
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionUp) return@onPreviewKeyEvent false
                val startIndex = focusedIndex ?: return@onPreviewKeyEvent false
                if (startIndex <= 0) {
                    onEscapeUp()
                    return@onPreviewKeyEvent true
                }
                scope.launch {
                    runCatching { listState.scrollToItem((startIndex - 1).coerceAtLeast(0)) }
                    // Retry the *default* move, not a manual per-row target - stop the moment
                    // focusedIndex actually changes (the move succeeded), bounded so a genuinely
                    // stuck case doesn't loop forever.
                    repeat(5) {
                        if (focusedIndex != startIndex) return@launch
                        delay(30)
                        focusManager.moveFocus(FocusDirection.Up)
                    }
                }
                true
            },
    ) {
        Text(
            "Categories",
            style = RedSurfType.sectionTitle,
            color = TextPrimary,
            modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
        )
        TvLazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(
                rows,
                key = { _, row ->
                    when (row) {
                        is GroupsRow.Header -> "hdr:${row.playlistId}"
                        is GroupsRow.Item -> "grp:${row.group.playlistId}:${row.group.groupName}"
                    }
                },
            ) { index, row ->
                when (row) {
                    is GroupsRow.Header -> PlaylistHeaderRow(
                        row = row,
                        onToggle = {
                            collapsedPlaylists = if (row.collapsed) {
                                collapsedPlaylists - row.playlistId
                            } else {
                                collapsedPlaylists + row.playlistId
                            }
                        },
                    )
                    is GroupsRow.Item -> {
                        val selected = row.group.key() == selectedGroup
                        GroupRow(
                            group = row.group,
                            selected = selected,
                            onFocused = {
                                onGroupFocused(row.group.key())
                                focusedIndex = index
                            },
                            modifier = if (selected) Modifier.focusRequester(initialFocus) else Modifier,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The accordion's distinct playlist-root look (user request, 2026-09-12: "give a distinct look
 * to the playlist name/root so the user can quickly see it on a fast scroll") - bold text, a
 * raised background so it visually separates from the group rows under it, and a chevron that
 * doubles as the collapse/expand affordance. OK toggles collapse - unlike a group row, this one's
 * onClick does something real.
 */
@Composable
private fun PlaylistHeaderRow(row: GroupsRow.Header, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        shape = RedSurfFocus.shape(8.dp),
        colors = RedSurfFocus.rowColors(resting = SurfaceRaised),
        scale = RedSurfFocus.scale(),
        border = RedSurfFocus.border(),
        glow = RedSurfFocus.glow(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (row.collapsed) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (row.collapsed) "Collapsed" else "Expanded",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                row.playlistName,
                style = RedSurfType.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                row.totalCount.toString(),
                style = RedSurfType.rowSecondary,
                color = TextSecondary,
                modifier = Modifier.padding(start = 10.dp, end = 4.dp),
            )
        }
    }
}

@Composable
private fun GroupRow(
    group: GroupCount,
    selected: Boolean,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // D-pad focus, not `selected` (the active group, which can differ from where focus currently
    // is - e.g. browsing Channels while a group remains selected on the left). Marquee (BACKLOG_
    // SWEEP-adjacent decision, AGENTS.md 2026-09-15: "only the focused row marquees") needs the
    // former.
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = {},
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            },
        shape = RedSurfFocus.shape(8.dp),
        colors = RedSurfFocus.rowColors(selected = selected),
        scale = RedSurfFocus.scale(),
        border = RedSurfFocus.border(),
        glow = RedSurfFocus.glow(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Selection marker: a 3dp accent bar, present in layout always so text doesn't shift.
            Box(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .width(3.dp)
                    .height(18.dp)
                    .background(if (selected) Accent else SurfaceColor.copy(alpha = 0f), RoundedCornerShape(2.dp)),
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                formatGroupName(group.groupName),
                style = RedSurfType.rowTitle,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).let { if (isFocused) it.basicMarquee() else it },
            )
            Text(
                group.count.toString(),
                style = RedSurfType.rowSecondary,
                color = TextSecondary,
                modifier = Modifier.padding(start = 10.dp, end = 12.dp),
            )
        }
    }
}
