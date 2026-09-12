package com.redsurf.tv.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.redsurf.tv.db.GroupCount
import kotlinx.coroutines.delay
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.RedSurfType
import com.redsurf.tv.ui.theme.Surface as SurfaceColor
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary

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

/**
 * Left column: live-channel groups with counts (docs/vision/UI_SPEC.md #4,
 * references/streamvault/LiveTV.png). Focusing a row - not clicking - selects the group; that's
 * what makes browsing cheap, matching TiViMate/StreamVault. onClick is a deliberate no-op: OK
 * doesn't need to do anything the focus move hasn't already done.
 *
 * Geometry from the reference measured at this canvas (960x540dp): the column is one panel card
 * with the header inside it, and rows are ~36dp tall at 14sp - roughly half the previous pass's
 * 60dp, which is the difference between 3 groups visible and 8. The selected group is marked by
 * an accent bar on its left edge plus a raised fill, not a full red block (see RedSurfFocus).
 */
@Composable
fun GroupsColumn(
    groups: List<GroupCount>,
    selectedGroup: GroupKey?,
    onGroupFocused: (GroupKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Only label rows with their playlist name when there's more than one playlist loaded - the
    // common single-playlist case stays exactly as it was (no redundant prefix on every row).
    val multiplePlaylists = remember(groups) { groups.map { it.playlistId }.distinct().size > 1 }

    // Initial focus: a TV screen with nothing focused shows no ring, and the first D-pad press
    // is spent just making one appear. Once, when the first group is selected and its row exists,
    // put focus on it - after a frame, so the lazy row is attached. runCatching because a row
    // that isn't attached yet throws rather than no-ops; losing initial focus is harmless,
    // crashing the screen is not.
    val initialFocus = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }
    LaunchedEffect(selectedGroup, groups.isNotEmpty()) {
        if (!initialFocusDone && selectedGroup != null && groups.isNotEmpty()) {
            delay(100)
            initialFocusDone = runCatching { initialFocus.requestFocus() }.isSuccess
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(SurfaceColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Text(
            "Categories",
            style = RedSurfType.sectionTitle,
            color = TextPrimary,
            modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
        )
        TvLazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(groups, key = { "${it.playlistId}:${it.groupName}" }) { group ->
                val selected = group.key() == selectedGroup
                GroupRow(
                    group = group,
                    selected = selected,
                    showPlaylistName = multiplePlaylists,
                    onFocused = { onGroupFocused(group.key()) },
                    modifier = if (selected) Modifier.focusRequester(initialFocus) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: GroupCount,
    selected: Boolean,
    showPlaylistName: Boolean,
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
                if (showPlaylistName) "${group.playlistName} › ${formatGroupName(group.groupName)}"
                else formatGroupName(group.groupName),
                style = RedSurfType.rowTitle,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
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
