package com.redsurf.tv.ui.livetv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.db.EpgProgramEntity
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.Background
import com.redsurf.tv.ui.theme.RedSurfDensity
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.RedSurfType
import com.redsurf.tv.ui.theme.SurfaceBand
import com.redsurf.tv.ui.theme.SurfaceRaised
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Lattice colours, hoisted (EPG_GRID_REDESIGN.md G.4) - a `Color.copy` per draw op per row per
// frame is exactly the allocation pattern a 449MB device doesn't need.
private val RowHairline = Color.White.copy(alpha = 0.06f)
private val HeaderHairline = Color.White.copy(alpha = 0.08f)
private val Spine = Color.White.copy(alpha = 0.10f)
private val TickHour = Color.White.copy(alpha = 0.10f)
private val TickHalf = Color.White.copy(alpha = 0.045f)
private val SlotTick = Color.White.copy(alpha = 0.14f)
private val CurrentFill = Accent.copy(alpha = 0.20f)
private val CursorFill = Accent.copy(alpha = 0.16f)
private val GapLine = TextSecondary.copy(alpha = 0.18f)
private val NowWakeStart = Accent.copy(alpha = 0f)
private val NowWakeEnd = Accent.copy(alpha = 0.10f)
private val PastTitle = TextPrimary.copy(alpha = 0.5f)
private val RestingTitle = TextPrimary.copy(alpha = 0.85f)
private val ChannelName = TextPrimary.copy(alpha = 0.92f)
private val ChannelNumber = TextSecondary.copy(alpha = 0.6f)
private val HeaderHour = TextPrimary.copy(alpha = 0.85f)
private val HeaderHalf = TextSecondary.copy(alpha = 0.6f)
private val NoListings = TextSecondary.copy(alpha = 0.45f)

private data class CursorKey(val playlistId: String, val streamId: String, val slotStart: Long)

/**
 * EPG_GRID_REDESIGN.md - "The Lattice." The Guide's timeline grid, rebuilt around one idea from
 * the Opus consultation: **draw the grid, and let the cells be content on it.** The previous
 * version made every programme a boxed `Surface` with its own shadow/border/scale and left the
 * time structure invisible; this one draws each row's structure once (`drawBehind`: row fill,
 * hairline, half-hour ticks) and cells are just text on that lattice, with a short inset tick at
 * each slot's start edge so a row reads as a ribbon marked along a timeline rather than a wall of
 * boxes. Materially cheaper than what it replaces: no shadow pass, no per-cell border, ~10 draw
 * primitives per row.
 *
 * Time model (G.1/G.2, `EpgSlots.kt`): every row renders a slot list that tiles the window
 * exactly, so cell widths are truthful against the header by construction. The minute scale is
 * derived from the measured viewport so exactly [VISIBLE_MINUTES] fill it on any screen; the
 * window starts on the previous :00/:30 so labels are round and the now-line has room to move.
 *
 * Shared horizontal axis (G.8): every row's timeline and the header's ruler scroll one shared
 * [ScrollState]. Deviation from the consultation's literal Part 8 form (one `horizontalScroll` on
 * the whole column with counter-translated labels), logged rather than hidden: with a single
 * scrolled container the pinned label block sits *over* the leftmost 150dp of the scrolled
 * content, so `bringIntoView` would happily "reveal" a cell straight underneath the opaque label.
 * Giving each row its own `horizontalScroll(sharedScroll)` on just its timeline portion keeps the
 * label outside the scrolled viewport entirely - no overlap, no `graphicsLayer` per row, and the
 * shared state still moves every row and the ruler together.
 *
 * Nothing animates at rest (`TELEPORT_MENU.md`'s rule): the one motion is "The Wash" (G.6), a
 * 180ms sweep across a cell on focus arrival, driven by one grid-level `Animatable` that the
 * focused cell alone reads while it runs.
 */
