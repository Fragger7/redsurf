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

/** The 48dp overscan safe margin (UI_SPEC.md #8), applied once at each screen root. */
fun Modifier.tvSafeArea(): Modifier = this.padding(48.dp)
