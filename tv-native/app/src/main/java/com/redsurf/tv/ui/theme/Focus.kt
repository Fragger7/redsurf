package com.redsurf.tv.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceGlow
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ClickableSurfaceShape
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
 *
 * Two fills, deliberately: [colors] is the filled-red "selected" for standalone pills (the nav's
 * active tab). [rowColors] is for rows inside a list: transparent at rest so they sit flat in
 * their panel, raised when focused or selected — a selected *row* shows its state with an accent
 * left bar drawn by the row itself (UI_SPEC.md #2 allows "filled pill or left bar"), not a
 * second full-red block competing with the nav's. The visual pass found the earlier all-red
 * selected group row was the loudest thing on the screen for the least important state.
 */
object RedSurfFocus {

    /** Rounded-rect for rows/cards; use [pillShape] for nav pills and buttons. */
    @Composable
    fun shape(radius: Dp = 10.dp): ClickableSurfaceShape = ClickableSurfaceDefaults.shape(
        shape = RoundedCornerShape(radius),
    )

    @Composable
    fun pillShape(): ClickableSurfaceShape = ClickableSurfaceDefaults.shape(
        shape = RoundedCornerShape(50),
    )

    @Composable
    fun rowColors(selected: Boolean = false, resting: Color = Color.Transparent): ClickableSurfaceColors =
        ClickableSurfaceDefaults.colors(
            containerColor = if (selected) SurfaceRaised else resting,
            contentColor = TextPrimary,
            focusedContainerColor = SurfaceRaised,
            focusedContentColor = TextPrimary,
        )

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