@Composable
fun EpgGridColumn(
    channels: List<ChannelEntity>,
    programsByChannel: Map<String, List<EpgProgramEntity>>,
    groupName: String?,
    now: Long,
    windowStart: Long,
    onTuneChannel: (ChannelEntity) -> Unit,
    firstCellFocusRequester: FocusRequester,
    // Sprint 2, 2026-09-23 (SEQUENCING.md Finding 5/6, FOCUS_MODEL.md rule 2) - when non-null and
    // present in [channels], the entry focus target is *that* channel's row (its first/current
    // slot), not always row 0 - restores the same "return to the exact channel" behaviour
    // `ChannelsColumn.kt`'s `returnFocusRequester` already has, lost when the Guide merge replaced
    // it with this grid's coarser row-0-only claim. Falls back to row 0 when null or not found
    // (fresh browsing, nothing to return to).
    targetChannelStreamId: String? = null,
    onCursorChanged: (ChannelEntity, EpgSlot) -> Unit = { _, _ -> },
    onFocusStateChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var infoCardProgramme by remember { mutableStateOf<EpgProgramEntity?>(null) }
    BackHandler(enabled = infoCardProgramme != null) { infoCardProgramme = null }

    // Cheap one-shot entrance kept from the previous build - one `Animatable`, one layer, once.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }

    val sharedScroll = rememberScrollState()
    // Sprint 2, 2026-09-23 (Finding 2) - this grid stays permanently composed (LiveTvScreen's own
    // `visible` param, Sprint 1), so `sharedScroll` was never reset between categories: scrolling
    // right in one category and switching to another left the axis exactly where it was, so the
    // newly-focused row-0 cell (already correctly "now"-aligned) scrolled out of view - reading as
    // "focus lands on the rightmost cell" even though the real target was always the leftmost one.
    // A fresh category's timeline always starts back at "now."
    LaunchedEffect(channels, groupName) { sharedScroll.scrollTo(0) }
    var cursorKey by remember { mutableStateOf<CursorKey?>(null) }
    val wash = remember { Animatable(1f) }
    LaunchedEffect(cursorKey) {
        if (cursorKey != null) {
            wash.snapTo(0f)
            wash.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        }
    }

    val density = LocalDensity.current
    val gapDash = remember(density) {
        with(density) { PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx())) }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = entrance.value
                translationY = (1f - entrance.value) * 24.dp.toPx()
            }
            .onFocusChanged { state -> onFocusStateChanged(state.hasFocus) },
    ) {
        val labelWidth = RedSurfDensity.GridLabelWidth
        val timelineViewport = (maxWidth - labelWidth).coerceAtLeast(1.dp)
        val pxPerMinute: Dp = timelineViewport / VISIBLE_MINUTES
        val timelineContentWidth: Dp = pxPerMinute * WINDOW_MINUTES
        val pxPerMinutePx = with(density) { pxPerMinute.toPx() }
        val labelWidthPx = with(density) { labelWidth.toPx() }
        val headerHeightPx = with(density) { RedSurfDensity.GridHeaderHeight.toPx() }
        val nowStrokePx = with(density) { 2.dp.toPx() }
        val nowCapPx = with(density) { 3.dp.toPx() }
        val wakePx = with(density) { 28.dp.toPx() }
        val hasRows = channels.isNotEmpty()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    // G.7 "The Crest" - drawn after the rows, in viewport space, clipped to the
                    // timeline so it never crosses the channel column. Three ops for the screen.
                    if (!hasRows) return@drawWithContent
                    val minutesIn = (now - windowStart) / MINUTE_MS.toFloat()
                    val x = labelWidthPx + minutesIn * pxPerMinutePx - sharedScroll.value
                    if (x < labelWidthPx || x > size.width) return@drawWithContent
                    clipRect(left = labelWidthPx, top = 0f, right = size.width, bottom = size.height) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to NowWakeStart,
                                1f to NowWakeEnd,
                                startX = x - wakePx,
                                endX = x,
                            ),
                            topLeft = Offset(x - wakePx, headerHeightPx),
                            size = Size(wakePx, size.height - headerHeightPx),
                        )
                        drawLine(Accent, Offset(x, headerHeightPx), Offset(x, size.height), strokeWidth = nowStrokePx)
                        drawCircle(Accent, radius = nowCapPx, center = Offset(x, headerHeightPx))
                    }
                },
        ) {
            GridTimeHeader(
                windowStart = windowStart,
                pxPerMinute = pxPerMinute,
                timelineContentWidth = timelineContentWidth,
                sharedScroll = sharedScroll,
            )
            if (!hasRows) {
                Text(
                    "No channels in this category",
                    style = RedSurfType.rowSecondary,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp),
                )
            } else {
                // Resolve once per (channels, target) change, not per row - a linear scan over a
                // real category's channel list on every recomposition would be real, avoidable work.
                val targetIndex = remember(channels, targetChannelStreamId) {
                    targetChannelStreamId?.let { id -> channels.indexOfFirst { it.streamId == id } } ?: -1
                }
                TvLazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(count = channels.size, key = { channels[it].streamId }) { index ->
                        val channel = channels[index]
                        val isFocusTarget = if (targetIndex >= 0) index == targetIndex else index == 0
                        val programmes = programsByChannel[channel.epgChannelId].orEmpty()
                        val slots = remember(programmes, windowStart) {
                            slotsFor(programmes, windowStart, windowStart + WINDOW_MINUTES * MINUTE_MS)
                        }
                        EpgChannelRow(
                            channel = channel,
                            displayName = remember(channel.name, groupName) { stripCategoryPrefix(channel.name, groupName) },
                            slots = slots,
                            now = now,
                            pxPerMinute = pxPerMinute,
                            timelineContentWidth = timelineContentWidth,
                            sharedScroll = sharedScroll,
                            gapDash = gapDash,
                            wash = wash,
                            onCursor = { key, slot ->
                                if (key != cursorKey) {
                                    cursorKey = key
                                    onCursorChanged(channel, slot)
                                }
                            },
                            onTune = onTuneChannel,
                            onShowInfo = { infoCardProgramme = it },
                            firstCellFocusRequester = if (isFocusTarget) firstCellFocusRequester else null,
                        )
                    }
                }
            }
        }

        infoCardProgramme?.let { programme -> ProgrammeInfoCard(programme = programme) }
    }
}

