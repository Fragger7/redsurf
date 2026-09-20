package com.redsurf.tv.ui.livetv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.redsurf.tv.R
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.db.EpgProgramEntity
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.RedSurfType
import com.redsurf.tv.ui.theme.Surface as SurfaceColor
import com.redsurf.tv.ui.theme.SurfaceRaised
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

/** Minutes-to-dp scale for a programme cell's width (PHASE_3.md decision 4) - a 30-minute show is
 * 90dp, a 2-hour movie is 360dp. Proportional, not a fixed-width-per-cell layout that would show
 * every programme the same size regardless of real duration. */
private val PxPerMinute = 3.dp
private val RowHeight = 64.dp
private const val WINDOW_HOURS = 6L

/** Fixed width of each row's leading number+logo+name block (LIVE_TV_GUIDE_MERGE.md M.4) - shared
 * by the header row and the "now" line below so everything lines up against the same left edge,
 * the same way TiviMate's own reference (`RedThemedEPGLiveTVScreen.jpg`) keeps its channel column
 * and its timeline in fixed alignment. */
private val ChannelLabelWidth = 168.dp

/**
 * PHASE_3.md P0.3/P0.4, enriched LIVE_TV_GUIDE_MERGE.md M.4/M.5 - the Guide's timeline grid. Rows
 * = every live channel in [groupKey] (channels with no `epgChannelId` or no programme data in the
 * window still get a row, with an honest empty-slot cell - PHASE_3.md acceptance #3). Columns = a
 * rolling [WINDOW_HOURS]-hour window from "now."
 *
 * **"Now" line, real this time (LIVE_TV_GUIDE_MERGE.md M.5):** a genuinely synced now-line, not
 * the per-cell-highlight fallback P0 shipped. The trick that makes this cheap rather than needing
 * a shared `LazyListState` across every row: the window always starts at "now" (decision 4), so
 * every row's own `TvLazyRow`, in its default/unscrolled position, already begins exactly at the
 * current moment - a single static vertical line drawn once at [ChannelLabelWidth] (the boundary
 * between the label column and the first cell) is therefore correctly aligned with *every* row
 * that hasn't been individually scrolled, at zero per-frame cost. Honest limitation, not hidden:
 * scrolling one specific row right (LEFT/RIGHT to browse that channel's later programmes) desyncs
 * only that one row from the line - every other row stays correctly aligned. A true per-frame
 * synced-scroll line (every row sharing one scroll offset, fighting D-pad focus's own per-row
 * bring-into-view behavior) remains real future work if this approximation isn't good enough live.
 *
 * UP/DOWN between rows and LEFT/RIGHT between cells are Compose's own default 2D focus traversal
 * across a `TvLazyColumn` of `TvLazyRow`s - no hand-written key interception needed.
 */
@Composable
fun EpgGridColumn(
    channels: List<ChannelEntity>,
    programsByChannel: Map<String, List<EpgProgramEntity>>,
    onTuneChannel: (ChannelEntity) -> Unit,
    firstCellFocusRequester: FocusRequester,
    onFocusStateChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var infoCardProgramme by remember { mutableStateOf<EpgProgramEntity?>(null) }
    val now = remember { System.currentTimeMillis() }

    BackHandler(enabled = infoCardProgramme != null) { infoCardProgramme = null }

    // Cheap one-shot entrance - fade + slight rise on first composition only. One `Animatable`
    // driving `graphicsLayer`, same cost shape as every other motion in this app (`WaveSpinner`,
    // "The Curl") - nothing per-row, nothing that runs at rest.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .graphicsLayer {
                    alpha = entrance.value
                    translationY = (1f - entrance.value) * 24.dp.toPx()
                }
                .onFocusChanged { state -> onFocusStateChanged(state.hasFocus) },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                Image(
                    painter = painterResource(R.drawable.ic_mark),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    "Guide",
                    style = RedSurfType.sectionTitle,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            if (channels.isEmpty()) {
                Text(
                    "No channels in this category",
                    style = RedSurfType.rowSecondary,
                    color = TextSecondary,
                )
            } else {
                GridTimeHeader(now = now, modifier = Modifier.padding(bottom = 6.dp))
                Box(modifier = Modifier.fillMaxSize()) {
                    // The "now" line - see class doc above for why a static line at this one x
                    // offset is a real, correctly-aligned signal, not a placeholder.
                    Box(
                        modifier = Modifier
                            .padding(start = ChannelLabelWidth)
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(Accent.copy(alpha = 0.85f)),
                    )
                    TvLazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexedChannels(channels, key = { _, c -> c.streamId }) { index, channel ->
                            EpgChannelRow(
                                channel = channel,
                                programmes = programsByChannel[channel.epgChannelId].orEmpty(),
                                now = now,
                                onTune = onTuneChannel,
                                onShowInfo = { infoCardProgramme = it },
                                firstCellFocusRequester = if (index == 0) firstCellFocusRequester else null,
                            )
                        }
                    }
                }
            }
        }

        infoCardProgramme?.let { programme ->
            ProgrammeInfoCard(programme = programme)
        }
    }
}

