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
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.Surface as SurfaceColor
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary

/**
 * Middle column: paged channels in the selected group (UI_SPEC.md #4). Uses the index form of
 * TvLazyColumn.items since tv-foundation alpha10 has no items(LazyPagingItems<T>) extension -
 * see docs/plans/PHASE_1.md #2.
 *
 * EPG is not populated in Phase 1 (the sync worker is never scheduled - a later phase), so the
 * subtitle line is always "No schedule information". That's the honest current state, not a
 * placeholder to be embarrassed about.
 */
@Composable
fun ChannelsColumn(
    groupName: String,
    groupCount: Int,
    channels: LazyPagingItems<ChannelEntity>,
    focusedChannelId: String?,
    onChannelFocused: (ChannelEntity) -> Unit,
    onChannelOpen: (ChannelEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxHeight().padding(end = 32.dp)) {
        Text(groupName.replace(";", " › "), style = MaterialTheme.typography.titleLarge, color = TextPrimary, maxLines = 1)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "$groupCount channels",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 20.dp),
        )
        TvLazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

@Composable
private fun ChannelRow(channel: ChannelEntity, selected: Boolean, onFocused: () -> Unit, onOpen: () -> Unit) {
    Surface(
        onClick = onOpen,
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(channel)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    "${channel.num}  ${channel.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("No schedule information", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}

/** Shown while a page hasn't loaded yet - Paging returns null placeholders during that gap. */
@Composable
private fun ChannelRowPlaceholder() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceColor))
    }
}

/** 52dp square logo chip (squarish, matching real channel logo art); falls back to the
 * channel's initial when the icon is blank or fails to load. */
@Composable
private fun ChannelLogo(channel: ChannelEntity) {
    Box(
        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceColor),
        contentAlignment = Alignment.Center,
    ) {
        val icon = channel.streamIcon
        if (icon.isNullOrBlank()) {
            Text(channel.name.take(1).uppercase(), style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
        } else {
            SubcomposeAsyncImage(
                model = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                error = {
                    Text(channel.name.take(1).uppercase(), style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                },
                loading = { /* blank while loading - avoids flicker on a fast-scrolling list */ },
            )
        }
    }
}