/**
 * G.2/G.9 (consultation Part 9) - a two-tier ruler, not six identical grey labels: on-the-hour
 * labels carry the meridiem and read primary; half-hours are shorter and secondary, so the eye
 * gets a rhythm. A short tick drops from each boundary to meet the lattice's own verticals in
 * the rows below - that join is what makes the time columns read as one structure.
 */
@Composable
private fun GridTimeHeader(
    windowStart: Long,
    pxPerMinute: Dp,
    timelineContentWidth: Dp,
    sharedScroll: ScrollState,
) {
    val density = LocalDensity.current
    val hairlinePx = with(density) { RedSurfDensity.Hairline.toPx() }
    val tickPx = with(density) { 4.dp.toPx() }
    val halfHourPx = with(density) { (pxPerMinute * 30).toPx() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RedSurfDensity.GridHeaderHeight)
            .drawBehind {
                drawLine(HeaderHairline, Offset(0f, size.height - hairlinePx / 2f), Offset(size.width, size.height - hairlinePx / 2f), hairlinePx)
            },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            headerDateFormat.format(Date(windowStart)),
            style = RedSurfType.gridMeta,
            color = TextSecondary,
            maxLines = 1,
            modifier = Modifier.width(RedSurfDensity.GridLabelWidth).padding(start = 4.dp, bottom = 5.dp),
        )
        Box(modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(sharedScroll)) {
            val columns = WINDOW_MINUTES / 30
            Row(
                modifier = Modifier
                    .width(timelineContentWidth)
                    .fillMaxHeight()
                    .drawBehind {
                        for (i in 0..columns) {
                            val x = i * halfHourPx
                            val onHour = isOnTheHour(windowStart + i * HALF_HOUR_MS)
                            drawLine(if (onHour) TickHour else TickHalf, Offset(x, size.height - tickPx), Offset(x, size.height), hairlinePx)
                        }
                    },
                verticalAlignment = Alignment.Bottom,
            ) {
                repeat(columns) { i ->
                    val t = windowStart + i * HALF_HOUR_MS
                    val onHour = isOnTheHour(t)
                    Text(
                        if (onHour) hourFormat.format(Date(t)) else halfHourFormat.format(Date(t)),
                        style = if (onHour) RedSurfType.gridHeaderHour else RedSurfType.gridHeaderHalf,
                        color = if (onHour) HeaderHour else HeaderHalf,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.width(pxPerMinute * 30).padding(start = 4.dp, bottom = 5.dp),
                    )
                }
            }
        }
    }
}

