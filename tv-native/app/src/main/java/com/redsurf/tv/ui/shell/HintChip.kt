package com.redsurf.tv.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.redsurf.tv.ui.theme.CrestPalette
import com.redsurf.tv.ui.theme.RedSurfType

/**
 * The one transient hint surface (nav feedback, 2026-10-26): "Press BACK again to exit", the
 * Teleport tip, the hidden-menu tip. Crest's inverse toast - off-white block, black text, a red
 * tick - bottom-centre, never focusable, so it can never take focus away from where the user is.
 * Kept composed and faded by alpha in a graphics layer (no re-layout when it comes and goes); the
 * last text stays set through the fade-out so it doesn't blank mid-fade.
 *
 * [countdownMs] (the exit confirm) draws Crest's countdown bar along the bottom edge, running out
 * over that time; [seq] restarts it when the same text is shown again. Draw-phase only.
 */
@Composable
fun HintChip(text: String?, modifier: Modifier = Modifier, countdownMs: Long? = null, seq: Int = 0) {
    val remaining = remember(seq) { Animatable(1f) }
    LaunchedEffect(seq, countdownMs) {
        if (countdownMs != null) remaining.animateTo(0f, tween(countdownMs.toInt(), easing = LinearEasing))
    }
    var shown by remember { mutableStateOf("") }
    if (text != null) shown = text
    val alpha by animateFloatAsState(if (text != null) 1f else 0f, tween(180), label = "hint")
    if (shown.isEmpty()) return
    Row(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .background(CrestPalette.ToastFill, RectangleShape)
            .drawBehind {
                if (countdownMs == null) return@drawBehind
                val h = 3.dp.toPx()
                drawRect(CrestPalette.Accent, Offset(0f, size.height - h), Size(size.width * remaining.value, h))
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(30.dp).background(CrestPalette.Accent))
        Text(
            shown,
            style = RedSurfType.label,
            color = CrestPalette.ToastText,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}
