package com.redsurf.tv.ui.shell

import android.provider.Settings as AndroidSettings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.itemsIndexed
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.redsurf.tv.R
import com.redsurf.tv.db.ChannelEntity
import com.redsurf.tv.db.GroupCount
import com.redsurf.tv.ui.livetv.GroupKey
import com.redsurf.tv.ui.livetv.key
import com.redsurf.tv.ui.theme.Accent
import com.redsurf.tv.ui.theme.Background
import com.redsurf.tv.ui.theme.RedSurfFocus
import com.redsurf.tv.ui.theme.RedSurfType
import com.redsurf.tv.ui.theme.Surface as SurfaceColor
import com.redsurf.tv.ui.theme.TextPrimary
import com.redsurf.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private val PanelWidth = 520.dp
private val PanelHeight = 440.dp
private const val OPEN_DURATION_MS = 260
private const val CLOSE_DURATION_MS = 180
private const val FAST_REPEAT_WINDOW_MS = 400L
private const val CRESCENT_BITE_START_DEG = 25f
private const val CRESCENT_BITE_SWEEP_DEG = 140f

/**
 * A RedSurf-original quick-navigation overlay (`docs/plans/TELEPORT_MENU.md`), opened by
 * long-press Back once the "Teleport Menu" Settings toggle is on (`AppShell.kt` decides when).
 * Two grades of row, same convention `SettingsScreen.kt`'s grey rows already established: real,
 * focusable [Live][TeleportRow.Live] rows for destinations that resolve to something right now,
 * and [Grey][TeleportRow.Grey] for ones that don't (either permanently, like Favorites - no
 * favorites view exists yet - or only for the current channel/category, like Root Category on a
 * category with no naming-convention "family").
 */
sealed class TeleportRow {
    data class Live(val label: String, val onSelect: () -> Unit) : TeleportRow()
    data class Grey(val label: String) : TeleportRow()
}

private val ROOT_CATEGORY_SEPARATORS = charArrayOf('-', '|', '•', ':')

/** Decision 4 - the earliest of `-`, `|`, `•`, `:` in a raw category name, trimmed; null when
 * none exists (that category has no "family" to jump within). */
internal fun categoryPrefix(groupName: String): String? {
    val index = groupName.indices.firstOrNull { groupName[it] in ROOT_CATEGORY_SEPARATORS } ?: return null
    return groupName.substring(0, index).trim().takeIf { it.isNotEmpty() }
}

/** Decision 4 - the first category (existing list order) sharing [selectedGroup]'s own prefix,
 * scoped to the same playlist. Null when nothing's focused, or the current category's name has
 * no separator to derive a family from. */
internal fun resolveRootCategoryTarget(groups: List<GroupCount>, selectedGroup: GroupKey?): GroupKey? {
    val current = selectedGroup ?: return null
    val currentInfo = groups.firstOrNull { it.key() == current } ?: return null
    val prefix = categoryPrefix(currentInfo.groupName) ?: return null
    return groups.firstOrNull { it.playlistId == current.playlistId && categoryPrefix(it.groupName) == prefix }?.key()
}

/** Decision 3 item 2 - the first category (existing list order) belonging to [currentChannel]'s
 * own playlist. Null when nothing's focused/playing. */
internal fun resolvePlaylistRootTarget(groups: List<GroupCount>, currentChannel: ChannelEntity?): GroupKey? {
    val playlistId = currentChannel?.playlistId ?: return null
    return groups.firstOrNull { it.playlistId == playlistId }?.key()
}

private fun easeOutCubic(t: Float): Float {
    val f = t - 1f
    return f * f * f + 1f
}