/**
 * One channel row: the 150dp label block (Part 7.4's exact layout) beside a horizontally
 * scrolled timeline of slots. The row's own `drawBehind` is the lattice: fill (a band one step
 * above the background while this row holds the cursor, so the channel stays identifiable with
 * the cursor hours to the right), a bottom hairline; the label draws the spine at its right edge;
 * the timeline draws the half-hour verticals in content space so they scroll with the cells.
 */
@Composable
private fun EpgChannelRow(
    channel: ChannelEntity,
    displayName: String,
    slots: List<EpgSlot>,
    now: Long,
    pxPerMinute: Dp,
    timelineContentWidth: Dp,
    sharedScroll: ScrollState,
    gapDash: PathEffect,
    wash: Animatable<Float, *>,
    onCursor: (CursorKey, EpgSlot) -> Unit,
    onTune: (ChannelEntity) -> Unit,
    onShowInfo: (EpgProgramEntity) -> Unit,
    firstCellFocusRequester: FocusRequester?,
) {
    var rowHasFocus by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val hairlinePx = with(density) { RedSurfDensity.Hairline.toPx() }
    val halfHourPx = with(density) { (pxPerMinute * 30).toPx() }
    val columns = WINDOW_MINUTES / 30

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RedSurfDensity.GridRowHeight)
            .onFocusChanged { rowHasFocus = it.hasFocus }
            .drawBehind {
                drawRect(if (rowHasFocus) SurfaceBand else Background)
                drawLine(RowHairline, Offset(0f, size.height - hairlinePx / 2f), Offset(size.width, size.height - hairlinePx / 2f), hairlinePx)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelLabelBlock(
            channel = channel,
            displayName = displayName,
            marquee = rowHasFocus,
            modifier = Modifier
                .width(RedSurfDensity.GridLabelWidth)
                .fillMaxHeight()
                .drawBehind {
                    drawLine(Spine, Offset(size.width - hairlinePx / 2f, 0f), Offset(size.width - hairlinePx / 2f, size.height), hairlinePx)
                },
        )
        Box(modifier = Modifier.weight(1f).fillMaxHeight().horizontalScroll(sharedScroll)) {
            Row(
                modifier = Modifier
                    .width(timelineContentWidth)
                    .fillMaxHeight()
                    .drawBehind {
                        // i = 0 is the spine's own position; the timeline starts right after it.
                        for (i in 1..columns) {
                            val x = i * halfHourPx
                            drawLine(if (i % 2 == 0) TickHour else TickHalf, Offset(x, 0f), Offset(x, size.height), hairlinePx)
                        }
                    },
            ) {
                slots.forEachIndexed { index, slot ->
                    SlotCell(
                        slot = slot,
                        isFirstInRow = index == 0,
                        channel = channel,
                        now = now,
                        pxPerMinute = pxPerMinute,
                        gapDash = gapDash,
                        wash = wash,
                        onCursor = onCursor,
                        onTune = onTune,
                        onShowInfo = onShowInfo,
                        focusRequester = if (index == 0) firstCellFocusRequester else null,
                    )
                }
            }
        }
    }
}

