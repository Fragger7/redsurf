package com.redsurf.tv.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The branded loading spinner (AGENTS.md Branding entry, 2026-09-16) - replaces the generic
 * Material `CircularProgressIndicator` on the app's own loading screen (`MainActivity.kt`). An
 * open arc, not a full ring, swept with a gradient tail so it reads as a wave crest curling
 * around rather than a dial spinning - the same visual language as the mark's own crescent wave,
 * not a stock spinner with the brand color dropped in. Deliberately simple (one Canvas, one
 * animated rotation) - a first version, not a final polish pass; a good place to revisit once
 * the app has more loading moments than just cold start (OTA download, playlist import both still
 * use plain text per AGENTS.md's backlog).
 */
@Composable
fun WaveSpinner(modifier: Modifier = Modifier, size: Dp = 56.dp, strokeWidth: Dp = 5.dp) {
    val transition = rememberInfiniteTransition(label = "wave-spinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave-spinner-rotation",
    )
    Canvas(modifier = modifier.size(size)) {
        rotate(rotation) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
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
}