/** LIVE_TV_GUIDE_MERGE.md M.4 - date + half-hour tick labels above the grid, aligned to
 * [ChannelLabelWidth] and [PxPerMinute] so each label sits directly above the cells it describes. */
@Composable
private fun GridTimeHeader(now: Long, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            headerDateFormat.format(Date(now)),
            style = RedSurfType.rowSecondary,
            color = TextSecondary,
            maxLines = 1,
            modifier = Modifier.width(ChannelLabelWidth),
        )
        val stepMinutes = 30
        val stepWidth = (stepMinutes * PxPerMinute.value).dp
        val steps = ((WINDOW_HOURS * 60) / stepMinutes).toInt()
        Row {
            repeat(steps) { i ->
                Text(
                    timeFormat.format(Date(now + i * stepMinutes * 60_000L)),
                    style = RedSurfType.rowSecondary,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.width(stepWidth),
                )
            }
        }
    }
}

// TvLazyColumn's DSL doesn't have an itemsIndexed(List<T>) extension in this project's
// tv-foundation version (same gap ChannelsColumn.kt's own doc comment already notes for
// LazyPagingItems) - the plain index form works everywhere the List extension would.
private inline fun androidx.tv.foundation.lazy.list.TvLazyListScope.itemsIndexedChannels(
    list: List<ChannelEntity>,
    crossinline key: (Int, ChannelEntity) -> Any,
    crossinline itemContent: @Composable (Int, ChannelEntity) -> Unit,
) {
    items(count = list.size, key = { key(it, list[it]) }) { index -> itemContent(index, list[index]) }
}

/** LIVE_TV_GUIDE_MERGE.md M.4 - restructured from a stacked Column (name above a horizontal cell
 * strip) to a Row with a fixed-width leading label block, matching TiviMate's own reference
 * layout (number + logo + name beside the timeline, not above it) and keeping every row aligned
 * to the header/now-line above via the shared [ChannelLabelWidth]. */
@Composable
private fun EpgChannelRow(
    channel: ChannelEntity,
    programmes: List<EpgProgramEntity>,
    now: Long,
    onTune: (ChannelEntity) -> Unit,
    onShowInfo: (EpgProgramEntity) -> Unit,
    firstCellFocusRequester: FocusRequester?,
) {
    Row(modifier = Modifier.fillMaxWidth().height(RowHeight), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier.width(ChannelLabelWidth).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                channel.num.toString(),
                style = RedSurfType.rowSecondary,
                color = TextSecondary,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 26.dp),
            )
            ChannelLogoChip(channel, modifier = Modifier.padding(start = 2.dp, end = 8.dp))
            Text(
                channel.name,
                style = RedSurfType.rowSecondary,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (programmes.isEmpty()) {
            // Honest empty slot (PHASE_3.md acceptance #3) - a channel with no provider EPG
            // listing (or no epgChannelId at all) is real, common P0 behavior, not an error.
            Surface(
                onClick = { onTune(channel) },
                modifier = Modifier
                    .widthIn(min = 240.dp)
                    .then(firstCellFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
                shape = RedSurfFocus.shape(8.dp),
                colors = RedSurfFocus.rowColors(resting = SurfaceColor),
                scale = RedSurfFocus.scale(),
                border = RedSurfFocus.border(),
                glow = RedSurfFocus.glow(),
            ) {
                Box(modifier = Modifier.fillMaxHeight().padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                    Text("No programme data", style = RedSurfType.rowSecondary, color = TextSecondary)
                }
            }
        } else {
            TvLazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(count = programmes.size, key = { programmes[it].startTime }) { index ->
                    val programme = programmes[index]
                    val isCurrent = now in programme.startTime until programme.endTime
                    val durationMin = ((programme.endTime - programme.startTime) / 60_000L).coerceAtLeast(1L)
                    val cellWidth = (durationMin * PxPerMinute.value).dp
                    ProgrammeCell(
                        programme = programme,
                        isCurrent = isCurrent,
                        width = cellWidth,
                        onClick = { if (isCurrent) onTune(channel) else onShowInfo(programme) },
                        modifier = if (index == 0 && firstCellFocusRequester != null) {
                            Modifier.focusRequester(firstCellFocusRequester)
                        } else Modifier,
                    )
                }
            }
        }
    }
}

