package com.redsurf.tv.ui.livetv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.redsurf.tv.db.GroupCount
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary

/**
 * Provider group names are often a raw hierarchy path ("Animation;Kids", "Comedy;Movies;Series")
 * with no space around the separator - unreadable as-is. Displayed with a visual arrow instead;
 * the underlying groupName (used for queries) is untouched.
 */
private fun formatGroupName(raw: String): String = raw.replace(";", " › ")

/**
 * Left column: live-channel groups with counts (docs/vision/UI_SPEC.md #4,
 * references/streamvault/LiveTV.png). Focusing a row - not clicking - selects the group; that's
 * what makes browsing cheap, matching TiViMate/StreamVault. onClick is a deliberate no-op: OK
 * doesn't need to do anything the focus move hasn't already done.
 */
@Composable
fun GroupsColumn(
    groups: List<GroupCount>,
    selectedGroup: String?,
    onGroupFocused: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxHeight().padding(end = 16.dp)) {
        Text(
            "Categories",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        TvLazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(groups, key = { it.groupName }) { group ->
                GroupRow(
                    group = group,
                    selected = group.groupName == selectedGroup,
                    onFocused = { onGroupFocused(group.groupName) },
                )
            }
        }
    }
}

@Composable
private fun GroupRow(group: GroupCount, selected: Boolean, onFocused: () -> Unit) {
    Surface(
        onClick = {},
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { if (it.isFocused) onFocused() },
        colors = RedSurfFocus.colors(selected = selected),
        scale = RedSurfFocus.scale(),
        border = RedSurfFocus.border(),
        glow = RedSurfFocus.glow(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatGroupName(group.groupName),
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                maxLines = 1,
            )
            Text(group.count.toString(), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}