/** Part 7.4's layout at the 180dp width: 4 | 26 (number, right-aligned) | 6 | 26 (logo) | 8 | 106 (name) | 4. */
@Composable
private fun ChannelLabelBlock(
    channel: ChannelEntity,
    displayName: String,
    marquee: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            channel.num.toString(),
            style = RedSurfType.gridMeta,
            color = ChannelNumber,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(26.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        ChannelLogoChip(channel, size = RedSurfDensity.GridLogo)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            displayName,
            style = RedSurfType.gridChannel,
            color = ChannelName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // The app's established focused-row marquee convention (AGENTS.md, 2026-09-15) - the
            // row holding the cursor, not the cell, since the name belongs to the whole row.
            modifier = Modifier.weight(1f).let { if (marquee) it.basicMarquee() else it },
        )
    }
}

/**
 * One slot on the lattice (G.4/G.5/G.6). A neutralized `Surface` (see `RedSurfFocus.gridCell`)
 * supplies TV focus and D-pad OK → onClick; everything visible is drawn here behind the text:
 * - Aired: a 2dp start-edge tick inset 7dp top and bottom (the whole "ribbon, not boxes" trick);
 *   the currently-airing slot alone is filled, with the same 3dp leading bar `GroupsColumn`'s
 *   selected row already uses; titles of slots wholly in the past dim to half.
 * - Gap: nothing but a dashed centre line across the span (the lattice ticks show through, so the
 *   30-minute columns are visibly present *inside* the gap), and a quiet "No listings" only when
 *   it's the row's first slot and at least 45 minutes long.
 * - Cursor: the row band (drawn by the row), a soft fill, a 1.5dp outline, the 3dp leading bar,
 *   and The Wash - one horizontal gradient sweep for 180ms after focus arrives, then nothing.
 */
@Composable
private fun SlotCell(
    slot: EpgSlot,
    isFirstInRow: Boolean,
    channel: ChannelEntity,
    now: Long,
    pxPerMinute: Dp,
    gapDash: PathEffect,
    wash: Animatable<Float, *>,
    onCursor: (CursorKey, EpgSlot) -> Unit,
    onTune: (ChannelEntity) -> Unit,
    onShowInfo: (EpgProgramEntity) -> Unit,
    focusRequester: FocusRequester?,
) {
    val minutes = (slot.end - slot.start) / MINUTE_MS.toFloat()
    val width = pxPerMinute * minutes
    val style = RedSurfFocus.gridCell(RedSurfDensity.CellRadius)
    val key = remember(channel.playlistId, channel.streamId, slot.start) { CursorKey(channel.playlistId, channel.streamId, slot.start) }
    var focused by remember { mutableStateOf(false) }
    val isCurrent = slot is AiredSlot && now >= slot.start && now < slot.end
    val isPast = slot.end <= now
    val density = LocalDensity.current
    val tickInsetPx = with(density) { 7.dp.toPx() }
    val tickStrokePx = with(density) { 2.dp.toPx() }
    val barPx = with(density) { 3.dp.toPx() }
    val insetPx = with(density) { 1.dp.toPx() }
    val radiusPx = with(density) { RedSurfDensity.CellRadius.toPx() }
    val outlinePx = with(density) { 1.5.dp.toPx() }
    val hairlinePx = with(density) { RedSurfDensity.Hairline.toPx() }
    val labelClearancePx = with(density) { 74.dp.toPx() }

    Surface(
        onClick = {
            when (slot) {
                is AiredSlot -> if (isCurrent) onTune(channel) else onShowInfo(slot.programme)
                is GapSlot -> onTune(channel)
            }
        },
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onCursor(key, slot)
            }
            .drawBehind {
                val w = size.width
                val h = size.height
                when (slot) {
                    is AiredSlot -> {
                        if (isCurrent) {
                            drawRoundRect(CurrentFill, Offset(0f, insetPx), Size(w, h - 2 * insetPx), CornerRadius(radiusPx))
                            drawRect(Accent, Offset.Zero, Size(barPx, h))
                        } else {
                            drawLine(SlotTick, Offset(tickStrokePx / 2f, tickInsetPx), Offset(tickStrokePx / 2f, h - tickInsetPx), tickStrokePx)
                        }
                    }
                    is GapSlot -> {
                        // Leave room for the "No listings" caption when this slot shows one
                        // (first in its row and ≥45 min) - otherwise the dash runs through it.
                        val dashStart = if (isFirstInRow && minutes >= 45f) labelClearancePx else 0f
                        if (w > dashStart) drawLine(GapLine, Offset(dashStart, h / 2f), Offset(w, h / 2f), hairlinePx, pathEffect = gapDash)
                    }
                }
                if (focused) {
                    drawRoundRect(CursorFill, Offset(0f, insetPx), Size(w, h - 2 * insetPx), CornerRadius(radiusPx))
                    drawRoundRect(
                        Accent,
                        Offset(outlinePx / 2f, insetPx + outlinePx / 2f),
                        Size(w - outlinePx, h - 2 * insetPx - outlinePx),
                        CornerRadius(radiusPx),
                        style = Stroke(outlinePx),
                    )
                    drawRect(Accent, Offset.Zero, Size(barPx, h))
                    val sweep = wash.value
                    if (sweep < 1f) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Transparent,
                                0.5f to Accent.copy(alpha = 0.30f * (1f - sweep)),
                                1f to Color.Transparent,
                                startX = -w + 2f * w * sweep,
                                endX = 2f * w * sweep,
                            ),
                            topLeft = Offset(0f, insetPx),
                            size = Size(w, h - 2 * insetPx),
                        )
                    }
                }
            },
        shape = style.shape,
        colors = style.colors,
        scale = style.scale,
        border = style.border,
        glow = style.glow,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(start = RedSurfDensity.CellPadH + 3.dp, end = RedSurfDensity.CellPadH),
            contentAlignment = Alignment.CenterStart,
        ) {
            when (slot) {
                is AiredSlot -> Text(
                    slot.programme.title.ifBlank { "Untitled" },
                    style = RedSurfType.gridCell,
                    color = when {
                        focused || isCurrent -> TextPrimary
                        isPast -> PastTitle
                        else -> RestingTitle
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                is GapSlot -> if (isFirstInRow && minutes >= 45f) {
                    Text("No listings", style = RedSurfType.gridMeta, color = NoListings, maxLines = 1)
                }
            }
        }
    }
}

