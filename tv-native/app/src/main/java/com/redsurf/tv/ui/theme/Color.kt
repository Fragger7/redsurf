package com.redsurf.tv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The only place a hex color literal may appear in this app (see docs/plans/PHASE_1.md #1.1).
 *
 * **Crest + Surf, 2026-10-26** (`docs/vision/design-language/RESEARCH.md` "A. Crest", decided by
 * the user 2026-10-20): a theme is a token set, not a re-skin, so a later Graphite/Surfglass theme
 * replaces [CrestPalette] and nothing else. The top-level names below are kept as aliases so every
 * existing call site compiles unchanged. Red is rationed: focus fill, active nav pill, now-line,
 * LIVE tag, progress, category tick, exit countdown - nothing decorative.
 */
object CrestPalette {
    val Background = Color(0xFF0A0A0B)
    /** The guide's channel-label column and list panels. */
    val Panel = Color(0xFF121214)
    /** A programme cell / raised card. */
    val Cell = Color(0xFF161618)
    /** The focused row's band. */
    val FocusRow = Color(0xFF202023)
    val Aired = Color(0xFF111113)
    val NoData = Color(0xFF0F0F11)
    val Accent = Color(0xFFDC2626)
    val TextPrimary = Color(0xFFF5F5F4)
    val TextSecondary = Color(0xFFA8A29E)
    val TextTertiary = Color(0xFF6B6763)
    /** Toasts are inverse: off-white pill, black text. */
    val ToastFill = Color(0xFFF5F5F4)
    val ToastText = Color(0xFF0A0A0B)
    /** Surf backdrop stops (calm screens only). */
    val SurfInner = Color(0xFF4A0B11)
    val SurfMid = Color(0xFF1C0709)
    val SurfOuter = Color(0xFF07070A)
}

val Background = CrestPalette.Background
val Surface = CrestPalette.Cell
val SurfaceRaised = CrestPalette.FocusRow
val Accent = CrestPalette.Accent
val TextPrimary = CrestPalette.TextPrimary
val TextSecondary = CrestPalette.TextSecondary
val TextTertiary = CrestPalette.TextTertiary
val SurfacePanel = CrestPalette.Panel

/** EPG_GRID_REDESIGN.md G.6 - the Guide grid's focused-row band. */
val SurfaceBand = CrestPalette.FocusRow
