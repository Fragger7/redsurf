package com.redsurf.tv.ui.theme

import androidx.compose.ui.unit.dp

/**
 * EPG_GRID_REDESIGN.md G.2 - one named density scale, so screens pick the same value for the same
 * job instead of each deciding its own padding. Introduced for the Guide grid (which was showing
 * ~2 channels in a 476dp canvas because every layer above it spent generously) and deliberately
 * named by job rather than by screen so the rest of the app can adopt the same scale later.
 * 4dp base unit throughout.
 */
object RedSurfDensity {
    val GridRowHeight = 40.dp
    /** Separation between grid rows is a drawn hairline, not empty space. */
    val GridRowGap = 0.dp
    /** 180, not the consultation's 150: on real provider names ("ABC 28 (KXXX)") a 74dp name slot
     * ellipsized nearly every row; 106dp holds ~18 characters at 13sp, marquee covers the rest. The
     * timeline still fits six 30-minute columns - they're just ~5dp narrower each. */
    val GridLabelWidth = 180.dp
    val GridLogo = 26.dp
    val CellPadH = 6.dp
    val CellRadius = 3.dp
    val PanelRadius = 10.dp
    val ColumnGap = 12.dp
    val CategoriesWidth = 200.dp
    val HeroExpanded = 136.dp
    val HeroCollapsed = 56.dp
    val GridHeaderHeight = 24.dp
    val Hairline = 1.dp
}
