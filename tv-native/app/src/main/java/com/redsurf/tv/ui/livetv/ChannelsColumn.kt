package com.redsurf.tv.ui.livetv

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.RedSurfType
import com.redsurf.tv.ui.theme.Surface as SurfaceColor
import com.redsurf.tv.ui.theme.SurfaceRaised
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary

/**
 * Middle column: paged channels in the selected group (UI_SPEC.md #4). Uses the index form of
 * TvLazyColumn.items since tv-foundation alpha10 has no items(LazyPagingItems<T>) extension -
 * see docs/plans/PHASE_1.md #2.
 *
 * Geometry from references/streamvault/LiveTV.png at this canvas: rows ~48dp with a 36dp square
 * logo, channel number in secondary colour ahead of the name, programme line beneath. The
 * previous pass's 84dp rows with 52dp logos fit three channels on screen; this fits six.
 *
 * EPG is not populated in Phase 1 (the sync worker is never scheduled - a later phase), so the
 * subtitle line is always "No schedule information". That's the honest current state, not a
 * placeholder to be embarrassed about.
 */
@Composable
fun ChannelsColumn(
    groupTitle: String,
    groupCount: Int,
    channels: LazyPagingItems<ChannelEntity>,
    focusedChannelId: String?,
    onChannelFocused: (ChannelEntity) -> Unit,
    onChannelOpen: (ChannelEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 14dp top padding matches the inner padding of the two panel cards either side, so all three
    // column headers sit on one baseline. groupTitle arrives pre-formatted (";" -> "›", and
    // playlist-name-prefixed when more than one playlist is loaded - GroupsColumn.kt) so both
    // columns format group names identically without duplicating that logic here.
    Column(modifier = modifier.fillMaxHeight().padding(top = 14.dp)) {
        Text(
            groupTitle,
            style = RedSurfType.sectionTitle,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "$groupCount channels",
            style = RedSurfType.rowSecondary,
            color = TextSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )
        TvLazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(count = channels.itemCount, key = channels.itemKey { it.streamId }) { index ->
                val channel = channels[index]
                if (channel != null) {
                    ChannelRow(
                        channel = channel,
                        selected = channel.streamId == focusedChannelId,
                        onFocused = { onChannelFocused(channel) },
                        onOpen = { onChannelOpen(channel) },
                    )
                } else {
                    ChannelRowPlaceholder()
                }
            }
        }
    }
}

private val RowHeight = 48.dp
private val LogoSize = 36.dp

@Composable
private fun ChannelRow(channel: ChannelEntity, selected: Boolean, onFocused: () -> Unit, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = RedSurfFocus.shape(12.dp),
        colors = RedSurfFocus.rowColors(selected = selected, resting = SurfaceColor),
        scale = RedSurfFocus.scale(),
        border = RedSurfFocus.border(),
        glow = RedSurfFocus.glow(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(RowHeight).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(channel)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        channel.num.toString(),
                        style = RedSurfType.rowTitle,
                        color = TextSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        channel.name,
                        style = RedSurfType.rowTitle,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "No schedule information",
                    style = RedSurfType.rowSecondary,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Shown while a page hasn't loaded yet - Paging returns null placeholders during that gap. */
@Composable
private fun ChannelRowPlaceholder() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .background(SurfaceColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(LogoSize).clip(RoundedCornerShape(8.dp)).background(SurfaceRaised))
    }
}

/** Square logo chip on a raised tile, matching real channel-logo art; falls back to the
 * channel's initial when the icon is blank or fails to load. */
@Composable
private fun ChannelLogo(channel: ChannelEntity) {
    Box(
        modifier = Modifier.size(LogoSize).clip(RoundedCornerShape(8.dp)).background(SurfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        val icon = channel.streamIcon
        if (icon.isNullOrBlank()) {
            Text(channel.name.take(1).uppercase(), style = RedSurfType.rowTitle, color = TextPrimary)
        } else {
            SubcomposeAsyncImage(
                model = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(4.dp),
                error = {
                    Text(channel.name.take(1).uppercase(), style = RedSurfType.rowTitle, color = TextPrimary)
                },
                loading = { /* blank while loading - avoids flicker on a fast-scrolling list */ },
            )
        }
    }
}