/**
 * The portal itself - "The Curl" (decision 5, Opus consultation 2026-09-19): the reveal mask is
 * the mark's own crescent, closing over itself into a disc, then stretching into the panel, its
 * leading edge riding `WaveSpinner`'s own arc (same brush, same gradient - the app's only other
 * animation, made to visibly belong to the same family). Six draw ops once open (scrim, one
 * reused-`Path` clip+fill, one arc stroke, one rim stroke) - no `saveLayer`, no offscreen buffer.
 * Fully static at rest (unlike `WaveSpinner`, which legitimately loops forever) - nothing here
 * re-triggers once `p` settles at 1f, so an open menu costs nothing extra per frame.
 *
 * [visible] drives open/close; this composable renders nothing at all (not even a transparent
 * `Box`) once fully closed, so it never captures stray focus or clicks between uses. Real content
 * (the header + rows) is genuinely focusable Compose content, never masked/clipped by the reveal
 * shape - clipping live text mid-animation is the specific thing the consultation flagged as
 * reading cheap; it only fades in, decoupled from the mask.
 */
@Composable
fun TeleportMenu(
    visible: Boolean,
    rows: List<TeleportRow>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Accessibility + deterministic machine-sweep timing (Opus consultation): a user with
    // animations off system-wide gets an instant menu, not a slow one they can't turn off here.
    val animatorDurationScale = remember {
        runCatching {
            AndroidSettings.Global.getFloat(context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
    }
    val p = remember { Animatable(0f) }
    var lastCloseTimeMs by remember { mutableStateOf(0L) }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (visible) {
            // Fast-repeat rule: bouncing the menu open again right after closing it shouldn't
            // feel like paying the full toll every time.
            val start = if (System.currentTimeMillis() - lastCloseTimeMs < FAST_REPEAT_WINDOW_MS) 0.6f else 0f
            p.snapTo(start)
            val duration = if (animatorDurationScale == 0f) 0 else OPEN_DURATION_MS
            p.animateTo(1f, tween(duration, easing = FastOutSlowInEasing))
            delay(30)
            runCatching { firstFocus.requestFocus() }
        } else if (p.value > 0f) {
            // Close is deliberately faster than open (180ms vs 260ms) - asymmetry is what makes
            // repeated use feel snappy rather than taxing.
            val duration = if (animatorDurationScale == 0f) 0 else CLOSE_DURATION_MS
            p.animateTo(0f, tween(duration, easing = FastOutLinearInEasing))
            lastCloseTimeMs = System.currentTimeMillis()
        }
    }

    if (p.value <= 0f) return

    val value = p.value
    // Phase 1 (0-65% of the open animation): crescent closes its own bite while growing into a
    // disc. Phase 2 (60-100%, a slight overlap so there's no seam): disc -> pill -> panel.
    val pCurl = (value / 0.65f).coerceIn(0f, 1f)
    val pSettle = ((value - 0.60f) / 0.40f).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val panelHalfW = PanelWidth.toPx() / 2f
            val panelHalfH = PanelHeight.toPx() / 2f
            val c = center

            drawRect(Background.copy(alpha = 0.72f * value))

            if (pSettle < 1f) {
                // Starts at 28dp, not 0 - below that the bite is sub-pixel at 10 feet and reads
                // as a blob, not a crescent (Opus consultation).
                val r = lerp(28.dp.toPx(), panelHalfH, easeOutCubic(pCurl))
                val shapeAlpha = (pCurl / 0.2f).coerceIn(0f, 1f)
                val thetaDeg = CRESCENT_BITE_START_DEG + CRESCENT_BITE_SWEEP_DEG * pCurl
                val thetaRad = Math.toRadians(thetaDeg.toDouble())
                // The bite: a second circle offset from center, shrinking to zero as pCurl -> 1 -
                // that closing offset is what makes the crescent read as curling shut.
                val biteOffset = r * 0.55f * (1f - pCurl)
                val c2 = c + Offset(cos(thetaRad).toFloat(), sin(thetaRad).toFloat()) * biteOffset

                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addOval(Rect(center = c, radius = r))
                    addOval(Rect(center = c2, radius = r))
                }
                clipPath(path) {
                    drawRect(SurfaceColor.copy(alpha = shapeAlpha), topLeft = c - Offset(r, r), size = Size(r * 2, r * 2))
                }

                // The leading edge - WaveSpinner's own arc (same brush, same 300° sweep), riding
                // the crescent's outer radius; its own 60° gap sits in the crescent's bite.
                if (pCurl < 0.85f) {
                    val strokeWidth = lerp(6.dp.toPx(), 2.dp.toPx(), pCurl)
                    val arcAlpha = 1f - (pCurl / 0.85f).coerceIn(0f, 1f)
                    val brush = Brush.sweepGradient(
                        0f to Accent.copy(alpha = 0f),
                        0.12f to Accent.copy(alpha = 0.2f * arcAlpha),
                        1f to Accent.copy(alpha = arcAlpha),
                    )
                    rotate(degrees = thetaDeg + 30f, pivot = c) {
                        drawArc(
                            brush = brush,
                            startAngle = 0f,
                            sweepAngle = 300f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                            topLeft = c - Offset(r, r),
                            size = Size(r * 2, r * 2),
                        )
                    }
                }
            }

            if (pSettle > 0f) {
                // Disc -> pill -> panel: one continuous animated rounded rect, corner radius
                // shrinking from a full circle down to the panel's own 16dp.
                val eased = easeOutCubic(pSettle)
                val halfW = lerp(panelHalfH, panelHalfW, eased)
                val halfH = panelHalfH
                val corner = lerp(panelHalfH, 16.dp.toPx(), eased)
                val topLeft = c - Offset(halfW, halfH)
                val size = Size(halfW * 2, halfH * 2)
                drawRoundRect(color = SurfaceColor, topLeft = topLeft, size = size, cornerRadius = CornerRadius(corner, corner))
                // The rim - persists once open, static, the portal's residue.
                drawRoundRect(
                    color = Accent.copy(alpha = 0.35f * value),
                    topLeft = topLeft,
                    size = size,
                    cornerRadius = CornerRadius(corner, corner),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
        }

        // Real, focusable content - fades in across phase 2, never clipped by the reveal mask
        // above (the consultation's specific warning: text cut mid-shape reads as cheap).
        if (pSettle > 0f) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(PanelWidth)
                    .height(PanelHeight)
                    .graphicsLayer { alpha = pSettle; translationY = (1f - pSettle) * 8.dp.toPx() }
                    .padding(24.dp)
                    // Focus trap (same fix as PlayerScreen.kt's own fullscreen root, AGENTS.md
                    // "state and focus discipline"): the screen this overlay sits on top of stays
                    // composed and focusable underneath it, so UP/DOWN with nowhere left to go
                    // inside this list must not escape into it.
                    .focusProperties { exit = { FocusRequester.Cancel } },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                    Image(painter = painterResource(R.drawable.ic_mark), contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Teleport to…", style = RedSurfType.sectionTitle, color = TextPrimary)
                }
                TvLazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(rows) { index, row ->
                        when (row) {
                            is TeleportRow.Live -> TeleportLiveRow(
                                label = row.label,
                                onSelect = {
                                    row.onSelect()
                                    onDismiss()
                                },
                                modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            )
                            is TeleportRow.Grey -> TeleportGreyRow(row.label)
                        }
                    }
                }
            }
        }
    }
}

/** A real row - `RedSurfFocus` styling like every other picker, plus the one deliberate deviation
 * from it (Opus consultation): a static crescent tick on the focused row's leading edge instead
 * of a plain bar, carrying the portal's own motif into the part of the UI the user stares at
 * while the menu is open. Scoped to this menu only, not a restyle of every picker in the app. */
@Composable
private fun TeleportLiveRow(label: String, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    Surface(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused },
        shape = RedSurfFocus.shape(8.dp),
        colors = RedSurfFocus.rowColors(),
        scale = RedSurfFocus.scale(),
        border = RedSurfFocus.border(),
        glow = RedSurfFocus.glow(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(14.dp)) {
                if (isFocused) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            color = Accent,
                            startAngle = -60f,
                            sweepAngle = 300f,
                            useCenter = false,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(label, style = RedSurfType.rowTitle, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A grey, unfocusable row - the exact same visual shape as [TeleportLiveRow] minus the
 * `Surface`/focus/click, matching `SettingsScreen.kt`'s own `GreyRowContent` convention: the
 * D-pad skips it entirely, there is nothing to press. */
@Composable
private fun TeleportGreyRow(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(24.dp))
        Text(label, style = RedSurfType.rowTitle, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
