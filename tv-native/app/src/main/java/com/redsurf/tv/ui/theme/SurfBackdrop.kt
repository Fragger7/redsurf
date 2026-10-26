package com.redsurf.tv.ui.theme

import android.graphics.Bitmap
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.random.Random

/**
 * Surf's backdrop (design-language RESEARCH.md, "Crest + Surf" decided 2026-10-20): a static
 * radial red-to-black wash with faint grain, for calm screens only - Home, Settings, onboarding,
 * loading. Never the guide or the player.
 *
 * Cost is the point. The gradient and grain are baked once into a small bitmap (half the
 * screen's pixels per side - at 1/4 the scaled-up grain read as blotches on the TV) the first
 * time the screen is sized; every frame after that is one scaled image draw - no shader evaluation, no per-frame noise. The grain also
 * dithers the gradient, which on an 8-bit panel would otherwise band. [show] is read at draw time,
 * so flipping it (AppShell's destination) only re-draws - it never re-bakes the bitmap.
 */
fun Modifier.surfBackdrop(show: () -> Boolean = { true }): Modifier = drawWithCache {
    val w = (size.width / 2).toInt().coerceAtLeast(1)
    val h = (size.height / 2).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint().apply {
        shader = RadialGradient(
            w * 0.3f, h * 0.2f, maxOf(w, h) * 0.95f,
            intArrayOf(CrestPalette.SurfInner.toArgb(), CrestPalette.SurfMid.toArgb(), CrestPalette.SurfOuter.toArgb()),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    val rnd = Random(7)
    for (i in px.indices) {
        val c = px[i]
        // +/-2% luminance grain: the first pass's +/-7% read as TV static on the device.
        val n = (rnd.nextInt(11) - 5)
        val r = ((c shr 16 and 0xFF) + n).coerceIn(0, 255)
        val g = ((c shr 8 and 0xFF) + n).coerceIn(0, 255)
        val b = ((c and 0xFF) + n).coerceIn(0, 255)
        px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    bmp.setPixels(px, 0, w, 0, 0, w, h)
    val image = bmp.asImageBitmap()
    val dst = IntSize(size.width.toInt(), size.height.toInt())
    onDrawBehind {
        if (show()) drawImage(image, IntOffset.Zero, IntSize(w, h), IntOffset.Zero, dst, filterQuality = FilterQuality.Low)
    }
}
