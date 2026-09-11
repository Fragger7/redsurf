package com.redsurf.tv.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceGlow
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.Glow

/**
 * The two-state focus model from docs/vision/UI_SPEC.md #2. Every focusable Surface(onClick=...)
 * in the app must configure all four of these — an element with no visible focus state is a bug,
 * not a style choice.
 *
 * - Focused (where the D-pad cursor is): a red ring + glow + slight scale-up.
 * - Selected (the active tab, the playing channel): filled red, no glow.
 * Both can be true at once (a focused, active tab) and must look right together — the glow/ring
 * from [border] and [glow] layers on top of whichever fill [colors] picked.
 */
object RedSurfFocus {

    @Composable
    fun border(): ClickableSurfaceBorder = ClickableSurfaceDefaults.border(
        focusedBorder = Border(border = BorderStroke(2.dp, Accent)),
    )

    @Composable
    fun glow(): ClickableSurfaceGlow = ClickableSurfaceDefaults.glow(
        focusedGlow = Glow(elevationColor = Accent.copy(alpha = 0.4f), elevation = 16.dp),
    )

    @Composable
    fun scale(): ClickableSurfaceScale = ClickableSurfaceDefaults.scale(
        focusedScale = 1.04f,
    )

    @Composable
    fun colors(selected: Boolean = false): ClickableSurfaceColors = ClickableSurfaceDefaults.colors(
        containerColor = if (selected) Accent else Surface,
        contentColor = TextPrimary,
        focusedContainerColor = if (selected) Accent else SurfaceRaised,
        focusedContentColor = TextPrimary,
    )
}
