package com.redsurf.tv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The only place a hex color literal may appear in this app (see docs/plans/PHASE_1.md #1.1).
 * Values from docs/vision/UI_SPEC.md #1.
 */
val Background = Color(0xFF09090B)
val Surface = Color(0xFF18181B)
val SurfaceRaised = Color(0xFF27272A)
val Accent = Color(0xFFDC2626)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA1A1AA)

/** EPG_GRID_REDESIGN.md G.6 - the Guide grid's focused-row band: one step above [Background] so
 * the channel identity stays visible while the cursor is hours to the right, without competing
 * with the [Accent] cell cursor the way [SurfaceRaised] would. */
val SurfaceBand = Color(0xFF151518)
