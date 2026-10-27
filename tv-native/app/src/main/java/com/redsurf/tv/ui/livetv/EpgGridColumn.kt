package com.redsurf.tv.ui.livetv

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.layout
import com.redsurf.tv.diag.traced
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.withFrameNanos
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.tv.material3.Text
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.epg.PublicEpg
import com.redsurf.tv.player.Catchup
import com.redsurf.tv.db.EpgProgramEntity
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.Background
import com.redsurf.tv.ui.theme.RedSurfDensity
import com.redsurf.tv.ui.theme.RedSurfType
import com.redsurf.tv.ui.theme.SurfaceBand
import com.redsurf.tv.ui.theme.SurfacePanel
import com.redsurf.tv.ui.theme.SurfaceRaised
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Lattice colours, hoisted (EPG_GRID_REDESIGN.md G.4) - a `Color.copy` per draw op per row per
// frame is exactly the allocation pattern a 449MB device doesn't need.
private val HeaderHairline = Color.White.copy(alpha = 0.08f)
private val TickHour = Color.White.copy(alpha = 0.10f)
private val TickHalf = Color.White.copy(alpha = 0.045f)
private val SlotTick = Color.White.copy(alpha = 0.14f)
private val CurrentFill = Accent.copy(alpha = 0.20f)
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

/** A pending time-preserving UP/DOWN (2026-10-05): move to row [index], to the cell covering
 * [time]. [nonce] makes a repeat of the same target a new request. [streamId] pins it to the
 * target *channel* (2026-10-19): matched by index alone, a jump left over from guide navigation
 * was handed to whatever row sat at that index in the next category browsed - whose effect then
 * called requestFocus() and pulled focus out of Categories into the grid mid-browse. */
private data class RowJump(val index: Int, val time: Long, val nonce: Long, val streamId: String)

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
 * Nothing animates (`TELEPORT_MENU.md`'s rule; Crest, 2026-10-26, retired "The Wash" - every
 * cursor move paid ~11 extra frames of redraw for it).
 */