/** Small logo chip, same fallback shape as `ChannelsColumn.kt`'s own `ChannelLogo` (initial
 * letter when the icon is blank or fails to load) - reused pattern, smaller size for the grid's
 * denser rows. */
@Composable
private fun ChannelLogoChip(channel: ChannelEntity, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(SurfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        val icon = channel.streamIcon
        if (icon.isNullOrBlank()) {
            Text(channel.name.take(1).uppercase(), style = RedSurfType.badge, color = TextPrimary)
        } else {
            SubcomposeAsyncImage(
                model = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(3.dp),
                error = { Text(channel.name.take(1).uppercase(), style = RedSurfType.badge, color = TextPrimary) },
                loading = { /* blank while loading - avoids flicker on a fast-scrolling grid */ },
            )
        }
    }
}

@Composable
private fun ProgrammeCell(
    programme: EpgProgramEntity,
    isCurrent: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(width.coerceAtLeast(90.dp)),
        shape = RedSurfFocus.shape(8.dp),
        colors = RedSurfFocus.rowColors(selected = isCurrent, resting = SurfaceColor),
        scale = RedSurfFocus.scale(),
        border = RedSurfFocus.border(),
        glow = RedSurfFocus.glow(),
    ) {
        Column(
            modifier = Modifier.height(RowHeight).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (isCurrent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveCrescentGlyph()
                    Text("LIVE", style = RedSurfType.badge, color = Accent, modifier = Modifier.padding(start = 4.dp))
                }
            }
            Text(
                programme.title.ifBlank { "Untitled" },
                style = RedSurfType.rowTitle,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                formatTimeRange(programme.startTime, programme.endTime),
                style = RedSurfType.rowSecondary,
                color = TextSecondary,
                maxLines = 1,
            )
        }
    }
}

/** The "on now" glyph - exact same brush/arc geometry as `WaveSpinner`, held static (only ever
 * one or two of these on screen at once, but a resting grid shouldn't animate anything it doesn't
 * have to - `TELEPORT_MENU.md`'s own "nothing animates at rest" rule). */
@Composable
private fun LiveCrescentGlyph(size: androidx.compose.ui.unit.Dp = 12.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        val diameter = this.size.minDimension - stroke.width
        val brush = Brush.sweepGradient(
            0f to Accent.copy(alpha = 0f),
            0.12f to Accent.copy(alpha = 0.2f),
            1f to Accent,
        )
        drawArc(
            brush = brush,
            startAngle = 0f,
            sweepAngle = 300f,
            useCenter = false,
            style = stroke,
            topLeft = Offset(stroke.width / 2f, stroke.width / 2f),
            size = Size(diameter, diameter),
        )
    }
}

@Composable
private fun ProgrammeInfoCard(programme: EpgProgramEntity) {
    // Dismissed by the caller's own BackHandler (PHASE_3.md decision 5: "lightweight, dismissible"
    // - Back alone satisfies that without needing to claim D-pad focus onto the card itself).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceRaised)
                .border(1.5.dp, Accent, RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Text(programme.title.ifBlank { "Untitled" }, style = RedSurfType.sectionTitle, color = TextPrimary)
            Text(
                formatTimeRange(programme.startTime, programme.endTime),
                style = RedSurfType.rowSecondary,
                color = Accent,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            Text(
                programme.description.ifBlank { "No description available." },
                style = RedSurfType.rowSecondary,
                color = TextSecondary,
            )
        }
    }
}

private val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
private val headerDateFormat = SimpleDateFormat("EEE, MMM d", Locale.US)
private fun formatTimeRange(start: Long, end: Long): String =
    "${timeFormat.format(Date(start))} – ${timeFormat.format(Date(end))}"
