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
 * Wraps content in the RedSurf palette + tv-material3's type scale in Crest's face (Type.kt).
 */
@Composable
fun RedSurfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RedSurfColorScheme,
        typography = CrestTypography,
        content = content,
    )
}

/**
 * Overscan safe margin (UI_SPEC.md #8), applied once at each screen root. Crest's margins
 * (design-language, 2026-10-20): 24dp sides, 14dp top/bottom - below Android TV's 5% guideline,
 * deliberately, since every modern panel this app targets shows the full frame and the guide's
 * row count is the scarce resource. Raise it here if a set ever crops the edges.
 */
fun Modifier.tvSafeArea(): Modifier = this.padding(horizontal = 24.dp, vertical = 14.dp)