@Composable
fun EpgGridColumn(
    channels: List<ChannelEntity>,
    programsByChannel: Map<String, List<EpgProgramEntity>>,
    groupName: String?,
    now: Long,
    windowStart: Long,
    onTuneChannel: (ChannelEntity) -> Unit,
    // Hold OK on any cell of a channel's row (TiviMate's own add-to-favorites path,
    // TELEPORT_MENU.md 2026-09-29) - the Surface's built-in long-click, so the short OK's own
    // click never fires alongside it.
    onLongPressChannel: (ChannelEntity) -> Unit = {},
    // Catch-up (EPG_WRAPUP.md 1.5): OK on a past programme still inside the channel's archive.
    onPlayCatchup: (ChannelEntity, EpgProgramEntity) -> Unit = { _, _ -> },
    // LEFT with nothing earlier worth visiting (2026-10-05) - LiveTvScreen lands on Categories.
    onLeftEdge: () -> Unit = {},
    // Bumped by LiveTvScreen's claimGridFocus: bring the focus-target row into view if it isn't
    // already (a lazy row that isn't composed can't take focus - the reason a deep target, e.g.
    // Teleport's Now Playing on channel 80 of a category, never landed).
    scrollToTargetNonce: Int = 0,
    // EPG_WRAPUP.md 1.7 / 1.6 - the focused row grows to 2x (Settings → Appearance), and the
    // channel playing right now (the hero preview) gets TiviMate's blue ▶ in its row.
    doubleHeightRow: Boolean = true,
    playingStreamId: String? = null,
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
    var gridHadFocus by remember { mutableStateOf(false) }
    var gridEntryCount by remember { mutableStateOf(0) }
    // Sprint 2, 2026-09-23 (Finding 2) - this grid stays permanently composed (LiveTvScreen's own
    // `visible` param, Sprint 1), so `sharedScroll` was never reset between categories: scrolling
    // right in one category and switching to another left the axis exactly where it was, so the
    // newly-focused row-0 cell (already correctly "now"-aligned) scrolled out of view - reading as
    // "focus lands on the rightmost cell" even though the real target was always the leftmost one.
    // A fresh category's timeline always starts back at "now."
    // EPG_WRAPUP.md 1.3: "the start" is now the current half-hour, not the window's left edge
    // (the window reaches PAST_MINUTES back) - see the scroll effect inside BoxWithConstraints,
    // which needs the measured minute scale.
    var cursorKey by remember { mutableStateOf<CursorKey?>(null) }

    val density = LocalDensity.current
    // One measurer for the whole grid: titles are measured while drawing, and its cache keeps a
    // steady screen from measuring the same title twice.
    val textMeasurer = rememberTextMeasurer(cacheSize = 256)
    val cellStyle = RedSurfType.gridCell
    val metaStyle = RedSurfType.gridMeta
    val channelStyle = RedSurfType.gridChannel
    val slotMetrics = remember(density, cellStyle, metaStyle, channelStyle) { SlotMetrics(density, cellStyle, metaStyle, channelStyle) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = entrance.value
                translationY = (1f - entrance.value) * 24.dp.toPx()
            }
            .onFocusChanged { state ->
                if (state.hasFocus && !gridHadFocus) gridEntryCount++
                gridHadFocus = state.hasFocus
                onFocusStateChanged(state.hasFocus)
            },
    ) {
        val labelWidth = RedSurfDensity.GridLabelWidth
        val timelineViewport = (maxWidth - labelWidth).coerceAtLeast(1.dp)
        val pxPerMinute: Dp = timelineViewport / VISIBLE_MINUTES
        val timelineContentWidth: Dp = pxPerMinute * WINDOW_MINUTES
        val pxPerMinutePx = with(density) { pxPerMinute.toPx() }
        val labelWidthPx = with(density) { labelWidth.toPx() }
        val headerHeightPx = with(density) { RedSurfDensity.GridHeaderHeight.toPx() }
        val nowStrokePx = with(density) { 2.dp.toPx() }
        val nowCapPx = with(density) { 4.5.dp.toPx() }
        val nowCapStroke = remember(density) { Stroke(width = with(density) { 2.dp.toPx() }, cap = StrokeCap.Round) }
        val wakePx = with(density) { 28.dp.toPx() }
        val hasRows = channels.isNotEmpty()
        val nowOffsetPx = ((floorToHalfHour(now) - windowStart) / MINUTE_MS) * pxPerMinutePx
        // Found live 2026-09-30: a single scrollTo here could run before the rows had measured,
        // and the now-cell's focus then scrolled only far enough to show it - "now" ended up at
        // the right edge. Wait for layout, and re-apply whenever focus enters the grid from
        // outside (TiviMate re-centres on now on entry too).
        suspend fun scrollToNow() {
            withFrameNanos { }
            withFrameNanos { }
            sharedScroll.scrollTo(nowOffsetPx.toInt())
        }
        LaunchedEffect(channels, groupName, windowStart, pxPerMinutePx) { scrollToNow() }
        LaunchedEffect(gridEntryCount) { if (gridEntryCount > 0) scrollToNow() }

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
                        // Crest's crest cap: the mark's crescent, open to the upper right like the
                        // wave (RESEARCH.md "A. Crest" - one of its two ornaments).
                        drawArc(
                            Accent,
                            startAngle = -10f,
                            sweepAngle = -280f,
                            useCenter = false,
                            topLeft = Offset(x - nowCapPx, headerHeightPx - nowCapPx),
                            size = Size(nowCapPx * 2, nowCapPx * 2),
                            style = nowCapStroke,
                        )
                    }
                },
        ) {
            // The supplement key shows only when public listings are actually on screen
            // (AGENTS.md's indicator decision: explained once, not a badge per cell).
            val hasSupplemented = remember(programsByChannel) {
                programsByChannel.values.any { list -> list.any { it.playlistId == PublicEpg.PLAYLIST_ID } }
            }
            GridTimeHeader(
                windowStart = windowStart,
                pxPerMinute = pxPerMinute,
                timelineContentWidth = timelineContentWidth,
                sharedScroll = sharedScroll,
                showSupplementKey = hasSupplemented,
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
                val rowsState = rememberLazyListState()
                LaunchedEffect(scrollToTargetNonce) {
                    if (scrollToTargetNonce == 0 || targetIndex < 0) return@LaunchedEffect
                    val visible = rowsState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
                    if (!visible) runCatching { rowsState.scrollToItem(targetIndex) }
                }
                // Time-preserving UP/DOWN (TiviMate; logged deferred since the Lattice pass, made
                // essential by the past window - found live 2026-10-05: UP landed on a past cell
                // and scrolled the timeline back). The key is handled explicitly and the target
                // row focuses its own cell for that time - never a focusProperties link to a row
                // that may not be composed (an unattached FocusRequester throws).
                var rowJump by remember { mutableStateOf<RowJump?>(null) }
                // The row that is (or is about to be) focused - the double-height row. Set on the
                // UP/DOWN key press itself, so the heights change in the same pass that starts the
                // move, not a frame later once focus lands (2026-10-19 trace: the late height
                // change cost ~55ms of extra layout per press).
                var cursorRow by remember { mutableStateOf(-1) }
                // A new category starts at its top row unless focus is returning to a specific
                // channel (found 2026-10-05: the previous category's vertical scroll carried over,
                // leaving the focused first row off-screen).
                LaunchedEffect(channels) {
                    if (targetIndex < 0) runCatching { rowsState.scrollToItem(0) }
                }
                LaunchedEffect(rowJump) {
                    val jump = rowJump ?: return@LaunchedEffect
                    val visible = rowsState.layoutInfo.visibleItemsInfo
                    val first = visible.firstOrNull()?.index ?: 0
                    val last = visible.lastOrNull()?.index ?: 0
                    if (jump.index < first) runCatching { rowsState.scrollToItem(jump.index) }
                    else if (jump.index >= last) runCatching { rowsState.scrollToItem((first + (jump.index - last) + 1).coerceAtLeast(0)) }
                }
                // Whole rows only (found on device 2026-10-27: focus scrolling left the top row
                // half-cut under the time header, reading as an overlap; TiviMate never shows a cut
                // row). Once a scroll settles, align the first row's top edge - back up to it unless
                // the cut is past half the row or the cursor is on the last visible row.
                LaunchedEffect(rowsState) {
                    snapshotFlow { rowsState.isScrollInProgress }.filter { !it }.collect {
                        val visible = rowsState.layoutInfo.visibleItemsInfo
                        val first = visible.firstOrNull() ?: return@collect
                        val cut = rowsState.firstVisibleItemScrollOffset
                        if (cut == 0) return@collect
                        val forward = cut > first.size / 2 || visible.lastOrNull()?.index == cursorRow
                        runCatching { rowsState.scrollToItem(if (forward) first.index + 1 else first.index) }
                    }
                }
                LazyColumn(state = rowsState, modifier = Modifier.fillMaxSize()) {
                    // One content type for every row, so rows leaving the list (a category change)
                    // are recycled for the new category's rows instead of built from nothing.
                    items(count = channels.size, key = { channels[it].streamId }, contentType = { 0 }) { index ->
                        val channel = channels[index]
                        val isFocusTarget = if (targetIndex >= 0) index == targetIndex else index == 0
                        val programmes = programsByChannel[channel.streamId].orEmpty()
                        val slots = remember(programmes, windowStart) {
                            slotsFor(programmes, windowStart, windowStart + WINDOW_MINUTES * MINUTE_MS)
                        }
                        traced("EpgRow") {
                            EpgChannelRow(
                                channel = channel,
                                displayName = remember(channel.name, groupName) { stripCategoryPrefix(channel.name, groupName) },
                                slots = slots,
                                now = now,
                                pxPerMinute = pxPerMinute,
                                timelineContentWidth = timelineContentWidth,
                                sharedScroll = sharedScroll,
                                onCursor = { key, slot ->
                                    if (key != cursorKey) {
                                        cursorKey = key
                                        onCursorChanged(channel, slot)
                                    }
                                },
                                onTune = onTuneChannel,
                                onLongPress = onLongPressChannel,
                                onPlayCatchup = onPlayCatchup,
                                onLeftEdge = onLeftEdge,
                                onShowInfo = { infoCardProgramme = it },
                                firstCellFocusRequester = if (isFocusTarget) firstCellFocusRequester else null,
                                expanded = doubleHeightRow && gridHadFocus && index == cursorRow,
                                onRowFocused = { cursorRow = index },
                                isPlaying = channel.streamId == playingStreamId,
                                jump = rowJump?.takeIf { it.index == index && it.streamId == channel.streamId },
                                onJumpConsumed = { nonce -> if (rowJump?.nonce == nonce) rowJump = null },
                                textMeasurer = textMeasurer,
                                metrics = slotMetrics,
                                onVerticalMove = { goingDown, time ->
                                    // From the row the last press was headed to (cursorRow), not this
                                    // one: on fast presses focus hasn't landed there yet, and moving
                                    // from here again would lose a press (as GroupsColumn did).
                                    val from = if (cursorRow in channels.indices) cursorRow else index
                                    val target = if (goingDown) from + 1 else from - 1
                                    if (target in channels.indices) {
                                        cursorRow = target
                                        rowJump = RowJump(target, time, System.nanoTime(), channels[target].streamId)
                                        true
                                    } else {
                                        false // past either end: let default search escape (e.g. UP to the nav strip)
                                    }
                                },
                            )
                        }
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
    showSupplementKey: Boolean,
) {
    val density = LocalDensity.current
    val hairlinePx = with(density) { RedSurfDensity.Hairline.toPx() }
    val tickPx = with(density) { 4.dp.toPx() }
    val halfHourPx = with(density) { (pxPerMinute * 30).toPx() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RedSurfDensity.GridHeaderHeight)
            // Decorative ruler: no accessibility nodes (a dozen texts diffed on every change).
            .clearAndSetSemantics { }
            .drawBehind {
                drawLine(HeaderHairline, Offset(0f, size.height - hairlinePx / 2f), Offset(size.width, size.height - hairlinePx / 2f), hairlinePx)
            },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            // The date at "now", not at the window's start - the window reaches 6h into the past,
            // so shortly after midnight the start was still yesterday (found 2026-10-05).
            headerDateFormat.format(Date(windowStart + PAST_MINUTES * MINUTE_MS)),
            style = RedSurfType.gridMeta,
            color = TextSecondary,
            maxLines = 1,
            modifier = Modifier
                .width(if (showSupplementKey) RedSurfDensity.GridLabelWidth - SupplementKeyWidth else RedSurfDensity.GridLabelWidth)
                .padding(start = 4.dp, bottom = 5.dp),
        )
        if (showSupplementKey) {
            // The legend for drawSlot's tint + corner dot: the same dot, so the non-colour cue is
            // what gets explained, not just the hue.
            Row(
                modifier = Modifier.width(SupplementKeyWidth).padding(bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(modifier = Modifier.size(6.dp)) { drawCircle(SupplementTick) }
                Text(
                    "Public guide",
                    style = RedSurfType.gridHeaderHalf,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
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
    onCursor: (CursorKey, EpgSlot) -> Unit,
    onTune: (ChannelEntity) -> Unit,
    onLongPress: (ChannelEntity) -> Unit,
    onPlayCatchup: (ChannelEntity, EpgProgramEntity) -> Unit,
    onShowInfo: (EpgProgramEntity) -> Unit,
    firstCellFocusRequester: FocusRequester?,
    onLeftEdge: () -> Unit = {},
    expanded: Boolean = false,
    onRowFocused: () -> Unit = {},
    isPlaying: Boolean = false,
    jump: RowJump? = null,
    onVerticalMove: (goingDown: Boolean, time: Long) -> Boolean = { _, _ -> false },
    onJumpConsumed: (nonce: Long) -> Unit = {},
    textMeasurer: TextMeasurer,
    metrics: SlotMetrics,
) {
    var rowHasFocus by remember { mutableStateOf(false) }
    // TiviMate F14 (2026-10-20): the row is the one focus target, as in TiviMate's guide, and
    // LEFT/RIGHT move a selected programme inside it. Per-cell focus put ~50 focusables on screen,
    // and every move ran focus search, bring-into-view and the accessibility diff over all of them
    // (on this TV an accessibility service is always on - Projectivy Launcher's).
    val rowFocus = remember { FocusRequester() }
    // The selected programme, by start time - listings can replace the slot list while the row is
    // focused, and an index would then point at a different programme. MIN_VALUE = none yet.
    var selectedStart by remember { mutableStateOf(Long.MIN_VALUE) }
    // Where a pending UP/DOWN wants the selection, applied when focus lands (see the jump effect).
    val pendingStart = remember { LongArray(1) { Long.MIN_VALUE } }
    val nowIndex = remember(slots, now) { nowSlotIndex(slots, now) }
    val selected = remember(slots, selectedStart) { slotIndexAt(slots, selectedStart) }
    // Is there anything before "now" worth walking LEFT into? Past listings, or an archive to
    // catch up from. If not, LEFT from the now-cell leaves for Categories instead of stepping
    // through hours of empty half-hours (2026-10-05).
    val hasPast = remember(slots, now, channel.tvArchiveDays) {
        channel.tvArchiveDays > 0 || slots.any { it is AiredSlot && it.start < now && it.end <= now }
    }
    // OK / hold-OK, tracked without state (no recomposition per press): [0] = this press began
    // on this row, [1] = its long-press already fired.
    val okPress = remember { BooleanArray(2) }
    // Whether this row has been placed on screen at least once. A lazy list pre-builds upcoming
    // rows before placing them; requestFocus() on one of those crashed the app with "Expected
    // BringIntoViewRequester to not be used before parents are placed" (crash log, 2026-10-19
    // 01:54). A plain holder - written at placement, read only by the effect below.
    val placed = remember { BooleanArray(1) }
    // Effects only while they have work: a row rebuilt for a new category would otherwise launch
    // two coroutines that return at once (onRemembered was ~4ms of the commit frame, 2026-10-27).
    if (jump != null) LaunchedEffect(jump.nonce) {
        pendingStart[0] = slots.getOrNull(slotIndexAt(slots, jump.time))?.start ?: Long.MIN_VALUE
        repeat(15) {
            withFrameNanos { }
            if (!placed[0]) return@repeat
            runCatching { rowFocus.requestFocus() }
            if (rowHasFocus) {
                onJumpConsumed(jump.nonce)
                return@LaunchedEffect
            }
        }
        // Spent either way - never replayed when this row is composed again later.
        pendingStart[0] = Long.MIN_VALUE
        onJumpConsumed(jump.nonce)
    }
    val density = LocalDensity.current
    val hairlinePx = with(density) { RedSurfDensity.Hairline.toPx() }
    val gutterPx = with(density) { RedSurfDensity.GridGutter.toPx() }
    val halfHourPx = with(density) { (pxPerMinute * 30).toPx() }
    val pxPerMinutePx = with(density) { pxPerMinute.toPx() }
    val columns = WINDOW_MINUTES / 30
    // The selection drives the hero and keeps itself in view - TiviMate holds the hours still
    // until the cursor reaches an edge; a programme already partly visible doesn't scroll.
    if (rowHasFocus) LaunchedEffect(selected) {
        if (selected !in slots.indices) return@LaunchedEffect
        val slot = slots[selected]
        onCursor(CursorKey(channel.playlistId, channel.streamId, slot.start), slot)
        val width = (slot.end - slot.start) / MINUTE_MS.toFloat() * pxPerMinutePx
        val offset = (slot.start - slots.first().start) / MINUTE_MS.toFloat() * pxPerMinutePx - sharedScroll.value
        val viewport = sharedScroll.viewportSize.toFloat()
        val distance = when {
            offset + width <= 0f -> if (width <= viewport) offset else offset + width - viewport
            offset >= viewport -> if (width <= viewport) offset + width - viewport else offset
            else -> 0f
        }
        if (distance != 0f) runCatching { sharedScroll.animateScrollBy(distance) }
    }

    fun onOk(slot: EpgSlot) {
        when (slot) {
            is AiredSlot -> when {
                now >= slot.start && now < slot.end -> onTune(channel)
                Catchup.isInArchive(channel.tvArchiveDays, slot.programme.startTime, now) -> onPlayCatchup(channel, slot.programme)
                else -> onShowInfo(slot.programme)
            }
            is GapSlot -> onTune(channel)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (expanded) RedSurfDensity.GridRowHeight * 2 else RedSurfDensity.GridRowHeight)
            .onPlaced { placed[0] = true }
            .layout { measurable, constraints ->
                val placeable = traced("EpgRow:measure") { measurable.measure(constraints) }
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .then(firstCellFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .focusRequester(rowFocus)
            .onFocusChanged {
                if (it.hasFocus && !rowHasFocus) {
                    // Entry lands on the programme airing now (EPG_WRAPUP.md 1.3); UP/DOWN on the
                    // one covering the anchor time.
                    selectedStart = pendingStart[0].takeIf { p -> p != Long.MIN_VALUE }
                        ?: slots.getOrNull(nowIndex)?.start ?: Long.MIN_VALUE
                    pendingStart[0] = Long.MIN_VALUE
                    onRowFocused()
                }
                rowHasFocus = it.hasFocus
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // Read from state, not the composed `selected`: on fast presses the row may not
                // have recomposed since the last move.
                val cur = slotIndexAt(slots, selectedStart).takeIf { it >= 0 } ?: nowIndex
                val slot = slots.getOrNull(cur) ?: return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (cur == 0 || (cur <= nowIndex && !hasPast)) onLeftEdge() else selectedStart = slots[cur - 1].start
                        true
                    }
                    Key.DirectionRight -> {
                        if (cur < slots.lastIndex) selectedStart = slots[cur + 1].start
                        true
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        // Anchor on the visible part of the programme, so a long one that began
                        // hours ago doesn't drag the target back to its start. From the live
                        // programme the anchor is *now* (user report 2026-10-19, TiviMate's
                        // behaviour): otherwise the next row could land on one already over,
                        // where OK only opens info.
                        val viewportLeftTime = slots.first().start + (sharedScroll.value / pxPerMinutePx).toLong() * MINUTE_MS
                        val anchor = if (now >= slot.start && now < slot.end) now else maxOf(slot.start, viewportLeftTime) + MINUTE_MS
                        onVerticalMove(event.key == Key.DirectionDown, anchor)
                    }
                    else -> false
                }
            }
            .onKeyEvent { event ->
                if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) {
                    return@onKeyEvent false
                }
                val native = event.nativeKeyEvent
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (native.repeatCount == 0) {
                            okPress[0] = true
                            okPress[1] = false
                        } else if (okPress[0] && !okPress[1] && native.isLongPress) {
                            okPress[1] = true
                            onLongPress(channel)
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        // Only a press that began here counts - not the release of an OK that
                        // opened something else first and handed focus to this row.
                        if (okPress[0] && !okPress[1]) {
                            slots.getOrNull(slotIndexAt(slots, selectedStart).takeIf { it >= 0 } ?: nowIndex)?.let { onOk(it) }
                        }
                        okPress[0] = false
                        true
                    }
                    else -> false
                }
            }
            .focusable()
            // One accessibility node per row (number, name, selected programme) instead of a node
            // per text and per cell - the diff that runs on every change stays small.
            .clearAndSetSemantics {
                // Built here, not in composition: only an accessibility pass pays for the string.
                val slot = slots.getOrNull(if (selected >= 0) selected else nowIndex)
                contentDescription = "${channel.num} $displayName · ${(slot as? AiredSlot)?.programme?.title ?: "No listings"}"
            }
            // Own drawing layer per row: a scroll step only moves the layers, and the Wash, the
            // marquee or a selection change re-records just this row, not the whole grid.
            .graphicsLayer()
            .drawBehind {
                // Crest: rows separate by a background gutter, not a line. Idle rows draw nothing:
                // the screen's own Background is already behind them (re-filling it was a whole
                // extra layer of overdraw per row, PERFORMANCE_MAX.md step 1).
                if (rowHasFocus) drawRect(SurfaceBand, size = Size(size.width, size.height - gutterPx))
            }
            .padding(bottom = RedSurfDensity.GridGutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        traced("EpgRow:label") {
            ChannelLabelBlock(
                channel = channel,
                displayName = displayName,
                marquee = rowHasFocus && !expanded,
                textMeasurer = textMeasurer,
                m = metrics,
                expanded = expanded,
                isPlaying = isPlaying,
                modifier = Modifier
                    .width(RedSurfDensity.GridLabelWidth)
                    .fillMaxHeight()
                    // Crest: the label column is one tone (Panel) on idle rows; the focused row's
                    // band runs straight through it.
                    .drawBehind { if (!rowHasFocus) drawRect(SurfacePanel) },
            )
        }
        // The timeline is painted, not composed (TiviMate F14): one draw pass over the slots in
        // view, translated by the shared scroll - scrolling and selection changes only redraw
        // this row's layer, and a new row costs a label and this Box instead of ~10 cells.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds()
                .drawBehind {
                    if (slots.isEmpty()) return@drawBehind
                    val scroll = sharedScroll.value.toFloat()
                    // The lattice's half-hour verticals; i = 0 is the spine's own position.
                    for (i in 1..columns) {
                        val x = i * halfHourPx - scroll
                        if (x < 0f || x > size.width) continue
                        drawLine(if (i % 2 == 0) TickHour else TickHalf, Offset(x, 0f), Offset(x, size.height), hairlinePx)
                    }
                    val origin = slots.first().start
                    val sel = if (rowHasFocus) selected else -1
                    for (index in slots.indices) {
                        val slot = slots[index]
                        val left = (slot.start - origin) / MINUTE_MS.toFloat() * pxPerMinutePx - scroll
                        val width = (slot.end - slot.start) / MINUTE_MS.toFloat() * pxPerMinutePx
                        if (left + width <= 0f || left >= size.width) continue
                        translate(left = left) {
                            clipRect(left = 0f, top = 0f, right = width, bottom = size.height) {
                                drawSlot(
                                    slot = slot,
                                    w = width,
                                    now = now,
                                    selected = index == sel,
                                    isNowCell = index == nowIndex,
                                    expanded = expanded,
                                    stickyShift = (-left).coerceIn(0f, (width - metrics.minText).coerceAtLeast(0f)),
                                    textMeasurer = textMeasurer,
                                    m = metrics,
                                )
                            }
                        }
                    }
                },
        )
    }
}

/**
 * Part 7.4's layout at the 180dp width: 4 | 26 (number, right-aligned) | 6 | 26 (logo) | 8 | name |
 * glyphs | 4 - painted, not composed (performance salvage, 2026-10-20: the composed label, and its
 * logo `AsyncImage` above all, was ~75% of building a row). The one composed exception is the
 * focused single-height row's name, which marquees (AGENTS.md, 2026-09-15).
 */
@Composable
private fun ChannelLabelBlock(
    channel: ChannelEntity,
    displayName: String,
    marquee: Boolean,
    textMeasurer: TextMeasurer,
    m: SlotMetrics,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    isPlaying: Boolean = false,
) {
    val context = LocalContext.current
    val icon = channel.streamIcon?.takeIf { it.isNotBlank() }
    var logo by remember(icon) { mutableStateOf(icon?.let { ChannelLogos.peek(it) }) }
    var logoFailed by remember(icon) { mutableStateOf(icon == null) }
    if (icon != null && logo == null && !logoFailed) {
        LaunchedEffect(icon) {
            val loaded = ChannelLogos.load(context, icon, (m.logo * 2).toInt())
            if (loaded == null) logoFailed = true else logo = loaded
        }
    }
    val hasCatchup = channel.tvArchiveDays > 0
    // Glyphs sit at the end, after the name's full width (the composed Row gave the name weight 1).
    val glyphsWidth = (if (isPlaying) m.glyphGap + m.playingGlyph else 0f) + (if (hasCatchup) m.glyphGap + m.catchupGlyph else 0f)
    Box(
        modifier = modifier.drawBehind {
            val h = size.height
            // Number, right-aligned in its 26dp.
            val number = textMeasurer.measure(channel.num.toString(), style = m.metaStyle, maxLines = 1)
            drawText(number, color = ChannelNumber, topLeft = Offset(m.labelPad + m.numberWidth - number.size.width, (h - number.size.height) / 2f))
            // Logo chip.
            val chipLeft = m.labelPad + m.numberWidth + m.numberGap
            val chipTop = (h - m.logo) / 2f
            drawRect(SurfaceRaised, Offset(chipLeft, chipTop), Size(m.logo, m.logo))
            val bitmap = logo
            if (bitmap != null) {
                val box = m.logo - 2 * m.chipPad
                val scale = minOf(box / bitmap.width, box / bitmap.height)
                val w = bitmap.width * scale
                val hh = bitmap.height * scale
                drawImage(
                    bitmap,
                    dstOffset = IntOffset((chipLeft + (m.logo - w) / 2f).toInt(), (chipTop + (m.logo - hh) / 2f).toInt()),
                    dstSize = IntSize(w.toInt(), hh.toInt()),
                    filterQuality = FilterQuality.Low,
                )
            } else if (logoFailed) {
                val letter = textMeasurer.measure(channel.name.take(1).uppercase(), style = m.metaStyle, maxLines = 1)
                drawText(letter, color = TextPrimary, topLeft = Offset(chipLeft + (m.logo - letter.size.width) / 2f, chipTop + (m.logo - letter.size.height) / 2f))
            }
            // Name.
            val nameLeft = chipLeft + m.logo + m.logoGap
            val nameWidth = (size.width - nameLeft - m.labelPad - glyphsWidth).toInt()
            if (!marquee && nameWidth > 0) {
                val name = textMeasurer.measure(
                    displayName,
                    style = m.channelStyle,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = if (expanded) 2 else 1,
                    constraints = Constraints(maxWidth = nameWidth),
                )
                drawText(name, color = ChannelName, topLeft = Offset(nameLeft, (h - name.size.height) / 2f))
            }
            // TiviMate's own order (EPG_WRAPUP.md 1.6): name, then the blue ▶ when this channel is
            // the one playing, then the catch-up glyph.
            var x = size.width - m.labelPad - glyphsWidth
            if (isPlaying) {
                x += m.glyphGap
                translate(x, (h - m.playingGlyph) / 2f) { drawPlayingGlyph(m.playingGlyph) }
                x += m.playingGlyph
            }
            if (hasCatchup) {
                x += m.glyphGap
                translate(x, (h - m.catchupGlyph) / 2f) { drawCatchupGlyph(m.catchupGlyph) }
            }
        },
    ) {
        if (marquee) {
            Text(
                displayName,
                style = m.channelStyle,
                color = ChannelName,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = with(LocalDensity.current) { (m.labelPad + m.numberWidth + m.numberGap + m.logo + m.logoGap).toDp() })
                    .padding(end = with(LocalDensity.current) { (m.labelPad + glyphsWidth).toDp() })
                    .graphicsLayer()
                    .basicMarquee(),
            )
        }
    }
}

/** Pixel sizes and text styles for [drawSlot], resolved once (styles are read in composition). */
private class SlotMetrics(density: Density, val cellStyle: TextStyle, val metaStyle: TextStyle, val channelStyle: TextStyle) {
    // Channel label (ChannelLabelBlock)
    val labelPad = with(density) { 4.dp.toPx() }
    val numberWidth = with(density) { 26.dp.toPx() }
    val numberGap = with(density) { 6.dp.toPx() }
    val logo = with(density) { RedSurfDensity.GridLogo.toPx() }
    val logoGap = with(density) { 8.dp.toPx() }
    val chipPad = with(density) { 2.dp.toPx() }
    val glyphGap = with(density) { 4.dp.toPx() }
    val playingGlyph = with(density) { 10.dp.toPx() }
    val catchupGlyph = with(density) { 12.dp.toPx() }
    // Slots (drawSlot)
    val tickInset = with(density) { 7.dp.toPx() }
    val tickStroke = with(density) { 2.dp.toPx() }
    val bar = with(density) { 3.dp.toPx() }
    val inset = with(density) { 1.dp.toPx() }
    val outline = with(density) { 1.5.dp.toPx() }
    val hairline = with(density) { RedSurfDensity.Hairline.toPx() }
    val labelClearance = with(density) { 74.dp.toPx() }
    val minText = with(density) { 90.dp.toPx() }
    val padStart = with(density) { (RedSurfDensity.CellPadH + 3.dp).toPx() }
    val padEnd = with(density) { RedSurfDensity.CellPadH.toPx() }
    val supplementDot = with(density) { 2.5.dp.toPx() }
    val supplementDotInset = with(density) { 7.dp.toPx() }
    val supplementDotMinWidth = with(density) { 24.dp.toPx() }
}

/**
 * One slot on the lattice (G.4/G.5/G.6), drawn at the current translation with width [w] - since
 * 2026-10-20 the timeline is painted, not composed (TiviMate F14: a new category built ~10
 * composed cells per row, ~350ms of main thread per category move):
 * - Aired: a 2dp start-edge tick inset 7dp top and bottom (the whole "ribbon, not boxes" trick);
 *   the currently-airing slot alone is filled, with the same 3dp leading bar `GroupsColumn`'s
 *   selected row already uses; titles of slots wholly in the past dim to half.
 * - Gap: nothing but a solid hairline (Crest: no dashes; a dash `PathEffect` was path-rendered
 *   every frame) across the span (the lattice ticks show through, so the
 *   30-minute columns are visibly present *inside* the gap), and a quiet "No listings" on the slot
 *   airing now - where the cursor lands.
 * - Selected: a solid red cell (Crest).
 * - Shapes are square (Crest's 0dp radius) - plain rects, the cheapest thing to draw.
 * - Text: TiviMate's sticky titles (2026-10-05) - a programme that started before the visible
 *   edge keeps its title pinned to that edge ([stickyShift]) instead of cut off mid-word.
 */
private fun DrawScope.drawSlot(
    slot: EpgSlot,
    w: Float,
    now: Long,
    selected: Boolean,
    isNowCell: Boolean,
    expanded: Boolean,
    stickyShift: Float,
    textMeasurer: TextMeasurer,
    m: SlotMetrics,
) {
    val h = size.height
    val isCurrent = slot is AiredSlot && now >= slot.start && now < slot.end
    when (slot) {
        is AiredSlot -> {
            // Public-supplement listings (EPG_WRAPUP.md 2.2; AGENTS.md's indicator decision,
            // 2026-09-15): a cool tint *and* a dashed start tick - never colour alone, and never
            // another red, which already means "focus/now".
            val supplemented = slot.programme.playlistId == PublicEpg.PLAYLIST_ID
            if (supplemented) {
                drawRect(SupplementFill, Offset(0f, m.inset), Size(w, h - 2 * m.inset))
                if (w > m.supplementDotMinWidth) {
                    drawCircle(SupplementTick, radius = m.supplementDot, center = Offset(w - m.supplementDotInset, m.inset + m.supplementDotInset))
                }
            }
            if (isCurrent) {
                drawRect(CurrentFill, Offset(0f, m.inset), Size(w, h - 2 * m.inset))
                drawRect(Accent, Offset.Zero, Size(m.bar, h))
            } else if (supplemented) {
                drawRect(SupplementTick, Offset(0f, m.tickInset), Size(m.tickStroke, h - 2 * m.tickInset))
            } else {
                drawRect(SlotTick, Offset(0f, m.tickInset), Size(m.tickStroke, h - 2 * m.tickInset))
            }
        }
        is GapSlot -> {
            val dashStart = if (isNowCell) m.labelClearance else 0f
            if (w > dashStart) drawRect(GapLine, Offset(dashStart, (h - m.hairline) / 2f), Size(w - dashStart, m.hairline))
        }
    }
    if (selected) {
        // Crest (design-language RESEARCH.md "A. Crest"): the cursor is a solid red cell - square,
        // no outline, no wash. Also the cheapest cursor to draw: one rect, no path or gradient.
        drawRect(Accent, Offset(0f, m.inset), Size(w, h - 2 * m.inset))
    }
    val textLeft = stickyShift + m.padStart
    val maxWidth = (w - textLeft - m.padEnd).toInt()
    if (maxWidth <= 0) return
    val constraints = Constraints(maxWidth = maxWidth)
    when (slot) {
        is AiredSlot -> {
            val title = textMeasurer.measure(
                slot.programme.title.ifBlank { "Untitled" },
                style = m.cellStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = constraints,
            )
            // Double-height row: the second line carries the times (TiviMate).
            val times = if (expanded) {
                textMeasurer.measure(
                    "${cellTimeFormat.format(Date(slot.start))} – ${cellTimeFormat.format(Date(slot.end))}",
                    style = m.metaStyle,
                    overflow = TextOverflow.Clip,
                    maxLines = 1,
                    constraints = constraints,
                )
            } else null
            val top = (h - title.size.height - (times?.size?.height ?: 0)) / 2f
            val titleColor = when {
                selected || isCurrent -> TextPrimary
                slot.end <= now -> PastTitle
                else -> RestingTitle
            }
            drawText(title, color = titleColor, topLeft = Offset(textLeft, top))
            times?.let { drawText(it, color = NoListings, topLeft = Offset(textLeft, top + title.size.height)) }
        }
        is GapSlot -> if (isNowCell) {
            val label = textMeasurer.measure("No listings", style = m.metaStyle, maxLines = 1, constraints = constraints)
            drawText(label, color = NoListings, topLeft = Offset(textLeft, (h - label.size.height) / 2f))
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
                .clip(RectangleShape)
                .background(SurfaceRaised)
                .border(1.5.dp, Accent, RectangleShape)
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

private val cellTimeFormat = java.text.SimpleDateFormat("h:mm", java.util.Locale.US)
private val PlayingBlue = Color(0xFF3B82F6)
// User bug #5 (2026-10-05): the public-guide marking went unnoticed at 7% - doubled, and a corner
// dot added (a shape, not only a colour, per AGENTS.md's indicator decision). Still a cool tone, so
// it never reads as the red that means focus/now.
private val SupplementFill = Color(0xFF38BDF8).copy(alpha = 0.14f)
private val SupplementTick = Color(0xFF7DD3FC).copy(alpha = 0.8f)
private val SupplementKeyWidth = 84.dp

/** TiviMate's "this channel is playing" mark - a small blue play triangle (EPG_WRAPUP.md 1.6). */
private fun DrawScope.drawPlayingGlyph(side: Float) {
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(side * 0.15f, 0f)
        lineTo(side, side / 2f)
        lineTo(side * 0.15f, side)
        close()
    }
    drawPath(path, PlayingBlue)
}

/** Catch-up available - a history glyph (open arc + arrowhead + clock hands), drawn rather than
 * pulled from an icon pack the app doesn't depend on (EPG_WRAPUP.md 1.6). */
private fun DrawScope.drawCatchupGlyph(side: Float) {
    val stroke = side * (1.4f / 12f)
    val r = side / 2f - stroke
    val c = Offset(side / 2f, side / 2f)
    drawArc(
        color = TextPrimary,
        startAngle = -200f,
        sweepAngle = 300f,
        useCenter = false,
        topLeft = Offset(c.x - r, c.y - r),
        size = Size(r * 2, r * 2),
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    val tip = Offset(c.x - r, c.y - r * 0.35f)
    drawLine(TextPrimary, tip, Offset(tip.x - r * 0.35f, tip.y - r * 0.35f), stroke, StrokeCap.Round)
    drawLine(TextPrimary, tip, Offset(tip.x + r * 0.35f, tip.y - r * 0.35f), stroke, StrokeCap.Round)
    drawLine(TextPrimary, c, Offset(c.x, c.y - r * 0.55f), stroke, StrokeCap.Round)
    drawLine(TextPrimary, c, Offset(c.x + r * 0.4f, c.y + r * 0.2f), stroke, StrokeCap.Round)
}
