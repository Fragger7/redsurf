package com.redsurf.tv.ui.livetv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
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

/**
 * PHASE_3.md P0.3/P0.4 - the Guide's timeline grid. Rows = every live channel in [groupKey]
 * (channels with no `epgChannelId` or no programme data in the window still get a row, with an
 * honest empty-slot cell - PHASE_3.md acceptance #3). Columns = a rolling [WINDOW_HOURS]-hour
 * window from "now."
 *
 * **Deviation from the brief's literal decision 4, logged here rather than silently:** decision 4
 * describes "a static 'now' vertical line drawn once." That presumes every row shares one
 * synchronized horizontal scroll position - a real feature (TiviMate itself works this way), but
 * a meaningfully bigger build (one shared scroll offset every row's `TvLazyRow` obeys, fighting
 * against D-pad focus's own per-row bring-into-view behavior) than P0's time budget allows.
 * Shipped instead: each row scrolls independently (standard Compose), and the currently-airing
 * cell in every row gets a distinct `Accent`-tinted treatment - same information ("what's on now"
 * is visually distinct from "what's next"), cheaper, and arguably more usable on a D-pad (no
 * swipe gesture exists to chase a moving line anyway). A synced-scroll now-line is real P1 work,
 * not a cut corner being hidden.
 *
 * UP/DOWN between rows and LEFT/RIGHT between cells are Compose's own default 2D focus traversal
 * across a `TvLazyColumn` of `TvLazyRow`s - no hand-written key interception needed, the same way
 * `GroupsColumn`/`ChannelsColumn` rely on default focus search for their own 1D case.
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

    // Cheap one-shot entrance (coordinator request, 2026-09-19: push visual quality harder than
    // a bare-functional grid) - fade + slight rise on first composition only (guideMode entering
    // is what gives this a fresh composition; switching categories while already in Guide mode
    // does NOT re-trigger it, since only the row/cell content changes then, not this whole
    // composable). One `Animatable` driving `graphicsLayer`, same cost shape as every other
    // motion in this app (`WaveSpinner`, "The Curl") - nothing per-row, nothing that runs at rest.
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
            // Brand-anchored header (Teleport Menu's own header established this pattern first -
            // the static mark costs nothing per-frame and ties this screen to the brand the same
            // way that one does), not a bare text label.
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

        infoCardProgramme?.let { programme ->
            ProgrammeInfoCard(programme = programme)
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

@Composable
private fun EpgChannelRow(
    channel: ChannelEntity,
    programmes: List<EpgProgramEntity>,
    now: Long,
    onTune: (ChannelEntity) -> Unit,
    onShowInfo: (EpgProgramEntity) -> Unit,
    firstCellFocusRequester: FocusRequester?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            channel.name,
            style = RedSurfType.rowSecondary,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 2.dp),
        )
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
                Box(modifier = Modifier.height(RowHeight).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
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

/** The "on now" glyph (coordinator request, 2026-09-19: reuse RedSurf's established visual
 * language rather than a plain text badge). Exact same brush/arc geometry as `WaveSpinner`
 * (`ui/theme/RedSurfSpinner.kt`) at a tiny static size - the same crescent-wave motif the mark
 * itself and "The Curl" (Teleport Menu) already established, just held still: only ever one or
 * two of these are on screen at once (one per row's currently-airing cell), so even the cost of
 * `WaveSpinner`'s own rotation would be affordable here, but a resting grid shouldn't animate
 * anything it doesn't have to (`TELEPORT_MENU.md`'s own "nothing animates at rest" rule) - the
 * shape alone already reads as distinctly RedSurf's, without spending a frame on it. */
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
    // - Back alone satisfies that without needing to claim D-pad focus onto the card itself, which
    // would also mean remembering to restore focus back onto the grid cell underneath on close).
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
private fun formatTimeRange(start: Long, end: Long): String =
    "${timeFormat.format(Date(start))} – ${timeFormat.format(Date(end))}"
