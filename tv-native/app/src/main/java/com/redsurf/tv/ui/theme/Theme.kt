package com.redsurf.tv.ui.theme

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val RedSurfColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = TextPrimary,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,
)

/**
 * Wraps content in the RedSurf palette + the default tv-material3 typography, which is already
 * sized for ~3 m TV viewing (see docs/vision/UI_SPEC.md #8 — never shrink it, never hardcode sp).
 */
@Composable
fun RedSurfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RedSurfColorScheme,
        content = content,
    )
}

/**
 * Overscan safe margin (UI_SPEC.md #8), applied once at each screen root. Android TV's rule is
 * 5% per edge: on this 960x540dp canvas that's 48dp horizontally and 27dp vertically. The
 * earlier 48dp-all-round version spent 96 of only 540 vertical dp on margin, which is a large
 * part of why so few rows fit; 32dp vertical keeps a little headroom over the 27dp minimum.
 */
fun Modifier.tvSafeArea(): Modifier = this.padding(horizontal = 48.dp, vertical = 32.dp)