/** Logo chip, same initial-letter fallback as `ChannelsColumn.kt`'s own `ChannelLogo`. */
@Composable
internal fun ChannelLogoChip(channel: ChannelEntity, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(5.dp)).background(SurfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        val icon = channel.streamIcon
        if (icon.isNullOrBlank()) {
            Text(channel.name.take(1).uppercase(), style = RedSurfType.gridMeta, color = TextPrimary)
        } else {
            SubcomposeAsyncImage(
                model = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(2.dp),
                error = { Text(channel.name.take(1).uppercase(), style = RedSurfType.gridMeta, color = TextPrimary) },
                loading = { /* blank while loading - avoids flicker on a fast-scrolling grid */ },
            )
        }
    }
}

/** The "on now" glyph - exact same brush/arc geometry as `WaveSpinner`, held static. Lives beside
 * the playing channel's name in the hero band now (EPG_GRID_REDESIGN.md G.3) - the one size where
 * it's legible; a 40dp grid cell has no room for it and the fill + bar already say "live." */
@Composable
internal fun LiveCrescentGlyph(size: Dp = 12.dp) {
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
    // Restyle toward TiviMate's small corner card is a logged follow-up (EPG_GRID_REDESIGN.md),
    // deliberately not part of the lattice pass.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
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

private val hourFormat = SimpleDateFormat("h:mm a", Locale.US)
private val halfHourFormat = SimpleDateFormat("h:mm", Locale.US)
private val headerDateFormat = SimpleDateFormat("EEE, MMM d", Locale.US)
private fun formatTimeRange(start: Long, end: Long): String =
    "${hourFormat.format(Date(start))} – ${hourFormat.format(Date(end))}"
